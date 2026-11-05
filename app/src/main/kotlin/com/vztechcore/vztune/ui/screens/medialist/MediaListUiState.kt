package com.vztechcore.vztune.ui.screens.medialist

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder

enum class MediaListTab(val label: String) {
    ALL("Tudo"),
    SONGS("Músicas"),
    VIDEOS("Vídeos"),
}

data class MediaListUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val owner: String? = null,
    val year: String? = null,
    val views: String? = null, // Visualizações adicionadas!
    val originalTrackCount: Int? = null,
    val thumbnailUrl: String? = null,
    val mosaicThumbnails: List<String> = emptyList(),
    val isAlbum: Boolean = false,
    val allTracks: List<Track> = emptyList(),
    val description: String? = null,
    val selectedTab: MediaListTab = MediaListTab.ALL,
    val sortOrder: SortOrder = SortOrder.OLD_TO_RECENT,
    val manualOrder: List<String> = emptyList(),
    val isShuffling: Boolean = false,
) {
    val showTabs: Boolean get() = !isAlbum
    val isSortEnabled: Boolean get() = selectedTab == MediaListTab.ALL

    val filteredTracks: List<Track>
        get() = when (selectedTab) {
            MediaListTab.ALL -> allTracks
            MediaListTab.SONGS -> allTracks.filter { it.source == Source.YOUTUBE_MUSIC }
            MediaListTab.VIDEOS -> allTracks.filter { it.source == Source.YOUTUBE }
        }

    fun applySort(tracks: List<Track>): List<Track> = when (sortOrder) {
        SortOrder.MANUAL, SortOrder.SHUFFLE -> {
            if (manualOrder.isEmpty()) tracks
            else {
                val map = manualOrder.withIndex().associate { it.value to it.index }
                tracks.sortedBy { map[it.id] ?: Int.MAX_VALUE }
            }
        }
        SortOrder.RECENT_TO_OLD -> tracks.reversed()
        SortOrder.OLD_TO_RECENT -> tracks
        SortOrder.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
        SortOrder.TITLE_DESC -> tracks.sortedByDescending { it.title.lowercase() }
        SortOrder.ARTIST_ASC -> tracks.sortedBy { it.artist.lowercase() }
        SortOrder.ARTIST_DESC -> tracks.sortedByDescending { it.artist.lowercase() }
    }

    fun getDisplayTracks(): List<Track> {
        val filtered = filteredTracks
        return if (isSortEnabled) {
            applySort(filtered)
        } else {
            filtered.reversed()
        }
    }
    
    // Header Subtitle reformulado: "Dono • Ano • Visualizações"
    val headerSubtitle: String?
        get() = buildString {
            val primary = owner ?: subtitle
            if (!primary.isNullOrBlank()) append(primary)
            if (!year.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(year)
            }
            if (!views.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(views)
            }
        }.takeIf { it.isNotBlank() }

    val trackCountLabel: String
        get() {
            val count = originalTrackCount ?: filteredTracks.size
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