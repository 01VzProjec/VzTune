package com.vztechcore.vztune.ui.screens.search

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track

data class SearchUiState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val videos: List<Track> = emptyList(),
) {
    val hasResults: Boolean
        get() = tracks.isNotEmpty() ||
            artists.isNotEmpty() ||
            albums.isNotEmpty() ||
            playlists.isNotEmpty() ||
            videos.isNotEmpty()

    val isIdle: Boolean
        get() = query.isBlank() && !isLoading && errorMessage == null

    val isEmptyResult: Boolean
        get() = !isLoading && errorMessage == null && !hasResults && query.isNotBlank()
}