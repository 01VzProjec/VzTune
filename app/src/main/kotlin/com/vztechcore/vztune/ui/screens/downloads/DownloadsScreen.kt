package com.vztechcore.vztune.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.layout.DetailTopBar
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.Sync
import com.vztechcore.vztune.ui.icons.media.DownloadDone

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onBackClick: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onCancelDownload: (Track) -> Unit,
    onRetryDownload: (Track) -> Unit,
    onDismissFailed: (Track) -> Unit,
    onDeleteDownloaded: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        DetailTopBar(
            title = "Downloads",
            subtitle = "Gerenciador offline",
            onBackClick = onBackClick,
        )

        val isEmpty = state.activeDownloads.isEmpty() &&
            state.recentCompleted7Days.isEmpty() &&
            state.allDownloaded.isEmpty()

        if (isEmpty) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhum download em andamento ou concluído no momento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // 1. Downloads em andamento (mostra porcentagem em tempo real)
                if (state.activeDownloads.isNotEmpty()) {
                    item(key = "header_active") {
                        SectionTitle("Em andamento")
                    }
                    items(state.activeDownloads, key = { "active_${it.key}" }) { item ->
                        ActiveDownloadCard(
                            item = item,
                            onCancelClick = { onCancelDownload(item.track) },
                            onRetryClick = { onRetryDownload(item.track) },
                            onDismissClick = { onDismissFailed(item.track) },
                        )
                    }
                }

                // 2. Concluídos nos últimos 7 dias ("Faixa {título} baixada")
                if (state.recentCompleted7Days.isNotEmpty()) {
                    item(key = "header_recent") {
                        Spacer(Modifier.height(4.dp))
                        SectionTitle("Concluídos recentemente (7 dias)")
                    }
                    items(state.recentCompleted7Days, key = { "recent_${it.key}" }) { downloaded ->
                        RecentCompletedCard(
                            item = downloaded,
                            onClick = { onPlayTrack(downloaded.toTrack()) },
                            onDeleteClick = { onDeleteDownloaded(downloaded.toTrack()) },
                        )
                    }
                }

                // 3. Todas as faixas salvas no dispositivo
                if (state.allDownloaded.isNotEmpty()) {
                    item(key = "header_all") {
                        Spacer(Modifier.height(4.dp))
                        SectionTitle("Todas as faixas baixadas (${state.allDownloaded.size})")
                    }
                    items(state.allDownloaded, key = { "all_${it.key}" }) { downloaded ->
                        SavedTrackRow(
                            item = downloaded,
                            onClick = { onPlayTrack(downloaded.toTrack()) },
                            onDeleteClick = { onDeleteDownloaded(downloaded.toTrack()) },
                        )
                    }
                }

                item(key = "credits") { AppCredits() }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ActiveDownloadCard(
    item: TrackDownloadItem,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    val isFailed = item.status == DownloadStatus.FAILED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121829))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(
                imageUrl = item.track.thumbnailUrl,
                shape = ThumbnailShape.SQUARE,
                size = 48.dp,
                cornerRadius = 8.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (item.status) {
                        DownloadStatus.QUEUED -> "Na fila..."
                        DownloadStatus.DOWNLOADING -> {
                            val sizeInfo = formatBytesProgress(item.bytesDownloaded, item.totalBytes)
                            if (sizeInfo != null) "${item.progressPercent}% • $sizeInfo"
                            else "${item.progressPercent}%"
                        }
                        DownloadStatus.FAILED -> item.errorMessage ?: "Falha no download"
                        DownloadStatus.COMPLETED -> "Concluído"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailed) Color(0xFFF87171) else Color(0xFF60A5FA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isFailed) {
                IconButton(onClick = onRetryClick, modifier = Modifier.size(34.dp)) {
                    Icon(VzIcons.Sync, contentDescription = "Tentar novamente", tint = Color.White)
                }
                IconButton(onClick = onDismissClick, modifier = Modifier.size(34.dp)) {
                    Icon(VzIcons.Close, contentDescription = "Dispensar", tint = Color.White.copy(alpha = 0.7f))
                }
            } else {
                IconButton(onClick = onCancelClick, modifier = Modifier.size(34.dp)) {
                    Icon(VzIcons.Close, contentDescription = "Cancelar download", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        if (!isFailed) {
            Spacer(Modifier.height(10.dp))
            if (item.status == DownloadStatus.QUEUED || item.totalBytes <= 0L) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color(0xFF60A5FA),
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color(0xFF60A5FA),
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun RecentCompletedCard(
    item: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121829))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(
            imageUrl = item.thumbnailUrl,
            shape = ThumbnailShape.SQUARE,
            size = 48.dp,
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Faixa ${item.title} baixada",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.artist} • ${formatBytes(item.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4ADE80),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = VzIcons.DownloadDone,
            contentDescription = null,
            tint = Color(0xFF4ADE80),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDeleteClick, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = VzIcons.Delete,
                contentDescription = "Remover download",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SavedTrackRow(
    item: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(
            imageUrl = item.thumbnailUrl,
            shape = ThumbnailShape.SQUARE,
            size = 50.dp,
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.artist} • ${formatBytes(item.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDeleteClick, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = VzIcons.Delete,
                contentDescription = "Remover download",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatBytesProgress(downloaded: Long, total: Long): String? {
    if (total <= 0L) return null
    return "${formatBytes(downloaded)} / ${formatBytes(total)}"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}