package com.vztechcore.vztune.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.settings.AppSettings
import com.vztechcore.vztune.data.prefs.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level ViewModel. Exposes settings to MainActivity so the theme
 * can react to changes without leaking the store into composables.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    appSettingsStore: AppSettingsStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = appSettingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )
}