package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import kotlinx.coroutines.delay

private val SubmenuSurfaceColor = Color(0xFF121829)
private val ActionBlueColor = Color(0xFF60A5FA)

/**
 * Mensagem flutuante exibida na tela sempre que uma faixa inicia o download.
 * Usa exatamente a mesma cor de fundo do [TrackOptionsSheet] (#121829),
 * thumbnail pequena à esquerda, texto "{nome da faixa} em download" e botão azul "Ver".
 */
@Composable
fun DownloadStartedBanner(
    track: Track?,
    onDismiss: () -> Unit,
    onViewClick: () -> Unit,
    modifier: Modifier = Modifier,
    autoHideMillis: Long = 4500L,
) {
    LaunchedEffect(track?.id, track?.source) {
        if (track != null) {
            delay(autoHideMillis)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        if (track != null) {
            val shape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(shape)
                    .background(SubmenuSurfaceColor)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .clickable {
                        onDismiss()
                        onViewClick()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaThumbnail(
                    imageUrl = track.thumbnailUrl,
                    shape = ThumbnailShape.SQUARE,
                    size = 38.dp,
                    cornerRadius = 8.dp,
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "${track.title} em download",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "Ver",
                    style = MaterialTheme.typography.labelLarge,
                    color = ActionBlueColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onDismiss()
                            onViewClick()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}