package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.Channel
import com.example.viewmodel.IPTVViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class GestureMode { Volume, Brightness }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player = viewModel.player
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val resizeModeState by viewModel.resizeMode.collectAsState()
    val isLandscape by viewModel.isManualLandscape.collectAsState()

    // Observe player states
    var isPlayingState by remember { mutableStateOf(false) }
    var isLoadingState by remember { mutableStateOf(true) }
    var isPlayerError by remember { mutableStateOf(false) }

    // Controls Visibility state
    var showControls by remember { mutableStateOf(true) }

    // Gesture indicator states
    var activeGesture by remember { mutableStateOf<GestureMode?>(null) }
    var gestureProgress by remember { mutableStateOf(0f) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    // Keep controls hidden after 4s
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Toggle screen orientation based on manual setting
    LaunchedEffect(isLandscape) {
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Monitor ExoPlayer state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoadingState = playbackState == Player.STATE_BUFFERING
                isPlayerError = playbackState == Player.STATE_IDLE && player.playerError != null
            }
        }
        player.addListener(listener)
        isPlayingState = player.isPlaying
        
        onDispose {
            player.removeListener(listener)
        }
    }

    // Reset lock/landscape when channel changes
    LaunchedEffect(selectedChannel) {
        isPlayerError = false
        isLoadingState = true
    }

    if (selectedChannel == null) {
        Box(
            modifier = modifier
                .background(Color.Black)
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "No Stream Loaded",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a channel to play",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .testTag("video_player_container")
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectVerticalDragGestures(
                        onDragStart = { showControls = !showControls },
                        onVerticalDrag = { _, _ -> },
                        onDragEnd = {}
                    )
                } else {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val isLeft = offset.x < size.width / 2f
                            activeGesture = if (isLeft) GestureMode.Brightness else GestureMode.Volume
                            
                            // Initialize progress
                            if (isLeft) {
                                val currentBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                gestureProgress = if (currentBrightness < 0) 0.5f else currentBrightness
                            } else {
                                val currentVolIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                gestureProgress = currentVolIndex / maxVolume
                            }
                            showControls = false
                        },
                        onDragEnd = { activeGesture = null },
                        onDragCancel = { activeGesture = null },
                        onVerticalDrag = { _, dragAmount ->
                            // dragAmount is negative when moving finger UP
                            val delta = -dragAmount / 400f
                            val newProgress = (gestureProgress + delta).coerceIn(0f, 1f)
                            gestureProgress = newProgress

                            if (activeGesture == GestureMode.Brightness) {
                                activity?.runOnUiThread {
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = newProgress
                                    activity.window.attributes = lp
                                }
                            } else if (activeGesture == GestureMode.Volume) {
                                val targetVol = (newProgress * maxVolume).roundToInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            }
                        }
                    )
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            }
    ) {
        // ExoPlayer Canvas Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    this.useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (resizeModeState) {
                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    4 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Indicator
        if (isLoadingState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Error Feedback State
        if (isPlayerError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Player error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unable to load stream URL.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = selectedChannel?.streamUrl ?: "",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.selectChannel(selectedChannel!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Retry", color = Color.Black)
                    }
                }
            }
        }

        // HUD overlay with gesture slider popups
        activeGesture?.let { mode ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (mode == GestureMode.Brightness) Icons.Default.Brightness5 else Icons.Default.VolumeUp,
                        contentDescription = mode.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { gestureProgress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .width(80.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        // Beautiful Interactive Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                // Top controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedChannel?.name ?: "IPTV Player",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "LIVE • ${selectedChannel?.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }

                    // Top Action Buttons
                    if (!isLocked) {
                        Row {
                            IconButton(
                                onClick = { viewModel.toggleFavorite(selectedChannel!!) },
                                modifier = Modifier.testTag("favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (selectedChannel?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (selectedChannel?.isFavorite == true) Color.Red else Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.cycleResizeMode() }) {
                                Icon(
                                    imageVector = when (resizeModeState) {
                                        3 -> Icons.Default.Fullscreen
                                        4 -> Icons.Default.ZoomIn
                                        else -> Icons.Default.FitScreen
                                    },
                                    contentDescription = "Resize Aspect Ratio",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Middle Buttons (Play/Pause & Lock/Unlock)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock Controls Button
                    IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock controls",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    if (!isLocked) {
                        // Play/Pause Main Button
                        IconButton(
                            onClick = {
                                if (isPlayingState) player.pause() else player.play()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause Toggle",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Mute button
                        IconButton(
                            onClick = { viewModel.toggleMute() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom row with Orientation settings
                if (!isLocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick hint stream URL snippet
                        Text(
                            text = (selectedChannel?.streamUrl ?: "").substringAfterLast("/").take(30),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray.copy(alpha = 0.6f),
                            maxLines = 1
                        )

                        // Manual Orientation Toggle
                        IconButton(
                            onClick = { viewModel.toggleOrientation() },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("orientation_toggle")
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenLockPortrait,
                                contentDescription = "Toggle manual landscape",
                                tint = if (isLandscape) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
