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

    suspend fun search(query: String, filter: SearchFilter): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.search(query) }.getOrNull()
                ?: return@withContext emptyList()
            val parsed = InnerTubeParser.parseSearchResults(json)

            parsed.mapNotNull { item -> item.toHit(filter) }.take(40)
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

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val playerJson = InnerTube.player(videoId)
            InnerTube.extractBestAudioUrl(playerJson)
        }.getOrNull()
    }

    private fun InnerTubeParser.ParsedItem.toHit(filter: SearchFilter): SearchHit? {
        return when (this) {
            is InnerTubeParser.ParsedItem.Song -> {
                val track = Track(
                    id = videoId,
                    title = title,
                    artist = artist,
                    durationSeconds = durationSeconds,
                    thumbnailUrl = thumbnailUrl,
                    source = source,
                )
                when {
                    source == Source.YOUTUBE && (filter == SearchFilter.ALL || filter == SearchFilter.VIDEOS) ->
                        SearchHit.VideoHit(track)
                    source == Source.YOUTUBE_MUSIC && (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) ->
                        SearchHit.SongHit(track)
                    else -> null
                }
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