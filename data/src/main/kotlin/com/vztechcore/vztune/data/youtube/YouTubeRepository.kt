package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube search via NewPipeExtractor.
 *
 * TODO(youtube): wire to NewPipeExtractor v0.24.8.
 *   Flow: NewPipe.init(downloader) once -> getService(YouTube)
 *         -> SearchExtractor(service, query, filters).fetchPage()
 *         -> initialPage.items -> map to Track / Playlist -> SearchHit
 *   Needs: NewPipeDownloaderImpl (OkHttpClient-backed).
 *   For now this returns an empty Success so the app pipeline compiles;
 *   the real aggregator (MusicRepository) is still stubbed too.
 */
@Singleton
class YouTubeRepository @Inject constructor() {

    suspend fun searchVideos(query: String): SearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResult.Success(emptyList())
        SearchResult.Success(emptyList())
    }

    suspend fun searchPlaylists(query: String): SearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResult.Success(emptyList())
        SearchResult.Success(emptyList())
    }
}