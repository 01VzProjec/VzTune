#!/bin/bash
# VzTune FIX3 - artista quadrado, album numero sem fundo + equalizador, crash favorito album
# Roda dentro da pasta do projeto: cd ~/VzTune && bash VzTune-FIX3.sh
set -e
echo "== VzTune FIX3 - 9 arquivos =="

echo "[1/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt" <<'VZ_EOF_1_FIX3'
package com.vztechcore.vztune.ui.screens.artist

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
import androidx.compose.foundation.layout.aspectRatio
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
        // Foto quadrada 1:1, nunca esticada como retângulo
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
 * Foto quadrada 1:1, Crop pra nunca esticar, com fade pra preto embaixo.
 * Sem foto, ícone grande no fundo preto.
 */
@Composable
private fun ArtistPhoto(imageUrl: String?, name: String) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
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
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
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
VZ_EOF_1_FIX3

echo "[2/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt" <<'VZ_EOF_2_FIX3'
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
import com.vztechcore.vztune.ui.components.player.PlayingEqualizer
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
                // Álbum: sem fundo cinza, só o número grande onde ficaria a thumbnail.
                // Tocando: equalizador subindo e descendo, sem fundo. Pausado: só •••.
                // Playlist: mostra a thumbnail normal.
                if (index != null) {
                    Box(
                        Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isPlaying -> PlayingEqualizer(
                                color = Color.White,
                                barCount = 4,
                                maxHeight = 24.dp,
                                barWidth = 3.dp,
                                gap = 3.dp,
                            )
                            isPaused -> Text(
                                text = "•••",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            else -> Text(
                                text = "$index",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
VZ_EOF_2_FIX3

echo "[3/9] data/src/main/kotlin/com/vztechcore/vztune/data/prefs/models/StoredPlaylist.kt"
mkdir -p "$(dirname "data/src/main/kotlin/com/vztechcore/vztune/data/prefs/models/StoredPlaylist.kt")"
cat > "data/src/main/kotlin/com/vztechcore/vztune/data/prefs/models/StoredPlaylist.kt" <<'VZ_EOF_3_FIX3'
package com.vztechcore.vztune.data.prefs.models

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import kotlinx.serialization.Serializable

@Serializable
data class StoredPlaylist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val mosaic: List<String> = emptyList(),
    // True quando o favorito era um álbum. Sem isso o álbum favoritado abria como
    // playlist e crashava com key duplicada. Default false mantém favoritos antigos válidos.
    val isAlbum: Boolean = false,
) {
    fun toPlaylist(source: Source = Source.YOUTUBE_MUSIC) = Playlist(
        id = id, title = title, owner = owner, thumbnailUrl = thumbnailUrl,
        source = source, mosaicThumbnails = mosaic,
    )
}
VZ_EOF_3_FIX3

echo "[4/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt" <<'VZ_EOF_4_FIX3'
package com.vztechcore.vztune.ui.screens.medialist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.MediaListPreferencesStore
import com.vztechcore.vztune.data.ytmusic.YTMusicOwnerValidator
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MediaListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val ytMusicRepository: YTMusicRepository,
    private val playerController: PlayerController,
    private val trackDownloader: TrackDownloader,
    private val store: MediaListPreferencesStore,
    private val favoritesStore: FavoritePlaylistsStore,
    private val localStore: LocalPlaylistsStore,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenArtist(val artist: Artist) : NavigationEvent
        data object CloseScreen : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val route = savedStateHandle.toRoute<Routes.MediaList>()

    private val _uiState = MutableStateFlow(
        MediaListUiState(
            listId = route.id,
            title = route.title,
            subtitle = route.subtitle,
            thumbnailUrl = route.thumbnailUrl,
            isAlbum = route.isAlbum,
            isLocal = isLocalPlaylistId(route.id),
        )
    )
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    init {
        val savedSort = store.getSortOrder(route.id)
        val savedManual = store.getManualOrder(route.id)
        _uiState.update { it.copy(sortOrder = savedSort, manualOrder = savedManual) }
        loadContent()
        viewModelScope.launch {
            favoritesStore.contains(route.id).collect { favorite ->
                _uiState.update { it.copy(isFavorite = favorite) }
            }
        }
    }

    private fun observeLocalPlaylist() {
        viewModelScope.launch {
            localStore.ensureRandomDefaultCover(route.id)
            localStore.playlist(route.id).collect { playlist ->
                _uiState.update {
                    if (playlist == null) {
                        it.copy(isLoading = false, errorMessage = "Playlist não encontrada.")
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            title = playlist.name,
                            owner = "Você",
                            subtitle = "Você",
                            thumbnailUrl = playlist.coverPath,
                            mosaicThumbnails = playlist.mosaic,
                            allTracks = playlist.tracks.mapNotNull { stored -> stored.toTrack() },
                        )
                    }
                }
            }
        }
    }

    private fun loadContent() {
        if (_uiState.value.isLocal) {
            observeLocalPlaylist()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (route.isAlbum) {
                    val album = ytMusicRepository.browseAlbum(route.id)

                    val resolvedArtist = listOfNotNull(
                        album.artist,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    val resolvedTitle = album.title ?: _uiState.value.title

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = resolvedTitle,
                            subtitle = resolvedArtist ?: it.subtitle,
                            owner = resolvedArtist,
                            ownerAvatarUrl = null,
                            artistId = album.artistId,
                            year = album.year,
                            thumbnailUrl = album.coverUrl ?: it.thumbnailUrl,
                            description = album.description,
                            views = album.views,
                            // Tira duplicadas (mesmo source + id) pra LazyColumn nunca crashar com key repetida
                            allTracks = album.tracks.distinctBy { "${it.source}_${it.id}" },
                        )
                    }

                    loadMoreAlbumsByArtist(
                        artistName = resolvedArtist,
                        artistId = album.artistId,
                        excludeAlbumTitle = resolvedTitle,
                    )
                } else {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)
                    if (playlist.tracks.isEmpty()) {
                        delay(1000)
                        playlist = ytMusicRepository.browsePlaylist(route.id)
                    }

                    val resolvedOwner = listOfNotNull(
                        playlist.owner,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = playlist.title ?: it.title,
                            subtitle = resolvedOwner ?: it.subtitle,
                            owner = resolvedOwner,
                            ownerAvatarUrl = playlist.ownerAvatarUrl,
                            year = playlist.year,
                            views = playlist.views,
                            originalTrackCount = playlist.trackCount,
                            thumbnailUrl = playlist.coverUrl ?: it.thumbnailUrl,
                            description = playlist.description,
                            mosaicThumbnails = playlist.tracks
                                .asSequence()
                                .mapNotNull { t -> t.thumbnailUrl }
                                .distinct()
                                .take(4)
                                .toList(),
                            allTracks = playlist.tracks.distinctBy { "${it.source}_${it.id}" },
                        )
                    }

                    if (playlist.ownerAvatarUrl.isNullOrBlank() && !resolvedOwner.isNullOrBlank()) {
                        loadPlaylistOwnerAvatar(resolvedOwner)
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    private fun loadPlaylistOwnerAvatar(ownerName: String) {
        viewModelScope.launch {
            val primary = ownerName
                .substringBefore(",")
                .substringBefore("&")
                .trim()
            if (primary.isBlank() || primary.equals("YouTube Music", ignoreCase = true)) return@launch
            val resolved = runCatching {
                ytMusicRepository.findChannelOrArtist(primary)
            }.getOrNull()
            if (resolved != null) {
                _uiState.update { current ->
                    current.copy(
                        artistId = current.artistId ?: resolved.id,
                        ownerAvatarUrl = current.ownerAvatarUrl ?: resolved.thumbnailUrl,
                    )
                }
            }
        }
    }

    private fun loadMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumTitle: String?,
    ) {
        viewModelScope.launch {
            val (artist, moreAlbums) = ytMusicRepository.getMoreAlbumsByArtist(
                artistName = artistName,
                artistId = artistId,
                excludeAlbumId = route.id,
                excludeAlbumTitle = excludeAlbumTitle,
            )
            _uiState.update { current ->
                current.copy(
                    artistId = artist?.id ?: current.artistId,
                    ownerAvatarUrl = artist?.thumbnailUrl ?: current.ownerAvatarUrl,
                    moreAlbumsByArtist = moreAlbums,
                )
            }
        }
    }

    fun onTabSelected(tab: MediaListTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSortOrderSelected(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = order, isShuffling = false) }
        store.setSortOrder(route.id, order)
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentTracks = _uiState.value.getDisplayTracks()
        if (fromIndex !in currentTracks.indices) return
        if (toIndex !in currentTracks.indices) return
        val newList = currentTracks.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        val newOrderIds = newList.map { it.id }
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.MANUAL) }
        store.setManualOrder(route.id, newOrderIds)
        store.setSortOrder(route.id, SortOrder.MANUAL)
    }

    fun startShuffle() { _uiState.update { it.copy(isShuffling = true) } }

    fun shuffleOnce() {
        val currentTracks = _uiState.value.allTracks
        if (currentTracks.isEmpty()) return
        val newOrderIds = currentTracks.map { it.id }.shuffled()
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.SHUFFLE) }
    }

    fun stopShuffle() {
        _uiState.update { it.copy(isShuffling = false) }
        store.setManualOrder(route.id, _uiState.value.manualOrder)
        store.setSortOrder(route.id, SortOrder.SHUFFLE)
    }

    fun onTrackClick(track: Track) {
        val sorted = _uiState.value.getDisplayTracks()
        val idx = sorted.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) playerController.playQueue(sorted, idx)
        else playerController.playQueue(listOf(track), 0)
    }

    fun onFavoriteClick() {
        val state = _uiState.value
        viewModelScope.launch {
            favoritesStore.toggle(
                StoredPlaylist(
                    id = route.id,
                    title = state.title,
                    owner = state.owner,
                    thumbnailUrl = state.thumbnailUrl,
                    mosaic = state.mosaicThumbnails,
                    isAlbum = state.isAlbum,
                )
            )
        }
    }

    fun onPlayNextClick(track: Track) { playerController.addToQueueNext(track) }

    fun onPlayNextAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.asReversed().forEach { playerController.addToQueueNext(it) }
    }

    fun onAddToQueueAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.forEach { playerController.addToQueueEnd(it) }
    }

    fun onDownloadAll() {
        val tracks = _uiState.value.allTracks
        if (tracks.isNotEmpty()) {
            trackDownloader.downloadAll(tracks)
        }
    }

    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }
        val artistName = state.owner?.takeIf { it.isNotBlank() }
        if (directId != null && artistName != null) {
            viewModelScope.launch {
                _navigationEvent.emit(
                    NavigationEvent.OpenArtist(
                        Artist(
                            id = directId,
                            name = artistName,
                            thumbnailUrl = state.ownerAvatarUrl,
                            source = Source.YOUTUBE_MUSIC,
                        )
                    )
                )
            }
            return
        }
        if (artistName != null) {
            onOpenArtistByName(artistName)
        }
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.findChannelOrArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found))
            }
        }
    }

    fun onDeleteLocalPlaylist() {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch {
            localStore.remove(route.id)
            _navigationEvent.emit(NavigationEvent.CloseScreen)
        }
    }

    fun onRenameLocalPlaylist(newTitle: String) {
        if (!_uiState.value.isLocal) return
        val clean = newTitle.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            localStore.rename(route.id, clean)
        }
    }

    fun onChangeLocalPlaylistCover(coverUri: Uri) {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val coversDir = File(context.filesDir, "playlist_covers").apply {
                    if (!exists()) mkdirs()
                }
                val safeId = route.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destFile = File(coversDir, "${safeId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(coverUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    _uiState.value.thumbnailUrl?.let { oldPath ->
                        val oldFile = File(oldPath)
                        if (oldFile.exists() && oldFile.parentFile == coversDir) {
                            runCatching { oldFile.delete() }
                        }
                    }
                    localStore.updateCover(route.id, destFile.absolutePath)
                }
            }
        }
    }

    fun onPlayAll() {
        val sorted = _uiState.value.getDisplayTracks()
        if (sorted.isNotEmpty()) playerController.playQueue(sorted, 0)
    }

    fun onShuffle() {
        val shuffled = _uiState.value.filteredTracks.shuffled()
        if (shuffled.isNotEmpty()) playerController.playQueue(shuffled, 0)
    }
}
VZ_EOF_4_FIX3

