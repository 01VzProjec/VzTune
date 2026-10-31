package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Legacy thumbnail with an "is playing" overlay.
 *
 * @Deprecated Use [MediaThumbnail] directly — it has the same behavior
 * with more shape/size options. Kept here so existing callers in
 * SearchAutocomplete.kt don't need to change on the same commit.
 *
 * The caller is expected to size this via `Modifier.size(...)` (like the old
 * version did). Do NOT pass a `.size()` AND rely on the default — pick one.
 */
@Deprecated(
    message = "Use MediaThumbnail instead",
    replaceWith = ReplaceWith(
        "MediaThumbnail(imageUrl = model as? String, " +
            "shape = ThumbnailShape.SQUARE, isPlaying = isPlaying, " +
            "size = <your-size>, cornerRadius = cornerRadius)",
        "com.vztechcore.vztune.ui.components.MediaThumbnail",
        "com.vztechcore.vztune.ui.components.ThumbnailShape",
    ),
)
@Composable
fun NowPlayingThumbnail(
    model: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
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
            PlayingOverlay(clipShape = shape)
        }
    }
}