package com.vztechcore.vztune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the most vibrant color from an image URL.
 * Returns Color.Black if the image is missing or extraction fails.
 */
@Composable
fun rememberDominantColor(url: String?): Color {
    var color by remember { mutableStateOf(Color.Black) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            color = Color.Black
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = drawable?.toBitmap()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    Color(
                        palette.getVibrantColor(
                            palette.getDominantColor(android.graphics.Color.BLACK)
                        )
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        if (extracted != null) color = extracted
    }
    return color
}

/**
 * App-wide background gradient: the dominant color of the current image
 * (top) fading into black (bottom).
 */
@Composable
fun EclipseBackground(
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val animated by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 900),
        label = "eclipse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to animated.copy(alpha = 0.55f),
                            0.45f to animated.copy(alpha = 0.16f),
                            1.0f to Color.Black,
                        )
                    )
                )
        )
        content()
    }
}