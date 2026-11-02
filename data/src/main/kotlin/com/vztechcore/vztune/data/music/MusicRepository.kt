package com.vztechcore.vztune.data.music

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeMusicNewPipeRepository
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeMusicNewPipeRepository: YouTubeMusicNewPipeRepository,
) {

    data class TechnicalInfo(
        val itag: String?,
        val mimeType: String?,
        val quality: String?,
    )

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
        var topArtistsLoaded: Boolean = false,
    )

    private val searchStates = mutableMapOf<String, SearchState>()

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
    ): SearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResult.Success(emptyList(), false)

        val stateKey = "$query|${filter.name}"
        val state = searchStates.getOrPut(stateKey) { SearchState() }

        if (!loadMore) {
            state.seenKeys.clear()
            state.ytHasMore = true
            state.ytmContinuation = null
            state.ytmHasMore = true
            state.topArtistsLoaded = false
            youTubeRepository.resetSession()
        }

        when (filter) {
            SearchFilter.ALL -> searchAll(query, loadMore, state)
            SearchFilter.VIDEOS -> searchVideos(query, loadMore, state)
            SearchFilter.SONGS -> searchSongs(query, loadMore, state)
            SearchFilter.PLAYLISTS -> searchPlaylists(query, loadMore, state)
            else -> searchOther(query, filter, loadMore, state)
        }
    }

    private suspend fun searchAll(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult = coroutineScope {
        val pageSize = if (loadMore) 10 else 20

        val topArtistsDeferred = if (!state.topArtistsLoaded && !loadMore) {
            async {
                runCatching { ytMusicRepository.searchArtists(query) }
                    .getOrDefault(emptyList()).take(3)
            }
        } else null

        val ytDeferred = async { youTubeRepository.searchVideos(query, loadMore, pageSize) }
        val ytmDeferred = async {
            ytMusicRepository.search(query, SearchFilter.ALL, state.ytmContinuation)
        }

        val ytPage = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        val topArtists = topArtistsDeferred?.await().orEmpty()

        state.ytHasMore = ytPage.hasMore
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null

        val merged = mutableListOf<SearchHit>()
        if (topArtists.isNotEmpty()) {
            topArtists.forEach { merged += SearchHit.ArtistHit(it) }
            state.topArtistsLoaded = true
        }

        val ytmTracksOnly = ytmPage.items.filterIsInstance<SearchHit.SongHit>()
        merged += interleave(ytPage.items, ytmTracksOnly.take(pageSize))

        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytHasMore || state.ytmHasMore)
    }

    private suspend fun searchVideos(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult {
        val ytPage = youTubeRepository.searchVideos(query, loadMore, 30)
        state.ytHasMore = ytPage.hasMore
        val newHits = ytPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytHasMore)
    }

    private suspend fun searchSongs(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult {
        val ytmPage = ytMusicRepository.search(query, SearchFilter.SONGS, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.take(30).filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun searchPlaylists(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult = coroutineScope {
        val ytDeferred = async {
            if (!loadMore) youTubeRepository.searchPlaylists(query, loadMore = false)
            else emptyList()
        }
        val ytmDeferred = async {
            ytMusicRepository.search(query, SearchFilter.PLAYLISTS, state.ytmContinuation)
        }
        val ytHits = ytDeferred.await()
        val ytmPage = ytmDeferred.await()

        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null

        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()
        val ytmValidated = validatePlaylists(ytmPlaylists)

        val merged = ytHits + ytmValidated
        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun validatePlaylists(
        playlists: List<SearchHit.PlaylistHit>,
    ): List<SearchHit.PlaylistHit> = coroutineScope {
        val semaphore = Semaphore(8)
        playlists.map { hit ->
            async {
                semaphore.withPermit {
                    val count = hit.playlist.trackCount
                    if (count != null && count > 0) return@withPermit hit

                    val hasTracks = runCatching {
                        withTimeoutOrNull(5000L) {
                            ytMusicRepository.browsePlaylist(hit.playlist.id)
                                .tracks.isNotEmpty()
                        }
                    }.getOrNull()

                    when {
                        hasTracks == null -> hit
                        hasTracks == false -> {
                            Log.d(TAG, "skip empty playlist: ${hit.playlist.id} '${hit.playlist.title}'")
                            null
                        }
                        else -> hit
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun searchOther(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult {
        val ytmPage = ytMusicRepository.search(query, filter, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    // ================= RADIO QUEUE =================

    suspend fun buildRadioQueue(track: Track): List<Track> = withContext(Dispatchers.IO) {
        val queue = when (track.source) {
            Source.YOUTUBE_MUSIC -> runCatching {
                ytMusicRepository.buildSongQueue(track)
            }.getOrDefault(listOf(track))

            Source.YOUTUBE -> runCatching {
                youTubeRepository.buildVideoQueue(track)
            }.getOrDefault(listOf(track))
        }
        if (queue.isEmpty()) listOf(track) else queue
    }

    // ================= TECHNICAL INFO =================

    suspend fun getTechnicalInfo(track: Track): TechnicalInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.youtube.com/watch?v=${track.id}"
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            // Preferir áudio puro
            val audio = extractor.audioStreams.maxByOrNull { it.averageBitrate }
            if (audio != null) {
                val bitrate = audio.averageBitrate
                return@runCatching TechnicalInfo(
                    itag = audio.itag.takeIf { it > 0 }?.toString() ?: "Desconhecido",
                    mimeType = audio.getFormat()?.mimeType ?: "Desconhecido",
                    quality = if (bitrate > 0) "${bitrate / 1000} kbps" else "Desconhecida",
                )
            }

            // Fallback para vídeo
            val video = extractor.videoStreams
                .filter { !it.isVideoOnly }
                .minByOrNull { it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE }
            
            if (video != null) {
                return@runCatching TechnicalInfo(
                    itag = video.itag.takeIf { it > 0 }?.toString() ?: "Desconhecido",
                    mimeType = video.getFormat()?.mimeType ?: "Desconhecido",
                    quality = video.resolution.takeIf { it.isNotBlank() } ?: "Desconhecida",
                )
            }

            null
        }.getOrElse { e ->
            Log.w(TAG, "getTechnicalInfo failed for ${track.id}: ${e.message}")
            null
        }
    }

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            runCatching { ytMusicRepository.suggestions(query) }
                .getOrDefault(SearchSuggestions())
        }

    // ================= PLAYBACK =================

    suspend fun getStreamUrl(track: Track): String? = withContext(Dispatchers.IO) {
        when (track.source) {
            Source.YOUTUBE_MUSIC -> runCatching {
                youTubeMusicNewPipeRepository.getAudioStreamUrl(track.id)
            }.getOrElse { e ->
                Log.w(TAG, "YTM stream failed for ${track.id}: ${e.message}")
                null
            }
            Source.YOUTUBE -> youTubeRepository.getStreamUrl(
                videoId = track.id,
                titleHint = track.title,
                artistHint = track.artist,
            )
        }
    }

    // ================= HELPERS =================

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = mutableListOf<T>()
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private companion object {
        const val TAG = "VzTuneMusic"
    }
}