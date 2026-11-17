package com.vztechcore.vztune.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * Transições de VOLTAR (pop) do VzTune — suaves e rápidas.
 * A entrada p/ frente é feita pela própria tela (ScreenSlideIn).
 *
 * - Detalhe (playlist, álbum, artista, ver tudo, downloads, info da faixa):
 *   ao voltar, sai pra direita revelando a tela anterior.
 * - Abas (Home, Analytics, Library, Search): a direção segue a ordem das abas.
 *   Voltando p/ uma aba à esquerda, a tela atual sai p/ direita; voltando
 *   p/ uma aba à direita, sai p/ esquerda.
 */

private const val SLIDE_IN_MS = 300
private const val SLIDE_OUT_MS = 280
private const val FADE_MS = 200

/** Índice da aba na barra de baixo, da esquerda p/ direita. */
private fun NavDestination.tabIndex(): Int? = when {
    hasRoute<Routes.Home>() -> 0
    hasRoute<Routes.Analytics>() -> 1
    hasRoute<Routes.Library>() -> 2
    hasRoute<Routes.Search>() -> 3
    else -> null
}

private fun slideInFromRight(): EnterTransition =
    slideInHorizontally(tween(SLIDE_IN_MS, easing = FastOutSlowInEasing)) { it } +
        fadeIn(tween(FADE_MS))

private fun slideInFromLeft(): EnterTransition =
    slideInHorizontally(tween(SLIDE_IN_MS, easing = FastOutSlowInEasing)) { -it } +
        fadeIn(tween(FADE_MS))

private fun slideOutToLeft(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_OUT_MS, easing = FastOutSlowInEasing)) { -it } +
        fadeOut(tween(FADE_MS))

private fun slideOutToRight(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_OUT_MS, easing = FastOutSlowInEasing)) { it } +
        fadeOut(tween(FADE_MS))

/** Entrada ao navegar p/ frente — não usada (a tela anima via ScreenSlideIn). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzEnterTransition(): EnterTransition {
    return EnterTransition.None
}

/** Saída ao navegar p/ frente — não usada (a tela que entra cobre a atual). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzExitTransition(): ExitTransition {
    return ExitTransition.None
}

/**
 * Saída ao navegar p/ frente — SEGURA a tela atual parada enquanto a nova
 * desliza por cima dela (efeito "passando por cima").
 *
 * Antes era ExitTransition.None: a tela atual sumia no 1º frame e a nova
 * deslizava por cima de um fundo preto — esse flash preto é o que parecia
 * "delay pra começar". Com o hold, a transição é contínua e parece imediata:
 * clicou, a nova já desliza por cima da atual.
 *
 * Dura o mesmo que o slide da ScreenSlideIn (300 ms). Depois disso o NavHost
 * remove a tela antiga.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzHoldTransition(): ExitTransition {
    // slideOut de 0 = não sai do lugar; fadeOut lento só p/ suavizar a remoção.
    return slideOutHorizontally(tween(SLIDE_OUT_MS + 20, easing = FastOutSlowInEasing)) { 0 } +
        fadeOut(tween(SLIDE_OUT_MS + 20))
}

/** Entrada ao voltar (pop). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzPopEnterTransition(): EnterTransition {
    val fromTab = initialState.destination.tabIndex() // tela que está saindo
    val toTab = targetState.destination.tabIndex() // tela p/ onde volta
    return when {
        // Volta entre abas: inverte a direção da ida.
        fromTab != null && toTab != null ->
            if (toTab < fromTab) slideInFromLeft() else slideInFromRight()
        // Detalhe: a tela anterior fica parada e é revelada.
        else -> EnterTransition.None
    }
}

/** Saída ao voltar (pop). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzPopExitTransition(): ExitTransition {
    val fromTab = initialState.destination.tabIndex()
    val toTab = targetState.destination.tabIndex()
    return when {
        // Volta entre abas: a aba atual sai p/ o lado oposto da que entra.
        fromTab != null && toTab != null ->
            if (toTab < fromTab) slideOutToRight() else slideOutToLeft()
        // Detalhe: sai p/ direita revelando a tela anterior.
        else -> slideOutToRight()
    }
}