echo "[5/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryUiState.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryUiState.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryUiState.kt" <<'VZ_EOF_5_FIX3'
package com.vztechcore.vztune.ui.screens.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.StarOutline
import com.vztechcore.vztune.ui.icons.media.CloudDownload
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.TrendingUp
import com.vztechcore.vztune.ui.icons.navigation.LibraryMusicFilled

/** The five tabs of the Library screen. */
enum class LibraryTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Sua biblioteca", VzIcons.LibraryMusicFilled),
    VZTUNE("Playlists VzTune", VzIcons.StarOutline),
    MY_PLAYLISTS("Suas playlists", VzIcons.PlaylistAdd),
    FAVORITES("Playlists favoritas", VzIcons.FavoriteOutline),
    DOWNLOADS("Playlists baixadas", VzIcons.Download),
}

/** The four shortcut cards on top of "Sua biblioteca"; each one opens its own list. */
enum class LibraryShortcut(val label: String, val icon: ImageVector, val accent: Color) {
    FAVORITES("Favoritos", VzIcons.FavoriteFilled, Color(0xFFEC4899)),
    MOST_PLAYED("Mais ouvidas", VzIcons.TrendingUp, Color(0xFF8B5CF6)),
    FOLLOWED("Seguindo", VzIcons.Groups, Color(0xFF38BDF8)),
    DOWNLOADS("Baixadas", VzIcons.CloudDownload, Color(0xFF10B981)),
}

