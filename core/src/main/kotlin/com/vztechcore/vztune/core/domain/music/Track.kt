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
    // Canal / video YouTube (NewPipe StreamInfoItem). Null quando nao vem do YouTube
    // ou quando o NewPipe nao expoe. Usado nos cards de canal e nos Shorts.
    val viewCount: Long? = null,
    // Texto ja formatado em pt-BR: hoje, ha 1 dia, ha 3 semanas, ha 2 meses, ha 1 ano.
    val publishedText: String? = null,
)
