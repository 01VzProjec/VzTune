package com.vztechcore.vztune.ui.screens.trackinfo

import com.vztechcore.vztune.data.prefs.LocalPlaylist

data class TrackInfoUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val durationSeconds: Long = 0L,
    val itag: String = "Carregando…",
    val mimeType: String = "Carregando…",
    val quality: String = "Carregando…",
    val viewCount: String? = null,
    val likeCount: String? = null,
    val description: String? = null,
    val sourceUrl: String = "",
    val isFavorite: Boolean = false,
    // Playlists the user created, offered by "Adicionar à playlist".
    val localPlaylists: List<LocalPlaylist> = emptyList(),
)
