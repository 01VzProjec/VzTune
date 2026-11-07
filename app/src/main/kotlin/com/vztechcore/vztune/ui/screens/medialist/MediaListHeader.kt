package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ================= HERO COVER WITH BOTTOM FADE + DESCRIPTION =================
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            if (state.mosaicThumbnails.size >= 4) {
                PlaylistMosaicLarge(state.mosaicThumbnails)
            } else {
                AsyncImage(
                    model = state.thumbnailUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Dark gradient — top for icons, bottom for the fade into the list
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.45f),
                                0.5f to Color.Transparent,
                                0.82f to Color.Black.copy(alpha = 0.85f),
                                1.0f to Color.Black
                            )
                        )
                    )
            )

            // Top action buttons
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaListCircleButton(Icons.Filled.Close, "Fechar", onBackClick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaListCircleButton(Icons.Filled.Search, "Pesquisar", onSearchClick)
                    MediaListCircleButton(Icons.Filled.MoreVert, "Mais", { })
                }
            }

            // Description card — slides down from the top when the playlist has a description
            val description = state.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                var expanded by remember(description) { mutableStateOf(false) }
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        initialOffsetY = { -it * 2 },
                        animationSpec = tween(
                            durationMillis = 700,
                            delayMillis = 200,
                            easing = FastOutSlowInEasing,
                        )
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 500, delayMillis = 200)
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                ) {
                    DescriptionCard(
                        description = description,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                    )
                }
            }
        }

        // ================= CENTERED TITLE & CREATOR DETAILS =================
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-12).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            // Creator profile row with avatar picture
            val ownerName = state.owner ?: state.subtitle
            if (!ownerName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!state.ownerAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.ownerAvatarUrl,
                            contentDescription = ownerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))
            }

            // Stats row: "<views> • <total duration>", falls back to track count + duration
            val statsText = buildString {
                if (!state.views.isNullOrBlank()) append(state.views)
                if (state.totalDurationLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(state.totalDurationLabel)
                }
            }.ifBlank { state.metadataLabel }

            if (statsText.isNotBlank()) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.showTabs) {
            MediaListTabs(state.selectedTab, onTabSelected)
            Spacer(Modifier.height(12.dp))
        }

        // ================= ACTION BUTTONS =================
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
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
                    .background(Color.White.copy(alpha = 0.12f))
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
private fun DescriptionCard(
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Sobre a playlist",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Recolher" else "Expandir",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MediaListCollapsedHeader(
    title: String,
    thumbnailUrl: String?,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.98f), Color.Black.copy(alpha = 0.85f), Color.Transparent)
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
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Filled.Search, contentDescription = "Pesquisar", tint = Color.White)
        }
    }
}

@Composable
internal fun PlaylistMosaicLarge(thumbnails: List<String>) {
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