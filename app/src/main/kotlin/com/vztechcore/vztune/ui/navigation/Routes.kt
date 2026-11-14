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
    )

    /** Artist profile page. [id] is the YouTube Music channel/browse id (UC...). */
    @Serializable data class Artist(
        val id: String,
        val name: String,
        val thumbnailUrl: String? = null,
    )

    /** "See all" list of an artist. [kind] is an ArtistListKind name. */
    @Serializable data class ArtistList(
        val artistId: String,
        val artistName: String,
        val kind: String,
        val browseId: String,
        val params: String? = null,
    )

    /** List opened from a Library shortcut card. [kind] is a LibraryShortcut name. */
    @Serializable data class LibraryList(val kind: String)

    @Serializable data class TrackInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val thumbnailUrl: String?,
        val sourceName: String, // "YOUTUBE" or "YOUTUBE_MUSIC"
        val durationSeconds: Long,
    )
}