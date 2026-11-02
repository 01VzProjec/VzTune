package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.search.SortOrder

@Composable
internal fun MediaListExpandedHeader(
    state: MediaListUiState,
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onShuffleToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaListCircleButton(Icons.Filled.Close, "Fechar", onBackClick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaListCircleButton(Icons.Filled.Search, "Pesquisar", onSearchClick)
                MediaListCircleButton(Icons.Filled.MoreVert, "Mais", { })
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.mosaicThumbnails.size >= 4) {
                PlaylistMosaicLarge(state.mosaicThumbnails)
            } else {
                AsyncImage(
                    model = state.thumbnailUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (!state.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (state.metadataLabel.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                state.metadataLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (state.showTabs) {
            Spacer(Modifier.height(16.dp))
            MediaListTabs(state.selectedTab, onTabSelected)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
                    Icons.Filled.PlayArrow,
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
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { onShuffleClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatório",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.isSortEnabled && state.sortOrder == SortOrder.SHUFFLE) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        imageVector = if (state.isShuffling) Icons.Filled.Stop else Icons.Filled.Sync,
                        contentDescription = "Sortear animado",
                        tint = if (state.isShuffling) Color(0xFF1E40AF) else Color.White,
                    )
                }
            }

            if (state.isSortEnabled) {
                IconButton(onClick = onSortClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
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
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.85f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaListCircleButton(Icons.Filled.Close, "Fechar", onBackClick)
        Spacer(Modifier.width(12.dp))
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
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
        IconButton(onClick = { }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White) }
        IconButton(onClick = { }) { Icon(Icons.Filled.Search, contentDescription = "Pesquisar", tint = Color.White) }
    }
}

@Composable
internal fun PlaylistMosaicLarge(thumbnails: List<String>) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                AsyncImage(
                    model = thumbnails.getOrNull(0),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AsyncImage(
                    model = thumbnails.getOrNull(2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            Column(Modifier.weight(1f)) {
                AsyncImage(
                    model = thumbnails.getOrNull(1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AsyncImage(
                    model = thumbnails.getOrNull(3),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}