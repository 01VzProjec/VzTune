package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.player.VzCoverColorPicker
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.AddPhotoAlternate
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.Edit
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled
import com.vztechcore.vztune.ui.screens.medialist.MediaListUiState

private val SheetContainerColor = Color(0xFF121829)
private val DownloadGreen = Color(0xFF4ADE80)
private val DangerRed = Color(0xFFF87171)

/**
 * Context menu opened from the three-dots button in the header of a playlist or album screen.
 *
 * For local playlists it includes the Vz color editor:
 * - "Alterar miniatura" picks a photo, the photo replaces the color.
 * - The color picker below it picks a Vz color, the color replaces the photo.
 * Both change the official cover shown in the Library and inside the playlist.
 *
 * All UI text is in Portuguese. Code and comments are in English.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListOptionsSheet(
    state: MediaListUiState,
    allTracksDownloaded: Boolean,
    isDownloadingBatch: Boolean = false,
    batchDownloadProgress: Float = 0f,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onOpenDownloadsScreen: () -> Unit,
    onOpenArtistByName: (String) -> Unit,
    onDeleteLocalPlaylist: () -> Unit,
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (Uri) -> Unit = {},
    onChangeCoverColor: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }

    val albumArtists = remember(state.owner, state.isAlbum) {
        if (state.isAlbum) parseMediaListArtists(state.owner.orEmpty()) else emptyList()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            onChangeLocalPlaylistCover(uri)
            onDismiss()
        }
    }

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

    if (showRenameSheet) {
        RenameLocalPlaylistSheet(
            currentTitle = state.title,
            onConfirmRename = { newTitle ->
                showRenameSheet = false
                onRenameLocalPlaylist(newTitle)
                onDismiss()
            },
            onDismiss = { showRenameSheet = false },
        )
        return
    }

    if (showArtistPicker) {
        MediaListArtistPickerSheet(
            artists = albumArtists,
            onPick = { selectedArtist ->
                showArtistPicker = false
                onDismiss()
                onOpenArtistByName(selectedArtist)
            },
            onDismiss = { showArtistPicker = false },
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
            // Header with cover preview
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
                            )
                        }
                        !state.thumbnailUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = state.thumbnailUrl,
                                contentDescription = state.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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

            // Local playlist management options
            if (state.isLocal) {
                ListOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir playlist",
                    labelColor = DangerRed,
                    onClick = { showDeleteConfirm = true },
                )
                ListOptionRow(
                    icon = VzIcons.Edit,
                    label = "Alterar título",
                    onClick = { showRenameSheet = true },
                )
                ListOptionRow(
                    icon = VzIcons.AddPhotoAlternate,
                    label = "Alterar miniatura",
                    subtitle = "Escolher foto da galeria",
                    onClick = { imagePickerLauncher.launch("image/*") },
                )

                // Vz color editor
                Text(
                    text = "Cor da capa Vz",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                )
                Text(
                    text = "Sem foto, o Vz com essa cor vira a capa da playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                VzCoverColorPicker(
                    selectedToken = state.thumbnailUrl.takeIf { PlaylistCoverPalette.isDefaultColorToken(it) },
                    onSelect = { token ->
                        onChangeCoverColor(token)
                        onDismiss()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                )
            }

            // Favorite / Unfavorite external playlist
            if (state.showFavorite) {
                ListOptionRow(
                    icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                    label = if (state.isFavorite) "Desfavoritar" else "Favoritar",
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                )
            }

            // Play next / queue
            ListOptionRow(
                icon = VzIcons.PlayCircleFilled,
                label = "Tocar em seguida",
                onClick = {
                    onPlayNextAll()
                    onDismiss()
                },
            )
            ListOptionRow(
                icon = VzIcons.QueueMusic,
                label = "Adicionar à fila",
                onClick = {
                    onAddToQueueAll()
                    onDismiss()
                },
            )

            // Download section
            if (state.allTracks.isNotEmpty()) {
                if (isDownloadingBatch) {
                    BatchDownloadProgressRow(
                        progress = batchDownloadProgress,
                        label = "Baixando... toque para ver",
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                } else if (allTracksDownloaded) {
                    ListOptionRow(
                        icon = VzIcons.DownloadDone,
                        iconTint = DownloadGreen,
                        label = "Todas as faixas baixadas",
                        subtitle = "Ver downloads",
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                } else {
                    ListOptionRow(
                        icon = VzIcons.Download,
                        label = if (state.isAlbum) "Baixar álbum" else "Baixar todas as faixas",
                        subtitle = "Até 30 downloads simultâneos",
                        onClick = {
                            onDownloadAll()
                            onDismiss()
                        },
                    )
                }
            }

            // Go to Artist(s) for albums
            if (albumArtists.isNotEmpty()) {
                val hasMultiple = albumArtists.size > 1
                ListOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = if (hasMultiple) "Ir para os artistas" else "Ir para o artista",
                    subtitle = if (hasMultiple) albumArtists.joinToString(", ") else albumArtists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistByName(albumArtists.first())
                        }
                    },
                )
            }

            // Share for external playlists and albums
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

/** Sheet to pick one artist when an album has several artists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListArtistPickerSheet(
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
                ListOptionRow(
                    icon = VzIcons.Artist,
                    label = artistName,
                    onClick = { onPick(artistName) },
                )
            }
        }
    }
}

/** Parses a raw artist string like "A, B feat. C" into individual artist names. */
internal fun parseMediaListArtists(raw: String): List<String> {
    if (raw.isBlank() || raw.equals("Desconhecido", ignoreCase = true)) return emptyList()
    return raw
        .split(
            Regex(
                "\\s*(?:,|&|/|;|\\be\\b|\\band\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b|\\bcom\\b|\\bwith\\b)\\s*",
                RegexOption.IGNORE_CASE,
            )
        )
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
        .distinct()
}

/** Row showing batch download progress. */
@Composable
private fun BatchDownloadProgressRow(
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
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Sheet to rename a local playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameLocalPlaylistSheet(
    currentTitle: String,
    onConfirmRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(currentTitle) { mutableStateOf(currentTitle) }
    val trimmed = text.trim()

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
                text = "Alterar título da playlist",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Nome da playlist", color = Color.White.copy(alpha = 0.45f)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { if (trimmed.isNotBlank()) onConfirmRename(trimmed) },
                    enabled = trimmed.isNotBlank() && trimmed != currentTitle,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E40AF),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Confirmation sheet for deleting a local playlist. */
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f).height(46.dp),
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
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Icon(VzIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Single option row inside the options sheet. */
@Composable
private fun ListOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val rowModifier = if (enabled) {
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 13.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp)
    }
    Row(
        modifier = rowModifier,
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

/** Shares the current playlist / album using the system share sheet. */
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
