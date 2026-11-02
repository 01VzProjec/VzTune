package com.vztechcore.vztune.core.domain.music

data class Playlist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int? = null,
    val source: Source,
    /**
     * Up to 4 thumbnails of the first tracks in the playlist.
     * Used by the UI to render a 2x2 mosaic when the playlist has no
     * custom cover (auto-generated playlists, radio, etc.).
     */
    val mosaicThumbnails: List<String> = emptyList(),
)