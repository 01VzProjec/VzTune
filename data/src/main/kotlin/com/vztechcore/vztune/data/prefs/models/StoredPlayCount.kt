package com.vztechcore.vztune.data.prefs.models

import kotlinx.serialization.Serializable

@Serializable
data class StoredPlayCount(
    val track: StoredTrack,
    val count: Int = 1,
)