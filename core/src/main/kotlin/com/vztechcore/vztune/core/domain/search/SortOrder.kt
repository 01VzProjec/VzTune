package com.vztechcore.vztune.core.domain.search

/**
 * Sort options for a media list (playlist or album).
 * Persisted per playlist/album id via MediaListPreferencesStore.
 */
enum class SortOrder(val label: String) {
    MANUAL("Manual"),
    RECENT_TO_OLD("Recente → Antigas"),
    OLD_TO_RECENT("Antigas → Recentes"),
    TITLE_ASC("Título A → Z"),
    TITLE_DESC("Título Z → A"),
    ARTIST_ASC("Artista A → Z"),
    ARTIST_DESC("Artista Z → A"),
    SHUFFLE("Sortear"),
}