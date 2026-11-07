package com.vztechcore.vztune.data.music

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicTextUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
) {

    // =====================================================================
    //  Fachada — API que os ViewModels esperam do MusicRepository
    // =====================================================================

    data class TechnicalInfo(
        val itag: String?,
        val mimeType: String?,
        val quality: String?,
    )

    /** Extração de áudio via NewPipe (delegado pro YouTubeRepository). */
    suspend fun getStreamUrl(track: Track): String? {
        val artistHint = track.artist
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
        val url = runCatching {
            youTubeRepository.getStreamUrl(
                videoId = track.id,
                titleHint = track.title.takeIf { it.isNotBlank() },
                artistHint = artistHint,
            )
        }.getOrElse { e ->
            Log.e(TAG, "getStreamUrl failed for ${track.id}: ${e.message}", e)
            null
        }
        if (url.isNullOrBlank()) {
            Log.w(TAG, "getStreamUrl returned null for ${track.id} '${track.title}'")
        }
        return url
    }

    /** Info técnica (itag / mime / bitrate) — usada pela tela TrackInfo. */
    suspend fun getTechnicalInfo(track: Track): TechnicalInfo? = runCatching {
        val info = youTubeRepository.getAudioStreamInfo(track.id) ?: return@runCatching null
        TechnicalInfo(itag = info.itag, mimeType = info.mimeType, quality = info.quality)
    }.getOrNull()

    /** Sugestões de busca — delegate direto pro YTMusic. */
    suspend fun suggestions(query: String): SearchSuggestions =
        ytMusicRepository.suggestions(query)

    /**
     * Fila "rádio" — escolhe o repositório certo pelo `seed.source`.
     *   YOUTUBE_MUSIC → ytMusic.buildSongQueue
     *   YOUTUBE       → youtube.buildVideoQueue
     */
    suspend fun buildRadioQueue(seed: Track): List<Track> = runCatching {
        when (seed.source) {
            Source.YOUTUBE_MUSIC -> ytMusicRepository.buildSongQueue(seed)
            Source.YOUTUBE -> youTubeRepository.buildVideoQueue(seed)
            else -> listOf(seed)
        }
    }.getOrElse { e ->
        Log.w(TAG, "buildRadioQueue failed for ${seed.id}: ${e.message}")
        listOf(seed)
    }

    // =====================================================================
    //  SEARCH
    // =====================================================================

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
        var topArtistsLoaded: Boolean = false,
    )

    private val searchStates = mutableMapOf<String, SearchState>()

    suspend fun search(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
    ): SearchResult {
        if (query.isBlank()) return SearchResult.Success(emptyList(), false)
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
        return when (filter) {
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
            async { runCatching { ytMusicRepository.searchArtists(query) }.getOrDefault(emptyList()).take(3) }
        } else null
        val ytDeferred = async { youTubeRepository.searchVideos(query, loadMore, pageSize) }
        val ytmDeferred = async { ytMusicRepository.search(query, SearchFilter.ALL, state.ytmContinuation) }
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
        val ytmAlbums = ytmPage.items.filterIsInstance<SearchHit.AlbumHit>()
        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()

        val validAlbumsDeferred = async { validateAlbums(ytmAlbums.take(3)) }
        val validPlaylistsDeferred = async { validatePlaylists(ytmPlaylists.take(3)) }

        merged += interleave(ytPage.items, ytmTracksOnly.take(pageSize))
        
        if (!loadMore) {
            merged += validAlbumsDeferred.await()
            merged += validPlaylistsDeferred.await()
        }

        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytHasMore || state.ytmHasMore)
    }

    private suspend fun searchVideos(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val ytPage = youTubeRepository.searchVideos(query, loadMore, 30)
        state.ytHasMore = ytPage.hasMore
        val newHits = ytPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytHasMore)
    }

    private suspend fun searchSongs(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val ytmPage = ytMusicRepository.search(query, SearchFilter.SONGS, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.take(30).filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun searchPlaylists(query: String, loadMore: Boolean, state: SearchState): SearchResult = coroutineScope {
        val ytDeferred = async { if (!loadMore) youTubeRepository.searchPlaylists(query, loadMore = false) else emptyList() }
        val ytmDeferred = async { ytMusicRepository.search(query, SearchFilter.PLAYLISTS, state.ytmContinuation) }
        val ytHits = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()
        val ytFiltered = ytHits.filterNot { hit ->
            hit.playlist.title.startsWith("Mix -", ignoreCase = true) &&
                hit.playlist.id.contains("RD", ignoreCase = true)
        }
        // YouTube playlists come without view counts, so fill them in like the YT Music ones.
        val ytWithStats = ytMusicRepository.fillPlaylistStats(ytFiltered)
        val allPlaylists = (ytWithStats + ytmPlaylists).distinctBy { hit ->
            YTMusicTextUtils.normalizePlaylistBrowseId(hit.playlist.id)
        }
        val validated = validatePlaylists(allPlaylists)
        val newHits = validated.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun validateAlbums(albums: List<SearchHit.AlbumHit>): List<SearchHit.AlbumHit> = coroutineScope {
        if (albums.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(6)
        val deferred = albums.map { hit ->
            async<SearchHit.AlbumHit?> {
                semaphore.withPermit {
                    val hasTracks = runCatching {
                        withTimeoutOrNull(10000L) { ytMusicRepository.browseAlbum(hit.album.id).tracks.isNotEmpty() }
                    }.getOrNull()
                    if (hasTracks == false) null else hit
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    private suspend fun validatePlaylists(playlists: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> = coroutineScope {
        if (playlists.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(8)
        val deferred = playlists.map { hit ->
            async<SearchHit.PlaylistHit?> {
                semaphore.withPermit {
                    val count = hit.playlist.trackCount
                    if (count != null && count > 0) return@withPermit hit
                    val hasTracks = runCatching {
                        withTimeoutOrNull(10000L) { ytMusicRepository.browsePlaylist(hit.playlist.id).tracks.isNotEmpty() }
                    }.getOrNull()
                    if (hasTracks == false) null else hit
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    private suspend fun searchOther(query: String, filter: SearchFilter, loadMore: Boolean, state: SearchState): SearchResult = coroutineScope {
        val ytmPage = ytMusicRepository.search(query, filter, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        if (filter == SearchFilter.ALBUMS) {
            val albums = ytmPage.items.filterIsInstance<SearchHit.AlbumHit>()
            val validated = validateAlbums(albums)
            val newHits = validated.filter { state.seenKeys.add(it.stableKey) }
            return@coroutineScope SearchResult.Success(newHits, state.ytmHasMore)
        }
        val newHits = ytmPage.items.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
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

    private companion object { const val TAG = "VzTuneMusic" }
}