/** One playlist card of the Library grids (local, favorite or downloaded). */
data class LibraryPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val mosaic: List<String> = emptyList(),
    val isAlbum: Boolean = false,
)

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.OVERVIEW,
    val recentTracks: List<Track> = emptyList(),
    val myPlaylists: List<LibraryPlaylistItem> = emptyList(),
    val favoritePlaylists: List<LibraryPlaylistItem> = emptyList(),
    val downloadedPlaylists: List<LibraryPlaylistItem> = emptyList(),
)
VZ_EOF_5_FIX3

echo "[6/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt" <<'VZ_EOF_6_FIX3'
package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.data.prefs.store.PlaylistCoverStorage
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.newLocalPlaylistId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    historyStore: PlayHistoryStore,
    private val localStore: LocalPlaylistsStore,
    favoritesStore: FavoritePlaylistsStore,
    private val coverStorage: PlaylistCoverStorage,
    private val playerController: PlayerController,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTab.OVERVIEW)

    private var recentTracks: List<Track> = emptyList()

    val uiState: StateFlow<LibraryUiState> = combine(
        selectedTab,
        historyStore.tracks,
        localStore.items,
        favoritesStore.items,
    ) { tab, recent, mine, favorites ->
        recentTracks = recent
        LibraryUiState(
            selectedTab = tab,
            recentTracks = recent,
            myPlaylists = mine.map { it.toItem() },
            favoritePlaylists = favorites.map { it.toItem() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onTabSelected(tab: LibraryTab) {
        selectedTab.value = tab
    }

    fun onTrackClick(track: Track) {
        val index = recentTracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (index >= 0) playerController.playQueue(recentTracks, index)
        else playerController.playQueue(listOf(track), 0)
    }

    /** Creates a local playlist; without a cover it shows the thumbnails of its tracks. */
    fun createPlaylist(name: String, coverUri: Uri?) {
        viewModelScope.launch {
            val id = newLocalPlaylistId()
            val coverPath = coverUri?.let { coverStorage.save(id, it) }
            localStore.addFirst(LocalPlaylist(id = id, name = name.trim(), coverPath = coverPath))
        }
    }

    private fun LocalPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = name,
        subtitle = "Sua playlist",
        imageUrl = coverPath ?: mosaic.firstOrNull(),
        mosaic = if (coverPath == null) mosaic else emptyList(),
    )

    private fun StoredPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = title,
        subtitle = owner,
        imageUrl = thumbnailUrl,
        mosaic = mosaic,
        isAlbum = isAlbum,
    )
}
VZ_EOF_6_FIX3

