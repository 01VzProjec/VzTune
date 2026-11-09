package com.vztechcore.vztune.data.prefs.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val coverPath: String? = null,
    val tracks: List<StoredTrack> = emptyList(),
) {
    val mosaic: List<String>
        get() = tracks.mapNotNull { it.thumbnailUrl }.distinct().take(4)
}