package com.vztechcore.vztune.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.layout.DetailTopBar
import com.vztechcore.vztune.ui.components.sheets.RemoveDownloadSheet
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.icons.player.Play

private val NavyBorder = Color(0xFF1E3A8A)
private val ProgressBlue = Color(0xFF3B82F6)
private val DownloadGreen = Color(0xFF4ADE80)
private val DangerRed = Color(0xFFF87171)

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onBackClick: () -> Unit,
    onTabSelected: (DownloadsTab) -> Unit,
    onTogglePauseResume: (Track) -> Unit,
    onCancelDownload: (Track) -> Unit,
    onDeleteDownload: (Track) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onCancelAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var trackPendingDeletion by remember { mutableStateOf<Track?>(null) }

    trackPendingDeletion?.let { targetTrack ->
        RemoveDownloadSheet(
            track = targetTrack,
            onConfirmRemove = {
                onDeleteDownload(targetTrack)
                trackPendingDeletion = null
            },
            onDismiss = { trackPendingDeletion = null },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        DetailTopBar(title = "Histórico de downloads", onBackClick = onBackClick)

        DownloadsTabBar(
            selectedTab = state.selectedTab,
            inProgressCount = state.activeDownloads.size,
            completedCount = state.completedDownloads.size,
            onTabSelected = onTabSelected,
        )

        Spacer(Modifier.height(10.dp))

        when (state.selectedTab) {
            DownloadsTab.IN_PROGRESS -> {
                if (state.activeDownloads.isEmpty()) {
                    EmptyDownloadsState(message = "Nenhum download em andamento no momento.")
                } else {
                    QueueStatusHeader(
                        state = state,
                        onPauseAll = onPauseAll,
                        onResumeAll = onResumeAll,
                        onCancelAll = onCancelAll,
                    )

                    Spacer(Modifier.height(10.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.activeDownloads,
                            key = { "${it.track.source.name}_${it.track.id}" },
                        ) { item ->
                            ActiveDownloadCard(
                                item = item,
                                onTogglePauseResume = { onTogglePauseResume(item.track) },
                                onCancel = { onCancelDownload(item.track) },
                            )
                        }
                        item(key = "credits_active") { AppCredits() }
                    }
                }
            }

            DownloadsTab.COMPLETED -> {
                if (state.completedDownloads.isEmpty()) {
                    EmptyDownloadsState(message = "Nenhuma faixa baixada ainda.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.completedDownloads,
                            key = { it.key },
                        ) { downloaded ->
                            val track = downloaded.toTrack()
                            CompletedDownloadCard(
                                downloaded = downloaded,
                                onClick = { onPlayTrack(track) },
                                onDeleteClick = { trackPendingDeletion = track },
                            )
                        }
                        item(key = "credits_completed") { AppCredits() }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueStatusHeader(
    state: DownloadsUiState,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val summaryParts = buildList {
        add("${state.downloadingCount}/${TrackDownloader.MAX_CONCURRENT_DOWNLOADS} simultâneos")
        if (state.queuedCount > 0) add("${state.queuedCount} na fila")
        if (state.pausedCount > 0) add("${state.pausedCount} pausados")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summaryParts.joinToString(" • "),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (state.activeDownloads.size > 1) {
            Spacer(Modifier.width(8.dp))
            if (state.hasRunningOrQueued) {
                QueueActionChip(label = "Pausar tudo", onClick = onPauseAll)
            } else if (state.hasPausedOrFailed) {
                QueueActionChip(label = "Retomar tudo", onClick = onResumeAll)
            }
            Spacer(Modifier.width(6.dp))
            QueueActionChip(label = "Limpar", isDanger = true, onClick = onCancelAll)
        }
    }
}

@Composable
private fun QueueActionChip(
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val borderColor = if (isDanger) DangerRed.copy(alpha = 0.45f) else NavyBorder
    val textColor = if (isDanger) DangerRed else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DownloadsTabBar(
    selectedTab: DownloadsTab,
    inProgressCount: Int,
    completedCount: Int,
    onTabSelected: (DownloadsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DownloadsTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val count = if (tab == DownloadsTab.IN_PROGRESS) inProgressCount else completedCount
            val labelText = if (count > 0) "${tab.label} ($count)" else tab.label

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) NavyBorder.copy(alpha = 0.65f) else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    item: TrackDownloadItem,
    onTogglePauseResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val isPaused = item.status == DownloadStatus.PAUSED
    val isFailed = item.status == DownloadStatus.FAILED
    val isQueued = item.status == DownloadStatus.QUEUED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Transparent)
            .border(BorderStroke(1.5.dp, NavyBorder), shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                if (item.track.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.track.thumbnailUrl,
                        contentDescription = item.track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        isFailed -> item.errorMessage ?: "Falha no download"
                        isPaused -> "Pausado • ${item.percentage}%"
                        isQueued -> "Aguardando vaga na fila..."
                        else -> "Baixando • ${item.percentage}%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isFailed -> DangerRed
                        isPaused || isQueued -> Color.White.copy(alpha = 0.6f)
                        else -> ProgressBlue
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Botão Pausar / Continuar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                    .clickable { onTogglePauseResume() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPaused || isFailed) VzIcons.Play else VzIcons.Pause,
                    contentDescription = if (isPaused || isFailed) "Continuar download" else "Pausar download",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Botão Parar / Cancelar (X)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.Close,
                    contentDescription = "Parar download",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            isQueued -> {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ProgressBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
            item.progress > 0f || isPaused -> {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (isPaused) Color.White.copy(alpha = 0.45f) else ProgressBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
            else -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ProgressBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    downloaded: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val sizeLabel = formatBytes(downloaded.fileSizeBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Transparent)
            .border(BorderStroke(1.5.dp, NavyBorder), shape)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (downloaded.thumbnailUrl != null) {
                AsyncImage(
                    model = downloaded.thumbnailUrl,
                    contentDescription = downloaded.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = downloaded.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = downloaded.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (sizeLabel != null) "Baixada • $sizeLabel" else "Baixada",
                style = MaterialTheme.typography.labelSmall,
                color = DownloadGreen,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                .clickable { onDeleteClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VzIcons.Delete,
                contentDescription = "Remover download",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun EmptyDownloadsState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatBytes(bytes: Long): String? {
    if (bytes <= 0L) return null
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}