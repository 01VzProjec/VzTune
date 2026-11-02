package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.MediaThumbnail
import com.vztechcore.vztune.ui.components.ThumbnailShape
import com.vztechcore.vztune.ui.components.TrackSubtitle

@Composable
fun MediaListScreen(
    state: MediaListUiState,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ================= HEADER =================
            item {
                MediaListHeader(
                    state = state,
                    onBackClick = onBackClick,
                    onPlayAllClick = onPlayAllClick,
                )
            }

            // ================= TRACK LIST =================
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else if (state.errorMessage != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(state.errorMessage, color = Color.Red)
                    }
                }
            } else if (state.tracks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhuma faixa encontrada.", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            } else {
                itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        index = index + 1,
                        track = track,
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaListHeader(
    state: MediaListUiState,
    onBackClick: () -> Unit,
    onPlayAllClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Botão de voltar no topo
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Capa ou Mosaico
        if (state.mosaicThumbnails.size >= 4) {
            // Mosaico 2x2 para playlists
            PlaylistMosaicLarge(state.mosaicThumbnails)
        } else {
            // Capa única para álbuns
            AsyncImage(
                model = state.thumbnailUrl,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (!state.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onPlayAllClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Tocar tudo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaylistMosaicLarge(thumbnails: List<String>) {
    Box(
        Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                AsyncImage(model = thumbnails[0], null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                AsyncImage(model = thumbnails[2], null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                AsyncImage(model = thumbnails[1], null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                AsyncImage(model = thumbnails[3], null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: Track,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(28.dp),
        )

        MediaThumbnail(
            imageUrl = track.thumbnailUrl,
            shape = ThumbnailShape.SQUARE,
            size = 48.dp,
            cornerRadius = 6.dp,
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // Aqui usa o TrackSubtitle que separa "Song —" e "Video —"
            TrackSubtitle(
                track = track,
                style = MaterialTheme.typography.bodySmall,
                prefixColor = Color.White.copy(alpha = 0.55f),
                artistColor = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}