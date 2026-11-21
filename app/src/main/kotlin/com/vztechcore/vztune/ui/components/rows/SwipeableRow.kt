package com.vztechcore.vztune.ui.components.rows

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.PlayNext

/**
 * Single source of truth para "arrastar pra tocar em seguida".
 * Antes copiado, linha por linha igual, em Search e MediaList.
 * Agora os dois usam este. Mexeu aqui, mudou nas duas telas.
 */
@Composable
fun SwipeableRow(
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (dragOffset > 0f) {
            val iconAlpha = (dragOffset / 100f).coerceIn(0.4f, 1f)
            Box(
                Modifier.fillMaxHeight().width(dragOffset.dp.coerceAtMost(80.dp)).padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(imageVector = VzIcons.PlayNext, contentDescription = "Tocar em seguida", tint = Color.White.copy(alpha = iconAlpha), modifier = Modifier.size(24.dp))
            }
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer { translationX = dragOffset }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragOffset > 30f) onPlayNext(); dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                ) { change, dragAmount ->
                    if (dragAmount > 0 || dragOffset > 0) { dragOffset = (dragOffset + dragAmount).coerceIn(0f, 300f); change.consume() }
                }
            },
        ) { content() }
    }
}
