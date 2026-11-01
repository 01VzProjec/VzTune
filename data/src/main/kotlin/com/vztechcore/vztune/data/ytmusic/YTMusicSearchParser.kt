package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import org.json.JSONArray
import org.json.JSONObject

internal object YTMusicSearchParser {

    sealed interface ParsedItem {
        val stableKey: String

        data class Song(
            val videoId: String,
            val title: String,
            val artist: String,
            val thumbnailUrl: String?,
            val durationSeconds: Long,
            val source: Source,
        ) : ParsedItem {
            override val stableKey: String get() = "song_${source.name}_$videoId"
        }

        data class Album(
            val browseId: String,
            val title: String,
            val artist: String,
            val thumbnailUrl: String?,
        ) : ParsedItem {
            override val stableKey: String get() = "album_$browseId"
        }

        data class Artist(
            val browseId: String,
            val name: String,
            val thumbnailUrl: String?,
        ) : ParsedItem {
            override val stableKey: String get() = "artist_$browseId"
        }

        data class Playlist(
            val browseId: String,
            val title: String,
            val owner: String,
            val thumbnailUrl: String?,
        ) : ParsedItem {
            override val stableKey: String get() = "playlist_$browseId"
        }
    }

    // ================= PUBLIC ENTRY =================

    fun parseSearchResults(json: JSONObject): List<ParsedItem> {
        val out = mutableListOf<ParsedItem>()
        YTMusicTextUtils.walk(json) { renderer ->
            renderer.optJSONObject("musicResponsiveListItemRenderer")?.let {
                parseResponsiveListItem(it, out)
            }
            renderer.optJSONObject("musicTwoRowItemRenderer")?.let {
                parseTwoRowItem(it, out)
            }
        }
        return out.distinctBy { it.stableKey }
    }

    fun parseSuggestions(json: JSONObject): List<String> {
        val out = mutableListOf<String>()
        YTMusicTextUtils.walk(json) { node ->
            val s = node.optJSONObject("searchSuggestionRenderer") ?: return@walk
            val text = s.optJSONObject("suggestion")?.optJSONArray("runs")
                ?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
            if (text != null && text !in out) out += text
        }
        return out
    }

