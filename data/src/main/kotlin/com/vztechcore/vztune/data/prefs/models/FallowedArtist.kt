package com.vztechcore.vztune.data.prefs.models

import kotlinx.serialization.Serializable

@Serializable
data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    // Canal do YouTube seguido abre como canal, nao como artista do YT Music.
    // Default false = compat com os salvos antigos (sem esse campo).
    val isYouTubeChannel: Boolean = false,
)