package com.vztechcore.vztune.core.domain.search

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track

/**
 * A single item rendered in the search results list.
 * Sealed to allow mixed-type lists while keeping type safety in `when`.
 */
sealed interface SearchHit {
    val stableKey: String

    data class SongHit(val track: Track) : SearchHit {
        override val stableKey: String get() = "song_${track.source.name}_${track.id}"
    }

    data class VideoHit(val track: Track) : SearchHit {
        override val stableKey: String get() = "video_${track.id}"
    }

    data class PlaylistHit(val playlist: Playlist) : SearchHit {
        override val stableKey: String get() = "playlist_${playlist.source.name}_${playlist.id}"
    }

    data class ArtistHit(val artist: Artist) : SearchHit {
        override val stableKey: String get() = "artist_${artist.source.name}_${artist.id}"
    }

    data class AlbumHit(val album: Album) : SearchHit {
        override val stableKey: String get() = "album_${album.source.name}_${album.id}"
    }
}