package com.vztechcore.vztune.ui.components.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import kotlin.math.abs

object PlaylistCoverPalette {

    private val colorMap: LinkedHashMap<String, Color> = linkedMapOf(
        "BLUE" to Color(0xFF1D4ED8),
        "BLACK" to Color(0xFF0B0D12),
        "WHITE" to Color(0xFFF8FAFC),
        "RED" to Color(0xFFDC2626),
        "GREEN" to Color(0xFF16A34A),
        "PURPLE" to Color(0xFF7C3AED),
        "ORANGE" to Color(0xFFEA580C),
        "PINK" to Color(0xFFDB2777),
        "CYAN" to Color(0xFF0891B2),
        "INDIGO" to Color(0xFF4338CA),
        "AMBER" to Color(0xFFD97706),
        "TEAL" to Color(0xFF0D9488),
        "YELLOW" to Color(0xFFEAB308),
        "LIME" to Color(0xFF84CC16),
        "ROSE" to Color(0xFFF43F5E),
        "BROWN" to Color(0xFF92400E),
        "GRAY" to Color(0xFF64748B),
        "VIOLET" to Color(0xFF8B5CF6)
    )

    private val colorEntries: List<Map.Entry<String, Color>> = colorMap.entries.toList()

    fun isDefaultColorToken(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)
    }

    fun randomToken(): String {
        val id = colorEntries.random().key
        return "${LocalPlaylistsStore.DEFAULT_COLOR_PREFIX}$id"
    }

    fun tokenFor(id: String): String {
        return "${LocalPlaylistsStore.DEFAULT_COLOR_PREFIX}${id.uppercase()}"
    }

    fun allColors(): List<Pair<String, Color>> {
        return colorEntries.map { it.key to it.value }
    }

    fun colorForId(id: String): Color? {
        return colorMap[id.uppercase()]
    }

    fun resolveBackgroundColor(coverPath: String?, seed: String): Color {
        if (!coverPath.isNullOrBlank() && coverPath.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)) {
            val tokenId = coverPath.removePrefix(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX).uppercase()
            colorMap[tokenId]?.let { return it }
        }
        if (seed.isBlank()) {
            return colorEntries.random().value
        }
        val index = abs(seed.hashCode()) % colorEntries.size
        return colorEntries[index].value
    }
}

@Composable
fun DefaultPlaylistCover(
    modifier: Modifier = Modifier,
    seed: String = "",
    coverPath: String? = null
) {
    val bgColor = remember(coverPath, seed) {
        PlaylistCoverPalette.resolveBackgroundColor(coverPath, seed)
    }
    val logoColor = remember(bgColor) {
        if (bgColor.luminance() > 0.55f) Color(0xFF0B0D12) else Color.White
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.46f)) {
            val w = size.width
            val h = size.height
            val strokeWidth = (w * 0.13f).coerceAtLeast(2.5f)
            val stroke = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            val vPath = Path().apply {
                moveTo(w * 0.08f, h * 0.24f)
                lineTo(w * 0.30f, h * 0.76f)
                lineTo(w * 0.52f, h * 0.24f)
            }
            drawPath(path = vPath, color = logoColor, style = stroke)

            val zPath = Path().apply {
                moveTo(w * 0.58f, h * 0.40f)
                lineTo(w * 0.92f, h * 0.40f)
                lineTo(w * 0.58f, h * 0.76f)
                lineTo(w * 0.92f, h * 0.76f)
            }
            drawPath(path = zPath, color = logoColor, style = stroke)

            drawCircle(
                color = logoColor.copy(alpha = 0.85f),
                radius = strokeWidth * 0.45f,
                center = Offset(w * 0.75f, h * 0.22f)
            )
        }
    }
}

@Composable
fun VzCoverColorPicker(
    selectedToken: String?,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PlaylistCoverPalette.allColors()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.chunked(6).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (id, color) ->
                    val token = PlaylistCoverPalette.tokenFor(id)
                    val isSelected = selectedToken == token
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(token) }
                    )
                }
            }
        }
    }
}