echo "[7/9] app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" <<'VZ_EOF_7_FIX3'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.EclipseBackground
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.sheets.TrackMenuContext
import com.vztechcore.vztune.ui.components.sheets.TrackMenuViewModel
import com.vztechcore.vztune.ui.components.sheets.TrackOptionsSheet
import com.vztechcore.vztune.ui.screens.artist.ArtistScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistViewModel
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListScreen
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadBannerViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadsScreen
import com.vztechcore.vztune.ui.screens.downloads.DownloadsViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryScreen
import com.vztechcore.vztune.ui.screens.library.LibraryViewModel
import com.vztechcore.vztune.ui.screens.library.list.LibraryListScreen
import com.vztechcore.vztune.ui.screens.library.list.LibraryListViewModel
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel
import com.vztechcore.vztune.ui.screens.medialist.MediaListScreen
import com.vztechcore.vztune.ui.screens.medialist.MediaListViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoScreen
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface

private data class ActiveTrackMenu(
    val track: Track,
    val context: TrackMenuContext = TrackMenuContext.DEFAULT,
    val localPlaylistId: String? = null,
)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentTab = when {
        backStackEntry?.destination?.hasRoute<Routes.Search>() == true -> AppTab.SEARCH
        backStackEntry?.destination?.hasRoute<Routes.Analytics>() == true -> AppTab.ANALYTICS
        backStackEntry?.destination?.hasRoute<Routes.Library>() == true -> AppTab.LIBRARY
        else -> AppTab.HOME
    }

    val isMediaListScreen = backStackEntry?.destination?.hasRoute<Routes.MediaList>() == true
    val isArtistScreen = backStackEntry?.destination?.hasRoute<Routes.Artist>() == true
    val isArtistListScreen = backStackEntry?.destination?.hasRoute<Routes.ArtistList>() == true
    val isLibraryListScreen = backStackEntry?.destination?.hasRoute<Routes.LibraryList>() == true
    val isDownloadsScreen = backStackEntry?.destination?.hasRoute<Routes.Downloads>() == true
    val isDetailScreen = isMediaListScreen || isArtistScreen || isArtistListScreen || isLibraryListScreen || isDownloadsScreen
    val isTrackInfoScreen = backStackEntry?.destination?.hasRoute<Routes.TrackInfo>() == true

    val playback = LocalNowPlaying.current
    val track = playback.currentTrack

    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val bannerVm: DownloadBannerViewModel = hiltViewModel()
    val menuVm: TrackMenuViewModel = hiltViewModel()

    val dominantColor = rememberDominantColor(track?.thumbnailUrl)
    val isMinimized by miniPlayerVm.isMinimized.collectAsStateWithLifecycle()
    val bannerTrack by bannerVm.bannerTrack.collectAsStateWithLifecycle()

    val favoriteKeys by menuVm.favoriteKeys.collectAsStateWithLifecycle()
    val downloadedKeys by menuVm.downloadedKeys.collectAsStateWithLifecycle()
    val activeDownloads by menuVm.activeDownloads.collectAsStateWithLifecycle()
    val localPlaylists by menuVm.localPlaylists.collectAsStateWithLifecycle()
    val resolvedAlbum by menuVm.resolvedAlbum.collectAsStateWithLifecycle()

    var activeMenu by remember { mutableStateOf<ActiveTrackMenu?>(null) }

    fun openTrackMenu(
        targetTrack: Track,
        context: TrackMenuContext = TrackMenuContext.DEFAULT,
        localPlaylistId: String? = null,
    ) {
        menuVm.resolveAlbumFor(targetTrack)
        activeMenu = ActiveTrackMenu(targetTrack, context, localPlaylistId)
    }

    LaunchedEffect(Unit) {
        menuVm.navigationEvent.collect { event ->
            when (event) {
                is TrackMenuViewModel.MenuNavigationEvent.OpenAlbum -> navController.openAlbum(event.album)
                is TrackMenuViewModel.MenuNavigationEvent.OpenArtist -> navController.navigate(
                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                )
            }
        }
    }

    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            miniPlayerVm.setMinimized(true)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30f) miniPlayerVm.setMinimized(true)
                else if (available.y > 30f) miniPlayerVm.setMinimized(false)
                return Offset.Zero
            }
        }
    }

    activeMenu?.let { menu ->
        val key = StoredTrack.from(menu.track).key
        TrackOptionsSheet(
            track = menu.track,
            menuContext = menu.context,
            resolvedAlbum = resolvedAlbum,
            isFavorite = key in favoriteKeys,
            isDownloaded = key in downloadedKeys,
            downloadItem = activeDownloads[key],
            localPlaylists = localPlaylists,
            onDismiss = { activeMenu = null },
            onRemoveFromRecentHistory = menuVm::onRemoveFromRecentHistory,
            onRemoveFromLocalPlaylist = { t ->
                menu.localPlaylistId?.let { pid -> menuVm.onRemoveFromLocalPlaylist(pid, t) }
            },
            onToggleFavorite = menuVm::onToggleFavorite,
            onAddToPlaylist = menuVm::onAddToPlaylist,
            onPlayNext = menuVm::onPlayNext,
            onAddToQueue = menuVm::onAddToQueue,
            onDownload = menuVm::onDownload,
            onRemoveDownload = menuVm::onRemoveDownload,
            onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
            onOpenAlbum = { album -> navController.openAlbum(album) },
            onOpenAlbumForTrack = menuVm::onOpenAlbumForTrack,
            onOpenArtistByName = menuVm::onOpenArtistByName,
            onTrackInfo = { t -> navController.navigateToTrackInfo(t) },
        )
    }

    EclipseBackground(baseColor = dominantColor) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
            ) {
                composable<Routes.Home> { Placeholder("Home — coming soon") }
                composable<Routes.Analytics> { Placeholder("Analytics — coming soon") }

                composable<Routes.Library> {
                    val vm: LibraryViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        state = state,
                        onTabSelected = vm::onTabSelected,
                        onShortcutClick = { navController.navigate(Routes.LibraryList(it.name)) },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(t, context = TrackMenuContext.RECENT_HISTORY)
                        },
                        onPlaylistClick = { item ->
                            navController.navigate(
                                Routes.MediaList(
                                    id = item.id,
                                    // Favorito de álbum abre como álbum, senão crashava com key duplicada
                                    isAlbum = item.isAlbum,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.imageUrl,
                                )
                            )
                        },
                        onCreatePlaylist = vm::createPlaylist,
                    )
                }

                composable<Routes.LibraryList> {
                    val vm: LibraryListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onShuffleClick = vm::onShuffleClick,
                        onPlayAllClick = vm::onPlayAllClick,
                        onDownloadsHistoryClick = { navController.navigate(Routes.Downloads) },
                    )
                }

                composable<Routes.Downloads> {
                    val vm: DownloadsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DownloadsScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTogglePauseResume = vm::onTogglePauseResume,
                        onCancelDownload = vm::onCancelDownload,
                        onDeleteDownload = vm::onDeleteDownload,
                        onPlayTrack = vm::onPlayTrack,
                        onPauseAll = vm::onPauseAll,
                        onResumeAll = vm::onResumeAll,
                        onCancelAll = vm::onCancelAll,
                    )
                }

                composable<Routes.Search> {
                    val vm: SearchViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is SearchViewModel.NavigationEvent.OpenPlaylist -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = false,
                                        title = event.title,
                                        subtitle = event.owner,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenAlbum -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = true,
                                        title = event.title,
                                        subtitle = event.artist,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                            }
                        }
                    }

                    SearchScreen(
                        state = state,
                        favoriteKeys = favoriteKeys,
                        downloadedKeys = downloadedKeys,
                        activeDownloads = activeDownloads,
                        localPlaylists = localPlaylists,
                        resolvedAlbum = resolvedAlbum,
                        onQueryChange = vm::onQueryChange,
                        onFocusChanged = vm::onFocusChanged,
                        onFilterSelect = vm::onFilterSelect,
                        onSubmit = vm::onSubmit,
                        onRefresh = vm::onRefresh,
                        onRemoveHistory = vm::onRemoveHistory,
                        onClearHistory = vm::onClearHistory,
                        onLoadMore = vm::onLoadMore,
                        onHitClick = vm::onHitClick,
                        onPlayNextClick = vm::onPlayNextClick,
                        onAddToQueueClick = vm::onAddToQueueClick,
                        onToggleFavorite = vm::onToggleFavorite,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadTrack = vm::onDownloadTrack,
                        onRemoveDownload = vm::onRemoveDownload,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onPrepareTrackMenu = menuVm::resolveAlbumFor,
                        onOpenAlbum = { album -> navController.openAlbum(album) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                    )
                }

                composable<Routes.MediaList> {
                    val vm: MediaListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val playlistId = state.listId
                    val menuCtx = when {
                        state.isAlbum -> TrackMenuContext.DEFAULT
                        state.isLocal || isLocalPlaylistId(playlistId) -> TrackMenuContext.LOCAL_PLAYLIST
                        else -> TrackMenuContext.EXTERNAL_PLAYLIST
                    }

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is MediaListViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                                )
                                MediaListViewModel.NavigationEvent.CloseScreen -> navController.popBackStack()
                            }
                        }
                    }

                    MediaListScreen(
                        state = state,
                        activeDownloads = activeDownloads,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTrackClick = vm::onTrackClick,
                        onPlayAllClick = vm::onPlayAll,
                        onShuffleClick = vm::onShuffle,
                        onSortSelected = vm::onSortOrderSelected,
                        onReorder = vm::onReorder,
                        onStartShuffle = vm::startShuffle,
                        onShuffleOnce = vm::shuffleOnce,
                        onStopShuffle = vm::stopShuffle,
                        onPlayNextClick = vm::onPlayNextClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(
                                targetTrack = t,
                                context = menuCtx,
                                localPlaylistId = playlistId.takeIf { menuCtx == TrackMenuContext.LOCAL_PLAYLIST },
                            )
                        },
                        onFavoriteClick = vm::onFavoriteClick,
                        onPlayNextAll = vm::onPlayNextAll,
                        onAddToQueueAll = vm::onAddToQueueAll,
                        onDownloadAll = vm::onDownloadAll,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onOpenArtist = vm::onOpenArtist,
                        onAlbumClick = { album ->
                            if (album.id != state.listId) navController.openAlbum(album)
                        },
                        onDeleteLocalPlaylist = vm::onDeleteLocalPlaylist,
                        onRenameLocalPlaylist = vm::onRenameLocalPlaylist,
                        onChangeLocalPlaylistCover = vm::onChangeLocalPlaylistCover,
                    )
                }

                composable<Routes.Artist> {
                    val vm: ArtistViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onRandomClick = vm::onRandomClick,
                        onFollowClick = vm::onFollowClick,
                        onSongClick = vm::onSongClick,
                        onVideoClick = vm::onVideoClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onSeeAllClick = { kind, ref ->
                            navController.navigate(
                                Routes.ArtistList(
                                    artistId = state.artistId,
                                    artistName = state.name,
                                    kind = kind.name,
                                    browseId = ref.browseId,
                                    params = ref.params,
                                )
                            )
                        },
                    )
                }

                composable<Routes.ArtistList> {
                    val vm: ArtistListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onLoadMore = vm::onLoadMore,
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                    )
                }

                composable<Routes.TrackInfo> {
                    val vm: TrackInfoViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    TrackInfoScreen(
                        state = state,
                        isDownloaded = state.isDownloaded,
                        onBackClick = { navController.popBackStack() },
                        onFavoriteClick = vm::onFavoriteClick,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadClick = {
                            if (state.isDownloaded) vm.onRemoveDownloadClick() else vm.onDownloadClick()
                        },
                    )
                }
            }

            PlaybackChrome(
                navController = navController,
                currentTab = currentTab,
                playback = playback,
                track = track,
                dominantColor = dominantColor,
                miniPlayerVm = miniPlayerVm,
                isMinimized = isMinimized,
                isDetailScreen = isDetailScreen,
                isTrackInfoScreen = isTrackInfoScreen,
                bannerTrack = bannerTrack,
                onDismissBanner = bannerVm::dismissBanner,
                onViewDownloadsClick = { navController.navigate(Routes.Downloads) },
            )
        }
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}
VZ_EOF_7_FIX3

