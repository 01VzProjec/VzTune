package com.vztechcore.vztune.core.domain.music

data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val source: Source,
)

/** Pointer to a YouTube Music "more" page (browse endpoint + optional params). */
data class BrowseRef(
    val browseId: String,
    val params: String? = null,
)

/** Which "see all" list of an artist is being shown. */
enum class ArtistListKind { SONGS, VIDEOS, ALBUMS, SINGLES, FEATURED }
