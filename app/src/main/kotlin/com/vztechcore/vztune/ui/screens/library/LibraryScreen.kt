package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.cards.CoverCard
import com.vztechcore.vztune.ui.components.text.VzChipRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Add
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * Library screen.
 *
 * Shows the user local playlists, shortcuts and recent tracks.
 * The create playlist sheet now includes the Vz color editor, so a new
 * playlist is created with the chosen color as its official cover.
 * All UI text is in Portuguese. Code and comments are in English.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onShortcutClick: (LibraryShortcut) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onPlaylistClick: (LibraryPlaylistItem) -> Unit,
    onCreatePlaylist: (name: String, coverUri: Uri?, colorToken: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Biblioteca",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                )
            }

            item(key = "tabs") {
                VzChipRow(
                    items = LibraryTab.entries,
                    selected = state.selectedTab,
                    label = { it.label },
                    onSelect = onTabSelected,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "shortcuts") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(LibraryShortcut.entries, key = { it.name }) { shortcut ->
                        ShortcutCard(
                            shortcut = shortcut,
                            onClick = { onShortcutClick(shortcut) },
                        )
                    }
                }
            }

            if (state.localPlaylists.isNotEmpty()) {
                item(key = "playlistsTitle") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Suas playlists",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { showCreateSheet = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = VzIcons.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(text = "Nova", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item(key = "playlistsRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.localPlaylists, key = { it.id }) { item ->
                            CoverCard(
                                title = item.title,
                                subtitle = item.subtitle,
                                imageUrl = item.imageUrl,
                                mosaic = item.mosaic,
                                onClick = { onPlaylistClick(item) },
                            )
                        }
                    }
                }
            } else {
                item(key = "emptyPlaylists") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { showCreateSheet = true }
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Crie sua primeira playlist",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Escolha uma cor Vz para a capa ou uma foto da galeria.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            if (state.recentTracks.isNotEmpty()) {
                item(key = "recentTitle") {
                    Text(
                        text = "Recentes",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
                    )
                }
                items(state.recentTracks, key = { "recent_${it.source.name}_${it.id}" }) { track ->
                    RecentTrackRow(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onMoreClick = { onTrackInfoClick(track) },
                    )
                }
            }
        }

        if (showCreateSheet) {
            CreatePlaylistSheet(
                onDismiss = { showCreateSheet = false },
                onConfirm = { name, coverUri, colorToken ->
                    showCreateSheet = false
                    onCreatePlaylist(name, coverUri, colorToken)
                },
            )
        }
    }
}

/** Small shortcut card in the Library top row. */
@Composable
private fun ShortcutCard(
    shortcut: LibraryShortcut,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Recent track row. */
@Composable
private fun RecentTrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = VzIcons.MoreVert,
            contentDescription = "Mais",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onMoreClick() }.padding(10.dp),
        )
    }
}
