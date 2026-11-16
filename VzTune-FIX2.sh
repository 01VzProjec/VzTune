#!/bin/bash
# VzTune FIX6 - 1) ícone de rádio / 2) canal real (método SimpMusic) / 3) Search: 1 letra + instantâneo + prefetch de todas as abas
# Rode na RAIZ do projeto. Aplica sobre FIX5 + HOTFIX + HOTFIX2 já aplicados. Sobrescreve 8 arquivos (1 novo).
# - Radio.kt (NOVO): ícone de rádio do YT Music, mesmo formato dos outros ícones (VzIcons.icon + PathParser).
# - TrackOptionsSheet.kt: "Iniciar rádio" usa VzIcons.Radio (o "Reproduzir como próxima" continua com PlayCircleFilled).
# - TrackMenuViewModel.kt + YTMusicRepository.kt + ArtistViewModel.kt + YouTubeRepository.kt: canal via channelId do próprio vídeo
#   (InnerTube player.videoDetails.channelId, fallback NewPipe uploaderUrl). Nunca busca por nome. Canal sem página de
#   artista no YT Music abre como canal (vídeos DELE via NewPipe), não como artista parecido.
# - SearchViewModel.kt + MusicRepository.kt + YouTubeRepository.kt: sugestões a partir de 1 letra, debounce 400ms -> 120ms,
#   histórico local instantâneo (0ms), e prefetch sequencial de TODAS as abas ao pesquisar. Cada aba tem sua sessão
#   NewPipe por searchId, então o prefetch não quebra o "carregar mais" da aba aberta.
set -e

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/icons/media/Radio.kt" <<'FIX6EOF'
package com.vztechcore.vztune.ui.icons.media

import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.ui.icons.VzIcons

val VzIcons.Radio: ImageVector
    get() = VzIcons.icon(
        name = "Radio",
        pathData = "M4,12q0,1.4 0.42,2.64t1.23,2.24q0.28,0.35 0.28,0.76t-0.3,0.71q-0.3,0.3-0.72,0.29T4.2,18.27q-1.05-1.3-1.63-2.89T2,12T2.58,8.6T4.2,5.72Q4.48,5.38 4.9,5.36T5.63,5.65t0.3,0.71q0,0.41-0.28,0.76Q4.85,8.15 4.43,9.39T4,12Zm4,0q0,0.57 0.15,1.09t0.43,0.96Q8.8,14.4 8.79,14.83q-0.01,0.43-0.31,0.72q-0.3,0.3-0.72,0.29Q7.33,15.83 7.1,15.48q-0.53-0.72-0.81-1.63T6,12q0-0.98 0.29-1.86T7.1,8.52Q7.35,8.17 7.76,8.17t0.71,0.3q0.3,0.3 0.31,0.71T8.58,9.95Q8.3,10.4 8.15,10.91T8,12Zm2.59,1.41Q10,12.83 10,12t0.59-1.41T12,10t1.41,0.59Q14,11.18 14,12t-0.59,1.41T12,14t-1.41-0.59ZM16,12q0-0.58-0.15-1.09T15.43,9.95Q15.2,9.6 15.23,9.17t0.32-0.72t0.7-0.29q0.4,0.01 0.65,0.36q0.52,0.73 0.81,1.61T18,12q0,0.95-0.29,1.85T16.9,15.48q-0.23,0.35-0.65,0.35t-0.72-0.3t-0.31-0.71t0.21-0.76q0.28-0.45 0.43-0.96Q16,12.58 16,12Zm4,0q0-1.4-0.42-2.64q-0.43-1.24-1.23-2.24q-0.28-0.35-0.29-0.76t0.29-0.71t0.74-0.29T19.8,5.72Q20.85,7 21.43,8.6T22,12t-0.57,3.39T19.8,18.27q-0.27,0.35-0.7,0.36t-0.73-0.29t-0.3-0.71q0-0.41 0.28-0.76q0.8-1.03 1.23-2.26Q20,13.38 20,12Z"
    )
FIX6EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt" <<'FIX6EOF'
package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Info
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Album
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.media.Radio
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled

private val SheetContainerColor = Color(0xFF121829)
private val DownloadGreen = Color(0xFF4ADE80)
private val DangerRed = Color(0xFFF87171)

enum class TrackMenuContext {
    DEFAULT,
    RECENT_HISTORY,
    EXTERNAL_PLAYLIST,
    LOCAL_PLAYLIST,
}

/**
 * Context menu opened from the three-dots button on any track card across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    menuContext: TrackMenuContext = TrackMenuContext.DEFAULT,
    resolvedAlbum: Album? = null,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    downloadItem: TrackDownloadItem? = null,
    localPlaylists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onRemoveFromRecentHistory: (Track) -> Unit = {},
    onRemoveFromLocalPlaylist: (Track) -> Unit = {},
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (playlistId: String, track: Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (Track) -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenAlbumForTrack: (Track) -> Unit = {},
    onOpenArtistByName: (String) -> Unit,
    onOpenArtistForTrack: (Track, String) -> Unit = { _, name -> onOpenArtistByName(name) },
    onStartRadio: (Track) -> Unit = {},
    onTrackInfo: (Track) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showRemoveDownloadSheet by remember { mutableStateOf(false) }

    val artists = remember(track.artist) { parseTrackArtists(track.artist) }
    val isDownloading = downloadItem?.status == DownloadStatus.DOWNLOADING ||
        downloadItem?.status == DownloadStatus.QUEUED ||
        downloadItem?.status == DownloadStatus.PAUSED

    val addToPlaylistLabel = when (menuContext) {
        TrackMenuContext.EXTERNAL_PLAYLIST, TrackMenuContext.LOCAL_PLAYLIST -> "Adicionar à outra playlist"
        else -> "Adicionar à playlist"
    }

    if (showRemoveDownloadSheet) {
        RemoveDownloadSheet(
            track = track,
            onConfirmRemove = {
                showRemoveDownloadSheet = false
                onRemoveDownload(track)
                onDismiss()
            },
            onDismiss = { showRemoveDownloadSheet = false },
        )
        return
    }

    if (showPlaylistPicker) {
        PlaylistPickerSheet(
            title = addToPlaylistLabel,
            playlists = localPlaylists,
            onPick = { playlist ->
                onAddToPlaylist(playlist.id, track)
                showPlaylistPicker = false
                onDismiss()
            },
            onDismiss = { showPlaylistPicker = false },
        )
        return
    }

    if (showArtistPicker) {
        ArtistPickerSheet(
            artists = artists,
            onPick = { artistName ->
                showArtistPicker = false
                onDismiss()
                // Passa a faixa: o resolver só aceita quem tem ESSA faixa no catálogo
                onOpenArtistForTrack(track, artistName)
            },
            onDismiss = { showArtistPicker = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // Context-specific top removal actions
            if (menuContext == TrackMenuContext.RECENT_HISTORY) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Apagar",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromRecentHistory(track)
                        onDismiss()
                    },
                )
            }

            if (menuContext == TrackMenuContext.LOCAL_PLAYLIST) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir faixa da playlist",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromLocalPlaylist(track)
                        onDismiss()
                    },
                )
            }

            // 1. Favorite / Unfavorite
            SheetOptionRow(
                icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                iconTint = if (isFavorite) Color(0xFFEC4899) else Color.White,
                label = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = {
                    onToggleFavorite(track)
                    onDismiss()
                },
            )

            // 2. Add to playlist / Add to another playlist
            SheetOptionRow(
                icon = VzIcons.PlaylistAdd,
                label = addToPlaylistLabel,
                onClick = { showPlaylistPicker = true },
            )

            // 3. Play next
            SheetOptionRow(
                icon = VzIcons.PlayCircleFilled,
                label = "Reproduzir como próxima faixa",
                onClick = {
                    onPlayNext(track)
                    onDismiss()
                },
            )

            // 4. Add to queue
            SheetOptionRow(
                icon = VzIcons.QueueMusic,
                label = "Adicionar à fila",
                onClick = {
                    onAddToQueue(track)
                    onDismiss()
                },
            )

            // 4b. FIX 3: Iniciar rádio (Mix infinito) - a partir de qualquer faixa,
            // em qualquer lugar (busca, playlist, álbum, artista). Nome "Iniciar rádio":
            // é o termo do YT Music / Spotify, mais profissional que "Mix".
            // Ícone de rádio (ondas) do YT Music, não o play genérico.
            SheetOptionRow(
                icon = VzIcons.Radio,
                label = "Iniciar rádio",
                subtitle = "Mix infinito a partir desta faixa",
                onClick = {
                    onStartRadio(track)
                    onDismiss()
                },
            )

            // 5. Download / Downloading / Downloaded
            when {
                isDownloading -> {
                    val progress = downloadItem?.progress ?: 0f
                    val pct = downloadItem?.percentage ?: 0
                    val statusLabel = if (downloadItem?.status == DownloadStatus.PAUSED) {
                        "Download pausado ($pct%)"
                    } else if (pct > 0) {
                        "Baixando faixa ($pct%)"
                    } else {
                        "Baixando faixa"
                    }
                    SheetDownloadProgressRow(
                        progress = progress,
                        label = statusLabel,
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                }
                isDownloaded -> {
                    SheetOptionRow(
                        icon = VzIcons.DownloadDone,
                        iconTint = DownloadGreen,
                        label = "Faixa baixada",
                        labelColor = Color.White,
                        onClick = { showRemoveDownloadSheet = true },
                    )
                }
                else -> {
                    SheetOptionRow(
                        icon = VzIcons.Download,
                        iconTint = Color.White,
                        label = "Baixar faixa",
                        onClick = {
                            onDownload(track)
                            onDismiss()
                        },
                    )
                }
            }

            // 6. Share
            SheetOptionRow(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    shareTrack(context, track)
                    onDismiss()
                },
            )

            // 7. Go to album (shows immediately when track.album is known or once resolved)
            val albumTitle = resolvedAlbum?.title ?: track.album?.takeIf {
                it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
            }
            if (!albumTitle.isNullOrBlank()) {
                SheetOptionRow(
                    icon = VzIcons.Album,
                    label = "Álbum",
                    subtitle = albumTitle,
                    onClick = {
                        onDismiss()
                        if (resolvedAlbum != null) {
                            onOpenAlbum(resolvedAlbum)
                        } else {
                            onOpenAlbumForTrack(track)
                        }
                    },
                )
            }

            // 8. Go to artist(s) / canal
            // FIX 2: vídeo do YouTube -> "Ir para o canal" (resolve o canal real via NewPipe).
            // YT Music -> "Ir para o(s) artista(s)" como antes.
            // Sem canal real, o ViewModel não navega (nunca chuta artista por nome).
            if (artists.isNotEmpty()) {
                val isYouTubeVideo = track.source == Source.YOUTUBE
                val hasMultiple = artists.size > 1 && !isYouTubeVideo
                SheetOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = when {
                        isYouTubeVideo -> "Ir para o canal"
                        hasMultiple -> "Ir para os artistas"
                        else -> "Ir para o artista"
                    },
                    subtitle = if (hasMultiple) artists.joinToString(", ") else artists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistForTrack(track, artists.first())
                        }
                    },
                )
            }

            // 9. Track details
            SheetOptionRow(
                icon = VzIcons.Info,
                label = "Ver informações da faixa",
                onClick = {
                    onDismiss()
                    onTrackInfo(track)
                },
            )
        }
    }
}

/**
 * Confirmation bottom sheet displayed when tapping "Faixa baixada" or the delete icon in completed downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveDownloadSheet(
    track: Track,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VzIcons.DownloadDone,
                        contentDescription = null,
                        tint = DownloadGreen,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Faixa salva para reprodução offline",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Esta música já está armazenada no seu dispositivo para você ouvir mesmo sem conexão com a internet. Deseja removê-la dos seus downloads?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Text(
                            text = "Manter faixa",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onConfirmRemove,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed.copy(alpha = 0.18f),
                            contentColor = DangerRed,
                        ),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(
                            imageVector = VzIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Apagar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSheetHeader(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (track.thumbnailUrl != null) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SheetDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(
                imageVector = VzIcons.Download,
                contentDescription = null,
                tint = DownloadGreen,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    title: String = "Adicionar à playlist",
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "Você ainda não criou nenhuma playlist. Crie uma na Biblioteca, em \"Suas playlists\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPickerSheet(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Escolher artista",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            artists.forEach { artistName ->
                SheetOptionRow(
                    icon = VzIcons.Artist,
                    label = artistName,
                    onClick = { onPick(artistName) },
                )
            }
        }
    }
}

private fun parseTrackArtists(raw: String): List<String> {
    if (raw.isBlank() || raw.equals("Desconhecido", ignoreCase = true)) return emptyList()
    return raw
        .split(Regex("\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private val SharePhrases = listOf(
    "Ouvindo no VzTune 🎧",
    "Escuta essa faixa no VzTune ✨",
    "Descobri no VzTune 🔥",
    "Tocando agora no VzTune 🎵",
    "Recomendo ouvir no VzTune 🚀",
    "Vibe boa no VzTune 🎶",
)

private fun shareTrack(context: Context, track: Track) {
    val url = if (track.source == Source.YOUTUBE) {
        "https://www.youtube.com/watch?v=${track.id}"
    } else {
        "https://music.youtube.com/watch?v=${track.id}"
    }
    val phrase = SharePhrases.random()
    val shareText = "$phrase\n${track.title} — ${track.artist}\n$url"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(sendIntent, "Compartilhar faixa")
    context.startActivity(chooser)
}
FIX6EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt" <<'FIX6EOF'
package com.vztechcore.vztune.ui.components.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel that powers the three-dots context menu across all screens
 * (Search, Library, LibraryList, MediaList, Artist, ArtistList) with album resolution.
 */
