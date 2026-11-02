package com.vztechcore.vztune.data.ytmusic

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

    data class PlaylistResult(
        val coverUrl: String?,
        val owner: String?,
        val title: String?,
        val description: String?,
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
        val hits = parsed.mapNotNull { YTMusicSearchParser.toHit(it, filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()
            val json = runCatching { InnerTube.searchSuggestions(query) }.getOrNull()
                ?: return@withContext SearchSuggestions()
            val queries = runCatching { YTMusicSearchParser.parseSuggestions(json) }
                .getOrDefault(emptyList())
            SearchSuggestions(queries = queries)
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

        // collectMusicItems agora filtra YOUTUBE e pega apenas tracks do álbum
        val rawTracks = collectMusicItems(json).distinctBy { it.id }
        val albumTracks = rawTracks.filter { it.source == Source.YOUTUBE_MUSIC }

        val finalTracks = enricher.enrichTracks(albumTracks, artist)

        AlbumResult(cover, artist, title, desc, finalTracks)
    }

    suspend fun browsePlaylist(playlistId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val json = runCatching { InnerTube.browse(playlistId) }.getOrNull()
            ?: return@withContext PlaylistResult(null, null, null, null, emptyList())

        val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")

        val cover = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
        val title = header?.let { YTMusicArtistParser.extractHeaderTitle(it) }
        val owner = header?.let { extractHeaderArtist(it) }
        val desc = extractDescription(json)

        val rawTracks = collectMusicItems(json).distinctBy { it.id }
        val finalTracks = enricher.enrichTracks(rawTracks, null)

        PlaylistResult(cover, owner, title, desc, finalTracks)
    }

    // ================= HELPERS =================

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
            ?: return null
        val descNode = shelf.optJSONObject("description") ?: return null
        val text = YTMusicTextUtils.extractRunsText(descNode)
        return text.takeIf { it.isNotBlank() }
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

    private fun collectMusicItems(node: Any?, out: MutableList<Track> = mutableListOf()): List<Track> {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
                    if (title != null && !videoId.isNullOrBlank() && videoId != "null") {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicTextUtils.runsText(subtitleCol)
                            .substringBefore("•")
                            .trim()
                            .ifBlank { "Desconhecido" }
                        out += Track(
                            id = videoId, title = title, artist = uploader,
                            thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(r)),
                            source = source,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is org.json.JSONArray -> for (i in 0 until node.length()) collectMusicItems(node.opt(i), out)
        }
        return out
    }
}