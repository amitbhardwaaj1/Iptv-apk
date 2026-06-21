package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.RequestMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.Channel
import com.example.data.IPTVDatabase
import com.example.data.IPTVRepository
import com.example.data.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IPTVViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: IPTVRepository

    // Playlists Flow
    val playlists: StateFlow<List<Playlist>>
    val activePlaylist = MutableStateFlow<Playlist?>(null)

    // Raw & Filtered Channels Flow
    private val _rawChannels = MutableStateFlow<List<Channel>>(emptyList())
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")

    private val _categories = MutableStateFlow<List<String>>(listOf("All"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    // Favorite channels
    val favoriteChannels: StateFlow<List<Channel>>

    // Combined filtered channel output
    private val _filteredChannels = MutableStateFlow<List<Channel>>(emptyList())
    val filteredChannels: StateFlow<List<Channel>> = _filteredChannels.asStateFlow()

    // Playing state
    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    // Import status
    val isImporting = MutableStateFlow(false)
    val importError = MutableStateFlow<String?>(null)

    // Player instance
    val player: ExoPlayer by lazy {
        // Add a default HTTP data source with a friendly User-Agent to improve compatibility
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("User-Agent" to "PulseIPTV/1.0"))
        val dataSourceFactory = DefaultDataSource.Factory(getApplication(), httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(getApplication())
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Player Visual States
    val isManualLandscape = MutableStateFlow(false)
    val isLocked = MutableStateFlow(false)
    val isMuted = MutableStateFlow(false)
    
    // Resize Modes: Fit (0), Fill (3), Zoom (4) in Media3 ResizeMode
    val resizeMode = MutableStateFlow(0) // 0 = FIT, 3 = FILL, 4 = ZOOM

    init {
        val database = IPTVDatabase.getDatabase(application)
        repository = IPTVRepository(database)

        playlists = repository.allPlaylists.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        favoriteChannels = repository.favoriteChannels.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Observe raw channels when activePlaylist edits
        viewModelScope.launch {
            activePlaylist.collectLatest { playlist ->
                if (playlist != null) {
                    repository.getChannelsForPlaylist(playlist.id).collectLatest { channelList ->
                        _rawChannels.value = channelList
                        
                        // Dynamically update categories
                        val distinctGroups = channelList.map { it.category }.distinct().sorted()
                        _categories.value = listOf("All") + distinctGroups
                        
                        // Select "All" Category by default on playlist change
                        if (selectedCategory.value !in _categories.value) {
                            selectedCategory.value = "All"
                        }
                    }
                } else {
                    _rawChannels.value = emptyList()
                    _categories.value = listOf("All")
                }
            }
        }

        // Combine queries and categories to generate filtered channel list
        viewModelScope.launch {
            combine(_rawChannels, searchQuery, selectedCategory) { list, query, category ->
                var filtered = list
                if (query.isNotEmpty()) {
                    filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
                }
                if (category != "All") {
                    filtered = filtered.filter { it.category == category }
                }
                filtered
            }.collectLatest {
                _filteredChannels.value = it
            }
        }

        // Observe playlists list to set the first active one (no automatic demo import)
        viewModelScope.launch {
            playlists.collect { list ->
                if (list.isNotEmpty()) {
                    val active = list.firstOrNull { it.isActive } ?: list.first()
                    if (activePlaylist.value?.id != active.id) {
                        activePlaylist.value = active
                        // If no channel is currently playing, set the first one of the playlist
                        viewModelScope.launch(Dispatchers.IO) {
                            val playlistChannels = repository.getChannelsListForPlaylist(active.id)
                            if (_selectedChannel.value == null && playlistChannels.isNotEmpty()) {
                                launch(Dispatchers.Main) {
                                    selectChannel(playlistChannels.first())
                                }
                            }
                        }
                    }
                } else {
                    activePlaylist.value = null
                }
            }
        }
    }

    private fun importPredefinedDemo() {
        viewModelScope.launch {
            isImporting.value = true
            val success = repository.parseAndSavePlaylist("Demo Channels", "built_in_demo")
            isImporting.value = false
            if (!success) {
                importError.value = "Failed to load built-in streams."
            }
        }
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel
        try {
            val builder = MediaItem.Builder().setUri(channel.streamUrl)

            // Apply per-channel HTTP headers if present
            val headers = mutableMapOf<String, String>()
            channel.userAgent?.let { headers["User-Agent"] = it }
            channel.referer?.let { headers["Referer"] = it }
            channel.origin?.let { headers["Origin"] = it }
            channel.cookie?.let { headers["Cookie"] = it }
            if (headers.isNotEmpty()) {
                val reqMeta = RequestMetadata.Builder().setHttpRequestHeaders(headers).build()
                builder.setRequestMetadata(reqMeta)
            }

            // Configure ClearKey DRM if requested
            if (channel.drmType?.equals("clearkey", ignoreCase = true) == true && !channel.drmLicenseKey.isNullOrBlank()) {
                try {
                    val drmBuilder = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    val lic = channel.drmLicenseKey.trim()
                    if (lic.startsWith("http")) {
                        drmBuilder.setLicenseUri(Uri.parse(lic))
                    } else if (lic.startsWith("{") || lic.startsWith("[")) {
                        // Inline JSON license - encode as data URI
                        val encoded = Base64.encodeToString(lic.toByteArray(), Base64.NO_WRAP)
                        drmBuilder.setLicenseUri(Uri.parse("data:application/json;base64,$encoded"))
                    }
                    builder.setDrmConfiguration(drmBuilder.build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val mediaItem = builder.build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun changePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.setActivePlaylist(playlistId)
            val updatedList = repository.getAllPlaylistsList()
            val active = updatedList.firstOrNull { it.id == playlistId }
            activePlaylist.value = active

            // Load and automatically play the first channel of the newly selected playlist
            val chans = repository.getChannelsListForPlaylist(playlistId)
            if (chans.isNotEmpty()) {
                selectChannel(chans.first())
            } else {
                _selectedChannel.value = null
                player.stop()
            }
        }
    }

    fun addNewPlaylist(name: String, m3uUrl: String) {
        viewModelScope.launch {
            isImporting.value = true
            importError.value = null
            val success = repository.parseAndSavePlaylist(name, m3uUrl)
            isImporting.value = false
            if (success) {
                importError.value = null
            } else {
                importError.value = "Failed to parse M3U. Please make sure the URL is valid and reachable."
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            if (activePlaylist.value?.id == playlist.id) {
                _selectedChannel.value = null
                player.stop()
            }
            repository.deletePlaylist(playlist)
            val currentList = repository.getAllPlaylistsList()
            if (currentList.isNotEmpty()) {
                activePlaylist.value = currentList.firstOrNull { it.isActive } ?: currentList.first()
                val chans = repository.getChannelsListForPlaylist(activePlaylist.value!!.id)
                if (chans.isNotEmpty()) {
                    selectChannel(chans.first())
                }
            } else {
                activePlaylist.value = null
            }
        }
    }

    fun deleteDemoPlaylists() {
        viewModelScope.launch {
            repository.deleteDemoPlaylists()
            val currentList = repository.getAllPlaylistsList()
            if (currentList.isNotEmpty()) {
                activePlaylist.value = currentList.firstOrNull { it.isActive } ?: currentList.first()
            } else {
                activePlaylist.value = null
            }
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val updatedFavoriteStatus = !channel.isFavorite
            repository.toggleFavorite(channel.id, updatedFavoriteStatus)
            
            // Sync with current playing channel if it is the one being favorited
            if (_selectedChannel.value?.id == channel.id) {
                _selectedChannel.value = _selectedChannel.value?.copy(isFavorite = updatedFavoriteStatus)
            }
            
            // Sync with raw channels list
            val updatedRaw = _rawChannels.value.map {
                if (it.id == channel.id) it.copy(isFavorite = updatedFavoriteStatus) else it
            }
            _rawChannels.value = updatedRaw
        }
    }

    fun toggleMute() {
        isMuted.value = !isMuted.value
        player.volume = if (isMuted.value) 0f else 1f
    }

    fun cycleResizeMode() {
        // Media3 tracking: Fit (0), Fill (3), Zoom (4)
        val current = resizeMode.value
        resizeMode.value = when (current) {
            0 -> 3 // FILL
            3 -> 4 // ZOOM
            else -> 0 // FIT
        }
    }

    fun toggleOrientation() {
        isManualLandscape.value = !isManualLandscape.value
    }

    fun toggleLock() {
        isLocked.value = !isLocked.value
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