echo "[8/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt" <<'VZ_EOF_8_FIX3'
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
                    // Key com índice: mesmo se o InnerTube devolver a mesma faixa 2x, nunca duplica e nunca crasha
                    key = { index, t -> "${t.source.name}_${t.id}_$index" },
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
                        val itemKey = "${track.source.name}_${track.id}_$index"
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
VZ_EOF_8_FIX3

echo "[9/9] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt" <<'VZ_EOF_9_FIX3'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

@Composable
internal fun MediaListSearchOverlay(
    allTracks: List<Track>,
    isAlbum: Boolean = false,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val nowPlaying = LocalNowPlaying.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filteredTracks = remember(allTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allTracks
        else allTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable {
                        keyboardController?.hide()
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.Close,
                    contentDescription = "Fechar pesquisa",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VzIcons.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (isAlbum) "Pesquisar no álbum..." else "Pesquisar na playlist...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = VzIcons.Close,
                        contentDescription = "Limpar texto",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhuma faixa encontrada para \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = filteredTracks,
                    key = { index, t -> "search_${t.source.name}_${t.id}_$index" },
                ) { _, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val originalIndex = if (isAlbum) {
                        allTracks.indexOfFirst { it.id == track.id && it.source == track.source }
                            .takeIf { it >= 0 }
                            ?.plus(1)
                    } else {
                        null
                    }

                    MediaListTrackRow(
                        track = track,
                        index = originalIndex,
                        isPlaying = isCurrent && nowPlaying.isPlaying,
                        isPaused = isCurrent && !nowPlaying.isPlaying,
                        onClick = {
                            keyboardController?.hide()
                            onTrackClick(track)
                        },
                        onMoreClick = { onTrackInfoClick(track) },
                        onPlayNext = { onPlayNextClick(track) },
                        showDragHandle = false,
                        dragHandleModifier = Modifier,
                    )
                }
                item(key = "credits_search") { AppCredits() }
            }
        }
    }
}
VZ_EOF_9_FIX3

echo ""
echo "Pronto! 9 arquivos atualizados."
echo " - Artista: foto quadrada 1:1 Crop, sem esticar"
echo " - Album: numero grande sem fundo, tocando = equalizador, pausado = ..."
echo " - Crash favorito album: abre como album + key unica + dedupe"
echo "Roda o build agora."
