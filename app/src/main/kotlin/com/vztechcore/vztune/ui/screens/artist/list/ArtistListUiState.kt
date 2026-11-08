package com.vztechcore.vztune.ui.screens.artist.list

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track

data class ArtistListUiState(
    val kind: ArtistListKind,
    val artistName: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    // Token of the next page; null means everything was loaded.
    val continuation: String? = null,
    val errorMessage: String? = null,
) {
    /** Screen title in Portuguese, by kind. */
    val title: String
        get() = when (kind) {
            ArtistListKind.SONGS -> "Músicas"
            ArtistListKind.VIDEOS -> "Vídeos"
            ArtistListKind.ALBUMS -> "Álbuns"
            ArtistListKind.SINGLES -> "Singles"
            ArtistListKind.FEATURED -> "Destacado"
        }

    val itemCount: Int
        get() = tracks.size + albums.size + playlists.size
}
