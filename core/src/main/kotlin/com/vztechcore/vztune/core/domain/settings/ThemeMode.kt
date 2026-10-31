package com.vztechcore.vztune.core.domain.settings

/**
 * Theme mode selected by the user.
 * SYSTEM follows the OS dark/light setting.
 * AMOLED forces pure black background (same colors as DARK but background is #000000).
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}