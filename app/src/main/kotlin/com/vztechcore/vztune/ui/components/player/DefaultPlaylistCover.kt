package com.vztechcore.vztune.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.ui.components.feedback.VzLogoStatic
import kotlin.math.abs

/**
 * Palette of randomized background colors used when a local playlist is created
 * without a custom cover image. O sistema escolhe sozinho - o usuario NAO escolhe a cor.
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
    )

    private val colorEntries: List<Map.Entry<String, Color>> = colorMap.entries.toList()

    // Cores escuras onde o logo oficial (V azul + Z branco) fica legivel.
    // WHITE e AMBER ficam fora do sorteio - senao o Z branco some no fundo claro.
    // Elas continuam aqui pra resolver tokens antigos que ja existem.
    private val randomPool: List<Map.Entry<String, Color>> = colorEntries.filter {
        it.key !in setOf("WHITE", "AMBER")
    }

    fun isDefaultColorToken(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)
    }

    fun randomToken(): String {
        val id = randomPool.random().key
        return "${LocalPlaylistsStore.DEFAULT_COLOR_PREFIX}$id"
    }

    fun resolveBackgroundColor(coverPath: String?, seed: String): Color {
        if (!coverPath.isNullOrBlank() && coverPath.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)) {
            val tokenId = coverPath.removePrefix(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX).uppercase()
            colorMap[tokenId]?.let { return it }
        }
        if (seed.isBlank()) {
            return randomPool.random().value
        }
        val index = abs(seed.hashCode()) % randomPool.size
        return randomPool[index].value
    }
}

/**
 * Capa de playlist sem thumbnail: fundo com cor aleatoria (o sistema escolhe)
 * + logo OFICIAL da VzTechCore (VzLogoStatic de VzLoading: V azul gradiente + Z branco).
 * Sem monograma inventado, sem pontinho em cima do z, sem z menor.
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
    // Em fundo claro antigo (WHITE), o Z branco sumiria - por isso um circulo preto atras do logo.
    val isLightBg = remember(bgColor) { bgColor.luminance() > 0.55f }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        if (isLightBg) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.72f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                VzLogoStatic(Modifier.fillMaxSize())
            }
        } else {
            VzLogoStatic(Modifier.fillMaxSize(0.62f))
        }
    }
}
