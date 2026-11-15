package com.vztechcore.vztune.ui.screens.artist

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ChevronRight
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * Artist profile screen.
 *
 * Background rule: the whole screen uses the solid accent color extracted
 * from the artist photo. No gradient, no fade to black. The same solid color
 * is used by [ArtistHeader] so the hero and the list blend into one flat color.
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
    onSeeAllClick: (ArtistListKind, ArtistListRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rememberArtistAccent(state.thumbnailUrl)
    val contentColor = contentColorForAccent(accent)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(accent)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                ArtistHeader(
                    state = state,
                    accent = accent,
                    onBackClick = onBackClick,
                    onRandomClick = onRandomClick,
                    onFollowClick = onFollowClick,
                )
            }

            if (state.topSongs.isNotEmpty()) {
                item(key = "topSongsTitle") {
                    RowSectionTitle(
                        title = "Músicas populares",
                        contentColor = contentColor,
                        onSeeAllClick = if (state.topSongsRef != null) {
                            { onSeeAllClick(ArtistListKind.TOP_SONGS, state.topSongsRef) }
                        } else null,
                    )
                }
                items(state.topSongs, key = { "song_${it.source.name}_${it.id}" }) { track ->
                    ArtistTrackRow(
                        track = track,
                        onClick = { onSongClick(track) },
                        onMoreClick = { onTrackInfoClick(track) },
                        contentColor = contentColor,
                    )
                }
            }

            if (state.albums.isNotEmpty()) {
                item(key = "albumsTitle") {
                    RowSectionTitle(
                        title = "Álbuns",
                        contentColor = contentColor,
                        onSeeAllClick = if (state.albumsRef != null) {
                            { onSeeAllClick(ArtistListKind.ALBUMS, state.albumsRef) }
                        } else null,
                    )
                }
                item(key = "albumsRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.albums, key = { "album_${it.id}" }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                contentColor = contentColor,
                            )
                        }
                    }
                }
            }

            if (state.singles.isNotEmpty()) {
                item(key = "singlesTitle") {
                    RowSectionTitle(
                        title = "Singles",
                        contentColor = contentColor,
                        onSeeAllClick = if (state.singlesRef != null) {
                            { onSeeAllClick(ArtistListKind.SINGLES, state.singlesRef) }
                        } else null,
                    )
                }
                item(key = "singlesRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.singles, key = { "single_${it.id}" }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                contentColor = contentColor,
                            )
                        }
                    }
                }
            }

            if (state.videos.isNotEmpty()) {
                item(key = "videosTitle") {
                    RowSectionTitle(
                        title = "Vídeos",
                        contentColor = contentColor,
                        onSeeAllClick = if (state.videosRef != null) {
                            { onSeeAllClick(ArtistListKind.VIDEOS, state.videosRef) }
                        } else null,
                    )
                }
                items(state.videos, key = { "video_${it.source.name}_${it.id}" }) { track ->
                    ArtistTrackRow(
                        track = track,
                        onClick = { onVideoClick(track) },
                        onMoreClick = { onTrackInfoClick(track) },
                        contentColor = contentColor,
                    )
                }
            }

            if (state.playlists.isNotEmpty()) {
                item(key = "playlistsTitle") {
                    RowSectionTitle(
                        title = "Playlists",
                        contentColor = contentColor,
                        onSeeAllClick = null,
                    )
                }
                item(key = "playlistsRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.playlists, key = { "playlist_${it.id}" }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                contentColor = contentColor,
                            )
                        }
                    }
                }
            }

            if (state.relatedArtists.isNotEmpty()) {
                item(key = "relatedTitle") {
                    RowSectionTitle(
                        title = "Artistas relacionados",
                        contentColor = contentColor,
                        onSeeAllClick = null,
                    )
                }
                item(key = "relatedRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.relatedArtists, key = { "related_${it.id}" }) { artist ->
                            RelatedArtistCard(
                                artist = artist,
                                onClick = { onArtistClick(artist) },
                                contentColor = contentColor,
                            )
                        }
                    }
                }
            }

            if (state.description.isNotBlank()) {
                item(key = "about") {
                    ArtistAbout(
                        description = state.description,
                        contentColor = contentColor,
                    )
                }
            }

            item(key = "credits") {
                AppCredits()
            }
        }
    }
}

/** Section title with an optional "Ver tudo" action. */
@Composable
private fun RowSectionTitle(
    title: String,
    contentColor: Color,
    onSeeAllClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAllClick != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeeAllClick() }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ver tudo",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = VzIcons.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Single song / video row inside the artist screen. */
@Composable
private fun ArtistTrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    contentColor: Color,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(
            imageUrl = track.thumbnailUrl,
            shape = ThumbnailShape.SQUARE,
            size = 52.dp,
            cornerRadius = 6.dp,
            isPlaying = playback.isPlaying,
            isPaused = playback.isPaused,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            TrackSubtitle(
                track = track,
                style = MaterialTheme.typography.bodySmall,
                prefixColor = contentColor.copy(alpha = 0.55f),
                artistColor = contentColor.copy(alpha = 0.6f),
            )
        }
        Icon(
            imageVector = VzIcons.MoreVert,
            contentDescription = "Mais",
            tint = contentColor.copy(alpha = 0.7f),
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onMoreClick() }
                .padding(10.dp),
        )
    }
}

/** Square album card used in the horizontal rows. */
@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    contentColor: Color,
) {
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.15f)),
        ) {
            if (!album.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = album.thumbnailUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.year?.toString()?.takeIf { it.isNotBlank() } ?: "Álbum",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Square playlist card used in the horizontal rows. */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    contentColor: Color,
) {
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.15f)),
        ) {
            if (!playlist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playlist.owner ?: "Playlist",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Circular related artist card. */
@Composable
private fun RelatedArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    contentColor: Color,
) {
    Column(
        Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(55.dp))
                .background(Color.Black.copy(alpha = 0.15f)),
        ) {
            if (!artist.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Collapsible artist description block. */
@Composable
private fun ArtistAbout(
    description: String,
    contentColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = "Sobre",
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.8f),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (expanded) "Ver menos" else "Ver mais",
            style = MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
        )
    }
}
