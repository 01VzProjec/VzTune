package com.vztechcore.vztune.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.SkipNext
import com.vztechcore.vztune.ui.icons.player.SkipPrevious

private val GlassFill = Color.Black.copy(alpha = 0.75f)
private val GlassBorder = Color.White.copy(alpha = 0.15f)

@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    progress: Float = 0f,
    dominantColor: Color,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val isVideo = track.source == Source.YOUTUBE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 4.dp)
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            if (isVideo) {
                SquareThumbWithProgress(
                    imageUrl = track.thumbnailUrl,
                    progress = progress,
                    dominantColor = dominantColor,
                    isLoading = isLoading
                )
            } else {
                DiscThumbWithProgress(
                    imageUrl = track.thumbnailUrl,
                    progress = progress,
                    dominantColor = dominantColor,
                    isPlaying = isPlaying,
                    isLoading = isLoading
                )
            }
        }

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
            TrackSubtitle(
                track = track,
                style = MaterialTheme.typography.bodySmall,
                prefixColor = Color.White.copy(alpha = 0.55f),
                artistColor = Color.White.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onPrevClick, modifier = Modifier.size(34.dp)) {
            Icon(VzIcons.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(38.dp)) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(if (isPlaying) VzIcons.Pause else VzIcons.Play, if (isPlaying) "Pausar" else "Tocar", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        IconButton(onClick = onNextClick, modifier = Modifier.size(34.dp)) {
            Icon(VzIcons.SkipNext, "Próxima", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SquareThumbWithProgress(
    imageUrl: String?,
    progress: Float,
    dominantColor: Color,
    isLoading: Boolean
) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val inset = 1.dp.toPx()
            val cornerRadius = 8.dp.toPx()
            val rect = Rect(inset, inset, size.width - inset, size.height - inset)
            val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(cornerRadius))) }

            drawPath(path, color = Color.White.copy(alpha = 0.15f), style = stroke)

            if (progress > 0f) {
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * progress, partial, true)
                drawPath(partial, color = dominantColor, style = stroke)
            }
        }

        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DiscThumbWithProgress(
    imageUrl: String?,
    progress: Float,
    dominantColor: Color,
    isPlaying: Boolean,
    isLoading: Boolean
) {
    val discRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, isLoading) {
        if (isPlaying || isLoading) {
            while (true) {
                discRotation.animateTo(
                    targetValue = discRotation.value + 360f,
                    animationSpec = tween(durationMillis = 12000, easing = LinearEasing)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val inset = 1.dp.toPx()

            drawCircle(color = Color.White.copy(alpha = 0.15f), radius = size.minDimension / 2 - inset, style = stroke)

            if (progress > 0f) {
                drawArc(
                    color = dominantColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = stroke
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(3.dp)
                .graphicsLayer { rotationZ = discRotation.value }
                .clip(CircleShape)
                .background(Color(0xFF0F1E3D))
        ) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(Color.Black))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}