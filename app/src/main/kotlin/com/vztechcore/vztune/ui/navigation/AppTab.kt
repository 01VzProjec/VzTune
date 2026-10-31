package com.vztechcore.vztune.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tabs shown on the floating bottom bar.
 * Order here determines the visual order on the bar.
 */
enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}