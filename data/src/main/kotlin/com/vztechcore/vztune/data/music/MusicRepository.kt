package com.vztechcore.vztune.data.music

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
) {

    suspend fun search(query: String, filter: SearchFilter): SearchResult =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchResult.Success(emptyList())

            val hits = when (filter) {
                SearchFilter.ALL -> coroutineScope {
                    val yt = async {
                        runCatching { youTubeRepository.searchVideos(query) }.getOrDefault(emptyList())
                    }
                    val ytm = async {
                        runCatching { ytMusicRepository.search(query, SearchFilter.ALL) }.getOrDefault(emptyList())
                    }
                    interleave(yt.await(), ytm.await())
                }
                SearchFilter.VIDEOS ->
                    runCatching { youTubeRepository.searchVideos(query) }.getOrDefault(emptyList())

                SearchFilter.PLAYLISTS -> coroutineScope {
                    val yt = async {
                        runCatching { youTubeRepository.searchPlaylists(query) }.getOrDefault(emptyList())
                    }
                    val ytm = async {
                        runCatching { ytMusicRepository.search(query, SearchFilter.PLAYLISTS) }.getOrDefault(emptyList())
                    }
                    yt.await() + ytm.await()
                }

                else ->
                    runCatching { ytMusicRepository.search(query, filter) }.getOrDefault(emptyList())
            }

            SearchResult.Success(hits.distinctBy { it.stableKey })
        }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            runCatching { ytMusicRepository.suggestions(query) }.getOrDefault(SearchSuggestions())
        }

    suspend fun getStreamUrl(track: Track): String? = withContext(Dispatchers.IO) {
        when (track.source) {
            Source.YOUTUBE -> youTubeRepository.getStreamUrl(track.id, track.title, track.artist)
            Source.YOUTUBE_MUSIC -> ytMusicRepository.getStreamUrl(track.id)
                ?: youTubeRepository.getStreamUrl(track.id, track.title, track.artist)
        }
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = mutableListOf<T>()
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }
}