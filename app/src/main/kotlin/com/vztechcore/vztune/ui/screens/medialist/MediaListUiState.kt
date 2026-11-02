package com.vztechcore.vztune.ui.screens.medialist

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track

enum class MediaListTab(val label: String) {
    ALL("Tudo"),
    SONGS("Músicas"),
    VIDEOS("Vídeos"),
}

data class MediaListUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // Header
    val title: String = "",
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val mosaicThumbnails: List<String> = emptyList(),
    val isAlbum: Boolean = false,

    // Content
    val allTracks: List<Track> = emptyList(),
    val description: String? = null,
    val selectedTab: MediaListTab = MediaListTab.ALL,
) {
    val filteredTracks: List<Track>
        get() = when (selectedTab) {
            MediaListTab.ALL -> allTracks
            MediaListTab.SONGS -> allTracks.filter { it.source == Source.YOUTUBE_MUSIC }
            MediaListTab.VIDEOS -> allTracks.filter { it.source == Source.YOUTUBE }
        }

    val trackCountLabel: String
        get() {
            val count = filteredTracks.size
            return if (count == 1) "1 faixa" else "$count faixas"
        }

    val totalDurationLabel: String
        get() {
            val totalSeconds = filteredTracks.sumOf { it.durationSeconds }
            if (totalSeconds <= 0) return ""
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) "${h}h, ${m}m" else "${m}m, ${s}s"
        }

    val metadataLabel: String
        get() = listOf(trackCountLabel, totalDurationLabel)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

    val descriptionLabel: String
        get() = description?.takeIf { it.isNotBlank() } ?: "Sem descrição"
}