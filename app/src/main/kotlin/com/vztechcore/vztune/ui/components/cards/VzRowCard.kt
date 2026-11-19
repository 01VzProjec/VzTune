package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * Card generico de linha: thumbnail + titulo + subtitulo + duracao + botao •••.
 * Era o RowCard privado do Search. Agora e compartilhado.
 *
 * TAMANHO = IGUAL ao VzTrackRow (card das playlists):
 * thumbnail 52dp, canto 6dp, padding 16dp esq / 8dp dir / 6dp cima-baixo,
 * botao ••• 40dp. Antes era 56dp + 8dp, por isso o Search parecia maior.
 * Altura total: 52 + 12 = 64dp, igual a playlist.
 */
@Composable
fun VzRowCard(
    imageUrl: String?,
    shape: ThumbnailShape,
    title: String,
    subtitle: @Composable () -> Unit,
    durationLabel: String?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMoreButton: Boolean,
    mosaic: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (mosaic.size >= 4) { Box(Modifier.size(52.dp)) { PlaylistMosaic(mosaic) } }
        else { MediaThumbnail(imageUrl = imageUrl, shape = shape, size = 52.dp, cornerRadius = 6.dp, isPlaying = isPlaying, isPaused = isPaused) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            subtitle()
        }
        if (durationLabel != null && showMoreButton) {
            Spacer(Modifier.width(8.dp))
            Text(text = durationLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
        if (showMoreButton) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
