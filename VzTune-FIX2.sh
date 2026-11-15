#!/bin/bash
# VzTune FIX2 - album só número, favoritar no álbum, artista pills + share, sheet azul marinho
# Roda dentro da pasta do projeto: cd ~/VzTune && bash VzTune-FIX2.sh
set -e
echo "== VzTune FIX2 - 7 arquivos =="

echo "[1/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt" <<'VZ_EOF_1_FIX2'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.components.text.VzChipRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.DragHandle
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.media.QueueMusic

@Composable
internal fun MediaListTrackRow(
    track: Track,
    index: Int?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    showDragHandle: Boolean,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (dragOffset > 0f) {
            val iconAlpha = (dragOffset / 100f).coerceIn(0.4f, 1f)
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(dragOffset.dp.coerceAtMost(80.dp))
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = VzIcons.QueueMusic,
                    contentDescription = "Tocar em seguida",
                    tint = Color.White.copy(alpha = iconAlpha),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragOffset }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 30f) onPlayNext()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { change, dragAmount ->
                        if (dragAmount > 0 || dragOffset > 0) {
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, 300f)
                            change.consume()
                        }
                    }
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Álbum: o InnerTube não dá a thumbnail exata da faixa, então mostra
                // só a numeração onde ficaria a thumbnail. Playlist: mostra a thumbnail normal.
                if (index != null) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$index",
                            color = if (isPlaying) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                        )
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
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    TrackSubtitle(
                        track = track,
                        style = MaterialTheme.typography.bodySmall,
                        prefixColor = Color.White.copy(alpha = 0.55f),
                        artistColor = Color.White.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = VzIcons.MoreVert,
                        contentDescription = "Mais",
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (showDragHandle) {
                    Icon(
                        imageVector = VzIcons.DragHandle,
                        contentDescription = "Arrastar",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .then(dragHandleModifier),
                    )
                }
            }
        }
    }
}

/**
 * 16:9 video card with title, channel and the "more" button.
 * [horizontalPadding] is the card's inner side inset; pass 0.dp when the parent already
 * provides the edge padding (e.g. horizontal rows on the artist screen).
 */
@Composable
internal fun MediaListVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 10.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(
                    track = track,
                    style = MaterialTheme.typography.bodySmall,
                    prefixColor = Color.White.copy(alpha = 0.55f),
                    artistColor = Color.White.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = VzIcons.MoreVert,
                    contentDescription = "Mais",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    VzChipRow(
        items = MediaListTab.entries,
        selected = selected,
        label = { it.label },
        onSelect = onSelect,
    )
}

@Composable
internal fun PlayableTrackRow(
    track: Track,
    index: Int?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListTrackRow(
        track = track,
        index = index,
        isPlaying = playback.isPlaying,
        isPaused = playback.isPaused,
        onClick = onClick,
        onMoreClick = onMoreClick,
        onPlayNext = onPlayNext,
        showDragHandle = false,
        dragHandleModifier = Modifier,
        modifier = modifier,
    )
}

@Composable
internal fun PlayableVideoCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListVideoCard(
        track = track,
        isPlaying = playback.isPlaying,
        isPaused = playback.isPaused,
        onClick = onClick,
        onMoreClick = onMoreClick,
        modifier = modifier,
    )
}
VZ_EOF_1_FIX2

echo "[2/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListUiState.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListUiState.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListUiState.kt" <<'VZ_EOF_2_FIX2'
package com.vztechcore.vztune.ui.screens.medialist

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder

enum class MediaListTab(val label: String) {
    ALL("Tudo"),
    SONGS("Músicas"),
    VIDEOS("Vídeos"),
}

