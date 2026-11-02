package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.ui.components.LocalNowPlaying
import com.vztechcore.vztune.ui.components.MediaThumbnail
import com.vztechcore.vztune.ui.components.ThumbnailShape
import com.vztechcore.vztune.ui.components.TrackSubtitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    state: MediaListUiState,
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onStartShuffle: () -> Unit,
    onShuffleOnce: () -> Unit,
    onStopShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nowPlaying = LocalNowPlaying.current
    var showSortSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var shuffleJob by remember { mutableStateOf<Job?>(null) }

    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 250
        }
    }

    val displayedTracks = remember(state.filteredTracks, state.sortOrder, state.manualOrder) {
        state.applySort(state.filteredTracks)
    }

    val canReorder = state.sortOrder == SortOrder.MANUAL

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (canReorder) onReorder(from.index, to.index)
    }

    // Shuffle Animation Loop
    LaunchedEffect(state.isShuffling) {
        if (state.isShuffling) {
            shuffleJob?.cancel()
            shuffleJob = launch {
                while (isActive) {
                    onShuffleOnce()
                    delay(150)
                }
            }
            delay(1000)
            onStopShuffle()
        } else {
            shuffleJob?.cancel()
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = state.sortOrder,
            onSelect = {
                onSortSelected(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                MediaListExpandedHeader(
                    state = state,
                    onBackClick = onBackClick,
                    onTabSelected = onTabSelected,
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onSortClick = { showSortSheet = true },
                    onShuffleToggle = {
                        if (state.isShuffling) onStopShuffle() else onStartShuffle()
                    }
                )
            }

            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                state.errorMessage != null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(state.errorMessage, color = Color.Red)
                    }
                }
                displayedTracks.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhuma faixa por aqui.", color = Color.White.copy(alpha = 0.6f))
                    }
                }
                else -> itemsIndexed(
                    items = displayedTracks,
                    key = { _, t -> t.id },
                ) { index, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val isPlaying = isCurrent && nowPlaying.isPlaying
                    val isPaused = isCurrent && !nowPlaying.isPlaying

                    if (canReorder) {
                        ReorderableItem(reorderableState, key = track.id) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            TrackRow(
                                track = track,
                                index = if (state.isAlbum) index + 1 else null,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                onClick = { onTrackClick(track) },
                                showDragHandle = true,
                                dragHandleModifier = Modifier.draggableHandle(),
                                modifier = Modifier.animateItemPlacement(
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ).background(Color.Black.copy(alpha = if (isDragging) 0.5f else 0f))
                            )
                        }
                    } else {
                        TrackRow(
                            track = track,
                            index = if (state.isAlbum) index + 1 else null,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            showDragHandle = false,
                            dragHandleModifier = Modifier,
                            modifier = Modifier
                        )
                    }
                }
            }

            item { MediaListFooter(state = state) }
        }

        AnimatedVisibility(
            visible = isCollapsed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            MediaListCollapsedHeader(
                title = state.title,
                thumbnailUrl = state.thumbnailUrl ?: state.mosaicThumbnails.firstOrNull(),
                onBackClick = onBackClick,
            )
        }
    }
}

// ================= EXPANDED HEADER =================

@Composable
private fun MediaListExpandedHeader(
    state: MediaListUiState,
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSortClick: () -> Unit,
    onShuffleToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Filled.Close, "Fechar", onBackClick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleIconButton(Icons.Filled.Search, "Pesquisar", { })
                CircleIconButton(Icons.Filled.MoreVert, "Mais", { })
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            if (state.mosaicThumbnails.size >= 4) {
                PlaylistMosaicLarge(state.mosaicThumbnails)
            } else {
                AsyncImage(
                    model = state.thumbnailUrl, contentDescription = state.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = state.title, style = MaterialTheme.typography.headlineMedium,
            color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (!state.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(state.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (state.metadataLabel.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(state.metadataLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (state.showTabs) {
            Spacer(Modifier.height(16.dp))
            MediaListTabs(state.selectedTab, onTabSelected)
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1E40AF)).clickable { onPlayAllClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.width(8.dp))

            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).clickable { onShuffleClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Aleatório", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.weight(1f))

            // Shuffle Animation Toggle
            IconButton(onClick = onShuffleToggle) {
                Icon(
                    imageVector = if (state.isShuffling) Icons.Filled.Stop else Icons.Filled.Casino,
                    contentDescription = "Sortear animado",
                    tint = if (state.isShuffling) Color(0xFF1E40AF) else Color.White,
                )
            }

            // Sort Menu
            IconButton(onClick = onSortClick) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar", tint = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ================= TRACK ROW =================

@Composable
private fun TrackRow(
    track: Track,
    index: Int?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    showDragHandle: Boolean,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text("$index", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.width(24.dp))
        }
        if (track.thumbnailUrl == null && index != null) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Text("$index", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.SQUARE,
                size = 52.dp,
                cornerRadius = 6.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            TrackSubtitle(track = track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
        }

        if (showDragHandle) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Arrastar",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
                    .then(dragHandleModifier)
            )
        } else {
            IconButton(onClick = { }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

// ================= SORT BOTTOM SHEET =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentSort: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141B30),
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text("Ordenar por", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SortOrder.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == currentSort,
                        onClick = { onSelect(option) },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF60A5FA), unselectedColor = Color.White.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

// ================= COLLAPSED HEADER =================

@Composable
private fun MediaListCollapsedHeader(title: String, thumbnailUrl: String?, onBackClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.Filled.Close, "Fechar", onBackClick)
        Spacer(Modifier.width(12.dp))
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White) }
        IconButton(onClick = { }) { Icon(Icons.Filled.Search, contentDescription = "Pesquisar", tint = Color.White) }
    }
}

// ================= TABS =================

@Composable
private fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaListTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val bg = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
            val fg = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)
            Box(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(tab) }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================= FOOTER =================

@Composable
private fun MediaListFooter(state: MediaListUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp)) {
        Text(state.descriptionLabel, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text("Vtzin x", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("@2026 VzTune v1.0.0", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
    }
}

// ================= HELPERS =================

@Composable
private fun PlaylistMosaicLarge(thumbnails: List<String>) {
    Box(Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                AsyncImage(model = thumbnails.getOrNull(0), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                AsyncImage(model = thumbnails.getOrNull(2), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                AsyncImage(model = thumbnails.getOrNull(1), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                AsyncImage(model = thumbnails.getOrNull(3), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}