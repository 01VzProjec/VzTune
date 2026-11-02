package com.vztechcore.vztune.ui.screens.medialist

import com.vztechcore.vztune.core.domain.music.Track

data class MediaListUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val mosaicThumbnails: List<String> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val hasContent: Boolean get() = tracks.isNotEmpty()
}