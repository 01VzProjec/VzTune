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
 * Transicoes de navegacao do VzTune - suaves e rapidas.
 *
 * - Detalhe (playlist, album, artista, ver tudo, downloads, info da faixa):
 *   entra da direita, por cima da tela atual. Ao voltar, sai pra direita
 *   revelando a tela anterior.
 * - Abas (Home, Analytics, Library, Search): a direcao segue a ordem das abas.
 *   Indo p/ uma aba a direita, a nova entra da direita; indo p/ uma aba a
 *   esquerda, entra da esquerda. Voltar inverte.
 * - Detalhe -> aba (tocar na barra de baixo dentro de um detalhe): o detalhe
 *   sai pra direita revelando a aba, como se fosse um voltar.
 */

// Suave mas rapida: slide em ~300ms + fade em ~200ms.
private const val SLIDE_IN_MS = 300
private const val SLIDE_OUT_MS = 280
private const val FADE_MS = 200

/** Indice da aba na barra de baixo, da esquerda p/ direita. */
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

/** Entrada ao navegar p/ frente. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzEnterTransition(): EnterTransition {
    val fromTab = initialState.destination.tabIndex()
    val toTab = targetState.destination.tabIndex()
    return when {
        // Troca de aba: direcao segue a ordem das abas.
        fromTab != null && toTab != null ->
            if (toTab > fromTab) slideInFromRight() else slideInFromLeft()
        // Detalhe -> aba: a aba fica parada e e revelada (o detalhe sai na exit).
        toTab != null -> EnterTransition.None
        // Detalhe (ou aba -> detalhe): entra da direita por cima.
        else -> slideInFromRight()
    }
}

/** Saida ao navegar p/ frente. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzExitTransition(): ExitTransition {
    val fromTab = initialState.destination.tabIndex()
    val toTab = targetState.destination.tabIndex()
    return when {
        // Troca de aba: a aba atual sai p/ o lado oposto da que entra.
        fromTab != null && toTab != null ->
            if (toTab > fromTab) slideOutToLeft() else slideOutToRight()
        // Detalhe -> aba: o detalhe sai p/ direita revelando a aba.
        toTab != null -> slideOutToRight()
        // Detalhe: a tela atual fica parada embaixo da nova (efeito "por cima").
        else -> ExitTransition.None
    }
}

/** Entrada ao voltar (pop). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzPopEnterTransition(): EnterTransition {
    val fromTab = initialState.destination.tabIndex() // tela que esta saindo
    val toTab = targetState.destination.tabIndex() // tela p/ onde volta
    return when {
        // Volta entre abas: inverte a direcao da ida.
        fromTab != null && toTab != null ->
            if (toTab < fromTab) slideInFromLeft() else slideInFromRight()
        // Detalhe: a tela anterior fica parada e e revelada.
        else -> EnterTransition.None
    }
}

/** Saida ao voltar (pop). */
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
