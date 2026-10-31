package com.vztechcore.vztune.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Each screen is a @Serializable object.
 * Routes that need arguments become @Serializable data class.
 */
object Routes {
    @Serializable
    data object Search

    @Serializable
    data object Library

    @Serializable
    data object Settings
}