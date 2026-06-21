package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader

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

    suspend fun parseAndSavePlaylist(name: String, m3uUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val content: String = if (m3uUrl == "built_in_demo") {
                    "" // Don't download demo
                } else {
                    val request = Request.Builder().url(m3uUrl).build()
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
                    channelsList.addAll(parseM3UContent(content, playlistId))
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

    private fun parseM3UContent(content: String, playlistId: Int): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(StringReader(content))
        var currentItem: ChannelTemp? = null

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                val info = trimmed.substring(8)
                val name = info.substringAfterLast(",").trim()
                val groupTitle = parseAttribute(info, "group-title") ?: "Uncategorized"
                val logoUrl = parseAttribute(info, "tvg-logo") ?: parseAttribute(info, "logo")
                currentItem = ChannelTemp(name, logoUrl, groupTitle)
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                currentItem?.let {
                    channels.add(
                        Channel(
                            playlistId = playlistId,
                            name = it.name.ifEmpty { "Channel ${channels.size + 1}" },
                            streamUrl = trimmed,
                            logoUrl = it.logo,
                            category = it.group.ifEmpty { "Uncategorized" }
                        )
                    )
                }
                currentItem = null
            }
        }
        return channels
    }

    private data class ChannelTemp(val name: String, val logo: String?, val group: String)

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
