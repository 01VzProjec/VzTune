package com.vztechcore.vztune.data.music

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over YouTube + YT Music repositories.
 *
 * TODO: when the real repositories are wired, fan out to both
 *       and interleave results.
 * Currently returns stub data for ALL filters so the UI pipeline
 * can be exercised end-to-end.
 */
@Singleton
class MusicRepository @Inject constructor() {

    suspend fun search(query: String, filter: SearchFilter): SearchResult =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchResult.Success(emptyList())

            val hits = mutableListOf<SearchHit>()

            when (filter) {
                SearchFilter.ALL -> {
                    hits += SearchHit.SongHit(stubSong(query, "1"))
                    hits += SearchHit.VideoHit(stubVideo(query, "1"))
                    hits += SearchHit.ArtistHit(stubArtist(query))
                    hits += SearchHit.AlbumHit(stubAlbum(query))
                    hits += SearchHit.PlaylistHit(stubPlaylist(query))
                }
                SearchFilter.SONGS -> {
                    hits += SearchHit.SongHit(stubSong(query, "1"))
                    hits += SearchHit.SongHit(stubSong(query, "2"))
                    hits += SearchHit.SongHit(stubSong(query, "3"))
                }
                SearchFilter.VIDEOS -> {
                    hits += SearchHit.VideoHit(stubVideo(query, "1"))
                    hits += SearchHit.VideoHit(stubVideo(query, "2"))
                }
                SearchFilter.ARTISTS -> {
                    hits += SearchHit.ArtistHit(stubArtist(query))
                    hits += SearchHit.ArtistHit(stubArtist("$query 2"))
                }
                SearchFilter.ALBUMS -> {
                    hits += SearchHit.AlbumHit(stubAlbum(query))
                    hits += SearchHit.AlbumHit(stubAlbum("$query 2"))
                }
                SearchFilter.PLAYLISTS -> {
                    hits += SearchHit.PlaylistHit(stubPlaylist(query))
                    hits += SearchHit.PlaylistHit(stubPlaylist("$query 2"))
                }
            }

            SearchResult.Success(hits)
        }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()
            SearchSuggestions(
                songs = listOf(stubSong(query, "sug")),
                queries = listOf("$query mix", "$query ao vivo", "$query remix"),
            )
        }

    // ---- Stub helpers ----

    private fun stubSong(q: String, id: String) = Track(
        id = "stub-song-$id",
        title = "[STUB] $q — faixa $id",
        artist = "VzTune Stub",
        album = "Stub Album",
        durationSeconds = 200L + id.hashCode().mod(100),
        source = Source.YOUTUBE_MUSIC,
    )

    private fun stubVideo(q: String, id: String) = Track(
        id = "stub-video-$id",
        title = "[STUB] $q (video $id)",
        artist = "VzTune Stub",
        durationSeconds = 240L + id.hashCode().mod(100),
        source = Source.YOUTUBE,
    )

    private fun stubArtist(q: String) = Artist(
        id = "stub-artist-${q.hashCode()}",
        name = "[STUB] $q",
        source = Source.YOUTUBE_MUSIC,
    )

    private fun stubAlbum(q: String) = Album(
        id = "stub-album-${q.hashCode()}",
        title = "[STUB] $q (álbum)",
        artist = "VzTune Stub",
        year = 2024,
        source = Source.YOUTUBE_MUSIC,
    )

    private fun stubPlaylist(q: String) = Playlist(
        id = "stub-playlist-${q.hashCode()}",
        title = "[STUB] $q (playlist)",
        owner = "VzTune Stub",
        trackCount = 25,
        source = Source.YOUTUBE,
    )
}