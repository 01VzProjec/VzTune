package com.vztechcore.vztune.data.ytmusic

import android.util.Log
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    /**
     * Enrich SearchHit list with:
     *   - Real artist name (via InnerTube.player -> videoDetails.author)
     *   - Real source (YOUTUBE vs YOUTUBE_MUSIC)
     *   - Real thumbnail (when missing)
     *
     * Only runs for SongHit items whose artist still looks like metadata.
     * Caches results in InnerTubeParser.authorCache so the same video
     * is only resolved once, even across searches.
     *
     * Ported from VzMusic's enrichTracksWithPlayer — same Semaphore(10)
     * to keep things fast without hammering the endpoint.
     */
    suspend fun enrichHits(hits: List<SearchHit>): List<SearchHit> =
        withContext(Dispatchers.IO) {
            if (hits.isEmpty()) return@withContext hits

            coroutineScope {
                val semaphore = Semaphore(10)
                val deferreds = hits.map { hit ->
                    async {
                        semaphore.withPermit { enrichSingle(hit) }
                    }
                }
                deferreds.awaitAll()
            }
        }

    private fun enrichSingle(hit: SearchHit): SearchHit {
        if (hit !is SearchHit.SongHit) return hit

        val track = hit.track
        val cachedAuthor = InnerTubeParser.authorCache[track.id]

        // Already cached and artist looks valid — nothing to do.
        if (!cachedAuthor.isNullOrBlank()) {
            return if (cachedAuthor != track.artist) {
                SearchHit.SongHit(track.copy(artist = cachedAuthor))
            } else {
                hit
            }
        }

        // Artist already looks real (not metadata). Trust the parser.
        if (!isLikelyMetadata(track.artist)) {
            InnerTubeParser.authorCache[track.id] = track.artist
            return hit
        }

        // Otherwise ask the player endpoint.
        val real = runCatching { fetchAuthorFromPlayer(track.id) }.getOrNull()
        if (real.isNullOrBlank()) return hit

        InnerTubeParser.authorCache[track.id] = real
        return SearchHit.SongHit(track.copy(artist = real))
    }

    /**
     * Checks if the parsed "artist" string is really metadata
     * (episode markers, dates, "Provided to YouTube", etc.) rather
     * than an artist name. If it is, we MUST call the player to fix.
     */
    private fun isLikelyMetadata(s: String): Boolean {
        if (s.isBlank()) return true
        val lower = s.lowercase()
        if (lower == "unknown" || lower == "desconhecido") return true
        if (lower.startsWith("episódio") || lower.startsWith("episodio")) return true
        if (lower.startsWith("temporada")) return true
        if (Regex("^\\d{1,2} de \\w+\\.? de \\d{4}").containsMatchIn(lower)) return true
        if (Regex("^[a-z]{3} \\d{1,2}, \\d{4}$").containsMatchIn(lower)) return true
        return false
    }

    private suspend fun fetchAuthorFromPlayer(videoId: String): String? {
        return try {
            val playerJson = InnerTube.player(videoId)
            extractArtistFromPlayer(playerJson)
        } catch (e: Exception) {
            Log.w(TAG, "player enrich failed for $videoId: ${e.message}")
            null
        }
    }

    /**
     * Same extraction as VzMusic's extractArtistFromPlayer:
     *   videoDetails.author -> microformat.ownerChannelName -> microformat.author
     * Then cleaned via cleanArtistName.
     */
    private fun extractArtistFromPlayer(playerJson: JSONObject): String? {
        val videoDetails = playerJson.optJSONObject("videoDetails")
        val microformat = playerJson.optJSONObject("microformat")
            ?.optJSONObject("playerMicroformatRenderer")

        val candidates = listOfNotNull(
            videoDetails?.optString("author")?.takeIf { it.isNotBlank() },
            microformat?.optString("ownerChannelName")?.takeIf { it.isNotBlank() },
            microformat?.optString("author")?.takeIf { it.isNotBlank() },
        )

        for (raw in candidates) {
            val cleaned = cleanArtistName(raw)
            if (cleaned.isNotBlank()) return cleaned
        }
        return null
    }

    private fun cleanArtistName(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.trim()
        s = s.removeSuffix(" - Topic").removeSuffix(" - topic").trim()
        if (s.startsWith("Provided to YouTube", true)) return ""
        if (s.startsWith("Auto-generated by YouTube", true)) return ""
        if (s.equals("Various Artists", true)) return "Various Artists"
        if (s.startsWith("UC") && s.length > 20) return ""
        if (s.equals("Album", true) || s.equals("Álbum", true)) return ""
        return s
    }

    // ================= ParsedItem -> SearchHit mapping =================

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

    private companion object {
        const val TAG = "VzTuneYTM"
    }
}