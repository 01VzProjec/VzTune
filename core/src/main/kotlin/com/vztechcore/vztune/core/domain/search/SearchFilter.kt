package com.vztechcore.vztune.core.domain.search

/**
 * Filter applied on the search results.
 * ALL = no filter (mixed results).
 */
enum class SearchFilter(val label: String) {
    ALL("Tudo"),
    SONGS("Músicas"),
    ARTISTS("Artistas"),
    ALBUMS("Álbuns"),
    PLAYLISTS("Playlists"),
    VIDEOS("Vídeos"),
}