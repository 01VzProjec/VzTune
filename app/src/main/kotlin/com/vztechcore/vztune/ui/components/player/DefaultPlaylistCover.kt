package com.vztechcore.vztune.ui.components.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

/**
 * Palette of background colors used when a local playlist is created
 * without a custom cover image.
 *
 * Each color is stored as a token like "vz_color:BLUE" in the playlist coverPath.
 * The same token is used in the Library card and inside the playlist screen,
 * so both places always show the exact same cover.
 */
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
        "YELLOW" to Color(0xFFFACC15),
        "LIME" to Color(0xFF84CC16),
        "ROSE" to Color(0xFFF43F5E),
        "BROWN" to Color(0xFF92400E),
        "GRAY" to Color(0xFF64748B),
        "VIOLET" to Color(0xFF8B5CF6),
    )

    /** All colors in display order for the color picker. */
    val allColors: List<Pair<String, Color>> = colorMap.entries.map { it.key to it.value }

    /** True when [value] is a Vz color token, for example "vz_color:RED". */
    fun isDefaultColorToken(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)
    }

    /** Builds a token for a color id, for example "RED" -> "vz_color:RED". */
    fun tokenFor(id: String): String {
        return "${LocalPlaylistsStore.DEFAULT_COLOR_PREFIX}$id"
    }

    /** Picks a random color token. Used when no color was chosen. */
    fun randomToken(): String {
        return tokenFor(colorMap.keys.random())
    }

    /**
     * Resolves the background color for a cover path.
     * If the path is a color token, its color is returned.
     * Otherwise a deterministic color is derived from [seed].
     */
    fun resolveBackgroundColor(coverPath: String?, seed: String): Color {
        if (!coverPath.isNullOrBlank() && coverPath.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)) {
            val tokenId = coverPath.removePrefix(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX).uppercase()
            colorMap[tokenId]?.let { return it }
        }
        if (seed.isBlank()) {
            return colorMap.values.random()
        }
        val index = abs(seed.hashCode()) % colorMap.size
        return colorMap.values.elementAt(index)
    }
}

/**
 * Temporary cover rendered for user playlists without a custom thumbnail.
 * It draws a solid background color plus the Vz monogram with automatic
 * light / dark contrast. The Vz logo is always visible.
 */
@Composable
fun DefaultPlaylistCover(
    modifier: Modifier = Modifier,
    seed: String = "",
    coverPath: String? = null,
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
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.46f)) {
            val w = size.width
            val h = size.height
            val strokeWidth = (w * 0.13f).coerceAtLeast(2.5f)
            val stroke = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            // "V" monogram stroke on the left
            val vPath = Path().apply {
                moveTo(w * 0.08f, h * 0.24f)
                lineTo(w * 0.30f, h * 0.76f)
                lineTo(w * 0.52f, h * 0.24f)
            }
            drawPath(path = vPath, color = logoColor, style = stroke)

            // "z" monogram stroke on the right
            val zPath = Path().apply {
                moveTo(w * 0.58f, h * 0.40f)
                lineTo(w * 0.92f, h * 0.40f)
                lineTo(w * 0.58f, h * 0.76f)
                lineTo(w * 0.92f, h * 0.76f)
            }
            drawPath(path = zPath, color = logoColor, style = stroke)

            // Subtle accent dot above "z"
            drawCircle(
                color = logoColor.copy(alpha = 0.85f),
                radius = strokeWidth * 0.45f,
                center = Offset(w * 0.75f, h * 0.22f),
            )
        }
    }
}

/**
 * Color picker used by the playlist editor.
 * It is reused in the create sheet and in the playlist options sheet,
 * so the same palette is available in both places.
 */
@Composable
fun VzCoverColorPicker(
    selectedToken: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, maxItemsInEachRow = 9) {
        PlaylistCoverPalette.allColors.forEach { (id, color) ->
            val token = PlaylistCoverPalette.tokenFor(id)
            val selected = token == selectedToken
            Box(
                Modifier
                    .padding(4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    )
                    .clickable { onSelect(token) }
            )
        }
    }
}
