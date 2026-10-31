package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.ui.components.AppBottomBar
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentTab = when {
        currentRoute?.startsWith("Search") == true -> AppTab.SEARCH
        currentRoute?.startsWith("Library") == true -> AppTab.LIBRARY
        currentRoute?.startsWith("Settings") == true -> AppTab.SETTINGS
        else -> AppTab.HOME
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
        ) {
            composable<Routes.Home> {
                PlaceholderScreen("Home — coming soon")
            }

            composable<Routes.Search> {
                val vm: SearchViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                SearchScreen(
                    state = state,
                    onQueryChange = vm::onQueryChange,
                    onTabSelect = vm::onTabSelect,
                    onSubmit = vm::onSubmit,
                )
            }

            composable<Routes.Library> {
                PlaceholderScreen("Library — coming soon")
            }

            composable<Routes.Settings> {
                PlaceholderScreen("Settings — coming soon")
            }
        }

        AppBottomBar(
            selected = currentTab,
            onSelect = { tab ->
                val route = when (tab) {
                    AppTab.HOME -> Routes.Home
                    AppTab.SEARCH -> Routes.Search
                    AppTab.LIBRARY -> Routes.Library
                    AppTab.SETTINGS -> Routes.Settings
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlaceholderScreen(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = VzOnSurface)
    }
}