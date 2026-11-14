package com.vztechcore.vztune.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.navigation.HomeFilled
import com.vztechcore.vztune.ui.icons.navigation.HomeOutline
import com.vztechcore.vztune.ui.icons.navigation.Insights
import com.vztechcore.vztune.ui.icons.navigation.LibraryMusicFilled
import com.vztechcore.vztune.ui.icons.navigation.LibraryMusicOutline
import com.vztechcore.vztune.ui.icons.navigation.Search

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("Home", VzIcons.HomeFilled, VzIcons.HomeOutline),
    ANALYTICS("Analytics", VzIcons.Insights, VzIcons.Insights),
    LIBRARY("Library", VzIcons.LibraryMusicFilled, VzIcons.LibraryMusicOutline),
    SEARCH("Search", VzIcons.Search, VzIcons.Search),
}