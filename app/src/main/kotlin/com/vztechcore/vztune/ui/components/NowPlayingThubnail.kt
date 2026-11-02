package com.vztechcore.vztune.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Legacy wrapper kept for backward compatibility with older call sites.
 *
 * Now delegates to [MediaThumbnail], which already handles:
 *   - square / circle / wide shapes
 *   - playing equalizer overlay
 *   - paused state (pause icon + small equalizer)
 *   - loading spinner
 *
 * New code should call [MediaThumbnail] directly.
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
    MediaThumbnail(
        imageUrl = model as? String,
        modifier = modifier,
        shape = ThumbnailShape.SQUARE,
        isPlaying = isPlaying,
        isPaused = false,
        isLoading = false,
        cornerRadius = cornerRadius,
    )
}