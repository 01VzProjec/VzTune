package com.vztechcore.vztune.core.domain.music

/**
 * Where a piece of music content comes from.
 * YOUTUBE = regular YouTube (via NewPipeExtractor)
 * YOUTUBE_MUSIC = YouTube Music (via InnerTube)
 */
enum class Source {
    YOUTUBE,
    YOUTUBE_MUSIC,
}