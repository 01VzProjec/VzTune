package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.VzLoading

private val RowPadding = PaddingValues(horizontal = 16.dp)

/**
 * Artist profile. Sections (hidden when empty), in order:
 * popular songs, albums, singles, videos, featured, related artists, description, credits.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    onSongClick: (Track) -> Unit,
    onVideoClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSeeAllClick: (ArtistListKind, BrowseRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                ArtistHeader(
                    state = state,
                    onBackClick = onBackClick,
                    onRandomClick = onRandomClick,
                    onFollowClick = onFollowClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier.fillMaxWidth().height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }

                state.errorMessage != null -> item(key = "error") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.errorMessage,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    // ---------- Popular songs (numbered) ----------
                    if (state.topSongs.isNotEmpty()) {
                        item(key = "songs_header") {
                            ArtistSectionHeader(
                                title = "Músicas populares",
                                onMoreClick = state.songsMore?.let { ref -> { onSeeAllClick(ArtistListKind.SONGS, ref) } },
                            )
                        }
                        itemsIndexed(state.topSongs.take(5), key = { _, t -> "song_${t.id}" }) { index, track ->
                            ArtistSongRow(
                                track = track,
                                number = index + 1,
                                onClick = { onSongClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                            )
                        }
                        item(key = "songs_space") { Spacer(Modifier.height(16.dp)) }
                    }

                    // ---------- Albums ----------
                    if (state.albums.isNotEmpty()) {
                        item(key = "albums_header") {
                            ArtistSectionHeader(
                                title = "Álbuns",
                                onMoreClick = state.albumsMore?.let { ref -> { onSeeAllClick(ArtistListKind.ALBUMS, ref) } },
                            )
                        }
                        item(key = "albums_row") {
                            LazyRow(
                                contentPadding = RowPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.albums, key = { it.id }) { album ->
                                    ArtistSquareCard(
                                        imageUrl = album.thumbnailUrl,
                                        title = album.title,
                                        subtitle = "Álbum",
                                        onClick = { onAlbumClick(album) },
                                        modifier = Modifier.width(168.dp),
                                    )
                                }
                            }
                        }
                        item(key = "albums_space") { Spacer(Modifier.height(20.dp)) }
                    }

                    // ---------- Singles ----------
                    if (state.singles.isNotEmpty()) {
                        item(key = "singles_header") {
                            ArtistSectionHeader(
                                title = "Singles",
                                onMoreClick = state.singlesMore?.let { ref -> { onSeeAllClick(ArtistListKind.SINGLES, ref) } },
                            )
                        }
                        item(key = "singles_row") {
                            LazyRow(
                                contentPadding = RowPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.singles, key = { it.id }) { single ->
                                    ArtistSquareCard(
                                        imageUrl = single.thumbnailUrl,
                                        title = single.title,
                                        subtitle = "Single",
                                        onClick = { onAlbumClick(single) },
                                        modifier = Modifier.width(168.dp),
                                    )
                                }
                            }
                        }
                        item(key = "singles_space") { Spacer(Modifier.height(20.dp)) }
                    }

                    // ---------- Videos (16:9) ----------
                    if (state.videos.isNotEmpty()) {
                        item(key = "videos_header") {
                            ArtistSectionHeader(
                                title = "Vídeos",
                                onMoreClick = state.videosMore?.let { ref -> { onSeeAllClick(ArtistListKind.VIDEOS, ref) } },
                            )
                        }
                        item(key = "videos_row") {
                            LazyRow(
                                contentPadding = RowPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.videos, key = { it.id }) { video ->
                                    ArtistVideoCard(
                                        track = video,
                                        onClick = { onVideoClick(video) },
                                        modifier = Modifier.width(264.dp),
                                    )
                                }
                            }
                        }
                        item(key = "videos_space") { Spacer(Modifier.height(20.dp)) }
                    }

                    // ---------- Featured ----------
                    if (state.featured.isNotEmpty()) {
                        item(key = "featured_header") {
                            ArtistSectionHeader(
                                title = "Destacado",
                                onMoreClick = state.featuredMore?.let { ref -> { onSeeAllClick(ArtistListKind.FEATURED, ref) } },
                            )
                        }
                        item(key = "featured_row") {
                            LazyRow(
                                contentPadding = RowPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.featured, key = { it.id }) { playlist ->
                                    ArtistSquareCard(
                                        imageUrl = playlist.thumbnailUrl,
                                        title = playlist.title,
                                        subtitle = playlist.owner,
                                        onClick = { onPlaylistClick(playlist) },
                                        modifier = Modifier.width(168.dp),
                                    )
                                }
                            }
                        }
                        item(key = "featured_space") { Spacer(Modifier.height(20.dp)) }
                    }

                    // ---------- Related artists ----------
                    if (state.relatedArtists.isNotEmpty()) {
                        item(key = "related_header") {
                            ArtistSectionHeader(title = "Artistas relacionados", onMoreClick = null)
                        }
                        item(key = "related_row") {
                            LazyRow(
                                contentPadding = RowPadding,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(state.relatedArtists, key = { it.id }) { artist ->
                                    ArtistCircleCard(artist = artist, onClick = { onArtistClick(artist) })
                                }
                            }
                        }
                        item(key = "related_space") { Spacer(Modifier.height(24.dp)) }
                    }

                    // ---------- Description ----------
                    item(key = "description") {
                        ArtistDescription(description = state.description)
                    }

                    // ---------- Credits ----------
                    item(key = "credits") {
                        ArtistCredits()
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDescription(description: String?) {
    var expanded by remember { mutableStateOf(false) }
    val text = description?.takeIf { it.isNotBlank() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Sobre",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text ?: "Sem descrição",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = if (text != null) 0.75f else 0.5f),
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(enabled = text != null) { expanded = !expanded },
        )
        if (text != null && text.length > 220) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "Ver menos" else "Ver mais",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun ArtistCredits() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Vtzin x - VzTechCore",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "@2026 VzTune v1.0.0",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.35f),
        )
    }
}