data class MediaListUiState(
    val listId: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val owner: String? = null,
    val ownerAvatarUrl: String? = null,
    val artistId: String? = null,
    val moreAlbumsByArtist: List<Album> = emptyList(),
    val year: String? = null,
    val views: String? = null,
    val originalTrackCount: Int? = null,
    val thumbnailUrl: String? = null,
    val mosaicThumbnails: List<String> = emptyList(),
    val isAlbum: Boolean = false,
    val allTracks: List<Track> = emptyList(),
    val description: String? = null,
    val selectedTab: MediaListTab = MediaListTab.ALL,
    val sortOrder: SortOrder = SortOrder.OLD_TO_RECENT,
    val manualOrder: List<String> = emptyList(),
    val isShuffling: Boolean = false,
    val isFavorite: Boolean = false,
    // Playlist created by the user inside the app (stored on the device).
    val isLocal: Boolean = false,
) {
    val showTabs: Boolean get() = !isAlbum

    // Só playlist local não pode favoritar. Álbum e playlist do YouTube podem.
    // Barra de cima fica: Lupa, Favoritar, 3 pontinhos.
    val showFavorite: Boolean get() = !isLocal

    // Albums keep their official tracklist order without sorting controls.
    val isSortEnabled: Boolean get() = !isAlbum && selectedTab == MediaListTab.ALL

    val filteredTracks: List<Track>
        get() = when (selectedTab) {
            MediaListTab.ALL -> allTracks
            MediaListTab.SONGS -> allTracks.filter { it.source == Source.YOUTUBE_MUSIC }
            MediaListTab.VIDEOS -> allTracks.filter { it.source == Source.YOUTUBE }
        }

    fun applySort(tracks: List<Track>): List<Track> = when (sortOrder) {
        SortOrder.MANUAL, SortOrder.SHUFFLE -> {
            if (manualOrder.isEmpty()) tracks
            else {
                val map = manualOrder.withIndex().associate { it.value to it.index }
                tracks.sortedBy { map[it.id] ?: Int.MAX_VALUE }
            }
        }
        SortOrder.RECENT_TO_OLD -> tracks.reversed()
        SortOrder.OLD_TO_RECENT -> tracks
        SortOrder.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
        SortOrder.TITLE_DESC -> tracks.sortedByDescending { it.title.lowercase() }
        SortOrder.ARTIST_ASC -> tracks.sortedBy { it.artist.lowercase() }
        SortOrder.ARTIST_DESC -> tracks.sortedByDescending { it.artist.lowercase() }
    }

    fun getDisplayTracks(): List<Track> {
        if (isAlbum) return allTracks
        val filtered = filteredTracks
        return if (isSortEnabled) {
            applySort(filtered)
        } else {
            filtered.reversed()
        }
    }

    val trackCountLabel: String
        get() {
            val count = originalTrackCount ?: filteredTracks.size
            return if (count == 1) "1 faixa" else "$count faixas"
        }

    val totalDurationLabel: String
        get() {
            val totalSeconds = filteredTracks.sumOf { it.durationSeconds }
            if (totalSeconds <= 0) return ""
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}min total"
                h > 0 -> "${h}h total"
                m > 0 -> "${m}min total"
                else -> "${s}s total"
            }
        }

    val metadataLabel: String
        get() = listOf(trackCountLabel, totalDurationLabel)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

    val descriptionLabel: String
        get() = description?.takeIf { it.isNotBlank() } ?: "Sem descrição"
}
VZ_EOF_2_FIX2

echo "[3/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt" <<'VZ_EOF_3_FIX2'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Sort
import com.vztechcore.vztune.ui.icons.actions.StopCircle
import com.vztechcore.vztune.ui.icons.actions.Sync
import com.vztechcore.vztune.ui.icons.media.DownloadForOffline
import com.vztechcore.vztune.ui.icons.navigation.Search
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle

