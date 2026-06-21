package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import org.json.JSONArray
import org.json.JSONObject

class IPTVRepository(private val database: IPTVDatabase) {
    private val playlistDao = database.playlistDao()
    private val channelDao = database.channelDao()
    private val client by lazy { OkHttpClient() }

    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val favoriteChannels: Flow<List<Channel>> = channelDao.getFavoriteChannels()

    suspend fun getAllPlaylistsList(): List<Playlist> = playlistDao.getAllPlaylistsList()

    fun getChannelsForPlaylist(playlistId: Int): Flow<List<Channel>> {
        return channelDao.getChannelsForPlaylist(playlistId)
    }

    suspend fun getChannelsListForPlaylist(playlistId: Int): List<Channel> {
        return channelDao.getChannelsForPlaylistList(playlistId)
    }

    suspend fun getActivePlaylist(): Playlist? {
        return playlistDao.getActivePlaylist()
    }

    suspend fun setActivePlaylist(playlistId: Int) {
        playlistDao.setActivePlaylist(playlistId)
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
        // Set another active playlist if we deleted the current active one
        val remaining = playlistDao.getAllPlaylistsList()
        if (remaining.isNotEmpty() && playlist.isActive) {
            playlistDao.setActivePlaylist(remaining.first().id)
        }
    }

    suspend fun toggleFavorite(channelId: Int, isFavorite: Boolean) {
        channelDao.updateFavoriteStatus(channelId, isFavorite)
    }

    suspend fun deleteChannelsForPlaylist(playlistId: Int) {
        channelDao.deleteChannelsForPlaylist(playlistId)
    }

    suspend fun deleteDemoPlaylists() {
        val all = playlistDao.getAllPlaylistsList()
        val demos = all.filter { it.url == "built_in_demo" }
        demos.forEach { demo ->
            channelDao.deleteChannelsForPlaylist(demo.id)
            playlistDao.deletePlaylist(demo)
        }
        // If the demo was active, ensure there's still a sensible active playlist
        val remaining = playlistDao.getAllPlaylistsList()
        if (remaining.isNotEmpty()) {
            playlistDao.setActivePlaylist(remaining.first().id)
        }
    }

    suspend fun parseAndSavePlaylist(name: String, m3uUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val content: String = if (m3uUrl == "built_in_demo") {
                    "" // Don't download demo
                } else {
                    val builder = Request.Builder().url(m3uUrl)
                    // Add a friendly User-Agent and special Referer for certain worker-hosted endpoints
                    builder.header("User-Agent", "PulseIPTV/1.0")
                    if (m3uUrl.contains("streamstar18.workers.dev")) {
                        builder.header("Referer", "https://noisy-truth-6766.streamstar18.workers.dev")
                    }
                    val request = builder.build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext false
                        response.body?.string() ?: return@withContext false
                    }
                }

                // Deactivate all first if we want this to be active
                val isFirst = playlistDao.getAllPlaylistsList().isEmpty()
                val playlistId = playlistDao.insertPlaylist(
                    Playlist(name = name, url = m3uUrl, isActive = isFirst)
                ).toInt()

