package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Moving light band drawn over the element (the "shimmer" of loading skeletons). */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    drawWithContent {
        drawContent()
        val w = size.width.coerceAtLeast(1f)
        // Progress is read inside the draw phase, so no recomposition per frame.
        val startX = -w + progress.value * 2f * w
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                startX = startX,
                endX = startX + w,
            )
        )
    }
}

/** Fades the content out towards the bottom edge, so skeletons "hide" at the end of the screen. */
fun Modifier.fadeOutBottom(fadeFraction: Float = 0.5f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    (1f - fadeFraction) to Color.Black,
                    1f to Color.Transparent,
                )
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .shimmer()
    )
}

/** Placeholder for a song / playlist / album row (square thumbnail + 2 text lines). */
@Composable
fun TrackRowSkeleton(horizontalPadding: Dp = 16.dp, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(56.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(11.dp))
        }
    }
}

/** Placeholder for a 16:9 video card (thumbnail + title + channel + more button area). */
@Composable
fun VideoCardSkeleton(horizontalPadding: Dp = 12.dp, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    ) {
        SkeletonBlock(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(11.dp))
            }
            Spacer(Modifier.width(8.dp))
            SkeletonBlock(Modifier.size(24.dp), RoundedCornerShape(12.dp))
        }
    }
}

/** A stack of skeleton rows or video cards. Apply [fadeOutBottom] on the parent to hide the end. */
@Composable
fun SkeletonColumn(
    count: Int,
    video: Boolean,
    horizontalPadding: Dp = if (video) 12.dp else 16.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (video) 12.dp else 0.dp)) {
        repeat(count) {
            if (video) VideoCardSkeleton(horizontalPadding) else TrackRowSkeleton(horizontalPadding)
        }
    }
}