@Composable
internal fun MediaListExpandedHeader(
    state: MediaListUiState,
    dominantColor: Color = Color.Black,
    allTracksDownloaded: Boolean = false,
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSortClick: () -> Unit,
    onShuffleToggle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
) {
    // Sem camaleao - fade sempre pra preto AMOLED
    val chameleonBase = Color.Black

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            val hasDefaultColorToken = PlaylistCoverPalette.isDefaultColorToken(state.thumbnailUrl)
            when {
                state.isLocal && !hasDefaultColorToken && !state.thumbnailUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = state.thumbnailUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.mosaicThumbnails.size >= 4 -> {
                    PlaylistMosaic(state.mosaicThumbnails)
                }
                state.isLocal -> {
                    DefaultPlaylistCover(
                        seed = state.listId,
                        coverPath = state.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    AsyncImage(
                        model = state.thumbnailUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.42f),
                                0.48f to Color.Transparent,
                                0.82f to chameleonBase.copy(alpha = 0.78f),
                                1.0f to chameleonBase,
                            )
                        )
                    )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ordem igual as playlists: Lupa, Favoritar, 3 pontinhos
                    MediaListCircleButton(VzIcons.Search, "Pesquisar", onSearchClick)
                    if (state.showFavorite) {
                        MediaListCircleButton(
                            icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                            contentDescription = if (state.isFavorite) "Remover dos favoritos" else "Favoritar",
                            onClick = onFavoriteClick,
                        )
                    }
                    MediaListCircleButton(VzIcons.MoreVert, "Mais opções", onMoreClick)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-12).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            val ownerName = state.owner?.takeIf { it.isNotBlank() }
            if (!ownerName.isNullOrBlank()) {
                val isClickableOwner = !state.isLocal && onArtistClick != null
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (isClickableOwner) {
                                Modifier
                                    .clickable { onArtistClick?.invoke() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (!state.ownerAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.ownerAvatarUrl,
                            contentDescription = ownerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            val statsText = if (state.isAlbum) {
                listOfNotNull(
                    "Álbum",
                    state.year?.takeIf { it.isNotBlank() },
                    state.trackCountLabel.takeIf { state.allTracks.isNotEmpty() },
                    state.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
            } else {
                listOfNotNull(
                    "Playlist",
                    state.year?.takeIf { it.isNotBlank() },
                    state.trackCountLabel.takeIf { (state.originalTrackCount ?: state.allTracks.size) > 0 },
                    state.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
            }

            if (statsText.isNotBlank()) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.showTabs) {
            MediaListTabs(state.selectedTab, onTabSelected)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E40AF))
                    .clickable { onPlayAllClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    VzIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Play",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onShuffleClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    VzIcons.Shuffle,
                    contentDescription = "Aleatório",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (allTracksDownloaded) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.DownloadForOffline,
                        contentDescription = if (state.isAlbum) "Álbum baixado" else "Playlist baixada",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (state.isSortEnabled && state.sortOrder == SortOrder.SHUFFLE) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        imageVector = if (state.isShuffling) VzIcons.StopCircle else VzIcons.Sync,
                        contentDescription = "Sortear animado",
                        tint = if (state.isShuffling) Color(0xFF1E40AF) else Color.White,
                    )
                }
            }

            if (state.isSortEnabled) {
                IconButton(onClick = onSortClick) {
                    Icon(
                        VzIcons.Sort,
                        contentDescription = "Ordenar",
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun MediaListCollapsedHeader(
    title: String,
    thumbnailUrl: String?,
    onBackClick: () -> Unit,
    dominantColor: Color = Color.Black,
    onSearchClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    circularThumbnail: Boolean = false,
    showFavorite: Boolean = false,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
) {
    val barTint = remember(dominantColor) {
        lerp(Color.Black, dominantColor, 0.38f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        barTint.copy(alpha = 0.98f),
                        barTint.copy(alpha = 0.88f),
                        Color.Transparent,
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
        Spacer(Modifier.width(12.dp))
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(if (circularThumbnail) CircleShape else RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSearchClick != null) {
            IconButton(onClick = onSearchClick) {
                Icon(VzIcons.Search, contentDescription = "Pesquisar", tint = Color.White)
            }
        }
        // Colapsado também fica Lupa, Favoritar, 3 pontinhos (quando tem favoritar)
        if (showFavorite && onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                    contentDescription = if (isFavorite) "Remover dos favoritos" else "Favoritar",
                    tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                )
            }
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick) {
                Icon(VzIcons.MoreVert, contentDescription = "Mais opções", tint = Color.White)
            }
        }
    }
}
VZ_EOF_3_FIX2

echo "[4/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt" <<'VZ_EOF_4_FIX2'
package com.vztechcore.vztune.ui.screens.medialist

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.ui.components.download.LocalDownloadedKeys
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.sheets.MediaListArtistPickerSheet
import com.vztechcore.vztune.ui.components.sheets.MediaListOptionsSheet
import com.vztechcore.vztune.ui.components.sheets.parseMediaListArtists
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val HEADER_OFFSET = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(
    state: MediaListUiState,
    activeDownloads: Map<String, TrackDownloadItem> = emptyMap(),
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
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayNextAll: () -> Unit = {},
    onAddToQueueAll: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onOpenArtistByName: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onDeleteLocalPlaylist: () -> Unit = {},
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nowPlaying = LocalNowPlaying.current
    val downloadedKeys = LocalDownloadedKeys.current
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showListOptionsSheet by remember { mutableStateOf(false) }
    var showHeaderArtistPicker by remember { mutableStateOf(false) }
    var shuffleJob by remember { mutableStateOf<Job?>(null) }

    val isCollapsed by rememberHeaderCollapsed(listState)

    // Fundo preto AMOLED - sem camaleao, sem gradiente de cor dominante
    val dominantColor = Color.Black
    // Thumb do header colapsado: se a capa for token de cor, usa o mosaico
    val collapsedThumbnailUrl = remember(state.thumbnailUrl, state.mosaicThumbnails) {
        val raw = state.thumbnailUrl
        if (!raw.isNullOrBlank() && !PlaylistCoverPalette.isDefaultColorToken(raw)) raw
        else state.mosaicThumbnails.firstOrNull()
    }

    val ownerArtists = remember(state.owner, state.isLocal) {
        if (!state.isLocal) parseMediaListArtists(state.owner.orEmpty()) else emptyList()
    }

    val handleHeaderArtistClick: (() -> Unit)? = remember(state.isLocal, state.owner, ownerArtists) {
        if (state.isLocal || state.owner.isNullOrBlank()) {
            null
        } else if (ownerArtists.size > 1) {
            { showHeaderArtistPicker = true }
        } else {
            onOpenArtist
        }
    }

    val displayedTracks = remember(state.filteredTracks, state.sortOrder, state.manualOrder, state.selectedTab, state.isAlbum) {
        state.getDisplayTracks()
    }

    val allTracksDownloaded = remember(state.allTracks, downloadedKeys) {
        state.allTracks.isNotEmpty() && state.allTracks.all {
            StoredTrack.from(it).key in downloadedKeys
        }
    }

    val batchDownloadState = remember(state.allTracks, downloadedKeys, activeDownloads) {
        if (state.allTracks.isEmpty()) {
            false to 0f
        } else {
            var activeCount = 0
            var totalProgress = 0f
            state.allTracks.forEach { t ->
                val k = StoredTrack.from(t).key
                val item = activeDownloads[k]
                when {
                    k in downloadedKeys -> totalProgress += 1f
                    item != null && (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) -> {
                        activeCount++
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                    item != null && item.status == DownloadStatus.PAUSED -> {
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                }
            }
            val isRunning = activeCount > 0
            val avgProgress = (totalProgress / state.allTracks.size.toFloat()).coerceIn(0f, 1f)
            isRunning to avgProgress
        }
    }

    val canReorder = !state.isAlbum && state.sortOrder == SortOrder.MANUAL && state.selectedTab == MediaListTab.ALL

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!canReorder) return@rememberReorderableLazyListState
        val fromTrack = from.index - HEADER_OFFSET
        val toTrack = to.index - HEADER_OFFSET
        if (fromTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        if (toTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        onReorder(fromTrack, toTrack)
    }

    LaunchedEffect(state.isShuffling) {
        if (state.isShuffling) {
            shuffleJob?.cancel()
            shuffleJob = launch {
                while (isActive) {
                    onShuffleOnce()
                    delay(150)
                }
            }
            delay(1500)
            onStopShuffle()
        } else {
            shuffleJob?.cancel()
        }
    }

    if (showHeaderArtistPicker && ownerArtists.size > 1) {
        MediaListArtistPickerSheet(
            artists = ownerArtists,
            onPick = { selectedArtist ->
                showHeaderArtistPicker = false
                onOpenArtistByName(selectedArtist)
            },
            onDismiss = { showHeaderArtistPicker = false },
        )
    }

    if (showSortSheet) {
        MediaListSortSheet(
            currentSort = state.sortOrder,
            onSelect = { onSortSelected(it); showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showListOptionsSheet) {
        MediaListOptionsSheet(
            state = state,
            allTracksDownloaded = allTracksDownloaded,
            isDownloadingBatch = batchDownloadState.first,
            batchDownloadProgress = batchDownloadState.second,
            onDismiss = { showListOptionsSheet = false },
            onToggleFavorite = onFavoriteClick,
            onPlayNextAll = onPlayNextAll,
            onAddToQueueAll = onAddToQueueAll,
            onDownloadAll = onDownloadAll,
            onOpenDownloadsScreen = onOpenDownloadsScreen,
            onOpenArtistByName = onOpenArtistByName,
            onDeleteLocalPlaylist = onDeleteLocalPlaylist,
            onRenameLocalPlaylist = onRenameLocalPlaylist,
            onChangeLocalPlaylistCover = onChangeLocalPlaylistCover,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            item(key = "header") {
                MediaListExpandedHeader(
                    state = state,
                    dominantColor = dominantColor,
                    allTracksDownloaded = allTracksDownloaded,
                    onBackClick = onBackClick,
                    onTabSelected = onTabSelected,
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onSearchClick = { showSearchOverlay = true },
                    onMoreClick = { showListOptionsSheet = true },
                    onSortClick = { showSortSheet = true },
                    onShuffleToggle = {
                        if (state.isShuffling) onStopShuffle() else onStartShuffle()
                    },
                    onFavoriteClick = onFavoriteClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }
                state.errorMessage != null -> item(key = "error") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.errorMessage, color = Color.Red)
                    }
                }
                displayedTracks.isEmpty() -> item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Nenhuma faixa por aqui.", color = Color.White.copy(alpha = 0.6f))
                    }
                }
                else -> itemsIndexed(
                    items = displayedTracks,
                    key = { _, t -> "${t.source.name}_${t.id}" },
                ) { index, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val isPlaying = isCurrent && nowPlaying.isPlaying
                    val isPaused = isCurrent && !nowPlaying.isPlaying

                    val useVideoLayout = state.selectedTab == MediaListTab.VIDEOS

                    if (useVideoLayout) {
                        MediaListVideoCard(
                            track = track,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                        )
                    } else if (canReorder) {
                        val itemKey = "${track.source.name}_${track.id}"
                        ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                            MediaListTrackRow(
                                track = track,
                                index = if (state.isAlbum) index + 1 else null,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                onClick = { onTrackClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                                onPlayNext = { onPlayNextClick(track) },
                                showDragHandle = true,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                modifier = Modifier
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        )
                                    )
                                    .background(Color.Black.copy(alpha = if (isDragging) 0.5f else 0f)),
                            )
                        }
                    } else {
                        MediaListTrackRow(
                            track = track,
                            index = if (state.isAlbum) index + 1 else null,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                            onPlayNext = { onPlayNextClick(track) },
                            showDragHandle = false,
                            dragHandleModifier = Modifier,
                            modifier = Modifier,
                        )
                    }
                }
            }

            item(key = "footer") {
                MediaListFooter(
                    state = state,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.title,
                thumbnailUrl = collapsedThumbnailUrl,
                dominantColor = dominantColor,
                onBackClick = onBackClick,
                onSearchClick = { showSearchOverlay = true },
                onMoreClick = { showListOptionsSheet = true },
                showFavorite = state.showFavorite,
                isFavorite = state.isFavorite,
                onFavoriteClick = onFavoriteClick,
            )
        }

        if (showSearchOverlay) {
            MediaListSearchOverlay(
                allTracks = displayedTracks,
                isAlbum = state.isAlbum,
                onClose = { showSearchOverlay = false },
                onTrackClick = onTrackClick,
                onPlayNextClick = onPlayNextClick,
                onTrackInfoClick = onTrackInfoClick,
            )
        }
    }
}
VZ_EOF_4_FIX2

echo "[5/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt" <<'VZ_EOF_5_FIX2'
package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vztechcore.vztune.ui.components.feedback.fadeOutBottom
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton

private val HeroMinHeight = 300.dp
private val ButtonHeight = 44.dp

// Camaleão desativado - sempre preto AMOLED. Mantido pra compatibilidade.
@Composable
fun rememberArtistAccent(thumbnailUrl: String?): Color = Color.Black

// Camaleão desativado - sempre preto sólido. Mantido pra compatibilidade.
@Composable
fun rememberArtistChameleonBrush(accent: Color): Brush =
    Brush.verticalGradient(listOf(Color.Black, Color.Black))

/**
 * Hero do artista como era antes: foto cheia em cima, nome e stats embaixo à esquerda,
 * botões pill embaixo: Seguir na esquerda, Aleatório na direita.
 * Fundo sempre preto AMOLED, sem camaleão.
 * Barra superior: Voltar na esquerda, Compartilhar na direita.
 */
@Composable
fun ArtistHeader(
    state: ArtistUiState,
    accent: Color,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hasPlayableItems = state.topSongs.isNotEmpty() || state.videos.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = HeroMinHeight)
                .animateContentSize()
        ) {
            ArtistPhoto(imageUrl = state.thumbnailUrl, name = state.name)

            // Sombra em cima pra barra ficar legível
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            // Barra superior: Voltar esquerda, Compartilhar direita
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaListCircleButton(VzIcons.ArrowBack, "Voltar", onBackClick)
                MediaListCircleButton(VzIcons.Share, "Compartilhar artista", onShareClick)
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            ) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.displaySmall.copy(
                        letterSpacing = (-0.5).sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            offset = Offset(0f, 3f),
                            blurRadius = 12f,
                        ),
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.statsLabel.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.statsLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Botões como era antes, mas com Seguir na esquerda e Aleatório na direita
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FollowButton(
                isFollowing = state.isFollowing,
                onClick = onFollowClick,
            )
            if (hasPlayableItems) {
                PillButton(
                    label = "Aleatório",
                    icon = VzIcons.Shuffle,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    onClick = onRandomClick,
                )
            }
        }
    }
}

