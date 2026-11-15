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
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled
import com.vztechcore.vztune.ui.screens.medialist.MediaListUiState

private val SheetContainerColor = Color(0xFF121829)
private val DownloadGreen = Color(0xFF4ADE80)
private val DangerRed = Color(0xFFF87171)

/**
 * Context menu opened from the three-dots button in the header of a Playlist or Album screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListOptionsSheet(
    state: MediaListUiState,
    allTracksDownloaded: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onOpenDownloadsScreen: () -> Unit,
    onOpenArtistByName: (String) -> Unit,
    onDeleteLocalPlaylist: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteLocalPlaylistSheet(
            title = state.title,
            onConfirmDelete = {
                showDeleteConfirm = false
                onDismiss()
                onDeleteLocalPlaylist()
            },
            onDismiss = { showDeleteConfirm = false },
        )
        return
    }

    val typeLabel = when {
        state.isAlbum -> "Álbum"
        state.isLocal -> "Sua playlist"
        else -> "Playlist"
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
            // Header
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
                    if (state.mosaicThumbnails.size >= 4) {
                        PlaylistMosaic(state.mosaicThumbnails)
                    } else if (!state.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.thumbnailUrl,
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val sub = listOfNotNull(
                        typeLabel,
                        state.owner?.takeIf { it.isNotBlank() },
                        state.trackCountLabel.takeIf { state.allTracks.isNotEmpty() },
                    ).joinToString(" • ")
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // 1. Delete local playlist (only for user-created playlists)
            if (state.isLocal) {
                ListOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir playlist",
                    labelColor = DangerRed,
                    onClick = { showDeleteConfirm = true },
                )
            }

            // 2. Favorite / Unfavorite external playlist
            if (state.showFavorite) {
                ListOptionRow(
                    icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                    iconTint = if (state.isFavorite) Color(0xFFEC4899) else Color.White,
                    label = if (state.isFavorite) "Remover dos favoritos" else "Salvar na biblioteca",
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                )
            }

            // 3. Play next all
            if (state.allTracks.isNotEmpty()) {
                ListOptionRow(
                    icon = VzIcons.PlayCircleFilled,
                    label = "Reproduzir a seguir",
                    subtitle = "${state.allTracks.size} faixas",
                    onClick = {
                        onPlayNextAll()
                        onDismiss()
                    },
                )

                // 4. Add all to queue
                ListOptionRow(
                    icon = VzIcons.QueueMusic,
                    label = "Adicionar à fila",
                    onClick = {
                        onAddToQueueAll()
                        onDismiss()
                    },
                )

                // 5. Download all tracks (up to 5 simultaneously)
                if (allTracksDownloaded) {
                    ListOptionRow(
                        icon = VzIcons.DownloadDone,
                        iconTint = DownloadGreen,
                        label = if (state.isAlbum) "Álbum baixado" else "Playlist baixada",
                        subtitle = "Disponível para reprodução offline",
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                } else {
                    ListOptionRow(
                        icon = VzIcons.Download,
                        label = if (state.isAlbum) "Baixar álbum" else "Baixar todas as faixas",
                        subtitle = "Até 5 downloads simultâneos",
                        onClick = {
                            onDownloadAll()
                            onDismiss()
                        },
                    )
                }
            }

            // 6. Go to Artist (for Albums)
            val artistName = state.owner?.takeIf {
                state.isAlbum && it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
            }
            if (artistName != null) {
                ListOptionRow(
                    icon = VzIcons.Artist,
                    label = "Ir para o artista",
                    subtitle = artistName,
                    onClick = {
                        onDismiss()
                        onOpenArtistByName(artistName)
                    },
                )
            }

            // 7. Share (for external playlists & albums)
            if (!state.isLocal) {
                ListOptionRow(
                    icon = VzIcons.Share,
                    label = "Compartilhar",
                    onClick = {
                        shareMediaList(context, state)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteLocalPlaylistSheet(
    title: String,
    onConfirmDelete: () -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Excluir \"$title\"?",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Esta playlist será removida permanentemente da sua biblioteca. As faixas baixadas no dispositivo não serão apagadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onConfirmDelete,
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
                    Icon(VzIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ListOptionRow(
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

private fun shareMediaList(context: Context, state: MediaListUiState) {
    val cleanId = state.listId.removePrefix("VL")
    val url = if (state.isAlbum) {
        "https://music.youtube.com/browse/$cleanId"
    } else {
        "https://music.youtube.com/playlist?list=$cleanId"
    }
    val kindLabel = if (state.isAlbum) "álbum" else "playlist"
    val ownerPart = state.owner?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
    val shareText = "Confere esse $kindLabel no VzTune 🎧\n${state.title}$ownerPart\n$url"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, state.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Compartilhar"))
}