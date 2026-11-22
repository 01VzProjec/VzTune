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
    val debugLog: String? = null,
    val topSongs: List<Track> = emptyList(),
    val songsMore: BrowseRef? = null,
    val albums: List<Album> = emptyList(),
    val albumsMore: BrowseRef? = null,
    val singles: List<Album> = emptyList(),
    val singlesMore: BrowseRef? = null,
    val videos: List<Track> = emptyList(),
    // Canal do YouTube (FIX5): Shorts e playlists do canal, banner do header.
    val shorts: List<Track> = emptyList(),
    val channelPlaylists: List<Playlist> = emptyList(),
    val bannerUrl: String? = null,
    // FIX18 - Canal: tem mais videos no UU? + carregando Buscar mais?
    val channelVideosHasMore: Boolean = false,
    val channelPlaylistsHasMore: Boolean = false,
    val isLoadingMoreChannelPlaylists: Boolean = false,
    val isLoadingMoreChannelVideos: Boolean = false,
    val isYouTubeChannel: Boolean = false,
    val videosMore: BrowseRef? = null,
    val featured: List<Playlist> = emptyList(),
    val featuredMore: BrowseRef? = null,
    val relatedArtists: List<Artist> = emptyList(),
    val isFollowing: Boolean = false,
    // Canal: ainda tem video/playlist la no YouTube que nao foi carregado.
    val hasMoreVideos: Boolean = false,
    val hasMorePlaylists: Boolean = false,
    val isLoadingMoreVideos: Boolean = false,
    val isLoadingMorePlaylists: Boolean = false,
) {
    /** "1,13 mi de inscritos • 512.669.634 visualizações" (parts are skipped when unknown). */
    val statsLabel: String
        get() = listOfNotNull(subscribers, views).filter { it.isNotBlank() }.joinToString(" • ")
}

/** Video mais famoso do canal (maior viewCount). Null quando nao tem viewCount em nenhum video. */
val ArtistUiState.mostViewedVideo: com.vztechcore.vztune.core.domain.music.Track?
    get() = videos.maxByOrNull { it.viewCount ?: -1L }?.takeIf { (it.viewCount ?: 0L) > 0 }
        ?: videos.firstOrNull()
/** Videos do canal ordenados do mais visto para o menos visto. */
val ArtistUiState.videosByViews: List<com.vztechcore.vztune.core.domain.music.Track>
    get() = videos.sortedByDescending { it.viewCount ?: -1L }
