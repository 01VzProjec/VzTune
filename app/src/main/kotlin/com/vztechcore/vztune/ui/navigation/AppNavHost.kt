package com.vztechcore.vztune.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Search,
        modifier = modifier,
    ) {
        composable<Routes.Search> {
            val viewModel: SearchViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SearchScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onTabSelect = viewModel::onTabSelect,
                onSubmit = viewModel::onSubmit,
            )
        }
    }
}