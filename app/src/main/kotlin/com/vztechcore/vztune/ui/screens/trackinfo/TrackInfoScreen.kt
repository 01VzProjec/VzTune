package com.vztechcore.vztune.ui.screens.trackinfo

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
import com.vztechcore.vztune.ui.components.rememberDominantColor
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoScreen(
    state: TrackInfoUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dominantColor = rememberDominantColor(state.thumbnailUrl)
    val context = LocalContext.current

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

            // Download button
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { /* TODO: download */ }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Download, "Baixar", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Baixar faixa em alta qualidade no dispositivo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Footer
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Vtzin x", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("@2026 VzTune v1.0.0", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
            }

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