/**
 * Foto cheia, FillWidth, sem crop forçado, com fade pra preto embaixo.
 * Sem foto, ícone grande no fundo preto.
 */
@Composable
private fun ArtistPhoto(imageUrl: String?, name: String) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(HeroMinHeight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VzIcons.Artist,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(140.dp),
            )
        }
        return
    }
    val context = LocalContext.current
    val request = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(400)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = name,
        contentScale = ContentScale.FillWidth,
        alignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .fadeOutBottom(fadeFraction = 0.55f),
    )
}

@Composable
private fun FollowButton(
    isFollowing: Boolean,
    onClick: () -> Unit,
) {
    if (isFollowing) {
        PillButton(
            label = "Seguindo",
            icon = VzIcons.PersonCheck,
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.35f),
            onClick = onClick,
        )
    } else {
        PillButton(
            label = "Seguir",
            icon = VzIcons.PersonAdd,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.35f),
            onClick = onClick,
        )
    }
}

/** Pill com ícone + texto. Igual era antes. */
@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    borderColor: Color? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(ButtonHeight)
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
VZ_EOF_5_FIX2

echo "[6/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt" <<'VZ_EOF_6_FIX2'
package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.animation.animateContentSize
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.CoverCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.layout.SectionHeader
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.screens.medialist.CollapsedHeaderHost
import com.vztechcore.vztune.ui.screens.medialist.MediaListCollapsedHeader
import com.vztechcore.vztune.ui.screens.medialist.MediaListVideoCard
import com.vztechcore.vztune.ui.screens.medialist.rememberHeaderCollapsed

