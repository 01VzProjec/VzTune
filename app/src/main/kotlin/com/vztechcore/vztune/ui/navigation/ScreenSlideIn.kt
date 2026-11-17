package com.vztechcore.vztune.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Animação de entrada da própria tela — suave e rápida.
 *
 * A tela desliza da direita (detalhe ou aba à direita) ou da esquerda
 * (aba à esquerda) + fade. Roda só na primeira composição da tela.
 *
 * Ao voltar (pop), a tela que volta já estava composta, então não anima
 * de novo — a tela que sai usa a popExit do NavHost (sai p/ direita).
 *
 * Por que na tela e não só no NavHost? Telas independentes (Search,
 * playlist, álbum, artista, Library...) carregam dado assíncrono do
 * ViewModel, então o slide do NavHost rolava antes do conteúdo existir
 * e parecia que não animava. Animando na tela, o slide acontece junto
 * com o conteúdo.
 */
@Composable
fun ScreenSlideIn(
    fromLeft: Boolean = false,
    content: @Composable () -> Unit,
) {
    // -1f = vem da esquerda (fora da tela), 1f = vem da direita.
    val offset = remember { Animatable(if (fromLeft) -1f else 1f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { offset.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(200)) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset.value * size.width
                this.alpha = alpha.value
            },
    ) {
        content()
    }
}
