package com.vztechcore.vztune.core.domain.search

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track

/**
 * Result of a search operation. Always returned, never thrown.
 * Repositories convert exceptions into SearchResult.Failure.
 */
sealed interface SearchResult {
    data class Success(
        val tracks: List<Track> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val albums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val videos: List<Track> = emptyList(),
    ) : SearchResult

    data class Failure(val message: String) : SearchResult
}