@HiltViewModel
class TrackMenuViewModel @Inject constructor(
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val playHistoryStore: PlayHistoryStore,
    private val trackDownloader: TrackDownloader,
    private val playerController: PlayerController,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    sealed interface MenuNavigationEvent {
        data class OpenAlbum(val album: Album) : MenuNavigationEvent
        data class OpenArtist(val artist: Artist) : MenuNavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<MenuNavigationEvent>()
    val navigationEvent: SharedFlow<MenuNavigationEvent> = _navigationEvent.asSharedFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.downloadedKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val albumCache = mutableMapOf<String, Album?>()
    private var activeAlbumKey: String? = null

    private val _resolvedAlbum = MutableStateFlow<Album?>(null)
    val resolvedAlbum: StateFlow<Album?> = _resolvedAlbum.asStateFlow()

    fun resolveAlbumFor(track: Track) {
        val key = StoredTrack.from(track).key
        activeAlbumKey = key
        if (albumCache.containsKey(key)) {
            _resolvedAlbum.value = albumCache[key]
            return
        }
        _resolvedAlbum.value = null
        viewModelScope.launch {
            val found = runCatching { ytMusicRepository.findAlbumForTrack(track) }.getOrNull()
            albumCache[key] = found
            if (activeAlbumKey == key) {
                _resolvedAlbum.value = found
            }
        }
    }

    fun onOpenAlbumForTrack(track: Track) {
        val key = StoredTrack.from(track).key
        val cached = albumCache[key]
        if (cached != null) {
            viewModelScope.launch {
                _navigationEvent.emit(MenuNavigationEvent.OpenAlbum(cached))
            }
            return
        }
        viewModelScope.launch {
            val found = runCatching { ytMusicRepository.findAlbumForTrack(track) }.getOrNull()
            albumCache[key] = found
            if (activeAlbumKey == key) {
                _resolvedAlbum.value = found
            }
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenAlbum(found))
            }
        }
    }

    fun onToggleFavorite(track: Track) {
        viewModelScope.launch { favoriteTracksStore.toggle(track) }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.addTrack(playlistId, track) }
    }

    fun onRemoveFromLocalPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.removeTrack(playlistId, track) }
    }

    fun onRemoveFromRecentHistory(track: Track) {
        viewModelScope.launch {
            val key = StoredTrack.from(track).key
            playHistoryStore.remove(key)
        }
    }

    fun onPlayNext(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueue(track: Track) {
        playerController.addToQueueEnd(track)
    }

    fun onDownload(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Sem contexto: ainda usa Levenshtein (mais parecido), nunca mais firstOrNull
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            }
        }
    }

    /** Clique em artista DENTRO de uma faixa: verifica quem tem ESSA faixa / ESSE álbum no catálogo. */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        // Vídeo do YouTube: o "artista" é o CANAL. Nunca resolve por nome no YT Music.
        if (track.source == Source.YOUTUBE) {
            onOpenChannelForTrack(track)
            return
        }
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            }
        }
    }

    /**
     * FIX 6 (método do SimpMusic): abre o CANAL REAL que postou o vídeo.
     * Ordem igual à do SimpMusic:
     *  1. InnerTube player(videoId).videoDetails.channelId -> UC... canônico.
     *     É o mesmo ID que o browse() entende como artista/canal, sem fetch de página.
     *  2. Fallback: NewPipe StreamExtractor.uploaderUrl -> UC... (ou @handle resolvido).
     * Nunca busca por nome. Se nenhum dos dois expõe o canal, não navega.
     *
     * getArtistFromVideo() já marca source=YOUTUBE quando o canal NÃO tem página
     * de artista no YT Music (canal puro de vídeo), então a tela de artista sabe
     * que é canal e carrega os vídeos DELE via NewPipe, em vez de chutar um
     * artista qualquer com nome parecido.
     */
    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(fromInnerTube))
                return@launch
            }
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                _navigationEvent.emit(
                    MenuNavigationEvent.OpenArtist(
                        Artist(
                            id = channel.channelId,
                            name = channel.channelName,
                            thumbnailUrl = channel.avatarUrl,
                            source = Source.YOUTUBE,
                        )
                    )
                )
            }
            // channel == null -> não navega. A UI já mostra "Ir para o canal" só pra
            // vídeos do YouTube; sem canal real não há pra onde ir.
        }
    }

    /**
     * FIX 3: "Iniciar rádio" - rádio infinito a partir da faixa.
     * Delega pro PlayerController.startRadio(), que toca o 1º lote e vai
     * buscando mais lotes quando a fila está acabando (nunca termina).
     */
    fun onStartRadio(track: Track) {
        viewModelScope.launch {
            val first = runCatching { musicRepository.buildRadioQueue(track) }
                .getOrDefault(listOf(track))
            playerController.startRadio(track, first)
        }
    }
}
FIX6EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt" <<'FIX6EOF'
package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YTMusicRepository @Inject constructor(
    private val enricher: YTMusicEnricher,
) {

    data class SearchPage(val items: List<SearchHit>, val continuation: String?)

    data class AlbumResult(
        val coverUrl: String?,
        val artist: String?,
        val title: String?,
        val description: String?,
        val views: String?,
        val tracks: List<Track>,
        val artistId: String? = null,
        val year: String? = null,
    )

    data class PlaylistResult(
        val coverUrl: String?,
        val owner: String?,
        val ownerAvatarUrl: String?,
        val title: String?,
        val description: String?,
        val trackCount: Int,
        val year: String?,
        val views: String?,
        val tracks: List<Track>,
        // UC... do canal dono da playlist (vem do header do InnerTube).
        // Sem isso a playlist caía no findChannelOrArtist() por nome e abria artista errado.
        val ownerChannelId: String? = null,
    )

    data class ArtistResult(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val relatedArtists: List<Artist>,
        val views: String? = null,
        val songsMore: BrowseRef? = null,
        val albumsMore: BrowseRef? = null,
        val singlesMore: BrowseRef? = null,
        val videos: List<Track> = emptyList(),
        val videosMore: BrowseRef? = null,
        val featured: List<Playlist> = emptyList(),
        val featuredMore: BrowseRef? = null,
    )

    /** One page of an artist "see all" list. Only the list matching the requested kind is filled. */
    data class ArtistListPage(
        val tracks: List<Track> = emptyList(),
        val albums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val continuation: String? = null,
    )

    data class VideoDetails(
        val viewCount: String?,
        val likeCount: String?,
        val description: String?,
    )

    private data class ResolvedAlbumRef(
        val browseId: String,
        val title: String,
    )

    private data class PlaylistHeaderExtras(
        val ownerAvatarUrl: String?,
        val year: String?,
        val channelId: String?,
    )

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        continuation: String?,
    ): SearchPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.search(query, filter.params)
            else InnerTube.searchContinuation(continuation)
        }.getOrNull() ?: return@withContext SearchPage(emptyList(), null)

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val next = InnerTube.findContinuationToken(json)
        val hits = parsed.mapNotNull { YTMusicSearchParser.toHit(it, filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    /**
     * Resolves the official YouTube Music [Album] (or Single/EP) that contains [track]
     * using the song's exact `MPREb_` browseId from InnerTube instead of guessing.
     */
    suspend fun findAlbumForTrack(track: Track): Album? = withContext(Dispatchers.IO) {
        val primaryArtist = track.artist
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat.")
            .substringBefore("ft.")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }

        val albumHint = track.album?.takeIf {
            it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
        }

        // 1. Search the exact song in YT Music to read its official MPREb_ album reference
        val songQuery = listOfNotNull(track.title, primaryArtist).joinToString(" ").trim()
        val exactRef = if (songQuery.isNotBlank()) {
            runCatching {
                val songJson = InnerTube.search(songQuery, SearchFilter.SONGS.params)
                extractAlbumRefFromSongSearch(songJson, track)
            }.getOrNull()
        } else {
            null
        }

        if (exactRef != null) {
            val header = runCatching { YTMusicAlbumParser.parseAlbumHeader(exactRef.browseId) }.getOrNull()
            return@withContext Album(
                id = exactRef.browseId,
                title = header?.title?.takeIf { it.isNotBlank() } ?: exactRef.title,
                artist = header?.artist?.takeIf { it.isNotBlank() } ?: primaryArtist ?: track.artist,
                thumbnailUrl = header?.coverUrl ?: track.thumbnailUrl,
                views = header?.views,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        // 2. Fallback only when track already carried an explicit album name
        val targetTitle = albumHint ?: return@withContext null
        val albumSearchQuery = listOfNotNull(targetTitle, primaryArtist).joinToString(" ").trim()
        if (albumSearchQuery.isBlank()) return@withContext null

        val json = runCatching { InnerTube.search(albumSearchQuery, SearchFilter.ALBUMS.params) }.getOrNull()
            ?: return@withContext null

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val albums = parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.ALBUMS) }
            .filterIsInstance<SearchHit.AlbumHit>()
            .map { it.album }

        albums.firstOrNull { album ->
            val titleMatches = album.title.equals(targetTitle, ignoreCase = true) ||
                album.title.contains(targetTitle, ignoreCase = true)
            val artistMatches = primaryArtist == null ||
                album.artist.contains(primaryArtist, ignoreCase = true) ||
                primaryArtist.contains(album.artist, ignoreCase = true)
            titleMatches && artistMatches
        }
    }

    /**
     * Fills missing views / owner on playlist hits that did not come from YouTube Music
     * (e.g. NewPipe results). Cover and mosaic are left untouched.
     */
    suspend fun fillPlaylistStats(hits: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> =
        enricher.fillPlaylistStats(hits)

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()

            coroutineScope {
                val queriesDeferred = async {
                    runCatching {
                        val json = InnerTube.searchSuggestions(query)
                        YTMusicSearchParser.parseSuggestions(json)
                    }.getOrDefault(emptyList())
                }

                val songsDeferred = async {
                    runCatching {
                        val limit = when {
                            query.length <= 2 -> 3
                            query.length == 3 -> 4
                            else -> 5
                        }

                        val json = InnerTube.search(query, SearchFilter.SONGS.params)
                        val parsed = YTMusicSearchParser.parseSearchResults(json)
                        parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.SONGS) }
                            .filterIsInstance<SearchHit.SongHit>()
                            .take(limit)
                            .map { it.track }
                    }.getOrDefault(emptyList())
                }

                SearchSuggestions(
                    songs = songsDeferred.await(),
                    queries = queriesDeferred.await()
                )
            }
        }

    // ================= BROWSE =================

    suspend fun browseAlbum(albumId: String): AlbumResult = withContext(Dispatchers.IO) {
        // Normalize OLAK5uy_ playlist-backed album IDs so InnerTube browse always resolves them
        val browseId = if (albumId.startsWith("OLAK5uy_")) "VL$albumId" else albumId

        val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
            ?: return@withContext AlbumResult(null, null, null, null, null, emptyList())

        val headerInfo = YTMusicAlbumParser.parseFromJson(json)
        val cover = headerInfo?.coverUrl
        val title = headerInfo?.title
        val artist = headerInfo?.artist
        val views = headerInfo?.views
        val desc = extractDescription(json)
        val (artistId, year) = extractAlbumHeaderExtras(json)

        // Check both musicShelfRenderer and musicPlaylistShelfRenderer used by two-column album layouts
        val primaryShelf = YTMusicTextUtils.findFirst(json, "musicShelfRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer")

        var rawTracks = (primaryShelf?.let { collectMusicItems(it) } ?: emptyList())
            .ifEmpty { collectMusicItems(json) }
            .distinctBy { it.id }

        // Fallback: some MPREb_ pages only expose the album's OLAK5uy_ playlistId in the Play button
        if (rawTracks.isEmpty()) {
            val fallbackPlaylistId = findAlbumAudioPlaylistId(json)
            if (!fallbackPlaylistId.isNullOrBlank() && fallbackPlaylistId != browseId) {
                val playlistFallback = runCatching { browsePlaylist(fallbackPlaylistId) }.getOrNull()
                if (playlistFallback != null && playlistFallback.tracks.isNotEmpty()) {
                    rawTracks = playlistFallback.tracks
                }
            }
        }

        val normalizedTracks = rawTracks.map { item ->
            val resolvedArtist = if (item.artist.isBlank() || item.artist.equals("Desconhecido", ignoreCase = true)) {
                artist ?: item.artist
            } else {
                item.artist
            }
            val resolvedAlbum = item.album?.takeIf { it.isNotBlank() } ?: title
            item.copy(
                artist = resolvedArtist,
                album = resolvedAlbum,
                thumbnailUrl = item.thumbnailUrl ?: cover,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        val finalTracks = enricher.enrichTracks(normalizedTracks, artist)

        AlbumResult(
            coverUrl = cover,
            artist = artist,
            title = title,
            description = desc,
            views = views,
            tracks = finalTracks,
            artistId = artistId,
            year = year,
        )
    }

    /**
     * Fetches the artist profile and other albums/singles by the same artist,
     * excluding the currently opened album.
     */
    suspend fun getMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumId: String,
        excludeAlbumTitle: String?,
    ): Pair<Artist?, List<Album>> = withContext(Dispatchers.IO) {
        val resolvedId = artistId?.takeIf { it.startsWith("UC") }
            ?: artistName?.takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
                ?.let { name ->
                    val primary = name.substringBefore(",").substringBefore("&").trim()
                    // Verifica que o artista tem esse álbum no catálogo, senão pega o homônimo errado
                    runCatching {
                        resolveArtist(primary, albumTitle = excludeAlbumTitle)?.id
                    }.getOrNull()
                }
            ?: return@withContext (null to emptyList())

        val artistDetail = runCatching { browseArtist(resolvedId) }.getOrNull()
            ?: return@withContext (null to emptyList())

        val artist = Artist(
            id = artistDetail.browseId,
            name = artistDetail.name,
            thumbnailUrl = artistDetail.thumbnailUrl,
            source = Source.YOUTUBE_MUSIC,
        )

        val cleanExcludeId = excludeAlbumId.removePrefix("VL")
        val otherReleases = (artistDetail.albums + artistDetail.singles)
            .distinctBy { it.id }
            .filter { candidate ->
                val sameId = candidate.id == excludeAlbumId || candidate.id.removePrefix("VL") == cleanExcludeId
                val sameTitle = !excludeAlbumTitle.isNullOrBlank() &&
                    candidate.title.equals(excludeAlbumTitle, ignoreCase = true)
                !sameId && !sameTitle
            }
            .take(15)

        artist to otherReleases
    }

    suspend fun browsePlaylist(playlistId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val cleanId = YTMusicTextUtils.normalizePlaylistBrowseId(playlistId)
        val json = runCatching { InnerTube.browse(cleanId) }.getOrNull()
            ?: return@withContext PlaylistResult(null, null, null, null, null, 0, null, null, emptyList(), null)

        val parsed = YTMusicPlaylistParser.parsePlaylist(json)
        val headerExtras = extractPlaylistHeaderExtras(json)
        val finalTracks = enricher.enrichTracks(parsed.tracks, null)

        val resolvedAvatar = parsed.ownerAvatarUrl?.takeIf { it.isNotBlank() }
            ?: headerExtras.ownerAvatarUrl
            ?: headerExtras.channelId?.let { cid ->
                runCatching { browseArtist(cid).thumbnailUrl }.getOrNull()
            }

        val resolvedYear = parsed.year?.takeIf { it.isNotBlank() } ?: headerExtras.year

        PlaylistResult(
            coverUrl = parsed.coverUrl,
            owner = parsed.owner,
            ownerAvatarUrl = resolvedAvatar,
            title = parsed.title,
            description = parsed.description,
            trackCount = parsed.trackCount.takeIf { it > 0 } ?: finalTracks.size,
            year = resolvedYear,
            views = parsed.views,
            tracks = finalTracks,
            ownerChannelId = headerExtras.channelId,
        )
    }

    // ================= ARTISTS =================

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Artist>()
        runCatching {
            val json = InnerTube.search(query, SearchFilter.ARTISTS.params)
            YTMusicArtistParser.extractArtistsFromSearch(json, out)
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.take(30)
    }

    /**
     * Resolves an artist or YouTube channel profile (including avatar thumbnail) by name.
     * Sem contexto de faixa/álbum: usa só o nome mais parecido (Levenshtein, SimpMusic).
     * Com contexto, usa resolveArtist() com verificação de catálogo.
     */
    suspend fun findChannelOrArtist(name: String): Artist? =
        resolveArtist(name, trackTitle = null, albumTitle = null)

    /**
     * Resolve o artista CERTO pra um nome clicado dentro de álbum/faixa.
     *
     * Antigo: searchArtists(nome).firstOrNull() -> pegava o primeiro, que com nome
     * repetido era quase sempre o artista errado ou o "- Topic".
     *
     * Novo (SimpMusic + verificação que você pediu):
     * 1. Busca candidatos (até 30), rankeia por Levenshtein no nome normalizado,
     *    desempata Topic (oficial sem " - Topic" ganha).
     * 2. Se tem nome exato não-Topic, devolve ele direto sem gastar browse.
     * 3. Se tem faixa/álbum de contexto, abre a página dos 3 mais parecidos e só
     *    aceita quem tem aquela faixa / aquele álbum no catálogo. Quem não tem, não é ele.
     * 4. Sem verificação ou ninguém verificado, cai pro mais parecido.
     */
    suspend fun resolveArtist(
        artistName: String,
        trackTitle: String? = null,
        albumTitle: String? = null,
    ): Artist? = withContext(Dispatchers.IO) {
        val cleanQuery = artistName.trim()
        if (cleanQuery.isBlank() || cleanQuery.equals("YouTube Music", ignoreCase = true)) {
            return@withContext null
        }

        // 1. Candidatos: filtro ARTISTS primeiro, igual antes
        var candidates = searchArtists(cleanQuery)

        // 2. Fallback pra busca geral (canal/curador UC...) quando ARTISTS não achou nada
        if (candidates.isEmpty()) {
            val fallbackOut = mutableListOf<Artist>()
            runCatching {
                val json = InnerTube.search(cleanQuery, null)
                YTMusicArtistParser.extractArtistsFromSearch(json, fallbackOut)
            }
            candidates = fallbackOut.distinctBy { it.id }
                .filter { !it.thumbnailUrl.isNullOrBlank() }
        }
        if (candidates.isEmpty()) return@withContext null

        // Rankeia: menor Levenshtein primeiro, não-Topic antes de Topic, ordem original desempata
        val ranked = candidates.mapIndexed { idx, artist ->
            Triple(artist, ArtistResolver.distance(cleanQuery, artist.name), idx)
        }.sortedWith(
            compareBy<Triple<Artist, Int, Int>> { it.second }
                .thenBy { if (ArtistResolver.isTopicName(it.first.name)) 1 else 0 }
                .thenBy { it.third }
        )

        // Exato não-Topic: é ele, sem gastar browse
        val exactNonTopic = ranked.firstOrNull {
            it.second == 0 && !ArtistResolver.isTopicName(it.first.name)
        }
        if (exactNonTopic != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank()) {
            return@withContext exactNonTopic.first
        }
        // Exato (mesmo que seja Topic) sem contexto: também é ele, sem gastar browse
        val exactAny = ranked.firstOrNull { it.second == 0 }
        if (exactAny != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank()) {
            return@withContext exactAny.first
        }

        // 3. Verificação de catálogo: só pros 3 mais parecidos, pra não estourar rede
        val hasContext = !trackTitle.isNullOrBlank() || !albumTitle.isNullOrBlank()
        if (hasContext) {
            for ((candidate, _, _) in ranked.take(3)) {
                val detail = runCatching { browseArtist(candidate.id) }.getOrNull() ?: continue
                val contains = ArtistResolver.catalogContains(
                    topSongTitles = detail.topSongs.map { it.title },
                    videoTitles = detail.videos.map { it.title },
                    albumTitles = detail.albums.map { it.title },
                    singleTitles = detail.singles.map { it.title },
                    trackTitle = trackTitle,
                    albumTitle = albumTitle,
                )
                if (contains) {
                    // Devolve com o nome/foto da página verificada (mais confiável que o da busca)
                    return@withContext Artist(
                        id = candidate.id,
                        name = detail.name.takeUnless { it == "Artista" || it == "Unknown Artist" }
                            ?: candidate.name,
                        thumbnailUrl = detail.thumbnailUrl ?: candidate.thumbnailUrl,
                        source = candidate.source,
                    )
                }
            }
            // Ninguém verificado: cai pro mais parecido abaixo (não retorna null, senão o clique morre)
        }

        // 4. Mais parecido (SimpMusic: bestMatchingIndex por Levenshtein)
        return@withContext ranked.first().first
    }

    suspend fun featuredArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val seeds = listOf(
            "top hits", "pop", "funk brasileiro", "sertanejo",
            "rock", "trap", "mpb", "lofi",
        ).shuffled().take(3)

        val out = mutableListOf<Artist>()
        for (seed in seeds) {
            runCatching {
                val json = InnerTube.search(seed, SearchFilter.ARTISTS.params)
                YTMusicArtistParser.extractArtistsFromSearch(json, out)
            }
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.shuffled().take(30)
    }

    suspend fun browseArtist(browseId: String): ArtistResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
                ?: return@withContext ArtistResult(
                    browseId = browseId, name = "Artista",
                    thumbnailUrl = null, subscribers = null, description = null,
                    topSongs = emptyList(), albums = emptyList(), singles = emptyList(),
                    relatedArtists = emptyList()
                )

            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)

            ArtistResult(
                browseId = parsed.browseId,
                name = parsed.name,
                thumbnailUrl = parsed.thumbnailUrl,
                subscribers = parsed.subscribers,
                description = parsed.description,
                topSongs = parsed.topSongs,
                albums = parsed.albums,
                singles = parsed.singles,
                relatedArtists = parsed.relatedArtists,
                views = parsed.views,
                songsMore = parsed.songsMore,
                albumsMore = parsed.albumsMore,
                singlesMore = parsed.singlesMore,
                videos = parsed.videos,
                videosMore = parsed.videosMore,
                featured = parsed.featured,
                featuredMore = parsed.featuredMore,
            )
        }

    /**
     * Loads one page of an artist "see all" list (songs, videos, albums, singles or featured playlists).
     * Pass [continuation] = null for the first page, then the token returned in the previous page.
     */
    suspend fun browseArtistList(
        kind: ArtistListKind,
        artistName: String,
        ref: BrowseRef,
        continuation: String?,
    ): ArtistListPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.browse(ref.browseId, ref.params)
            else InnerTube.browseContinuation(continuation)
        }.getOrNull() ?: return@withContext ArtistListPage()

        val page = YTMusicArtistParser.parseListPage(kind, artistName, json)
        ArtistListPage(page.tracks, page.albums, page.playlists, page.continuation)
    }

    /**
     * FIX 6 (método do SimpMusic): artista/canal DONO de um vídeo, pelo channelId
     * que o próprio vídeo carrega (player.videoDetails.channelId).
     *
     * NUNCA busca por nome: o channelId do player é a fonte da verdade.
     *
     * Distinção que faltava (era isso que abria artista parecido):
     *  - Se o YT Music tem página de artista pra esse UC... (topSongs/álbuns/
     *    vídeos/relacionados), devolve como YOUTUBE_MUSIC, com nome/foto da página.
     *  - Se NÃO tem página (canal puro de vídeo: podcast, gamer, vlogger...),
     *    devolve como YOUTUBE, com o nome do author do vídeo. A tela de artista
     *    entende source=YOUTUBE como "canal" e carrega os vídeos DELE via NewPipe,
     *    em vez de chutar um artista do YT Music com nome parecido.
     */
    suspend fun getArtistFromVideo(videoId: String): Artist? =
        withContext(Dispatchers.IO) {
            val playerJson = runCatching { InnerTube.player(videoId) }.getOrNull()
                ?: return@withContext null
            val videoDetails = playerJson.optJSONObject("videoDetails")
                ?: return@withContext null
            val channelId = videoDetails.optString("channelId")
                .takeIf { it.isNotBlank() && it != "null" }
            val author = videoDetails.optString("author")
                .takeIf { it.isNotBlank() && it != "null" }
            if (channelId == null || author == null) return@withContext null
            val cleanName = YTMusicTextUtils.cleanArtistName(author)
            if (cleanName.isBlank()) return@withContext null
            val detail = runCatching { browseArtist(channelId) }.getOrNull()
            val isMusicArtist = detail != null &&
                detail.name != "Artista" && detail.name != "Unknown Artist" &&
                (detail.topSongs.isNotEmpty() || detail.albums.isNotEmpty() ||
                    detail.singles.isNotEmpty() || detail.videos.isNotEmpty() ||
                    detail.featured.isNotEmpty() || detail.relatedArtists.isNotEmpty())
            if (isMusicArtist) {
                Artist(
                    id = channelId,
                    name = detail!!.name,
                    thumbnailUrl = detail.thumbnailUrl,
                    source = Source.YOUTUBE_MUSIC,
                )
            } else {
                Artist(
                    id = channelId,
                    name = cleanName,
                    thumbnailUrl = detail?.thumbnailUrl,
                    source = Source.YOUTUBE,
                )
            }
        }

    // ================= QUEUE =================

    suspend fun buildSongQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.artist.isNotBlank()) append(seed.artist)
            if (seed.title.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.title)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val json = runCatching {
            InnerTube.search(query, SearchFilter.SONGS.params)
        }.getOrNull() ?: return@withContext listOf(seed)

        val found = collectMusicItems(json)
            .distinctBy { it.id }
            .filter { it.source == Source.YOUTUBE_MUSIC }

        val normalized = found.map { item ->
            val uploader = item.artist.takeIf {
                it.isNotBlank() && !it.equals("Unknown", true)
            } ?: seed.artist
            item.copy(artist = uploader)
        }

        val list = if (normalized.any { it.id == seed.id }) {
            listOf(seed) + normalized.filter { it.id != seed.id }
        } else {
            listOf(seed) + normalized
        }
        list.take(50)
    }

    // ================= VIDEO DETAILS =================

    suspend fun getVideoDetails(videoId: String): VideoDetails? =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = InnerTube.player(videoId)
                val videoDetails = json.optJSONObject("videoDetails")
                    ?: return@runCatching null

                val viewCountRaw = videoDetails.optString("viewCount")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.toLongOrNull()

                var description = videoDetails.optString("shortDescription")
                    .takeIf { it.isNotBlank() && it != "null" }

                if (description == null) {
                    description = json.optJSONObject("microformat")
                        ?.optJSONObject("playerMicroformatRenderer")
                        ?.optJSONObject("description")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                }

                VideoDetails(
                    viewCount = viewCountRaw?.let { formatNumber(it) },
                    likeCount = null,
                    description = description,
                )
            }.getOrNull()
        }

    // ================= HELPERS =================

    private fun extractPlaylistHeaderExtras(json: JSONObject): PlaylistHeaderExtras {
        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return PlaylistHeaderExtras(null, null, null)

        val avatarUrl = YTMusicThumbnailUtils.thumbnailOf(header.optJSONObject("straplineThumbnail"))
            ?: YTMusicThumbnailUtils.thumbnailOf(
                header.optJSONObject("facepile")
                    ?.optJSONObject("avatarStackViewModel")
                    ?.optJSONArray("avatars")
                    ?.optJSONObject(0)
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
            )

        var year: String? = null
        var channelId: String? = null

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (channelId == null && browseId != null && browseId.startsWith("UC")) {
                    channelId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }

        return PlaylistHeaderExtras(
            ownerAvatarUrl = avatarUrl,
            year = year,
            channelId = channelId,
        )
    }

    private fun extractAlbumHeaderExtras(json: JSONObject): Pair<String?, String?> {
        var artistId: String? = null
        var year: String? = null

        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return (null to null)

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (artistId == null && browseId != null && browseId.startsWith("UC")) {
                    artistId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }
        return artistId to year
    }

    private fun findAlbumAudioPlaylistId(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val pid = node.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                if (!pid.isNullOrBlank() && pid != "null" && pid.startsWith("OLAK5uy_")) {
                    return pid
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findAlbumAudioPlaylistId(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findAlbumAudioPlaylistId(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractAlbumRefFromSongSearch(
        node: Any?,
        targetTrack: Track,
        candidates: MutableList<Pair<Boolean, ResolvedAlbumRef>> = mutableListOf(),
    ): ResolvedAlbumRef? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val rowTitle = columns?.let { YTMusicTextUtils.flexText(it, 0) }.orEmpty()
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    val exactIdMatch = !videoId.isNullOrBlank() && videoId == targetTrack.id
                    val titleMatch = rowTitle.isNotBlank() &&
                        rowTitle.equals(targetTrack.title, ignoreCase = true)

                    if ((exactIdMatch || titleMatch) && columns != null) {
                        val albumRef = findAlbumRunInColumns(columns)
                        if (albumRef != null) {
                            candidates += (exactIdMatch to albumRef)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractAlbumRefFromSongSearch(node.opt(keys.next()), targetTrack, candidates)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractAlbumRefFromSongSearch(node.opt(i), targetTrack, candidates)
                }
            }
        }
        return candidates.firstOrNull { it.first }?.second ?: candidates.firstOrNull()?.second
    }

    private fun findAlbumRunInColumns(columns: JSONArray): ResolvedAlbumRef? {
        for (c in 0 until columns.length()) {
            val runs = columns.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue

            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" } ?: continue

                if (browseId.startsWith("MPREb_")) {
                    val albumTitle = run.optString("text").trim()
                    if (albumTitle.isNotBlank()) {
                        return ResolvedAlbumRef(browseId = browseId, title = albumTitle)
                    }
                }
            }
        }
        return null
    }

    private fun extractVideoIdFromRenderer(r: JSONObject, columns: JSONArray?): String? {
        // 1. Standard playlist/search item data
        r.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 2. Top-level navigationEndpoint
        r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 3. First column title run navigationEndpoint (common on album tracklists)
        columns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 4. Overlay play button endpoint (used on newer YTMusic album rows without playlistItemData)
        r.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        return null
    }

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
            ?: return null
        val descNode = shelf.optJSONObject("description") ?: return null
        val text = YTMusicTextUtils.extractRunsText(descNode)
        return text.takeIf { it.isNotBlank() }
    }

    private fun collectMusicItems(
        node: Any?,
        out: MutableList<Track> = mutableListOf(),
    ): List<Track> {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    if (title != null && !videoId.isNullOrBlank()) {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                            ?: subtitle.substringBefore("•").trim().ifBlank { "Desconhecido" }
                        val albumTitle = columns.let { findAlbumRunInColumns(it)?.title }
                        out += Track(
                            id = videoId,
                            title = title,
                            artist = uploader,
                            album = albumTitle,
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils
                                .parseDuration(YTMusicTextUtils.fixedText(r)),
                            source = source,
                            isExplicit = YTMusicSearchParser.hasExplicitBadge(r),
                        )
                    }
                }
                node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    val videoId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val title = r.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text")
                    if (!videoId.isNullOrBlank() && !title.isNullOrBlank()) {
                        val subtitle = YTMusicTextUtils.runsText(r.optJSONObject("subtitle"))
                        out += Track(
                            id = videoId, title = title,
                            artist = subtitle.substringBefore("•").trim()
                                .ifBlank { "Desconhecido" },
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                            durationSeconds = 0L,
                            source = Source.YOUTUBE,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) {
                collectMusicItems(node.opt(i), out)
            }
        }
        return out
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000_000L -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
FIX6EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt" <<'FIX6EOF'
package com.vztechcore.vztune.data.youtube

