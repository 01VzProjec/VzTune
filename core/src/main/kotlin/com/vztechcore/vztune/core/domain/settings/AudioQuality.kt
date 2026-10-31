package com.vztechcore.vztune.core.domain.settings

/**
 * Preferred audio quality for streaming and downloads.
 * Maps internally to bitrate/format decisions in the player layer.
 */
enum class AudioQuality {
    LOW,
    MEDIUM,
    HIGH,
}