package com.vztechcore.vztune.data.prefs.models

import kotlinx.serialization.Serializable

@Serializable
data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)