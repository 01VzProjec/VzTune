package com.vztechcore.vztune.core.domain.music

data class Playlist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int? = null,
    val source: Source,
)