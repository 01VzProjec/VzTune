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
        val sourceName: String? = null, // FIX20 - YOUTUBE ou YOUTUBE_MUSIC. Sem isso a playlist perdia o mundo.
    )

    /** Artist profile page. [id] is the YouTube Music channel/browse id (UC...). */
    @Serializable data class Artist(
        val id: String,
        val name: String,
        val thumbnailUrl: String? = null,
        // true = canal do YouTube (videos/shorts/playlists via NewPipe).
        // false = artista do YT Music (musicas/albuns via InnerTube).
        val isYouTubeChannel: Boolean = false,
        // Aba inicial do canal (0 Inicio, 1 Videos, 2 Playlists, 3 Shorts, 4 Sobre).
        val initialTab: Int = 0,
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

    /**
     * Tela cheia de Shorts (rolagem vertical). A lista de Shorts vai no
     * ShortsFeed (memoria); a rota leva so a chave + indice inicial + canal.
     */
    @Serializable data class Shorts(
        val feedKey: String,
        val startIndex: Int = 0,
        val channelId: String,
        val channelName: String,
        val channelAvatarUrl: String? = null,
        val channelHandle: String? = null,
    )
}
