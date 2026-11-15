package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onOpenArtistByName: (String) -> Unit,
    onTrackInfo: (Track) -> Unit,
) {
    val context = LocalContext.current
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
                onOpenArtistByName(artistName)
            },
            onDismiss = { showArtistPicker = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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

            // 7. Go to album (universal across all submenus whenever available)
            val albumTitle = resolvedAlbum?.title ?: track.album?.takeIf {
                it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
            }
            if (resolvedAlbum != null && !albumTitle.isNullOrBlank()) {
                SheetOptionRow(
                    icon = VzIcons.Album,
                    label = "Álbum",
                    subtitle = albumTitle,
                    onClick = {
                        onDismiss()
                        onOpenAlbum(resolvedAlbum)
                    },
                )
            }

            // 8. Go to artist(s)
            if (artists.isNotEmpty()) {
                val hasMultiple = artists.size > 1
                SheetOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = if (hasMultiple) "Ir para os artistas" else "Ir para o artista",
                    subtitle = if (hasMultiple) artists.joinToString(", ") else artists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistByName(artists.first())
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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