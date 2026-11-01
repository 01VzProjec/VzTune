package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YT Music facade. Only exposes public operations; every parsing / caching /
 * enrichment detail lives in the sibling files:
 *
 *   YTMusicCaches        - shared caches
 *   YTMusicTextUtils     - text & JSON primitives
 *   YTMusicThumbnailUtils- thumbnail extraction
 *   YTMusicSearchParser  - search result parsing + source detection
 *   YTMusicArtistParser  - artist page parsing
 *   YTMusicEnricher      - real artist/thumbnail via player endpoint
 */
@Singleton
class YTMusicRepository @Inject constructor(
    private val enricher: YTMusicEnricher,
) {

    // ================= PUBLIC DATA TYPES =================

    data class SearchPage(
        val items: List<SearchHit>,
        val continuation: String?,
    )

    data class AlbumResult(
        val coverUrl: String?,
        val artist: String?,
        val title: String?,
        val tracks: List<Track>,
    )

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        continuation: String?,
    ): SearchPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.search(query, filter.params)
            else InnerTube.searchContinuation(continuation)
        }.getOrNull() ?: return@withContext SearchPage(emptyList(), null)

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val next = InnerTube.findContinuationToken(json)
        val hits = parsed.mapNotNull { it.toHit(filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()
            val json = runCatching { InnerTube.searchSuggestions(query) }.getOrNull()
                ?: return@withContext SearchSuggestions()
            val queries = runCatching {
                YTMusicSearchParser.parseSuggestions(json)
            }.getOrDefault(emptyList())
            SearchSuggestions(queries = queries)
        }

    // ================= BROWSE =================

    suspend fun browseTracks(
        browseId: String,
        fallbackArtist: String? = null,
    ): List<Track> = withContext(Dispatchers.IO) {
        val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
            ?: return@withContext emptyList()

        val items = collectMusicItems(json).distinctBy { it.id }
        val withArtist = if (fallbackArtist.isNullOrBlank()) items
        else items.map { item ->
            if (item.artist.isBlank() || item.artist.equals("Unknown", true)) {
                item.copy(artist = fallbackArtist)
            } else item
        }
        enricher.enrichTracks(withArtist, fallbackArtist)
    }

    suspend fun browseAlbum(albumId: String): AlbumResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(albumId) }.getOrNull()
                ?: return@withContext AlbumResult(null, null, null, emptyList())

            val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
                ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
                ?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")

            val headerCover = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
            val albumTitle = header?.let { YTMusicArtistParser.extractHeaderTitle(it) }
            var artistName = header?.let { extractHeaderArtist(it) }

            val tracks = collectMusicItems(json).distinctBy { it.id }

            if (artistName.isNullOrBlank() && tracks.isNotEmpty()) {
                artistName = runCatching {
                    val playerJson = InnerTube.player(tracks.first().id)
                    extractArtistFromPlayer(playerJson).takeIf { it.isNotBlank() }
                }.getOrNull()
            }
            if (artistName.isNullOrBlank()) artistName = "Artista Desconhecido"

            val coverUrl = headerCover
                ?: YTMusicThumbnailUtils.findBestCoverUrl(json)
                ?: tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

            val pre = tracks.map { t ->
                var updated = t
                if (updated.artist.isBlank() || updated.artist.equals("Unknown", true)) {
                    updated = updated.copy(artist = artistName!!)
                }
                if (updated.thumbnailUrl.isNullOrBlank() && !coverUrl.isNullOrBlank()) {
                    updated = updated.copy(thumbnailUrl = coverUrl)
                }
                updated
            }

            val finalTracks = enricher.enrichTracks(pre, artistName)

            AlbumResult(
                coverUrl = coverUrl,
                artist = artistName,
                title = albumTitle,
                tracks = finalTracks,
            )
        }

    // ================= ARTISTS =================

    suspend fun searchArtists(query: String): List<Artist> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<Artist>()
            runCatching {
                val json = InnerTube.search(query, SearchFilter.ARTISTS.params)
                YTMusicArtistParser.extractArtistsFromSearch(json, out)
            }
            out.distinctBy { it.id }
                .filter { !it.thumbnailUrl.isNullOrBlank() }
                .take(30)
        }

    suspend fun featuredArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val seeds = listOf(
            "top hits", "pop", "funk brasileiro", "sertanejo",
            "rock", "trap", "mpb", "lofi",
        ).shuffled().take(3)

        val out = mutableListOf<Artist>()
        for (seed in seeds) {
            runCatching {
                val json = InnerTube.search(seed, SearchFilter.ARTISTS.params)
                YTMusicArtistParser.extractArtistsFromSearch(json, out)
            }
        }

        out.distinctBy { it.id }
            .filter { !it.thumbnailUrl.isNullOrBlank() }
            .shuffled()
            .take(30)
    }

    suspend fun browseArtist(browseId: String): YTMusicArtistParser.ArtistResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
                ?: return@withContext YTMusicArtistParser.ArtistResult(
                    browseId = browseId,
                    name = "Artista",
                    thumbnailUrl = null,
                    subscribers = null,
                    description = null,
                    topSongs = emptyList(),
                    allSongs = emptyList(),
                    allVideos = emptyList(),
                    allAlbums = emptyList(),
                    allSingles = emptyList(),
                    featured = emptyList(),
                    relatedArtists = emptyList(),
                )
            YTMusicArtistParser.parseArtistPage(browseId, json)
        }

    suspend fun getArtistFromVideo(videoId: String): Artist? =
        withContext(Dispatchers.IO) {
            val playerJson = runCatching { InnerTube.player(videoId) }.getOrNull()
                ?: return@withContext null

            val videoDetails = playerJson.optJSONObject("videoDetails")
                ?: return@withContext null

            val channelId = videoDetails.optString("channelId")
                .takeIf { it.isNotBlank() && it != "null" }
            val author = videoDetails.optString("author")
                .takeIf { it.isNotBlank() && it != "null" }
            if (channelId == null || author == null) return@withContext null

            val cleanName = YTMusicTextUtils.cleanArtistName(author)
            if (cleanName.isBlank()) return@withContext null

            val detail = runCatching { browseArtist(channelId) }.getOrNull()

            Artist(
                id = channelId,
                name = cleanName,
                thumbnailUrl = detail?.thumbnailUrl,
                source = Source.YOUTUBE_MUSIC,
            )
        }

    // ================= QUEUE =================

    suspend fun buildSongQueue(seed: Track): List<Track> =
        withContext(Dispatchers.IO) {
            val query = seed.artist.ifBlank { seed.title }.trim()
            if (query.isBlank()) return@withContext listOf(seed)

            val json = runCatching {
                InnerTube.search(query, SearchFilter.SONGS.params)
            }.getOrNull() ?: return@withContext listOf(seed)

            val found = collectMusicItems(json).distinctBy { it.id }
            val normalized = found.map { item ->
                val uploader = item.artist.takeIf {
                    it.isNotBlank() && !it.equals("Unknown", true)
                } ?: seed.artist
                item.copy(artist = uploader)
            }

            val list = if (normalized.any { it.id == seed.id }) {
                listOf(seed) + normalized.filter { it.id != seed.id }
            } else {
                listOf(seed) + normalized
            }
            list.take(50)
        }

    // ================= INTERNAL HELPERS =================

    private fun collectMusicItems(
        node: Any?,
        out: MutableList<Track> = mutableListOf(),
    ): List<Track> {
        when (node) {
            is org.json.JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    val videoId = r.optJSONObject("playlistItemData")
                        ?.optString("videoId")
                    if (title != null && !videoId.isNullOrBlank() && videoId != "null") {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicTextUtils.runsText(subtitleCol)
                            .substringBefore("•")
                            .trim()
                            .ifBlank { "Unknown" }
                        out += Track(
                            id = videoId,
                            title = title,
                            artist = uploader,
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils.parseDuration(
                                YTMusicTextUtils.fixedText(r)
                            ),
                            source = source,
                        )
                    }
                }
                node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    val videoId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val title = r.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text")
                    if (!videoId.isNullOrBlank() && title != null) {
                        out += Track(
                            id = videoId, title = title,
                            artist = YTMusicTextUtils.runsText(
                                r.optJSONObject("subtitle")
                            ),
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                            durationSeconds = 0L,
                            source = Source.YOUTUBE_MUSIC,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is org.json.JSONArray -> for (i in 0 until node.length()) {
                collectMusicItems(node.opt(i), out)
            }
        }
        return out
    }

    private fun extractHeaderArtist(header: JSONObject): String? {
        val subtitleRuns = header.optJSONObject("subtitle")?.optJSONArray("runs")
        if (subtitleRuns != null) {
            val artists = mutableListOf<String>()
            for (i in 0 until subtitleRuns.length()) {
                val run = subtitleRuns.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                if (text.isBlank() || text == "•" || text == "·") continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                when {
                    browseId?.startsWith("MPREb_") == true -> break
                    browseId?.startsWith("VL") == true -> break
                    browseId?.startsWith("UC") == true -> {
                        if (text !in artists) artists += text
                    }
                }
            }
            if (artists.isNotEmpty()) return artists.joinToString(", ")
        }
        return null
    }

    private fun extractArtistFromPlayer(playerJson: JSONObject): String {
        val videoDetails = playerJson.optJSONObject("videoDetails")
        val microformat = playerJson.optJSONObject("microformat")
            ?.optJSONObject("playerMicroformatRenderer")
        val candidates = listOfNotNull(
            videoDetails?.optString("author")?.takeIf { it.isNotBlank() },
            microformat?.optString("ownerChannelName")?.takeIf { it.isNotBlank() },
            microformat?.optString("author")?.takeIf { it.isNotBlank() },
        )
        for (raw in candidates) {
            val cleaned = YTMusicTextUtils.cleanArtistName(raw)
            if (cleaned.isNotBlank()) return cleaned
        }
        return ""
    }

    private companion object {
        const val TAG = "VzTuneYTM"
    }
}