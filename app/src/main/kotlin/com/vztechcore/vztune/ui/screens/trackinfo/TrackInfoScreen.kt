package com.vztechcore.vztune.ui.screens.trackinfo

import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.components.AppCredits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoScreen(
    state: TrackInfoUiState,
    onBackClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val dominantColor = rememberDominantColor(state.thumbnailUrl)
    val context = LocalContext.current

    if (showPlaylistSheet) {
        AddToPlaylistSheet(
            playlists = state.localPlaylists,
            onPick = { playlist ->
                onAddToPlaylist(playlist.id)
                showPlaylistSheet = false
            },
            onDismiss = { showPlaylistSheet = false },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Gradient from dominant color to black
        Box(
            Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.6f),
                            dominantColor.copy(alpha = 0.2f),
                            Color.Black,
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text("Sobre a faixa", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Thumbnail
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                if (state.thumbnailUrl != null) {
                    AsyncImage(
                        model = state.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Info rows
            InfoRow("Título", state.title)
            InfoRow(if (state.isVideo) "Canal" else "Artistas", state.artist)
            if (!state.isVideo) {
                InfoRow("Álbum", state.album ?: "Desconhecido")
            }
            InfoRow("Itag", state.itag)
            InfoRow("Tipo Mime", state.mimeType)
            InfoRow("Qualidade", state.quality)

            if (state.isLoading) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                state.viewCount?.let { InfoRow("Visualizações", it) }
                state.likeCount?.let { InfoRow("Gostei", it) }
                state.description?.let { InfoRow("Descrição", it) }
            }

            // Source URL (clickable)
            if (state.sourceUrl.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("URL fonte", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.sourceUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF60A5FA),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                            context.startActivity(intent)
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            TrackInfoAction(
                icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (state.isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = onFavoriteClick,
            )
            TrackInfoAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Adicionar à playlist",
                onClick = { showPlaylistSheet = true },
            )
            TrackInfoAction(
                icon = Icons.Filled.Download,
                label = "Baixar faixa em alta qualidade no dispositivo",
                onClick = { /* TODO: download */ },
            )

            AppCredits()

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/** Rounded action button of the track info screen. */
@Composable
private fun TrackInfoAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Lists the user's local playlists; tapping one adds the track to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF141414)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                "Adicionar à playlist",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    "Você ainda não criou nenhuma playlist. Crie uma na Biblioteca, em \"Suas playlists\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            playlists.forEach { playlist ->
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(playlist) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
}
