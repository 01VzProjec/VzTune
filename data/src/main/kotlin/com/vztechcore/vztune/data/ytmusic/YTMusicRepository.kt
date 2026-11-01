package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import com.vztechcore.vztune.data.innertube.InnerTubeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YTMusicRepository @Inject constructor() {

    data class SearchPage(
        val items: List<SearchHit>,
        val continuation: String?,
    )

    suspend fun search(
        query: String,
        filter: SearchFilter,
        continuation: String?,
    ): SearchPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.search(query)
            else InnerTube.searchContinuation(continuation)
        }.getOrNull() ?: return@withContext SearchPage(emptyList(), null)

        val parsed = InnerTubeParser.parseSearchResults(json)
        val next = InnerTube.findContinuationToken(json)

        val hits = parsed.mapNotNull { item -> item.toHit(filter) }
        SearchPage(hits, next)
    }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()
            val json = runCatching { InnerTube.searchSuggestions(query) }.getOrNull()
                ?: return@withContext SearchSuggestions()
            val queries = runCatching { InnerTubeParser.parseSuggestions(json) }
                .getOrDefault(emptyList())
            SearchSuggestions(queries = queries)
        }

    // NOTE: no getStreamUrl here — playback is always resolved by NewPipe
    // (in YouTubeRepository) because InnerTube does not decode the `n` cipher.

    private fun InnerTubeParser.ParsedItem.toHit(filter: SearchFilter): SearchHit? {
        return when (this) {
            is InnerTubeParser.ParsedItem.Song -> {
                if (source == Source.YOUTUBE) return null
                if (filter != SearchFilter.ALL && filter != SearchFilter.SONGS) return null
                SearchHit.SongHit(
                    Track(
                        id = videoId,
                        title = title,
                        artist = artist,
                        durationSeconds = durationSeconds,
                        thumbnailUrl = thumbnailUrl,
                        source = source,
                    )
                )
            }
            is InnerTubeParser.ParsedItem.Album -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.ALBUMS) return null
                SearchHit.AlbumHit(
                    Album(
                        id = browseId,
                        title = title,
                        artist = artist,
                        thumbnailUrl = thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
            is InnerTubeParser.ParsedItem.Artist -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.ARTISTS) return null
                SearchHit.ArtistHit(
                    Artist(
                        id = browseId,
                        name = name,
                        thumbnailUrl = thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
            is InnerTubeParser.ParsedItem.Playlist -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.PLAYLISTS) return null
                SearchHit.PlaylistHit(
                    Playlist(
                        id = browseId,
                        title = title,
                        owner = owner,
                        thumbnailUrl = thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
        }
    }
}