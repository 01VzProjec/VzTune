package com.vztechcore.vztune.ui.screens.trackinfo

import com.vztechcore.vztune.data.prefs.models.LocalPlaylist

data class TrackInfoUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val durationSeconds: Long = 0L,
    val sourceUrl: String = "",
    val itag: String = "Carregando...",
    val mimeType: String = "Carregando...",
    val quality: String = "Carregando...",
    val viewCount: String? = null,
    val likeCount: String? = null,
    val description: String? = null,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPlaylists: List<LocalPlaylist> = emptyList(),
)