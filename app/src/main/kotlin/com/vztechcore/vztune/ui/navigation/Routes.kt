package com.vztechcore.vztune.ui.navigation

import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Home
    @Serializable data object Analytics
    @Serializable data object Library
    @Serializable data object Search
    @Serializable data object Downloads

    @Serializable data class MediaList(
        val id: String,
        val isAlbum: Boolean,
        val title: String,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
        val sourceName: String? = null, // FIX20 - YOUTUBE ou YOUTUBE_MUSIC. Sem isso a playlist perdia o mundo.