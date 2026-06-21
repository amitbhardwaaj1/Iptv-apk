package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Channel
import com.example.data.Playlist
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import com.example.viewmodel.IPTVViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val activePlaylist by viewModel.activePlaylist.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLandscape by viewModel.isManualLandscape.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }

    // If landscape is active, make the video player full-viewport!
    if (isLandscape && selectedChannel != null) {
        VideoPlayerView(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Selector Bar at the Top of the Page
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { showPlaylistDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("playlist_selector_trigger"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = "Playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activePlaylist?.name ?: "No Playlists Connected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            actions = {
                // Quick info badge displaying total available channels
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text(
                        text = "${filteredChannels.size} Chs",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        )

        // 2. Sticky Video Player at the Top of the Screens
        if (selectedChannel != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(Color.Black)
            ) {
                VideoPlayerView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Category selector + search input elements
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Live Search Outline input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search channel / sports feed...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("channel_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontally scrolling category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    val chipTextColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .clickable { viewModel.selectedCategory.value = category }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("category_chip_$category"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = chipTextColor
                        )
                    }
                }
            }
        }

        Divider(color = DarkContrastDivider, thickness = 1.dp)

        // 4. Square Channels Grid (Preferred Mobile Layout)
        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Channels Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check your search query or connect a custom playlist in settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("channels_grid")
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    val isPlayingNow = selectedChannel?.id == channel.id
                    SquareChannelCard(
                        channel = channel,
                        isPlaying = isPlayingNow,
                        onChannelClick = { viewModel.selectChannel(channel) }
                    )
                }
            }
        }
    }

    // Playlist Selector modal Dialog
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = "Playlists",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Playlist", color = TextWhite)
                }
            },
            text = {
                if (playlists.isEmpty()) {
                    Text("No connected playlists found. Please register one in Settings.", color = TextMuted)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        playlists.forEach { playlist ->
                            val isActive = playlist.id == activePlaylist?.id
                            Card(
                                onClick = {
                                    viewModel.changePlaylist(playlist.id)
                                    showPlaylistDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else SlateSurface
                                ),
                                border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("playlist_item_${playlist.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextWhite,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = if (playlist.url == "built_in_demo") "Preloaded channels" else playlist.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = SlateSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SquareChannelCard(
    channel: Channel,
    isPlaying: Boolean,
    onChannelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Elegant system dark color palette matching Sophisticated Dark
    val gradientColors = remember(channel.id) {
        val colorPairs = listOf(
            listOf(Color(0xFF2D2B33), Color(0xFF211F26)),
            listOf(Color(0xFF25232A), Color(0xFF1C1B1F))
        )
        colorPairs[channel.id % colorPairs.size]
    }

    Box(
        modifier = modifier
            .aspectRatio(1f) // Perfect Square Aspect Ratio!
            .clip(RoundedCornerShape(16.dp)) // 16.dp matches rounded-2xl
            .background(
                if (isPlaying) {
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)))
                } else {
                    Brush.verticalGradient(gradientColors)
                }
            )
            .border(
                border = if (isPlaying) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(
                    1.dp,
                    DarkContrastDivider
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onChannelClick)
            .testTag("channel_card_${channel.id}"),
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail logo loading or initial visual display
        if (!channel.logoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isPlaying) 0.5f else 0.40f) // Fade behind so labels pop elegantly
            )
        }

        // Ambient cyber overlay gradient to ensure text readability under all icons
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.80f)
                            ),
                            startY = 40f
                        )
                    )
            )
        }

        // Floating Favorite Badge
        if (channel.isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .background(if (isPlaying) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite tag",
                    tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else Color.Red,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        // Live glow text/label aligned at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper center space: Channel Initial Character placeholder if logo is empty
            if (channel.logoUrl.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            // Lower space: Channel Name Label
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else TextWhite,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.15f
            )
        }
    }
}
