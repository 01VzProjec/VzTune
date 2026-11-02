package com.vztechcore.vztune.core.domain.search

enum class SearchFilter(val label: String, val params: String?) {
    ALL("Tudo", null),
    SONGS("Músicas", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    ALBUMS("Álbuns", "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"),
    ARTISTS("Artistas", "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"),
    PLAYLISTS("Playlists", "EgWKAQIoAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("Vídeos", null),
}