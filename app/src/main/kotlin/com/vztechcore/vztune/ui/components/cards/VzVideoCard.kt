package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.PlayArrow

/**
 * UNICO card de video 16:9 do app.
 * A linha de meta fica DENTRO da coluna do titulo, entao acompanha o titulo:
 * titulo de 1 linha -> meta na linha 2; titulo de 2 linhas -> meta na linha 3.
 * [showChannelRow]=true  -> [foto][nome][play_arrow views data]
 * [showChannelRow]=false -> [nome][play_arrow views data]  ("Para voce")
 */
@Composable
fun VzVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    showChannelRow: Boolean = true,
) {
    val viewsDate = videoViewsDate(track)
    Column(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 10.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
            DurationBubble(
                seconds = track.durationSeconds,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showChannelRow) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!track.channelAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.channelAvatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = VzIcons.Artist,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!viewsDate.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = VzIcons.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = viewsDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = VzIcons.MoreVert,
                    contentDescription = "Mais",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
