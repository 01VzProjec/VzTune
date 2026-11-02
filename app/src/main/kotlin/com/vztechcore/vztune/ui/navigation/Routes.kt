package com.vztechcore.vztune.ui.navigation

import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Home
    @Serializable data object Analytics
    @Serializable data object Library
    @Serializable data object Search

    @Serializable data class MediaList(
        val id: String,
        val isAlbum: Boolean,
        val title: String,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
    )

    @Serializable data class TrackInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val thumbnailUrl: String?,
        val sourceName: String, // "YOUTUBE" or "YOUTUBE_MUSIC"
        val durationSeconds: Long,
    )
}