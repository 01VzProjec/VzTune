package com.vztechcore.vztune.core.domain.music

data class Playlist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val ownerAvatarUrl: String? = null,
    val views: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int? = null,
    val source: Source,
    // Up to 4 thumbnails from the first tracks, for the 2x2 mosaic.
    val mosaicThumbnails: List<String> = emptyList(),
)