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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.components.rows.SheetDownloadProgressRow
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.theme.DownloadGreen
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
import com.vztechcore.vztune.ui.icons.actions.Credits
import com.vztechcore.vztune.ui.icons.actions.PlayNext
import com.vztechcore.vztune.ui.icons.actions.AddToQueue
import com.vztechcore.vztune.ui.icons.actions.RemoveFromPlaylist
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd

import com.vztechcore.vztune.ui.icons.media.Radio


private val SheetContainerColor = Color(0xFF121829)
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
    var showCredits by remember { mutableStateOf(false) }
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

    if (showCredits) {
        TrackCreditsSheet(track = track, onDismiss = { showCredits = false })
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

            // FIX10: 3 cards no topo (retangulos medios lado a lado, nome abaixo, icone dentro)
    val isYouTubeVideoTop = track.source == Source.YOUTUBE
    val downloadProgressTop = downloadItem?.progress ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val isLocal = menuContext == TrackMenuContext.LOCAL_PLAYLIST
        val isRecent = menuContext == TrackMenuContext.RECENT_HISTORY
        if (isLocal) {
            TopActionCard(icon = VzIcons.RemoveFromPlaylist, label = "Excluir da playlist", iconTint = Color(0xFFFF6B6B), onClick = { onRemoveFromLocalPlaylist(track); onDismiss() })
        } else if (isRecent) {
            TopActionCard(icon = VzIcons.DeleteFilled, label = "Apagar", iconTint = Color(0xFFFF6B6B), onClick = { onRemoveFromRecentHistory(track); onDismiss() })
        } else if (isDownloading) {
            TopActionCard(icon = VzIcons.Download, label = "Baixando...", iconTint = DownloadGreen, onClick = { onOpenDownloadsScreen(); onDismiss() })
        } else if (isDownloaded) {
            TopActionCard(icon = VzIcons.DownloadDone, label = "Baixada", iconTint = DownloadGreen, onClick = { showRemoveDownloadSheet = true })
        } else {
            TopActionCard(icon = VzIcons.Download, label = "Baixar faixa", iconTint = Color.White, onClick = { onDownload(track); onDismiss() })
        }
        TopActionCard(icon = VzIcons.PlaylistAdd, label = "Adicionar a uma playlist", iconTint = Color.White, onClick = { showPlaylistPicker = true })
        TopActionCard(icon = VzIcons.Share, label = "Compartilhar", iconTint = Color.White, onClick = { shareTrack(context, track); onDismiss() })
    }
    // 1. Baixar (somente playlist local / recentemente tocadas: vira primeiro da lista)
    if (menuContext == TrackMenuContext.LOCAL_PLAYLIST || menuContext == TrackMenuContext.RECENT_HISTORY) {
        if (isDownloading) {
            SheetDownloadProgressRow(progress = downloadProgressTop, label = "Baixando...", onClick = { onOpenDownloadsScreen(); onDismiss() })
        } else if (isDownloaded) {
            SheetOptionRow(icon = VzIcons.DownloadDone, label = "Baixada", subtitle = "Ver em Downloads", iconTint = DownloadGreen, onClick = { onOpenDownloadsScreen(); onDismiss() })
        } else {
            SheetOptionRow(icon = VzIcons.Download, label = "Baixar faixa", subtitle = "Ouvir offline", onClick = { onDownload(track); onDismiss() })
        }
    }
    // 2. Salvar aos favoritos
    SheetOptionRow(
        icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
        label = if (isFavorite) "Remover dos favoritos" else "Salvar aos favoritos",
        iconTint = if (isFavorite) Color(0xFFEC4899) else Color.White,
        onClick = { onToggleFavorite(track); onDismiss() },
    )
    // 3. Reproduzir como proxima faixa
    SheetOptionRow(icon = VzIcons.AddToQueue, label = "Reproduzir como proxima faixa", subtitle = "Toca logo depois da atual", onClick = { onPlayNext(track); onDismiss() })
    // 4. Adicionar a fila
    SheetOptionRow(icon = VzIcons.PlayNext, label = "Adicionar a fila", subtitle = "Toca no final da fila", onClick = { onAddToQueue(track); onDismiss() })
    // 5. Iniciar radio
    SheetOptionRow(icon = VzIcons.Radio, label = "Iniciar radio", subtitle = "Baseada nesta faixa", onClick = { onStartRadio(track); onDismiss() })
    // 6. Ir para o album
    if (resolvedAlbum != null || !track.album.isNullOrBlank()) {
        SheetOptionRow(
            icon = VzIcons.Album,
            label = "Ir para o album",
            subtitle = resolvedAlbum?.title ?: track.album,
            onClick = {
                if (resolvedAlbum != null) onOpenAlbum(resolvedAlbum) else onOpenAlbumForTrack(track)
                onDismiss()
            },
        )
    }
    // 7. Ir para a pagina do artista (1 artista / varios artistas / video = canal)
    if (artists.isNotEmpty()) {
        val hasMultipleArtists = artists.size > 1 && !isYouTubeVideoTop
        SheetOptionRow(
            icon = if (hasMultipleArtists) VzIcons.Groups else VzIcons.Artist,
            label = when {
                isYouTubeVideoTop -> "Ir para o canal"
                hasMultipleArtists -> "Ir para a pagina do artista..."
                else -> "Ir para a pagina do artista"
            },
            subtitle = if (hasMultipleArtists) artists.joinToString(", ") else artists.first(),
            onClick = {
                if (hasMultipleArtists) {
                    showArtistPicker = true
                } else {
                    onDismiss()
                    onOpenArtistForTrack(track, artists.first())
                }
            },
        )
    }
    // 8. Mostrar creditos (somente musica ou video clipe da musica)
    val showCreditsOption = track.source == Source.YOUTUBE_MUSIC || (track.source == Source.YOUTUBE && !track.album.isNullOrBlank())
    if (showCreditsOption) {
        SheetOptionRow(icon = VzIcons.Credits, label = "Mostrar creditos da musica", subtitle = "Compositores, produtores e mais", onClick = { showCredits = true })
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
    // Atalho fino: logica mora em components/rows/SheetOptionRow.kt
    SheetOptionRow(icon = icon, label = label, subtitle = subtitle, iconTint = iconTint, labelColor = labelColor, onClick = onClick)
}



@Composable
private fun SheetDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    // Atalho fino: logica mora em components/rows/SheetOptionRow.kt
    SheetDownloadProgressRow(progress = progress, label = label, onClick = onClick)
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

@Composable
private fun RowScope.TopActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, iconTint: Color = Color.White, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
