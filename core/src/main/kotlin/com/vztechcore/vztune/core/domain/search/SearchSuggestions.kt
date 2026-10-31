package com.vztechcore.vztune.core.domain.search

import com.vztechcore.vztune.core.domain.music.Track

/**
 * Suggestions shown while the user is typing (before submitting).
 * Songs come from YT Music quick results; queries are text completions.
 */
data class SearchSuggestions(
    val songs: List<Track> = emptyList(),
    val queries: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && queries.isEmpty()
}