    fun toHit(item: ParsedItem, filter: SearchFilter): SearchHit? {
        return when (item) {
            is ParsedItem.Song -> {
                if (item.source == Source.YOUTUBE) return null
                if (filter != SearchFilter.ALL && filter != SearchFilter.SONGS) return null
                SearchHit.SongHit(
                    Track(
                        id = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        durationSeconds = item.durationSeconds,
                        thumbnailUrl = item.thumbnailUrl,
                        source = item.source,
                    )
                )
            }
            is ParsedItem.Album -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.ALBUMS) return null
                SearchHit.AlbumHit(
                    Album(
                        id = item.browseId,
                        title = item.title,
                        artist = item.artist,
                        thumbnailUrl = item.thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
            is ParsedItem.Artist -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.ARTISTS) return null
                SearchHit.ArtistHit(
                    Artist(
                        id = item.browseId,
                        name = item.name,
                        thumbnailUrl = item.thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
            is ParsedItem.Playlist -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.PLAYLISTS) return null
                SearchHit.PlaylistHit(
                    Playlist(
                        id = item.browseId,
                        title = item.title,
                        owner = item.owner,
                        thumbnailUrl = item.thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
        }
    }

    // ================= SOURCE DETECTION =================

    fun detectSource(videoId: String, renderer: JSONObject): Source {
        YTMusicCaches.sourceCache[videoId]?.let { return it }

        val type = findFirstMusicVideoType(renderer)
        if (type != null) {
            val resolved: Source? = when {
                type == "MUSIC_VIDEO_TYPE_ATV" ||
                    type == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" -> Source.YOUTUBE_MUSIC
                type.startsWith("MUSIC_VIDEO_TYPE_") -> Source.YOUTUBE
                else -> null
            }
            if (resolved != null) {
                YTMusicCaches.sourceCache[videoId] = resolved
                return resolved
            }
        }

        detectSourceFromThumbnailUrl(
            YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnail"))
        )?.let {
            YTMusicCaches.sourceCache[videoId] = it
            return it
        }

        val fallback = Source.YOUTUBE_MUSIC
        YTMusicCaches.sourceCache[videoId] = fallback
        return fallback
    }

    fun detectSourceFromThumbnailUrl(url: String?): Source? {
        if (url.isNullOrBlank()) return null
        if (url.contains("ytimg.com")) return Source.YOUTUBE
        if (url.contains("googleusercontent.com")) {
            val regex = Regex("=w(\\d+)-h(\\d+)")
            val match = regex.find(url) ?: return Source.YOUTUBE_MUSIC
            val w = match.groupValues[1].toIntOrNull() ?: return null
            val h = match.groupValues[2].toIntOrNull() ?: return null
            return when {
                w == h -> Source.YOUTUBE_MUSIC
                w > h -> Source.YOUTUBE
                else -> null
            }
        }
        return null
    }

    fun findFirstMusicVideoType(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.optString("musicVideoType")
                if (direct.startsWith("MUSIC_VIDEO_TYPE_")) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    findFirstMusicVideoType(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                findFirstMusicVideoType(node.opt(i))?.let { return it }
            }
        }
        return null
    }

    // ================= RESPONSIVE ITEM =================

    private fun parseResponsiveListItem(r: JSONObject, out: MutableList<ParsedItem>) {
        val columns = r.optJSONArray("flexColumns") ?: return
        val title = YTMusicTextUtils.flexText(columns, 0) ?: return
        val subtitleCol = columns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
        val thumb = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail"))

        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
        if (!videoId.isNullOrBlank() && videoId != "null") {
            val source = detectSource(videoId, r)
            val uploader = when (source) {
                Source.YOUTUBE -> extractChannelName(subtitleCol) ?: "Unknown"
                else -> extractArtistFromSubtitle(subtitleCol, subtitle) ?: "Unknown"
            }
            out += ParsedItem.Song(
                videoId = videoId,
                title = title,
                artist = uploader,
                thumbnailUrl = thumb,
                durationSeconds = YTMusicTextUtils.parseDuration(
                    YTMusicTextUtils.fixedText(r)
                ),
                source = source,
            )
            return
        }

        val browseId = extractBrowseId(r) ?: return
        val author = subtitle.substringBefore("•").trim()
        when {
            browseId.startsWith("VL") -> out += ParsedItem.Playlist(
                browseId, title, author.ifBlank { "Playlist" }, thumb
            )
            browseId.startsWith("MPREb_") -> out += ParsedItem.Album(
                browseId, title, author, thumb
            )
            browseId.startsWith("UC") -> out += ParsedItem.Artist(
                browseId, title, thumb
            )
        }
    }

    private fun parseTwoRowItem(r: JSONObject, out: MutableList<ParsedItem>) {
        val titleRuns = r.optJSONObject("title")?.optJSONArray("runs")
        val first = titleRuns?.optJSONObject(0) ?: return
        val title = first.optString("text").takeIf { it.isNotBlank() } ?: return
        val browseId = first.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: extractBrowseId(r) ?: return
        val subtitle = YTMusicTextUtils.runsText(r.optJSONObject("subtitle"))
        val thumb = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnailRenderer"))
        val author = subtitle.substringBefore("•").trim()

        when {
            browseId.startsWith("MPREb_") -> out += ParsedItem.Album(
                browseId, title, author, thumb
            )
            browseId.startsWith("VL") -> out += ParsedItem.Playlist(
                browseId, title, author.ifBlank { "Playlist" }, thumb
            )
            browseId.startsWith("UC") -> out += ParsedItem.Artist(
                browseId, title, thumb
            )
        }
    }

    // ================= ARTIST / CHANNEL =================

    /**
     * Extracts the artist list.
     *
     * First pass: iterate runs, break on duration/album/playlist, skip
     * type words, dates, years, view counts.
     *
     * Fallback (no runs): split the raw subtitle by "•" and apply the SAME
     * filters to each segment. This is what fixes the
     *   "Música • 3:45"
     * case that used to leak through as an artist name — now all segments
     * get filtered and null is returned, so the enricher kicks in.
     */
    private fun extractArtistFromSubtitle(
        subtitleCol: JSONObject?,
        subtitleText: String,
    ): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs")
            ?: subtitleCol?.optJSONArray("runs")

        if (runs != null) {
            val artists = mutableListOf<String>()
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                    .trim(' ', ',', '&', '·', '•', '-', '/', ';', '\n', '\t')
                if (text.isBlank() || text == "•" || text == "·") continue
                if (YTMusicTextUtils.isDuration(text)) break

                val nav = run.optJSONObject("navigationEndpoint")
                val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                when {
                    browseId?.startsWith("MPREb_") == true -> break
                    browseId?.startsWith("VL") == true -> break
                }

                if (YTMusicTextUtils.isTypeWord(text)) continue
                if (YTMusicTextUtils.isYear(text)) continue
                if (YTMusicTextUtils.isDate(text)) continue
                if (YTMusicTextUtils.isEpisode(text)) continue
                if (YTMusicTextUtils.isViewCount(text)) continue
                if (text.length > 80) continue

                if (text !in artists) artists += text
            }
            if (artists.isNotEmpty()) return artists.joinToString(", ")
        }

        // Fallback: split by "•" and filter each segment.
        val segments = subtitleText
            .split("•")
            .map { it.trim().trim(',', '&', '·', '•', '-', '/', ';', ' ') }
            .filter { it.isNotBlank() }

        val valid = segments.filter { seg ->
            !YTMusicTextUtils.isTypeWord(seg) &&
                !YTMusicTextUtils.isDuration(seg) &&
                !YTMusicTextUtils.isYear(seg) &&
                !YTMusicTextUtils.isDate(seg) &&
                !YTMusicTextUtils.isEpisode(seg) &&
                !YTMusicTextUtils.isViewCount(seg) &&
                seg.length <= 80
        }

        return valid.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun extractChannelName(subtitleCol: JSONObject?): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs")
            ?: subtitleCol?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                if (text.isBlank() || text == "•" || text == "·") continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                if (browseId?.startsWith("UC") == true) return text
            }
        }

        val full = YTMusicTextUtils.runsText(subtitleCol)
        val segments = full.split("•").map { it.trim() }.filter { it.isNotBlank() }
        return segments.firstOrNull {
            it.length < 80 && !YTMusicTextUtils.isTypeWord(it)
        }
    }

    private fun extractBrowseId(r: JSONObject): String? {
        val nav = r.optJSONObject("navigationEndpoint")
        nav?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return null
    }
}