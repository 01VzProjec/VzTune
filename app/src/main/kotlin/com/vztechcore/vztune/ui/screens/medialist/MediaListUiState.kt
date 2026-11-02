package com.vztechcore.vztune.ui.screens.medialist

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track

enum class MediaListTab(val label: String) {
    ALL("Tudo"),
    SONGS("Músicas"),
    VIDEOS("Vídeos"),
}

enum class SortOrder(val label: String) {
    MANUAL("Manual"),
    RECENT_TO_OLD("Recente → Antigas"),
    OLD_TO_RECENT("Antigas → Recentes"),
    TITLE_ASC("Título A → Z"),
    TITLE_DESC("Título Z → A"),
    ARTIST_ASC("Artista A → Z"),
    ARTIST_DESC("Artista Z → A"),
    SHUFFLE("Sortear"),
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
    val sortOrder: SortOrder = SortOrder.MANUAL,
) {
    val showTabs: Boolean get() = !isAlbum

    /**
     * Applies tab filter. SORT is applied in the screen composable
     * (with `remember`) to avoid shuffling on every recomposition.
     */
    val filteredTracks: List<Track>
        get() = when (selectedTab) {
            MediaListTab.ALL -> allTracks
            MediaListTab.SONGS -> allTracks.filter { it.source == Source.YOUTUBE_MUSIC }
            MediaListTab.VIDEOS -> allTracks.filter { it.source == Source.YOUTUBE }
        }

    /**
     * Recomputed by the screen. The UI applies `sortOrder` on top of this
     * list with a `remember(filteredTracks, sortOrder)` block so `SHUFFLE`
     * produces a stable order until the user re-triggers it.
     */
    fun applySort(tracks: List<Track>): List<Track> = when (sortOrder) {
        SortOrder.MANUAL -> tracks
        SortOrder.RECENT_TO_OLD -> tracks.reversed()
        SortOrder.OLD_TO_RECENT -> tracks
        SortOrder.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
        SortOrder.TITLE_DESC -> tracks.sortedByDescending { it.title.lowercase() }
        SortOrder.ARTIST_ASC -> tracks.sortedBy { it.artist.lowercase() }
        SortOrder.ARTIST_DESC -> tracks.sortedByDescending { it.artist.lowercase() }
        SortOrder.SHUFFLE -> tracks.shuffled()
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