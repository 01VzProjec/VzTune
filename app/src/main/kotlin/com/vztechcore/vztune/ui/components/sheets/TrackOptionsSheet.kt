package com.vztechcore.vztune.ui.components.sheets

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreHoriz
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    localPlaylists: List<LocalPlaylist> = emptyList(),
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (playlistId: String) -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onOpenArtist: (artistName: String) -> Unit = {},
    onTrackInfoClick: () -> Unit,
) {
    val context = LocalContext.current
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }

    val artists = remember(track.artist) {
        parseArtists(track.artist)
    }

    if (showPlaylistPicker) {
        PlaylistPickerSheet(
            playlists = localPlaylists,
            onPick = { playlist ->
                onAddToPlaylist(playlist.id)
                showPlaylistPicker = false
                onDismiss()
            },
            onDismiss = { showPlaylistPicker = false },
        )
    }

    if (showArtistPicker) {
        ArtistPickerSheet(
            artists = artists,
            onPick = { artistName ->
                showArtistPicker = false
                onDismiss()
                onOpenArtist(artistName)
            },
            onDismiss = { showArtistPicker = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121829),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Cabeçalho com capa, título e subtítulo da faixa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaThumbnail(
                    imageUrl = track.thumbnailUrl,
                    shape = ThumbnailShape.SQUARE,
                    size = 54.dp,
                    cornerRadius = 10.dp,
                )
                Spacer(Modifier.width(12.dp))
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
                    TrackSubtitle(
                        track = track,
                        style = MaterialTheme.typography.bodySmall,
                        prefixColor = Color.White.copy(alpha = 0.55f),
                        artistColor = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // 1. Favoritar / Desfavoritar
            OptionItem(
                icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                iconTint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                label = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
            )

            // 2. Adicionar à playlist
            OptionItem(
                icon = VzIcons.PlaylistAdd,
                label = "Adicionar à playlist",
                onClick = { showPlaylistPicker = true },
            )

            // 3. Reproduzir como próxima faixa
            OptionItem(
                icon = VzIcons.PlayCircleFilled,
                label = "Reproduzir como próxima faixa",
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
            )

            // 4. Adicionar à fila
            OptionItem(
                icon = VzIcons.QueueMusic,
                label = "Adicionar à fila",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                },
            )

            // 5. Baixar faixa
            OptionItem(
                icon = if (isDownloaded) VzIcons.DownloadDone else VzIcons.Download,
                iconTint = if (isDownloaded) Color(0xFF4ADE80) else Color.White,
                label = if (isDownloaded) "Faixa baixada" else "Baixar faixa",
                onClick = {
                    onDownloadClick()
                    onDismiss()
                },
            )

            // 6. Compartilhar faixa
            OptionItem(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    val url = if (track.source == Source.YOUTUBE) {
                        "https://www.youtube.com/watch?v=${track.id}"
                    } else {
                        "https://music.youtube.com/watch?v=${track.id}"
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, track.title)
                        putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n$url")
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Compartilhar faixa")
                    context.startActivity(shareIntent)
                    onDismiss()
                },
            )

            // 7. Ir para o artista / Ir para os artistas
            if (artists.isNotEmpty()) {
                val hasMultipleArtists = artists.size > 1
                OptionItem(
                    icon = if (hasMultipleArtists) VzIcons.Groups else VzIcons.Artist,
                    label = if (hasMultipleArtists) "Ir para os artistas" else "Ir para o artista",
                    onClick = {
                        if (hasMultipleArtists) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtist(artists.first())
                        }
                    },
                )
            }

            // 8. Ver informações da faixa (tela existente)
            OptionItem(
                icon = VzIcons.MoreHoriz,
                label = "Ver informações da faixa",
                onClick = {
                    onDismiss()
                    onTrackInfoClick()
                },
            )
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
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
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B32),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Adicionar à playlist",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "Você ainda não criou nenhuma playlist. Crie uma na Biblioteca, em \"Suas playlists\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(playlist) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VzIcons.PlaylistAdd,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${playlist.tracks.size} faixas",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
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
        containerColor = Color(0xFF141B32),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            artists.forEach { artistName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(artistName) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VzIcons.Artist,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun parseArtists(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw
        .split(Regex("\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}