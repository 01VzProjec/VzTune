package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Album thumbnail that overlays a "now playing" dim + equalizer dot
 * when the given track is the one currently playing.
 * Old project bundled the "is playing" logic inline; now it's a param
 * so this component stays pure.
 */
@Composable
fun NowPlayingThumbnail(
    model: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerBars()
            }
        }
    }
}

@Composable
private fun EqualizerBars() {
    // Simplified animated bars. Real animation can be added later.
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(6.dp, 12.dp, 8.dp).forEach { h ->
            Box(
                Modifier
                    .size(width = 3.dp, height = h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        }
    }
}