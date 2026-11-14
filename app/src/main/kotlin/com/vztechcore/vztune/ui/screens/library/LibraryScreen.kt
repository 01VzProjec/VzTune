package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.CoverCard
import com.vztechcore.vztune.ui.components.layout.SectionHeader
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.text.VzChipRow

/** Library: 5 tabs (overview, VzTune playlists, own playlists, favorites, downloads). */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onShortcutClick: (LibraryShortcut) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onPlaylistClick: (LibraryPlaylistItem) -> Unit,
    onCreatePlaylist: (name: String, coverUri: Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 200.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") { LibraryTitle() }
            item(key = "tabs") {
                VzChipRow(
                    items = LibraryTab.entries,
                    selected = state.selectedTab,
                    label = { it.label },
                    icon = { it.icon },
                    onSelect = onTabSelected,
                )
                Spacer(Modifier.height(16.dp))
            }

            when (state.selectedTab) {
                LibraryTab.OVERVIEW -> overviewTab(state.recentTracks, onShortcutClick, onTrackClick, onTrackInfoClick)

                LibraryTab.VZTUNE -> item(key = "vztune_soon") {
                    EmptyMessage("As playlists do VzTune vão aparecer aqui em breve.")
                }

                LibraryTab.MY_PLAYLISTS -> playlistGrid(
                    items = state.myPlaylists,
                    emptyText = "Toque em \"Nova playlist\" para criar a primeira.",
                    showCreateCard = true,
                    onCreateClick = { showCreateSheet = true },
                    onPlaylistClick = onPlaylistClick,
                )

                LibraryTab.FAVORITES -> playlistGrid(
                    items = state.favoritePlaylists,
                    emptyText = "Favorite uma playlist (coração na tela dela) para ela aparecer aqui.",
                    onPlaylistClick = onPlaylistClick,
                )

                LibraryTab.DOWNLOADS -> playlistGrid(
                    items = state.downloadedPlaylists,
                    emptyText = "Nenhuma playlist baixada ainda.",
                    onPlaylistClick = onPlaylistClick,
                )
            }

            item(key = "credits") { AppCredits() }
        }

        if (showCreateSheet) {
            CreatePlaylistSheet(
                onDismiss = { showCreateSheet = false },
                onConfirm = { name, cover ->
                    onCreatePlaylist(name, cover)
                    showCreateSheet = false
                },
            )
        }
    }
}

@Composable
private fun LibraryTitle() {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Sua Biblioteca",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "Tudo que é seu, num só lugar",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

// ============================== OVERVIEW TAB ==============================

private fun LazyListScope.overviewTab(
    recentTracks: List<Track>,
    onShortcutClick: (LibraryShortcut) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    items(LibraryShortcut.entries.chunked(2), key = { row -> row.first().name }) { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { shortcut ->
                ShortcutCard(shortcut, { onShortcutClick(shortcut) }, Modifier.weight(1f))
            }
        }
    }

    item(key = "recent_header") {
        Spacer(Modifier.height(16.dp))
        SectionHeader(title = "Recentemente tocadas")
    }

    if (recentTracks.isEmpty()) {
        item(key = "recent_empty") { EmptyMessage("Nada tocado ainda. Toque uma faixa e ela aparece aqui.") }
    } else {
        itemsIndexed(recentTracks, key = { _, t -> "recent_${t.source.name}_${t.id}" }) { _, track ->
            TrackListRow(
                track = track,
                onClick = { onTrackClick(track) },
                onMoreClick = { onTrackInfoClick(track) },
            )
        }
    }
}

@Composable
private fun ShortcutCard(shortcut: LibraryShortcut, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(54.dp)
            .clip(shape)
            .background(shortcut.accent.copy(alpha = 0.14f))
            .border(BorderStroke(1.5.dp, shortcut.accent.copy(alpha = 0.55f)), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(shortcut.icon, contentDescription = null, tint = shortcut.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}


// ============================== PLAYLIST GRIDS ==============================

private sealed interface GridCell {
    val key: String

    data object Create : GridCell {
        override val key = "create"
    }

    data class Playlist(val item: LibraryPlaylistItem) : GridCell {
        override val key get() = item.id
    }
}

/**
 * Grid of playlists, now 3 per row. Shared by "Suas playlists" (with the create card),
 * "Playlists favoritas" and "Playlists baixadas".
 */
private fun LazyListScope.playlistGrid(
    items: List<LibraryPlaylistItem>,
    emptyText: String,
    onPlaylistClick: (LibraryPlaylistItem) -> Unit,
    showCreateCard: Boolean = false,
    onCreateClick: () -> Unit = {},
) {
    val cells = buildList<GridCell> {
        if (showCreateCard) add(GridCell.Create)
        items.forEach { add(GridCell.Playlist(it)) }
    }

    // 3 columns per row
    val columns = 3

    items(cells.chunked(columns), key = { row -> row.joinToString("|") { it.key } }) { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { cell ->
                Box(Modifier.weight(1f)) {
                    when (cell) {
                        GridCell.Create -> CreatePlaylistCard(onCreateClick)
                        is GridCell.Playlist -> CoverCard(
                            title = cell.item.title,
                            subtitle = cell.item.subtitle,
                            imageUrl = cell.item.imageUrl,
                            mosaic = cell.item.mosaic,
                            onClick = { onPlaylistClick(cell.item) },
                        )
                    }
                }
            }
            // Fill the remaining slots so the last row keeps the same card width
            repeat(columns - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (items.isEmpty()) item(key = "grid_empty") { EmptyMessage(emptyText) }
}

@Composable
private fun CreatePlaylistCard(onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                VzIcons.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Nova playlist",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}

@Composable
internal fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}