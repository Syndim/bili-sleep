package org.syndim.bilisleep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import org.syndim.bilisleep.data.model.PlaylistItem
import org.syndim.bilisleep.ui.components.SleepTimerDialog
import org.syndim.bilisleep.ui.components.SleepTimerIndicator
import org.syndim.bilisleep.viewmodel.PlayerViewModel

/**
 * Represents a group of playlist items from the same video
 */
private data class VideoGroup(
    val bvid: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val parts: List<PlaylistItemWithIndex>,
    val isMultiPart: Boolean
)

/**
 * PlaylistItem with its original index in the playlist
 */
private data class PlaylistItemWithIndex(
    val item: PlaylistItem,
    val playlistIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val showSleepTimerDialog by viewModel.showSleepTimerDialog.collectAsState()
    
    val currentItem = playerState.currentItem
    
    // Sleep timer dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentSettings = playerState.sleepTimer,
            onSaveSettings = viewModel::saveSleepTimerSettings,
            onDismiss = viewModel::hideSleepTimerDialog
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Sleep timer indicator (when active)
                    if (playerState.sleepTimer.enabled) {
                        SleepTimerIndicator(
                            remainingMillis = playerState.sleepTimer.remainingMillis,
                            onCancel = viewModel::stopSleepTimer,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    // Sleep timer settings button (always visible)
                    IconButton(onClick = viewModel::showSleepTimerDialog) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer Settings"
                        )
                    }
                },
                windowInsets = WindowInsets(0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentItem != null) {
                // Compute grouped items outside LazyColumn
                val upNextItems = playerState.playlist.drop(playerState.currentIndex + 1)
                val groupedItems = remember(upNextItems, playerState.currentIndex) {
                    groupPlaylistItems(upNextItems, playerState.currentIndex + 1)
                }
                
                // Scrollable content: cover, title, and playlist
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover image and title section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Cover image
                            AsyncImage(
                                model = currentItem.coverUrl,
                                contentDescription = currentItem.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Title
                            Text(
                                text = currentItem.getDisplayTitle(),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Author
                            Text(
                                text = currentItem.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Playlist section (if more than 1 item)
                    if (playerState.playlist.size > 1) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp)
                            )
                            
                            Text(
                                text = "Up Next (${upNextItems.size} items)",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        items(
                            items = groupedItems,
                            key = { it.bvid }
                        ) { group ->
                            VideoGroupItem(
                                group = group,
                                onPlayPart = { playlistIndex ->
                                    viewModel.playAtIndex(playlistIndex)
                                }
                            )
                        }
                    }
                }
                
                // Fixed bottom section: progress bar and controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress bar
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Slider(
                            value = playerState.progress,
                            onValueChange = { viewModel.seekToPercent(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(playerState.currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTime(playerState.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous
                        IconButton(
                            onClick = viewModel::playPrevious,
                            enabled = playerState.hasPrevious,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(24.dp))
                        
                        // Play/Pause
                        FilledIconButton(
                            onClick = viewModel::togglePlayPause,
                            modifier = Modifier.size(72.dp)
                        ) {
                            if (playerState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(24.dp))
                        
                        // Next
                        IconButton(
                            onClick = viewModel::playNext,
                            enabled = playerState.hasNext,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Error snackbar
    playerState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error and clear it
            viewModel.clearError()
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Group playlist items by video (bvid)
 */
private fun groupPlaylistItems(items: List<PlaylistItem>, startIndex: Int): List<VideoGroup> {
    val groups = mutableListOf<VideoGroup>()
    var currentIndex = startIndex
    
    items.groupBy { it.bvid }.forEach { (bvid, parts) ->
        val firstPart = parts.first()
        val partsWithIndex = parts.map { part ->
            val index = currentIndex
            currentIndex++
            PlaylistItemWithIndex(part, index)
        }
        
        groups.add(
            VideoGroup(
                bvid = bvid,
                title = firstPart.title,
                author = firstPart.author,
                coverUrl = firstPart.coverUrl,
                parts = partsWithIndex,
                isMultiPart = parts.size > 1
            )
        )
    }
    
    return groups
}

@Composable
private fun VideoGroupItem(
    group: VideoGroup,
    onPlayPart: (Int) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column {
        // Main video row
        ListItem(
            headlineContent = {
                Text(
                    text = group.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                val partsInfo = if (group.isMultiPart) {
                    "${group.parts.size} parts • ${group.author}"
                } else {
                    "${group.author} • ${formatTime(group.parts.first().item.duration * 1000)}"
                }
                Text(
                    text = partsInfo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                AsyncImage(
                    model = group.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            },
            trailingContent = {
                if (group.isMultiPart) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (group.isMultiPart) {
                        isExpanded = !isExpanded
                    } else {
                        onPlayPart(group.parts.first().playlistIndex)
                    }
                }
        )
        
        // Expandable parts list for multi-part videos
        if (group.isMultiPart) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    group.parts.forEach { partWithIndex ->
                        PartItemRow(
                            item = partWithIndex.item,
                            onClick = { onPlayPart(partWithIndex.playlistIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartItemRow(
    item: PlaylistItem,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = item.partTitle ?: "P${item.partNumber}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        supportingContent = {
            Text(
                text = formatTime(item.duration * 1000),
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P${item.partNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    )
}
