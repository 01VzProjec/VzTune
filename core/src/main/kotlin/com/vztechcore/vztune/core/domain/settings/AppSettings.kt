package com.vztechcore.vztune.core.domain.settings

/**
 * Global app settings persisted via AppSettingsStore.
 * Pure domain model — no Android or DataStore types leak here.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val lastSearchTab: String = "ALL",
)