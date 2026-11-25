package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close

/**
 * Viewer em tela cheia da foto de perfil / banner do canal (Parte 3).
 * - A imagem "viaja" pro centro com animacao de escala + fade.
 * - O fundo escurece (e o conteudo atras fica borrado via Modifier.blur na tela).
 * - Pinch zoom (1x a 5x) + arrastar quando zoomada; duplo toque reseta.
 * - Toque fora / no X fecha.
 */
@Composable
fun ImageViewerOverlay(url: String?, onDismiss: () -> Unit) {
    if (url.isNullOrBlank()) return
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(url) {
        scale = 1f
        offset = Offset.Zero
        appear.snapTo(0f)
        appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f * appear.value))
            .clickable(enabled = scale <= 1.01f) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val s = (0.6f + 0.4f * appear.value) * scale
                    scaleX = s
                    scaleY = s
                    translationX = offset.x
                    translationY = offset.y
                    alpha = appear.value
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.01f) onDismiss() },
                        onDoubleTap = { scale = 1f; offset = Offset.Zero },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var prevDist = -1f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val dist = (pressed[0].position - pressed[1].position).getDistance()
                                if (prevDist > 0f && dist > 0f) {
                                    scale = (scale * (dist / prevDist)).coerceIn(1f, 5f)
                                    if (scale <= 1.01f) offset = Offset.Zero
                                    event.changes.forEach { it.consume() }
                                }
                                prevDist = dist
                            } else {
                                prevDist = -1f
                            }
                        }
                    }
                }
                .pointerInput(scale > 1.01f) {
                    if (scale > 1.01f) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offset += drag
                        }
                    }
                },
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        ) {
            Icon(imageVector = VzIcons.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
