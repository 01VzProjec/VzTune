package com.vztechcore.vztune.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.theme.VzSecondary

enum class ThumbnailShape { SQUARE, CIRCLE, WIDE }

@Composable
fun MediaThumbnail(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: ThumbnailShape = ThumbnailShape.SQUARE,
    isPlaying: Boolean = false,
    isPaused: Boolean = false,
    isLoading: Boolean = false,
    size: Dp = 56.dp,
    cornerRadius: Dp = 8.dp,
) {
    val clipShape: Shape = when (shape) {
        ThumbnailShape.SQUARE -> RoundedCornerShape(cornerRadius)
        ThumbnailShape.CIRCLE -> CircleShape
        ThumbnailShape.WIDE -> RoundedCornerShape(cornerRadius + 4.dp)
    }

    val baseModifier = when (shape) {
        ThumbnailShape.WIDE -> modifier.fillMaxWidth().aspectRatio(16f / 9f)
        else -> modifier.size(size)
    }

    Box(
        modifier = baseModifier
            .clip(clipShape)
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        when {
            isPlaying -> PlayingOverlayLarge(clipShape)
            isPaused -> PlayingOverlayPaused(clipShape)
            isLoading -> Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlayingOverlayLarge(clipShape: Shape) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(clipShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.15f),
                        VzSecondary.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.6f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlayingEqualizer(
            color = Color.White,
            barCount = 3,
            maxHeight = 20.dp,
            barWidth = 3.dp,
            gap = 3.dp,
        )
    }
}

@Composable
internal fun PlayingOverlayPaused(clipShape: Shape) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(clipShape)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Icon(
            imageVector = Icons.Filled.Pause,
            contentDescription = "Pausado",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        ) {
            PlayingEqualizer(
                color = Color.White,
                barCount = 3,
                maxHeight = 8.dp,
                barWidth = 2.dp,
                gap = 2.dp,
            )
        }
    }
}

@Composable
fun PlayingEqualizer(
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    maxHeight: Dp = 20.dp,
    barWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
) {
    val anims = (0 until barCount).map { i ->
        val infinite = rememberInfiniteTransition(label = "eq$i")
        infinite.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 380 + i * 90,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        anims.forEach { anim ->
            val h by anim
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * h)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color),
            )
        }
    }
}