package com.vztechcore.vztune.core.domain.music

data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val source: Source,
)