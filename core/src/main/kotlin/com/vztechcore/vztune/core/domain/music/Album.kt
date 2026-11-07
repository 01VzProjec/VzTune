package com.vztechcore.vztune.core.domain.music

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val views: String? = null,
    val thumbnailUrl: String? = null,
    val source: Source,
)