                val channelsList = mutableListOf<Channel>()
                if (m3uUrl == "built_in_demo") {
                    channelsList.addAll(getDemoChannels(playlistId))
                } else {
                    val trimmed = content.trim()
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        channelsList.addAll(parseJsonContent(content, playlistId))
                    } else {
                        channelsList.addAll(parseM3UContent(content, playlistId))
                    }
                }

                if (channelsList.isNotEmpty()) {
                    channelDao.insertChannels(channelsList)
                    true
                } else {
                    // Clean up of empty playlists
                    playlistDao.deletePlaylist(Playlist(id = playlistId, name = name, url = m3uUrl))
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun parseJsonContent(content: String, playlistId: Int): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val trimmed = content.trim()
            val array = if (trimmed.startsWith("{")) {
                // Might be an object with a "channels" array
                val obj = JSONObject(trimmed)
                when {
                    obj.has("channels") -> obj.getJSONArray("channels")
                    obj.has("items") -> obj.getJSONArray("items")
                    else -> JSONArray().also { // wrap single object into array
                        val a = JSONArray()
                        a.put(obj)
                    }
                }
            } else {
                JSONArray(trimmed)
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = when {
                    item.has("name") -> item.getString("name")
                    item.has("title") -> item.getString("title")
                    item.has("channel") -> item.getString("channel")
                    else -> "Channel ${i + 1}"
                }

                val stream = when {
                    item.has("streamUrl") -> item.getString("streamUrl")
                    item.has("url") -> item.getString("url")
                    item.has("stream") -> item.getString("stream")
                    else -> null
                } ?: continue

                val logo = when {
                    item.has("logo") -> item.optString("logo", null)
                    item.has("tvg-logo") -> item.optString("tvg-logo", null)
                    else -> null
                }

                val category = when {
                    item.has("category") -> item.optString("category", "Uncategorized")
                    item.has("group") -> item.optString("group", "Uncategorized")
                    else -> "Uncategorized"
                }

                // Optional header/drm fields in JSON
                val userAgent = when {
                    item.has("userAgent") -> item.optString("userAgent", null)
                    item.has("User-Agent") -> item.optString("User-Agent", null)
                    else -> null
                }
                val referer = item.optString("referer", item.optString("Referer", null))
                val origin = item.optString("origin", null)
                val cookie = item.optString("cookie", null)
                val drmType = item.optString("drmType", item.optString("license_type", null))
                val drmLicenseKey = item.optString("drmLicenseKey", item.optString("license_key", null))
                val manifestType = item.optString("manifestType", item.optString("manifest_type", null))

                channels.add(
                    Channel(
                        playlistId = playlistId,
                        name = name,
                        streamUrl = stream,
                        logoUrl = logo,
                        category = category,
                        position = i,
                        userAgent = userAgent,
                        referer = referer,
                        origin = origin,
                        cookie = cookie,
                        drmType = drmType,
                        drmLicenseKey = drmLicenseKey,
                        manifestType = manifestType
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    private fun parseM3UContent(content: String, playlistId: Int): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(StringReader(content))
        var currentItem: ChannelTemp? = null

        reader.forEachLine { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF:") -> {
                    val info = trimmed.substring(8)
                    val name = info.substringAfterLast(",").trim()
                    val groupTitle = parseAttribute(info, "group-title") ?: "Uncategorized"
                    val logoUrl = parseAttribute(info, "tvg-logo") ?: parseAttribute(info, "logo")
                    currentItem = ChannelTemp(name, logoUrl, groupTitle)
                }
                trimmed.startsWith("#KODIPROP:") -> {
                    // Example: #KODIPROP:inputstream.adaptive.manifest_type=mpd
                    val kv = trimmed.substringAfter(":").trim()
                    val (k, v) = kv.substringBefore("=").trim() to kv.substringAfter("=").trim()
                    currentItem = currentItem ?: ChannelTemp("", null, "Uncategorized")
                    when {
                        k.contains("manifest_type") -> currentItem.manifestType = v
                        k.contains("license_type") -> currentItem.drmType = v
                        k.contains("license_key") -> currentItem.drmLicenseKey = v
                        else -> {
                            // ignore other kodi props for now
                        }
                    }
                }
                trimmed.startsWith("#EXTVLCOPT:") -> {
                    val kv = trimmed.substringAfter(":").trim()
                    currentItem = currentItem ?: ChannelTemp("", null, "Uncategorized")
                    parseExtVlcOpt(kv, currentItem)
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    currentItem?.let {
                        // Determine manifest type from URL if not set
                        val manifest = it.manifestType ?: when {
                            trimmed.contains(".m3u8") -> "hls"
                            trimmed.contains(".mpd") -> "mpd"
                            trimmed.contains(".mp4") -> "progressive"
                            else -> null
                        }

                        channels.add(
                            Channel(
                                playlistId = playlistId,
                                name = it.name.ifEmpty { "Channel ${channels.size + 1}" },
                                streamUrl = trimmed,
                                logoUrl = it.logo,
                                category = it.group.ifEmpty { "Uncategorized" },
                                position = channels.size,
                                userAgent = it.userAgent,
                                referer = it.referer,
                                origin = it.origin,
                                cookie = it.cookie,
                                drmType = it.drmType,
                                drmLicenseKey = it.drmLicenseKey,
                                manifestType = manifest
                            )
                        )
                    }
                    currentItem = null
                }
                else -> {
                    // ignore other lines
                }
            }
        }
        return channels
    }

    private data class ChannelTemp(
        var name: String,
        var logo: String?,
        var group: String,
        var userAgent: String? = null,
        var referer: String? = null,
        var origin: String? = null,
        var cookie: String? = null,
        var drmType: String? = null,
        var drmLicenseKey: String? = null,
        var manifestType: String? = null
    )

    private fun parseExtVlcOpt(kv: String, item: ChannelTemp) {
        // EXTVLCOPT may contain tokens like "http-user-agent=..." or "User-Agent=..." or "http-referrer=..."
        val parts = kv.split(Regex("[ \\;]+"))
        for (p in parts) {
            val token = p.trim()
            if (!token.contains("=")) continue
            val key = token.substringBefore("=").trim()
            val value = token.substringAfter("=").trim().trim('"')
            when (key.lowercase()) {
                "http-user-agent", "user-agent", "http-useragent" -> item.userAgent = value
                "http-referrer", "referrer", "referer" -> item.referer = value
                "origin" -> item.origin = value
                "cookie", "http-cookie" -> item.cookie = value
                else -> {
                    // unknown option - ignore
                }
            }
        }
    }

    private fun parseAttribute(info: String, name: String): String? {
        val key = "$name=\""
        val startIndex = info.indexOf(key)
        if (startIndex == -1) return null
        val valStart = startIndex + key.length
        val endIndex = info.indexOf("\"", valStart)
        if (endIndex == -1) return null
        return info.substring(valStart, endIndex)
    }

    private fun getDemoChannels(playlistId: Int): List<Channel> {
        return listOf(
            Channel(
                playlistId = playlistId,
                name = "Big Buck Bunny (Animation)",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                logoUrl = "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?q=80&w=250",
                category = "Movies"
            ),
            Channel(
                playlistId = playlistId,
                name = "Sintel (Fantasy Drama)",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=250",
                category = "Movies"
            ),
            Channel(
                playlistId = playlistId,
                name = "Tears of Steel (Sci-Fi HLS)",
                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                logoUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?q=80&w=250",
                category = "HLS Live Feeds"
            ),
            Channel(
                playlistId = playlistId,
                name = "BipBop Adaptive HLS",
                streamUrl = "https://playertest.longtailvideo.com/adaptive/bipbop/bipbop.m3u8",
                logoUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=250",
                category = "HLS Live Feeds"
            ),
            Channel(
                playlistId = playlistId,
                name = "Elephant's Dream (Sci-Fi)",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                logoUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=250",
                category = "Movies"
            ),
            Channel(
                playlistId = playlistId,
                name = "Subaru Outback Nature Track",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutback.mp4",
                logoUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?q=80&w=250",
                category = "Nature"
            ),
            Channel(
                playlistId = playlistId,
                name = "For Bigger Blazes (Tech)",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=250",
                category = "Tech News"
            ),
            Channel(
                playlistId = playlistId,
                name = "For Bigger Escapes (Scenic)",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                logoUrl = "https://images.unsplash.com/photo-1542838132-92c53300491e?q=80&w=250",
                category = "Nature"
            ),
            Channel(
                playlistId = playlistId,
                name = "For Bigger Fun (Gamer)",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                logoUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?q=80&w=250",
                category = "Tech News"
            )
        )
    }
}
