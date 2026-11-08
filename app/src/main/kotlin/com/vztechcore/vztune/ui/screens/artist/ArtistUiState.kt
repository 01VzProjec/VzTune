package com.vztechcore.vztune.ui.screens.artist

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track

data class ArtistUiState(
    val artistId: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val subscribers: String? = null,
    val views: String? = null,
    val description: String? = null,
    val topSongs: List<Track> = emptyList(),
    val songsMore: BrowseRef? = null,
    val albums: List<Album> = emptyList(),
    val albumsMore: BrowseRef? = null,
    val singles: List<Album> = emptyList(),
    val singlesMore: BrowseRef? = null,
    val videos: List<Track> = emptyList(),
    val videosMore: BrowseRef? = null,
    val featured: List<Playlist> = emptyList(),
    val featuredMore: BrowseRef? = null,
    val relatedArtists: List<Artist> = emptyList(),
    val isFollowing: Boolean = false,
) {
    /** "1,13 mi de inscritos • 512.669.634 visualizações" (parts are skipped when unknown). */
    val statsLabel: String
        get() = listOfNotNull(subscribers, views).filter { it.isNotBlank() }.joinToString(" • ")
}