private val RowPadding = PaddingValues(horizontal = 16.dp)
private val CoverWidth = 160.dp
private val VideoWidth = 280.dp
private val RelatedArtistSize = 124.dp
private val SectionGap = 24.dp

/**
 * Artist profile. Sections (hidden when empty), in order:
 * popular songs, albums, singles, videos, featured, related artists, description, credits.
 * The whole screen sits on a backdrop tinted with the photo color; once the hero scrolls away,
 * a sticky bar (close + photo + name) fades in.
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
    val listState = rememberLazyListState()
    val isCollapsed by rememberHeaderCollapsed(listState)
    val nowPlaying = LocalNowPlaying.current

    // Fundo sempre preto AMOLED - camaleao desativado por enquanto

    fun seeAll(kind: ArtistListKind, ref: BrowseRef?): (() -> Unit)? =
        ref?.let { { onSeeAllClick(kind, it) } }

    val context = LocalContext.current
    // Compartilhar artista: abre a barra de compartilhar do Android com link do YouTube Music
    val onShareClick: () -> Unit = {
        val url = "https://music.youtube.com/browse/${state.artistId}"
        val shareText = "Confere esse artista no VzTune \uD83C\uDFB�\n${state.name}\n$url"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, state.name)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Compartilhar artista"))
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                ArtistHeader(
                    state = state,
                    accent = Color.Black,
                    onBackClick = onBackClick,
                    onRandomClick = onRandomClick,
                    onFollowClick = onFollowClick,
                    onShareClick = onShareClick,
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
                            SectionHeader(
                                title = "Músicas populares",
                                onActionClick = seeAll(ArtistListKind.SONGS, state.songsMore),
                            )
                        }
                        itemsIndexed(state.topSongs.take(5), key = { _, t -> "song_${t.id}" }) { index, track ->
                            TrackListRow(
                                track = track,
                                number = index + 1,
                                onClick = { onSongClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                            )
                        }
                        item(key = "songs_space") { Spacer(Modifier.height(SectionGap)) }
                    }

                    rowSection(
                        key = "albums", title = "Álbuns", items = state.albums,
                        onMoreClick = seeAll(ArtistListKind.ALBUMS, state.albumsMore),
                        itemKey = { it.id },
                    ) { album ->
                        CoverCard(
                            title = album.title, subtitle = "Álbum", imageUrl = album.thumbnailUrl,
                            onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

                    rowSection(
                        key = "singles", title = "Singles", items = state.singles,
                        onMoreClick = seeAll(ArtistListKind.SINGLES, state.singlesMore),
                        itemKey = { it.id },
                    ) { single ->
                        CoverCard(
                            title = single.title, subtitle = "Single", imageUrl = single.thumbnailUrl,
                            onClick = { onAlbumClick(single) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

                    rowSection(
                        key = "videos", title = "Vídeos", items = state.videos,
                        onMoreClick = seeAll(ArtistListKind.VIDEOS, state.videosMore),
                        itemKey = { it.id },
                    ) { video ->
                        val playback = nowPlaying.playbackOf(video)
                        MediaListVideoCard(
                            track = video,
                            isPlaying = playback.isPlaying,
                            isPaused = playback.isPaused,
                            onClick = { onVideoClick(video) },
                            onMoreClick = { onTrackInfoClick(video) },
                            modifier = Modifier.width(VideoWidth),
                            // The row already has its own 16dp edge padding; the card's default
                            // inset would push the thumbnails away from the section title.
                            horizontalPadding = 0.dp,
                        )
                    }

                    rowSection(
                        key = "featured", title = "Destacado", items = state.featured,
                        onMoreClick = seeAll(ArtistListKind.FEATURED, state.featuredMore),
                        itemKey = { it.id },
                    ) { playlist ->
                        CoverCard(
                            title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl,
                            onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

                    rowSection(
                        key = "related", title = "Artistas relacionados", items = state.relatedArtists,
                        onMoreClick = null, spacing = 16.dp,
                        itemKey = { it.id },
                    ) { artist ->
                        ArtistCircleCard(
                            name = artist.name, imageUrl = artist.thumbnailUrl,
                            onClick = { onArtistClick(artist) },
                            size = RelatedArtistSize,
                        )
                    }

                    item(key = "description") { ArtistDescription(description = state.description) }
                }
            }

            item(key = "credits") { AppCredits() }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.name,
                thumbnailUrl = state.thumbnailUrl,
                onBackClick = onBackClick,
                dominantColor = Color.Black,
                circularThumbnail = true,
            )
        }
    }
}

/** A titled horizontal row ("Álbuns", "Vídeos"...). Nothing is emitted when [items] is empty. */
private fun <T> LazyListScope.rowSection(
    key: String,
    title: String,
    items: List<T>,
    onMoreClick: (() -> Unit)?,
    itemKey: (T) -> Any,
    spacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_header") { SectionHeader(title = title, onActionClick = onMoreClick) }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = RowPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = itemKey) { itemContent(it) }
        }
    }
    item(key = "${key}_space") { Spacer(Modifier.height(SectionGap)) }
}