import android.net.Uri
import android.util.Log
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The metadata a YouTube search match returns.
 * Used by YTMusicEnricher as a last-resort artist / duration / thumb resolver.
 */
data class VideoMetadata(
    val videoId: String,
    val channelName: String,
    val title: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
)

/** Canal real do YouTube que postou um vídeo (via NewPipe, não via busca por nome). */
data class YouTubeChannelInfo(
    val channelId: String,
    val channelName: String,
    val avatarUrl: String?,
    val channelUrl: String?,
)

/** Info técnica do stream de áudio resolvido pelo NewPipe (usada na tela TrackInfo). */
data class AudioStreamInfo(
    val itag: String?,
    val mimeType: String?,
    val quality: String?,
)

@Singleton
class YouTubeRepository @Inject constructor(
    private val downloader: NewPipeDownloader,
) {
    init {
        NewPipeInitializer.ensureInitialized(downloader)
    }

    /**
     * FIX 6: uma sessão de busca por (query + aba), não uma única sessão global.
     * Antes, ALL e VIDEOS compartilhavam a mesma sessão: quando o prefetch de abas
     * carregava VIDEOS depois de ALL, o "carregar mais" de ALL passava a paginar
     * a busca de VIDEOS. Com uma sessão por searchId, cada aba pagina a sua própria
     * busca, mesmo com o prefetch rodando em segundo plano.
     * searchId vem do MusicRepository ("$query|ALL", "$query|VIDEOS", ...).
     * buildVideoQueue() usa searchId = query (padrão), então não interfere nas abas.
     */
    private data class VideoSearchSession(
        val extractor: SearchExtractor,
        var nextPage: Page?,
    )

    private data class PlaylistSearchSession(
        val extractor: SearchExtractor,
        var nextPage: Page?,
    )

    private val videoSearches = mutableMapOf<String, VideoSearchSession>()
    private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()

    fun resetSession() {
        videoSearches.clear()
        playlistSearches.clear()
    }

    /** Canal do YouTube (não-artista do YT Music): vídeos que ele postou. */
    data class ChannelPage(
        val videos: List<Track>,
        val channelName: String?,
        val avatarUrl: String?,
        val subscriberCount: String?,
    )

    data class VideoPage(
        val items: List<SearchHit.VideoHit>,
        val hasMore: Boolean,
    )

    // ================= SEARCH =================

    suspend fun searchVideos(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 20,
        searchId: String = query,
    ): VideoPage = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchVideos: NewPipe not initialized")
            return@withContext VideoPage(emptyList(), false)
        }
        runCatching {
            if (!loadMore) {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()
                val session = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                videoSearches[searchId] = session
                val items = parseVideoItems(extractor.initialPage.items).take(pageSize)
                VideoPage(items, session.nextPage != null)
            } else {
                val session = videoSearches[searchId]
                    ?: return@runCatching VideoPage(emptyList(), false)
                val page = session.nextPage
                    ?: return@runCatching VideoPage(emptyList(), false)
                val next = session.extractor.getPage(page)
                session.nextPage = next.nextPage
                VideoPage(parseVideoItems(next.items).take(pageSize), session.nextPage != null)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchVideos failed for query=$query", e)
            VideoPage(emptyList(), false)
        }
    }

    suspend fun searchPlaylists(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
        searchId: String = query,
    ): List<SearchHit.PlaylistHit> = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchPlaylists: NewPipe not initialized")
            return@withContext emptyList()
        }
        runCatching {
            if (!loadMore) {
                val extractor = ServiceList.YouTube.getSearchExtractor(
                    query, listOf("playlists"), null,
                )
                extractor.fetchPage()
                val session = PlaylistSearchSession(extractor, extractor.initialPage.nextPage)
                playlistSearches[searchId] = session
                parsePlaylistItems(extractor.initialPage.items).take(pageSize)
            } else {
                val session = playlistSearches[searchId]
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val page = session.nextPage
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val next = session.extractor.getPage(page)
                session.nextPage = next.nextPage
                parsePlaylistItems(next.items).take(pageSize)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchPlaylists failed for query=$query", e)
            emptyList()
        }
    }

    // ================= SMART VIDEO MATCH (for artist fallback) =================

    /**
     * Finds the best YouTube video match for the given title/artist hints
     * and returns the full metadata (channel, duration, thumbnail).
     *
     * Scoring:
     *   +1000 if the videoId matches exactly (strongest signal)
     *   +200  if the thumbnail URL matches exactly
     *   +100  if the title matches case-insensitively
     *   +50   if the uploader contains the artist hint
     *   +30   if the duration is within 5s of durationHint
     *   +10   if the duration is within 15s of durationHint
     *
     * Returns null if no candidate reaches MIN_SCORE (60).
     */
    suspend fun findVideoMetadata(
        videoId: String?,
        title: String,
        artistHint: String?,
        durationHintSeconds: Long? = null,
        thumbnailHint: String? = null,
    ): VideoMetadata? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (title.isBlank()) return@withContext null

        // 1) Try exact videoId lookup first — most accurate.
        if (!videoId.isNullOrBlank()) {
            runCatching {
                extractMetadataForId(videoId)
            }.getOrNull()?.let { return@withContext it }
        }

        // 2) Search + score.
        val query = buildString {
            append(title)
            if (!artistHint.isNullOrBlank()) {
                append(" ")
                append(artistHint)
            }
        }

        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            val titleLower = title.lowercase().trim()
            val artistLower = artistHint?.lowercase()?.trim().orEmpty()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val candidateId = extractVideoId(stream.url) ?: return@mapNotNull null
                    val candidateTitle = stream.name ?: return@mapNotNull null
                    val candidateTitleLower = candidateTitle.lowercase().trim()
                    val candidateUploader = stream.uploaderName?.trim().orEmpty()
                    val candidateUploaderLower = candidateUploader.lowercase()
                    val candidateDuration = stream.duration
                    val candidateThumb = stream.thumbnails.lastOrNull()?.url

                    var score = 0

                    if (candidateId == videoId) score += 1000
                    if (!thumbnailHint.isNullOrBlank() && candidateThumb == thumbnailHint) score += 200
                    if (candidateTitleLower == titleLower) score += 100
                    if (artistLower.isNotBlank() && candidateUploaderLower.contains(artistLower)) score += 50

                    if (durationHintSeconds != null && durationHintSeconds > 0 && candidateDuration > 0) {
                        val diff = kotlin.math.abs(candidateDuration - durationHintSeconds)
                        when {
                            diff <= 5 -> score += 30
                            diff <= 15 -> score += 10
                        }
                    }

                    if (score < MIN_SCORE) return@mapNotNull null

                    score to VideoMetadata(
                        videoId = candidateId,
                        channelName = cleanChannelName(candidateUploader),
                        title = candidateTitle,
                        durationSeconds = candidateDuration,
                        thumbnailUrl = candidateThumb,
                    )
                }
                .maxByOrNull { it.first }
                ?.second
        }.getOrElse { e ->
            Log.w(TAG, "findVideoMetadata failed for '$title': ${e.message}")
            null
        }
    }

    /**
     * Direct lookup by videoId via getStreamExtractor — no scoring needed.
     * Falls back to null on any error (removed video, private, etc).
     */
    private fun extractMetadataForId(videoId: String): VideoMetadata? {
        return runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val uploader = extractor.uploaderName?.trim().orEmpty()
            if (uploader.isBlank()) return@runCatching null

            VideoMetadata(
                videoId = videoId,
                channelName = cleanChannelName(uploader),
                title = extractor.name ?: "",
                durationSeconds = extractor.length,
                thumbnailUrl = extractor.thumbnails?.lastOrNull()?.url,
            )
        }.getOrNull()
    }

    private fun cleanChannelName(raw: String): String {
        var s = raw.trim()
        s = s.removeSuffix(" - Topic").removeSuffix(" - topic").trim()
        if (s.startsWith("Provided to YouTube", true)) return ""
        if (s.startsWith("Auto-generated by YouTube", true)) return ""
        return s
    }

    // ================= STREAM RESOLUTION =================

    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "getStreamUrl: NewPipe not initialized")
            return@withContext null
        }

        Log.d(TAG, "getStreamUrl: videoId=$videoId")

        val fromWww = runCatching {
            extractFromUrl("https://www.youtube.com/watch?v=$videoId")
        }.getOrElse { e ->
            Log.w(TAG, "www attempt failed for $videoId: ${e.message}")
            null
        }
        if (!fromWww.isNullOrBlank()) {
            Log.d(TAG, "resolved via www: ${fromWww.take(80)}...")
            return@withContext fromWww
        }

        val fromMusic = runCatching {
            extractFromUrl("https://music.youtube.com/watch?v=$videoId")
        }.getOrElse { e ->
            Log.w(TAG, "music attempt failed for $videoId: ${e.message}")
            null
        }
        if (!fromMusic.isNullOrBlank()) {
            Log.d(TAG, "resolved via music: ${fromMusic.take(80)}...")
            return@withContext fromMusic
        }

        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                val fromAlt = runCatching {
                    extractFromUrl("https://www.youtube.com/watch?v=$altId")
                }.getOrNull()
                if (!fromAlt.isNullOrBlank()) {
                    Log.d(TAG, "resolved via fallback $altId: ${fromAlt.take(80)}...")
                    return@withContext fromAlt
                }
            }
        }

        Log.e(TAG, "NO STREAMS for $videoId")
        null
    }

    /**
     * Retorna info técnica (itag / mime / bitrate) do melhor stream de áudio.
     * Usado pela tela TrackInfo pra exibir detalhes técnicos.
     */
    suspend fun getAudioStreamInfo(videoId: String): AudioStreamInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "getAudioStreamInfo: NewPipe not initialized")
            return@withContext null
        }
        runCatching {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            val audio = extractor.audioStreams
                .filter { it.averageBitrate > 0 }
                .maxByOrNull { it.averageBitrate }
                ?: extractor.audioStreams.firstOrNull()
                ?: return@runCatching null

            AudioStreamInfo(
                itag = runCatching { audio.itag.toString() }.getOrNull(),
                mimeType = runCatching { audio.format?.mimeType }.getOrNull(),
                quality = audio.averageBitrate
                    .takeIf { it > 0 }
                    ?.let { "${it / 1000} kbps" },
            )
        }.getOrElse { e ->
            Log.w(TAG, "getAudioStreamInfo failed for $videoId: ${e.message}")
            null
        }
    }

    private fun extractFromUrl(url: String): String {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val audioStreams = extractor.audioStreams
        val bestAudio: AudioStream? = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.firstOrNull()
        if (bestAudio?.content?.isNotBlank() == true) return bestAudio.content

        extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val progressive = extractor.videoStreams
            .filter { !it.isVideoOnly }
            .minByOrNull {
                it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }
        if (progressive?.content?.isNotBlank() == true) return progressive.content

        throw Exception("No streams available for $url")
    }

    private fun findBestMatchVideoId(title: String, artist: String?): String? {
        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()

        val titleWords = title.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 2 }
            .toSet()
        val artistLower = artist?.lowercase()?.trim().orEmpty()

        return extractor.initialPage.items
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { stream ->
                val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
                val name = stream.name?.lowercase() ?: return@mapNotNull null
                val uploader = stream.uploaderName?.lowercase().orEmpty()
                var score = titleWords.count { name.contains(it) } * 10
                if (artistLower.isNotBlank() && uploader.contains(artistLower)) score += 40
                videoId to score
            }
            .filter { it.second >= 20 }
            .maxByOrNull { it.second }
            ?.first
    }

    // ================= FALLBACK THUMBNAIL =================

    suspend fun findVideoThumbnail(
        videoId: String,
        title: String,
        artist: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null

        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }

        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            val titleWords = title.lowercase()
                .split(Regex("\\s+"))
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .toSet()
            val artistLower = artist?.lowercase()?.trim().orEmpty()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val name = stream.name?.lowercase() ?: return@mapNotNull null
                    val uploader = stream.uploaderName?.lowercase().orEmpty()
                    val thumb = stream.thumbnails.lastOrNull()?.url ?: return@mapNotNull null
                    var score = titleWords.count { name.contains(it) } * 10
                    if (artistLower.isNotBlank() && uploader.contains(artistLower)) score += 40
                    thumb to score
                }
                .filter { it.second >= 20 }
                .maxByOrNull { it.second }
                ?.first
        }.getOrElse { e ->
            Log.w(TAG, "findVideoThumbnail failed for $videoId: ${e.message}")
            null
        }
    }

    // ================= PARSERS =================

    private fun parseVideoItems(items: List<InfoItem>): List<SearchHit.VideoHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
            SearchHit.VideoHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parsePlaylistItems(items: List<InfoItem>): List<SearchHit.PlaylistHit> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { playlist ->
            val listId = extractListId(playlist.url) ?: return@mapNotNull null
            SearchHit.PlaylistHit(
                Playlist(
                    id = "YT:$listId",
                    title = playlist.name ?: "Playlist",
                    owner = playlist.uploaderName ?: "",
                    thumbnailUrl = playlist.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun extractVideoId(url: String): String? = try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v")
            ?: uri.lastPathSegment?.takeIf { uri.host?.contains("youtu.be") == true }
    } catch (e: Exception) {
        null
    }

    private fun extractListId(url: String): String? = try {
        Uri.parse(url).getQueryParameter("list")
    } catch (e: Exception) {
        null
    }

    /**
     * Resolve o CANAL que postou o vídeo pelo videoId, via StreamExtractor do NewPipe.
     * Nunca busca por nome: uploaderUrl é a fonte da verdade (UC... ou @handle).
     * Retorna null quando o NewPipe não expõe o canal -> o chamador deve ESCONDER
     * "Ir para o canal", nunca cair pra busca por nome no YT Music.
     */
    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (videoId.isBlank()) return@withContext null
        runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            // NewPipe 0.27: não existe getUploaderAvatarUrl(). O avatar vem como
            // lista em getUploaderAvatars() -> List<Image>. Fallback: thumb do vídeo.
            val avatarUrl = extractor.uploaderAvatars.lastOrNull()?.url
                ?: extractor.thumbnails?.lastOrNull()?.url
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            YouTubeChannelInfo(
                channelId = channelId,
                channelName = cleanChannelName(uploaderName),
                avatarUrl = avatarUrl,
                channelUrl = uploaderUrl,
            )
        }.getOrNull()
    }

    /**
     * Extrai o UC... de uma uploaderUrl do NewPipe.
     * Formas: .../channel/UC..., .../UC..., .../@handle, .../c/nome, .../user/nome.
     * @handle / /c / /user não têm UC na URL -> resolve via ChannelExtractor do NewPipe.
     */
    private fun extractChannelId(uploaderUrl: String): String? {
        val ucRegex = Regex("UC[\\w-]{22}")
        ucRegex.find(uploaderUrl)?.let { return it.value }
        // @handle, /c/, /user/: ChannelExtractor resolve o id real.
        return runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(uploaderUrl)
            extractor.fetchPage()
            extractor.id?.takeIf { it.startsWith("UC") }
        }.getOrNull()
    }

    /**
     * FIX 6: vídeos do CANAL REAL, pelo UC... dele.
     * Usado pela tela de artista quando o YT Music NÃO tem página de artista
     * pra esse UC... (canal puro de vídeo). O ChannelExtractor do NewPipe abre
     * o canal pelo ID, não por nome — então nunca cai num canal/artista parecido.
     * Retorna null quando o canal não existe / não tem vídeos (o chamador mostra
     * erro, nunca chuta outro artista).
     */
    suspend fun browseChannel(channelId: String): ChannelPage? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (channelId.isBlank()) return@withContext null
        runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(
                "https://www.youtube.com/channel/$channelId"
            )
            extractor.fetchPage()
            val channelName = runCatching { extractor.name }.getOrNull()?.trim().orEmpty()
            val avatarUrl = runCatching { extractor.avatars?.lastOrNull()?.url }.getOrNull()
            val subscriberCount = runCatching {
                extractor.subscriberCount.takeIf { it > 0 }?.let { formatSubscribers(it) }
            }.getOrNull()
            val videos = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(
                        id = videoId,
                        title = stream.name ?: "Untitled",
                        artist = channelName,
                        durationSeconds = stream.duration,
                        thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                        source = Source.YOUTUBE,
                    )
                }
            ChannelPage(
                videos = videos,
                channelName = channelName.takeIf { it.isNotBlank() },
                avatarUrl = avatarUrl,
                subscriberCount = subscriberCount,
            )
        }.getOrNull()
    }

    private fun formatSubscribers(count: Long): String {
        return when {
            count >= 1_000_000 -> {
                val v = count / 1_000_000.0
                "${trimDecimal(v)} mi de inscritos"
            }
            count >= 1_000 -> {
                val v = count / 1_000.0
                "${trimDecimal(v)} mil inscritos"
            }
            else -> "$count inscritos"
        }
    }

    private fun trimDecimal(v: Double): String {
        val s = "%.1f".format(v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    suspend fun buildVideoQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.title.isNotBlank()) append(seed.title)
            if (seed.artist.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.artist)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val page = searchVideos(query, loadMore = false, pageSize = 30)
        val tracks = page.items.map { it.track }

        val list = if (tracks.any { it.id == seed.id }) {
            listOf(seed) + tracks.filter { it.id != seed.id }
        } else {
            listOf(seed) + tracks
        }
        list.distinctBy { it.id }.take(50)
    }

    private companion object {
        const val TAG = "VzTuneYT"
        const val MIN_SCORE = 60
    }
}
FIX6EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt" <<'FIX6EOF'
package com.vztechcore.vztune.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val playerController: PlayerController,
    private val followedStore: FollowedArtistsStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.Artist>()

    private val _uiState = MutableStateFlow(
        ArtistUiState(
            artistId = route.id,
            name = route.name,
            thumbnailUrl = route.thumbnailUrl,
        )
    )
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        loadContent()
        viewModelScope.launch {
            followedStore.isFollowing(route.id).collect { following ->
                _uiState.update { it.copy(isFollowing = following) }
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            if (result != null) {
                val isEmpty = result.topSongs.isEmpty() && result.albums.isEmpty() && result.singles.isEmpty() &&
                    result.videos.isEmpty() && result.featured.isEmpty() && result.relatedArtists.isEmpty()
                if (!isEmpty) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            // Keep the name/photo from the card when the page could not provide them
                            name = result.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                            thumbnailUrl = result.thumbnailUrl ?: it.thumbnailUrl,
                            subscribers = result.subscribers,
                            views = result.views,
                            description = result.description,
                            topSongs = result.topSongs,
                            songsMore = result.songsMore,
                            albums = result.albums,
                            albumsMore = result.albumsMore,
                            singles = result.singles,
                            singlesMore = result.singlesMore,
                            videos = result.videos,
                            videosMore = result.videosMore,
                            featured = result.featured,
                            featuredMore = result.featuredMore,
                            relatedArtists = result.relatedArtists,
                        )
                    }
                    return@launch
                }
            }
            // FIX 6: o YT Music NÃO tem página de artista pra esse UC...
            // É um canal puro de vídeo (podcast, gamer, vlogger...), não um artista.
            // NUNCA busca por nome (era isso que abria artista parecido).
            // Abre o CANAL REAL pelo UC... via NewPipe e mostra OS VÍDEOS DELE.
            // O nome/foto vêm da rota (que já veio do canal real do vídeo), não de busca.
            val channel = runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull()
            if (channel != null && channel.videos.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                        subscribers = channel.subscriberCount ?: it.subscribers,
                        videos = channel.videos,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
            }
        }
    }

    /** Plays a random track of the artist (popular songs first, videos as fallback). */
    fun onRandomClick() {
        val state = _uiState.value
        val pool = state.topSongs.ifEmpty { state.videos }
        if (pool.isEmpty()) return
        playerController.playQueue(pool.shuffled(), 0)
    }

    fun onFollowClick() {
        val state = _uiState.value
        viewModelScope.launch {
            followedStore.toggle(FollowedArtist(state.artistId, state.name, state.thumbnailUrl))
        }
    }

    fun onSongClick(track: Track) {
        val songs = _uiState.value.topSongs
        val idx = songs.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(songs, idx) else playerController.playQueue(listOf(track), 0)
    }

    fun onVideoClick(track: Track) {
        val videos = _uiState.value.videos
        val idx = videos.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(videos, idx) else playerController.playQueue(listOf(track), 0)
    }
}
FIX6EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt" <<'FIX6EOF'
package com.vztechcore.vztune.data.music

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicTextUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
) {

    // =====================================================================
    //  Fachada — API que os ViewModels esperam do MusicRepository
    // =====================================================================

    data class TechnicalInfo(
        val itag: String?,
        val mimeType: String?,
        val quality: String?,
    )

    /** Extração de áudio via NewPipe (delegado pro YouTubeRepository). */
    suspend fun getStreamUrl(track: Track): String? {
        val artistHint = track.artist
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
        val url = runCatching {
            youTubeRepository.getStreamUrl(
                videoId = track.id,
                titleHint = track.title.takeIf { it.isNotBlank() },
                artistHint = artistHint,
            )
        }.getOrElse { e ->
            Log.e(TAG, "getStreamUrl failed for ${track.id}: ${e.message}", e)
            null
        }
        if (url.isNullOrBlank()) {
            Log.w(TAG, "getStreamUrl returned null for ${track.id} '${track.title}'")
        }
        return url
    }

    /** Info técnica (itag / mime / bitrate) — usada pela tela TrackInfo. */
    suspend fun getTechnicalInfo(track: Track): TechnicalInfo? = runCatching {
        val info = youTubeRepository.getAudioStreamInfo(track.id) ?: return@runCatching null
        TechnicalInfo(itag = info.itag, mimeType = info.mimeType, quality = info.quality)
    }.getOrNull()

    /** Sugestões de busca — delegate direto pro YTMusic. */
    suspend fun suggestions(query: String): SearchSuggestions =
        ytMusicRepository.suggestions(query)

    /**
     * Fila "rádio" — escolhe o repositório certo pelo `seed.source`.
     *   YOUTUBE_MUSIC → ytMusic.buildSongQueue
     *   YOUTUBE       → youtube.buildVideoQueue
     */
    suspend fun buildRadioQueue(seed: Track): List<Track> = runCatching {
        when (seed.source) {
            Source.YOUTUBE_MUSIC -> ytMusicRepository.buildSongQueue(seed)
            Source.YOUTUBE -> youTubeRepository.buildVideoQueue(seed)
            else -> listOf(seed)
        }
    }.getOrElse { e ->
        Log.w(TAG, "buildRadioQueue failed for ${seed.id}: ${e.message}")
        listOf(seed)
    }

    /**
     * FIX 3: próximo lote do rádio infinito.
     * Estratégia pra não repetir os mesmos 50 e manter o "clima" da seed
     * (mesmo artista / artistas parecidos / mesma língua-gênero implícita na busca):
     *  1. Busca pela última faixa tocada (artista + título) -> varia o centro.
     *  2. Busca só pelo artista da seed -> volta pro núcleo.
     *  3. YT Music: topSongs dos artistas relacionados ao artista da seed.
     *  4. YouTube: buildVideoQueue da última faixa.
     * Sempre filtra os ids já tocados e embaralha.
     */
    suspend fun buildRadioContinuation(
        seed: Track,
        excludeIds: Set<String>,
        lastTrack: Track?,
    ): List<Track> = runCatching {
        val candidates = mutableListOf<Track>()
        when (seed.source) {
            Source.YOUTUBE -> {
                val anchor = lastTrack ?: seed
                candidates += youTubeRepository.buildVideoQueue(anchor)
                if (candidates.size < 15 && lastTrack != null && lastTrack.id != seed.id) {
                    candidates += youTubeRepository.buildVideoQueue(seed)
                }
            }
            else -> {
                // 1. Última faixa como âncora
                lastTrack?.let { candidates += ytMusicRepository.buildSongQueue(it) }
                // 2. Artista da seed sozinho (mais amplo que artista+título)
                if (candidates.size < 20 && seed.artist.isNotBlank()) {
                    val artistOnly = Track(
                        id = seed.id, title = "", artist = seed.artist,
                        source = Source.YOUTUBE_MUSIC,
                    )
                    candidates += ytMusicRepository.buildSongQueue(artistOnly)
                }
                // 3. Artistas relacionados: abre a página do artista e pega topSongs dos relacionados
                if (candidates.size < 20) {
                    runCatching {
                        val artistId = ytMusicRepository.resolveArtist(seed.artist)?.id
                        if (!artistId.isNullOrBlank()) {
                            val page = ytMusicRepository.browseArtist(artistId)
                            page.relatedArtists.shuffled().take(3).forEach { related ->
                                runCatching {
                                    candidates += ytMusicRepository.browseArtist(related.id).topSongs
                                }
                            }
                        }
                    }
                }
            }
        }
        candidates
            .distinctBy { "${it.source}_${it.id}" }
            .filter { it.id != seed.id && "${it.source}_${it.id}" !in excludeIds && it.id !in excludeIds }
            .shuffled()
            .take(30)
    }.getOrElse { e ->
        Log.w(TAG, "buildRadioContinuation failed for ${seed.id}: ${e.message}")
        emptyList()
    }

    // =====================================================================
    //  SEARCH
    // =====================================================================

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
        var topArtistsLoaded: Boolean = false,
    )

    private val searchStates = mutableMapOf<String, SearchState>()

    suspend fun search(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
    ): SearchResult {
        if (query.isBlank()) return SearchResult.Success(emptyList(), false)
        val stateKey = "$query|${filter.name}"
        val state = searchStates.getOrPut(stateKey) { SearchState() }
        if (!loadMore) {
            state.seenKeys.clear()
            state.ytHasMore = true
            state.ytmContinuation = null
            state.ytmHasMore = true
            state.topArtistsLoaded = false
            // FIX 6: NÃO chama resetSession() aqui. Ele limpava TODAS as sessões do
            // NewPipe, então o prefetch de abas apagava a paginação da aba atual.
            // Cada aba já tem sua própria sessão por searchId (YouTubeRepository),
            // e uma busca nova (loadMore=false) recria a sessão daquela aba.
        }
        return when (filter) {
            SearchFilter.ALL -> searchAll(query, loadMore, state)
            SearchFilter.VIDEOS -> searchVideos(query, loadMore, state)
            SearchFilter.SONGS -> searchSongs(query, loadMore, state)
            SearchFilter.PLAYLISTS -> searchPlaylists(query, loadMore, state)
            else -> searchOther(query, filter, loadMore, state)
        }
    }

    private suspend fun searchAll(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult = coroutineScope {
        val pageSize = if (loadMore) 10 else 20
        val topArtistsDeferred = if (!state.topArtistsLoaded && !loadMore) {
            async { runCatching { ytMusicRepository.searchArtists(query) }.getOrDefault(emptyList()).take(3) }
        } else null
        val ytDeferred = async { youTubeRepository.searchVideos(query, loadMore, pageSize, searchId = "$query|ALL") }
        val ytmDeferred = async { ytMusicRepository.search(query, SearchFilter.ALL, state.ytmContinuation) }
        val ytPage = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        val topArtists = topArtistsDeferred?.await().orEmpty()
        state.ytHasMore = ytPage.hasMore
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val merged = mutableListOf<SearchHit>()
        if (topArtists.isNotEmpty()) {
            topArtists.forEach { merged += SearchHit.ArtistHit(it) }
            state.topArtistsLoaded = true
        }
        
        val ytmTracksOnly = ytmPage.items.filterIsInstance<SearchHit.SongHit>()
        val ytmAlbums = ytmPage.items.filterIsInstance<SearchHit.AlbumHit>()
        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()

        val validAlbumsDeferred = async { validateAlbums(ytmAlbums.take(3)) }
        val validPlaylistsDeferred = async { validatePlaylists(ytmPlaylists.take(3)) }

        merged += interleave(ytPage.items, ytmTracksOnly.take(pageSize))
        
        if (!loadMore) {
            merged += validAlbumsDeferred.await()
            merged += validPlaylistsDeferred.await()
        }

        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytHasMore || state.ytmHasMore)
    }

    private suspend fun searchVideos(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val ytPage = youTubeRepository.searchVideos(query, loadMore, 30, searchId = "$query|VIDEOS")
        state.ytHasMore = ytPage.hasMore
        val newHits = ytPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytHasMore)
    }

    private suspend fun searchSongs(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val ytmPage = ytMusicRepository.search(query, SearchFilter.SONGS, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.take(30).filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun searchPlaylists(query: String, loadMore: Boolean, state: SearchState): SearchResult = coroutineScope {
        val ytDeferred = async { if (!loadMore) youTubeRepository.searchPlaylists(query, loadMore = false, searchId = "$query|PLAYLISTS") else emptyList() }
        val ytmDeferred = async { ytMusicRepository.search(query, SearchFilter.PLAYLISTS, state.ytmContinuation) }
        val ytHits = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()
        val ytFiltered = ytHits.filterNot { hit ->
            hit.playlist.title.startsWith("Mix -", ignoreCase = true) &&
                hit.playlist.id.contains("RD", ignoreCase = true)
        }
        // YouTube playlists come without view counts, so fill them in like the YT Music ones.
        val ytWithStats = ytMusicRepository.fillPlaylistStats(ytFiltered)
        val allPlaylists = (ytWithStats + ytmPlaylists).distinctBy { hit ->
            YTMusicTextUtils.normalizePlaylistBrowseId(hit.playlist.id)
        }
        val validated = validatePlaylists(allPlaylists)
        val newHits = validated.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun validateAlbums(albums: List<SearchHit.AlbumHit>): List<SearchHit.AlbumHit> = coroutineScope {
        if (albums.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(6)
        val deferred = albums.map { hit ->
            async<SearchHit.AlbumHit?> {
                semaphore.withPermit {
                    val hasTracks = runCatching {
                        withTimeoutOrNull(10000L) { ytMusicRepository.browseAlbum(hit.album.id).tracks.isNotEmpty() }
                    }.getOrNull()
                    if (hasTracks == false) null else hit
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    private suspend fun validatePlaylists(playlists: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> = coroutineScope {
        if (playlists.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(8)
        val deferred = playlists.map { hit ->
            async<SearchHit.PlaylistHit?> {
                semaphore.withPermit {
                    val count = hit.playlist.trackCount
                    if (count != null && count > 0) return@withPermit hit
                    val hasTracks = runCatching {
                        withTimeoutOrNull(10000L) { ytMusicRepository.browsePlaylist(hit.playlist.id).tracks.isNotEmpty() }
                    }.getOrNull()
                    if (hasTracks == false) null else hit
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    private suspend fun searchOther(query: String, filter: SearchFilter, loadMore: Boolean, state: SearchState): SearchResult = coroutineScope {
        val ytmPage = ytMusicRepository.search(query, filter, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        if (filter == SearchFilter.ALBUMS) {
            val albums = ytmPage.items.filterIsInstance<SearchHit.AlbumHit>()
            val validated = validateAlbums(albums)
            val newHits = validated.filter { state.seenKeys.add(it.stableKey) }
            return@coroutineScope SearchResult.Success(newHits, state.ytmHasMore)
        }
        val newHits = ytmPage.items.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = mutableListOf<T>()
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private companion object { const val TAG = "VzTuneMusic" }
}
FIX6EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt" <<'FIX6EOF'
package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.RecentPlayedStore
import com.vztechcore.vztune.data.prefs.store.SearchHistoryStore
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val historyStore: SearchHistoryStore,
    private val recentPlayedStore: RecentPlayedStore,
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val trackDownloader: TrackDownloader,
    private val playerController: PlayerController,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenPlaylist(
            val id: String, val title: String, val owner: String?,
            val thumbnailUrl: String?, val mosaic: List<String>,
        ) : NavigationEvent
        data class OpenAlbum(
            val id: String, val title: String, val artist: String,
            val thumbnailUrl: String?,
        ) : NavigationEvent
        data class OpenArtist(
            val id: String, val name: String, val thumbnailUrl: String?,
        ) : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private data class CachedTab(val query: String, val hits: List<SearchHit>, val hasMore: Boolean)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.items
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filterCache = mutableMapOf<SearchFilter, CachedTab>()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch {
            historyStore.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch {
            recentPlayedStore.tracks.collect { list ->
                _uiState.update { it.copy(recentTracks = list) }
            }
        }
    }

    fun onQueryChange(value: String) {
        suggestJob?.cancel()
        searchJob?.cancel()
        prefetchJob?.cancel()
        filterCache.clear()
        _uiState.update {
            it.copy(
                query = value,
                errorMessage = null,
                hasSearched = false,
                hits = emptyList(),
                hasMore = false,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
            )
        }
        if (value.isBlank() || value.trim().length < 1) {
            _uiState.update { it.copy(suggestions = SearchSuggestions()) }
            return
        }
        // FIX 6: sugestões a partir de 1 letra (antes: 2) e sem o delay de 400 ms.
        // 1) Instantâneo (0 ms): histórico local que casa com o que foi digitado.
        //    É local, não bate na rede — aparece na hora, igual ao SimpMusic.
        // 2) Rede (debounce curto de 120 ms, não 400 ms): sugestões do YT Music +
        //    faixas. O debounce só evita disparar uma request por tecla quando o
        //    usuário está digitando rápido; 120 ms é imperceptível.
        //    A rede em si continua levando o tempo dela — o que some é a espera
        //    artificial antes de disparar + a espera por algo local.
        val trimmed = value.trim()
        val localQueries = _uiState.value.history
            .filter { it.contains(trimmed, ignoreCase = true) && !it.equals(trimmed, ignoreCase = true) }
            .take(5)
        if (localQueries.isNotEmpty()) {
            _uiState.update { it.copy(suggestions = SearchSuggestions(queries = localQueries)) }
        }
        suggestJob = viewModelScope.launch {
            delay(120)
            val s = musicRepository.suggestions(value)
            // Usuário já digitou outra coisa? Não sobrescreve sugestões de outra query.
            if (_uiState.value.query != value) return@launch
            _uiState.update { it.copy(suggestions = s) }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
    }

    fun onFilterSelect(filter: SearchFilter) {
        if (filter == _uiState.value.selectedFilter) return
        val currentQuery = _uiState.value.query
        val cached = filterCache[filter]
        if (cached != null && cached.query == currentQuery) {
            _uiState.update { it.copy(selectedFilter = filter, hits = cached.hits, hasMore = cached.hasMore, hasSearched = true) }
            return
        }
        _uiState.update { it.copy(selectedFilter = filter) }
        if (currentQuery.isNotBlank()) {
            // A aba ainda não foi pré-carregada: busca ela agora e, depois, pré-carrega
            // o resto (o prefetch anterior é cancelado pra não duplicar a busca).
            prefetchJob?.cancel()
            runSearch(currentQuery, filter, isRefresh = false, loadMore = false)
            prefetchOtherTabs(currentQuery, filter)
        }
    }

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(query) }
        filterCache.clear()
        prefetchJob?.cancel()
        val selected = _uiState.value.selectedFilter
        runSearch(query, selected, isRefresh = false, loadMore = false)
        prefetchOtherTabs(query, selected)
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        filterCache.clear()
        prefetchJob?.cancel()
        val selected = _uiState.value.selectedFilter
        runSearch(_uiState.value.query, selected, isRefresh = true, loadMore = false)
        prefetchOtherTabs(_uiState.value.query, selected)
    }

    /**
     * FIX 6: pré-carrega TODAS as outras abas em segundo plano, assim que a busca
     * da aba aberta dispara. Quando o usuário troca de aba, o resultado já está
     * no filterCache e aparece na hora (onFilterSelect só mostra o cache).
     *
     * Por que SEQUENCIAL e não paralelo: o MusicRepository guarda um SearchState
     * por aba (ok em paralelo), mas as buscas do YouTube (NewPipe) compartilham
     * o OkHttp/downloader — rodar 6 buscas ao mesmo tempo estoura timeout e
     * mistura paginação. Sequencial é mais lento no total, mas a aba aberta já
     * mostra o resultado dela primeiro; o resto chega em segundo plano.
     *
     * Cada aba tem sua própria sessão NewPipe por searchId (FIX 6 no
     * YouTubeRepository), então o prefetch NÃO quebra o "carregar mais" da aba aberta.
     */
    private fun prefetchOtherTabs(query: String, selected: SearchFilter) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            for (filter in SearchFilter.entries) {
                if (filter == selected) continue
                // Query mudou no meio do prefetch? Para, a busca nova vai pré-carregar de novo.
                if (_uiState.value.query.trim() != query) break
                val already = filterCache[filter]
                if (already != null && already.query == query) continue
                val result = runCatching { musicRepository.search(query, filter, loadMore = false) }.getOrNull()
                if (result is SearchResult.Success) {
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    // Usuário trocou pra essa aba enquanto ela carregava: mostra na hora.
                    if (_uiState.value.selectedFilter == filter && _uiState.value.query.trim() == query) {
                        _uiState.update { it.copy(hits = result.hits, hasMore = result.hasMore, isLoading = false) }
                    }
                }
            }
        }
    }

    fun onRemoveHistory(query: String) { viewModelScope.launch { historyStore.remove(query) } }
    fun onClearHistory() { viewModelScope.launch { historyStore.clear() } }

    fun onLoadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.query.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val filter = state.selectedFilter
            when (val result = musicRepository.search(state.query, filter, loadMore = true)) {
                is SearchResult.Success -> {
                    val merged = _uiState.value.hits + result.hits
                    filterCache[filter] = CachedTab(state.query, merged, result.hasMore)
                    _uiState.update { it.copy(isLoadingMore = false, hits = merged, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onHitClick(hit: SearchHit) {
        when (hit) {
            is SearchHit.SongHit -> playRadio(hit.track)
            is SearchHit.VideoHit -> playRadio(hit.track)
            is SearchHit.PlaylistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenPlaylist(
                            hit.playlist.id,
                            hit.playlist.title,
                            hit.playlist.owner,
                            hit.playlist.thumbnailUrl,
                            hit.playlist.mosaicThumbnails,
                        )
                    )
                }
            }
            is SearchHit.AlbumHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenAlbum(
                            hit.album.id,
                            hit.album.title,
                            hit.album.artist,
                            hit.album.thumbnailUrl,
                        )
                    )
                }
            }
            is SearchHit.ArtistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            hit.artist.id,
                            hit.artist.name,
                            hit.artist.thumbnailUrl,
                        )
                    )
                }
            }
        }
    }

    fun onPlayNextClick(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueueClick(track: Track) {
        playerController.addToQueueEnd(track)
    }

    /** Strictly toggles favorite status without triggering any automatic download. */
    fun onToggleFavorite(track: Track) {
        viewModelScope.launch {
            favoriteTracksStore.toggle(track)
        }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            localPlaylistsStore.addTrack(playlistId, track)
        }
    }

    fun onDownloadTrack(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Mais parecido por Levenshtein, nunca mais o primeiro da busca
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }
    }

    private fun playRadio(seed: Track) {
        if (_uiState.value.isBuildingQueue) return
        _uiState.update { it.copy(isBuildingQueue = true) }
        viewModelScope.launch { recentPlayedStore.add(seed) }
        viewModelScope.launch {
            val queue = runCatching { musicRepository.buildRadioQueue(seed) }.getOrDefault(listOf(seed))
            _uiState.update { it.copy(isBuildingQueue = false) }
            // Rádio infinito: startRadio() faz a fila crescer sozinha quando está acabando.
            playerController.startRadio(seed, queue)
        }
    }

    private fun runSearch(query: String, filter: SearchFilter, isRefresh: Boolean, loadMore: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !loadMore,
                    isRefreshing = isRefresh,
                    hasSearched = true,
                    errorMessage = null,
                )
            }
            when (val result = musicRepository.search(query, filter, loadMore)) {
                is SearchResult.Success -> {
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    // Só mostra se a query/aba ainda for essa (prefetch ou troca de aba
                    // no meio da busca não pode sobrescrever a aba atual).
                    if (_uiState.value.query.trim() == query.trim() && _uiState.value.selectedFilter == filter) {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, hits = result.hits, hasMore = result.hasMore) }
                    }
                }
                is SearchResult.Failure -> {
                    if (_uiState.value.query.trim() == query.trim() && _uiState.value.selectedFilter == filter) {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message) }
                    }
                }
            }
        }
    }

}
FIX6EOF
