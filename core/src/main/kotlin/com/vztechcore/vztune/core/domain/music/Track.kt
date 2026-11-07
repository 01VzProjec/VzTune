package com.vztechcore.vztune.core.domain.music

/**
 * A playable audio item. Can originate from YouTube (video) or YouTube Music (song).
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val source: Source,
    val streamUrl: String? = null,
    // True when the source flags the track as containing explicit lyrics.
    val isExplicit: Boolean = false,
)