/** "About" card: soft translucent surface, expands in place when the text is long. */
@Composable
private fun ArtistDescription(description: String?) {
    var expanded by remember { mutableStateOf(false) }
    val text = description?.takeIf { it.isNotBlank() }
    val canExpand = text != null && text.length > 220

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .animateContentSize()
            .clickable(enabled = canExpand) { expanded = !expanded }
            .padding(16.dp)
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
        )
        if (canExpand) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) "Ver menos" else "Ver mais",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
VZ_EOF_6_FIX2

echo "[7/7] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/CreatePlaylistSheet.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/CreatePlaylistSheet.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/CreatePlaylistSheet.kt" <<'VZ_EOF_7_FIX2'
package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.AddPhotoAlternate

private val SheetBackground = Color(0xFF121829) // Azul marinho igual os outros sheets
private val AccentBlue = Color(0xFF60A5FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistSheet(
    onDismiss: () -> Unit,
    onConfirm: (name: String, coverUri: Uri?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cover = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Nova playlist",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = "Capa da playlist",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp),
                        )
                    } else {
                        Icon(
                            VzIcons.AddPhotoAlternate,
                            contentDescription = "Escolher capa",
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nome da playlist", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sem capa, o VzTune escolhe uma cor aleatória pra sua playlist.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.75f))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(name, cover) },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color(0xFF0A0F1E),
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
VZ_EOF_7_FIX2

echo ""
echo "Pronto! 7 arquivos atualizados:"
echo " - Álbum: só numeração onde ficaria a thumbnail"
echo " - Álbum: barra Lupa, Favoritar, 3 pontinhos (igual playlist)"
echo " - Artista: pills Seguir esq / Aleatório dir, como era antes"
echo " - Artista: botão compartilhar em cima, link YouTube Music"
echo " - Criar playlist: fundo azul marinho 0xFF121829 igual outros sheets"
echo "Roda o build agora."