package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YTMusicRepository @Inject constructor(
    private val enricher: YTMusicEnricher,
) {

    data class SearchPage(val items: List<SearchHit>, val continuation: String?)

    data class AlbumResult(
        val coverUrl: String?,
        val artist: String?,
        val title: String?,
        val description: String?,
        val tracks: List<Track>,
    )

    // Adicionado trackCount e year para a nossa nova UI
    data class PlaylistResult(
        val coverUrl: String?,
        val owner: String?,
        val title: String?,
        val description: String?,
        val trackCount: Int,
        val year: String?,
        val tracks: List<Track>,
    )

    data class ArtistResult(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val relatedArtists: List<Artist>
    )

    data class VideoDetails(
        val viewCount: String?,
        val likeCount: String?,
        val description: String?,
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
        val hits = parsed.mapNotNull { YTMusicSearchParser.toHit(it, filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()

            coroutineScope {
                val queriesDeferred = async {
                    runCatching {
                        val json = InnerTube.searchSuggestions(query)
                        YTMusicSearchParser.parseSuggestions(json)
                    }.getOrDefault(emptyList())
                }

                val songsDeferred = async {
                    runCatching {
                        val limit = when {
                            query.length <= 2 -> 3
                            query.length == 3 -> 4
                            else -> 5
                        }
                        
                        val json = InnerTube.search(query, SearchFilter.SONGS.params)
                        val parsed = YTMusicSearchParser.parseSearchResults(json)
                        parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.SONGS) }
                            .filterIsInstance<SearchHit.SongHit>()
                            .take(limit)
                            .map { it.track }
                    }.getOrDefault(emptyList())
                }

                SearchSuggestions(
                    songs = songsDeferred.await(),
                    queries = queriesDeferred.await()
                )
            }
        }

    // ================= BROWSE =================

    suspend fun browseAlbum(albumId: String): AlbumResult = withContext(Dispatchers.IO) {
        val json = runCatching { InnerTube.browse(albumId) }.getOrNull()
            ?: return@withContext AlbumResult(null, null, null, null, emptyList())

        val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")

        val cover = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
        val title = header?.let { YTMusicArtistParser.extractHeaderTitle(it) }
        val artist = header?.let { extractHeaderArtist(it) }
        val desc = extractDescription(json)

        val rawTracks = collectMusicItems(json).distinctBy { it.id }
        val albumTracks = rawTracks.filter { it.source == Source.YOUTUBE_MUSIC }
        val finalTracks = enricher.enrichTracks(albumTracks, artist)

        AlbumResult(cover, artist, title, desc, finalTracks)
    }

    suspend fun browsePlaylist(playlistId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val cleanId = YTMusicTextUtils.normalizePlaylistBrowseId(playlistId)
        val json = runCatching { InnerTube.browse(cleanId) }.getOrNull()
            ?: return@withContext PlaylistResult(null, null, null, null, 0, null, emptyList())

        val parsed = YTMusicPlaylistParser.parsePlaylist(json)
        val finalTracks = enricher.enrichTracks(parsed.tracks, null)

        PlaylistResult(
            coverUrl = parsed.coverUrl,
            owner = parsed.owner,
            title = parsed.title,
            description = parsed.description,
            trackCount = parsed.trackCount.takeIf { it > 0 } ?: finalTracks.size,
            year = parsed.year,
            tracks = finalTracks
        )
    }

    // ================= ARTISTS =================

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Artist>()
        runCatching {
            val json = InnerTube.search(query, SearchFilter.ARTISTS.params)
            YTMusicArtistParser.extractArtistsFromSearch(json, out)
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.take(30)
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
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.shuffled().take(30)
    }

    suspend fun browseArtist(browseId: String): ArtistResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
                ?: return@withContext ArtistResult(
                    browseId = browseId, name = "Artista",
                    thumbnailUrl = null, subscribers = null, description = null,
                    topSongs = emptyList(), albums = emptyList(), singles = emptyList(),
                    relatedArtists = emptyList()
                )
            
            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)
            
            ArtistResult(
                browseId = parsed.browseId,
                name = parsed.name,
                thumbnailUrl = parsed.thumbnailUrl,
                subscribers = parsed.subscribers,
                description = parsed.description,
                topSongs = parsed.topSongs,
                albums = parsed.albums,
                singles = parsed.singles,
                relatedArtists = parsed.relatedArtists
            )
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
                id = channelId, name = cleanName,
                thumbnailUrl = detail?.thumbnailUrl,
                source = Source.YOUTUBE_MUSIC,
            )
        }

    // ================= QUEUE =================

    suspend fun buildSongQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.artist.isNotBlank()) append(seed.artist)
            if (seed.title.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.title)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val json = runCatching {
            InnerTube.search(query, SearchFilter.SONGS.params)
        }.getOrNull() ?: return@withContext listOf(seed)

        val found = collectMusicItems(json)
            .distinctBy { it.id }
            .filter { it.source == Source.YOUTUBE_MUSIC }

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

    // ================= VIDEO DETAILS =================

    suspend fun getVideoDetails(videoId: String): VideoDetails? =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = InnerTube.player(videoId)
                val videoDetails = json.optJSONObject("videoDetails")
                    ?: return@runCatching null

                val viewCountRaw = videoDetails.optString("viewCount")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.toLongOrNull()

                var description = videoDetails.optString("shortDescription")
                    .takeIf { it.isNotBlank() && it != "null" }

                if (description == null) {
                    description = json.optJSONObject("microformat")
                        ?.optJSONObject("playerMicroformatRenderer")
                        ?.optJSONObject("description")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                }

                VideoDetails(
                    viewCount = viewCountRaw?.let { formatNumber(it) },
                    likeCount = null,
                    description = description,
                )
            }.getOrNull()
        }

    // ================= HELPERS =================

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
            ?: return null
        val descNode = shelf.optJSONObject("description") ?: return null
        val text = YTMusicTextUtils.extractRunsText(descNode)
        return text.takeIf { it.isNotBlank() }
    }

    private fun collectMusicItems(
        node: Any?,
        out: MutableList<Track> = mutableListOf(),
    ): List<Track> {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    
                    var videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
                    if (videoId.isNullOrBlank() || videoId == "null") {
                        videoId = r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                    }
                    if (videoId.isNullOrBlank() || videoId == "null") {
                        val nav = columns?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                        videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")
                    }

                    if (title != null && !videoId.isNullOrBlank() && videoId != "null") {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                            ?: subtitle.substringBefore("•").trim().ifBlank { "Desconhecido" }
                        out += Track(
                            id = videoId, title = title, artist = uploader,
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils
                                .parseDuration(YTMusicTextUtils.fixedText(r)),
                            source = source,
                        )
                    }
                }
                node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    val videoId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val title = r.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text")
                    if (!videoId.isNullOrBlank() && !title.isNullOrBlank()) {
                        val subtitle = YTMusicTextUtils.runsText(r.optJSONObject("subtitle"))
                        out += Track(
                            id = videoId, title = title,
                            artist = subtitle.substringBefore("•").trim()
                                .ifBlank { "Desconhecido" },
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                            durationSeconds = 0L,
                            source = Source.YOUTUBE,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) {
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

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000_000L -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}