package com.vztechcore.vztune.ui.navigation

import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Home
    @Serializable data object Search
    @Serializable data object Library
    @Serializable data object Settings
}