package com.vztechcore.vztune.data.innertube

import com.vztechcore.vztune.core.domain.music.Source
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object InnerTubeParser {

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

    private val sourceCache = ConcurrentHashMap<String, Source>()

    // Words that indicate the segment is a metadata type, not an artist.
    private val TYPE_KEYWORDS = setOf(
        "song", "songs",
        "música", "músicas", "musica", "musicas",
        "video", "vídeo", "videos", "vídeos",
        "episódio", "episodio", "episode", "episodes",
        "single", "singles",
        "album", "álbum", "albums", "álbuns",
        "playlist", "playlists",
    )

    private val DURATION_REGEX = Regex("^\\d+:\\d+.*$")
    private val YEAR_REGEX = Regex("^\\d{4}$")

    fun parseSearchResults(json: JSONObject): List<ParsedItem> {
        val out = mutableListOf<ParsedItem>()
        walkSearch(json) { renderer ->
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
        walkSearch(json) { node ->
            val s = node.optJSONObject("searchSuggestionRenderer") ?: return@walkSearch
            val text = s.optJSONObject("suggestion")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
            if (text != null && text !in out) out += text
        }
        return out
    }

    // ================= RESPONSIVE ITEM =================

    private fun parseResponsiveListItem(r: JSONObject, out: MutableList<ParsedItem>) {
        val columns = r.optJSONArray("flexColumns") ?: return
        val title = flexText(columns, 0) ?: return
        val subtitleCol = columns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val subtitle = runsText(subtitleCol)
        val thumb = thumbnailOf(r.optJSONObject("thumbnail"))

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
                durationSeconds = parseDuration(fixedText(r)),
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
        val subtitle = runsText(r.optJSONObject("subtitle"))
        val thumb = thumbnailOf(r.optJSONObject("thumbnailRenderer"))
        val author = subtitle.substringBefore("•").trim()

        when {
            browseId.startsWith("MPREb_") -> out += ParsedItem.Album(browseId, title, author, thumb)
            browseId.startsWith("VL") -> out += ParsedItem.Playlist(browseId, title, author.ifBlank { "Playlist" }, thumb)
            browseId.startsWith("UC") -> out += ParsedItem.Artist(browseId, title, thumb)
        }
    }

    // ================= SOURCE DETECTION =================

    private fun detectSource(videoId: String, renderer: JSONObject): Source {
        sourceCache[videoId]?.let { return it }

        val type = findFirstMusicVideoType(renderer)
        if (type != null) {
            val resolved: Source? = when {
                type == "MUSIC_VIDEO_TYPE_ATV" ||
                    type == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" -> Source.YOUTUBE_MUSIC
                type.startsWith("MUSIC_VIDEO_TYPE_") -> Source.YOUTUBE
                else -> null
            }
            if (resolved != null) {
                sourceCache[videoId] = resolved
                return resolved
            }
        }

        detectSourceFromThumbnailUrl(thumbnailOf(renderer.optJSONObject("thumbnail")))?.let {
            sourceCache[videoId] = it
            return it
        }

        val fallback = Source.YOUTUBE_MUSIC
        sourceCache[videoId] = fallback
        return fallback
    }

    private fun detectSourceFromThumbnailUrl(url: String?): Source? {
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

    private fun findFirstMusicVideoType(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.optString("musicVideoType")
                if (direct.startsWith("MUSIC_VIDEO_TYPE_")) return direct
                val keys = node.keys()
                while (keys.hasNext()) findFirstMusicVideoType(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findFirstMusicVideoType(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun extractBrowseId(r: JSONObject): String? {
        val nav = r.optJSONObject("navigationEndpoint")
        nav?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
            ?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return null
    }

    // ================= ARTIST / CHANNEL EXTRACTION =================

    /**
     * Extract the artist list for a YOUTUBE_MUSIC hit.
     * Skips type words (Song / Música / Video / Episódio / etc.),
     * connectors, years, view counts, durations, and overly long text.
     * Returns null if no valid artist segment was found.
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
                val raw = run.optString("text")
                val text = raw.trim().trim(' ', ',', '&', '·', '•', '-', '/', ';', '\n', '\t')
                if (text.isBlank()) continue
                if (text == "•" || text == "·") continue
                if (DURATION_REGEX.matches(text)) break

                val nav = run.optJSONObject("navigationEndpoint")
                val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                when {
                    browseId?.startsWith("MPREb_") == true -> break
                    browseId?.startsWith("VL") == true -> break
                }

                if (text.lowercase() in TYPE_KEYWORDS) continue
                if (YEAR_REGEX.matches(text)) continue
                if (text.contains("views", true) ||
                    text.contains("visualizaç", true) ||
                    text.contains("reproduç", true)) continue
                if (text.length > 80) continue

                if (text !in artists) artists += text
            }
            if (artists.isNotEmpty()) {
                return artists.joinToString(" & ")
                    .replace(Regex(",\\s*,"), ",")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }

        // Fallback: parse the plain subtitle string
        val segments = subtitleText
            .split("•")
            .map { it.trim().trim(',', '&', '·', '•', '-', '/', ';', ' ') }
            .filter { it.isNotBlank() }
            .filter { it.lowercase() !in TYPE_KEYWORDS }
            .filter { !DURATION_REGEX.matches(it) }
            .filter { !YEAR_REGEX.matches(it) }
            .filter { !it.contains("views", true) }
            .filter { !it.contains("visualizaç", true) }

        if (segments.isEmpty()) return null
        return segments.joinToString(" & ")
    }

    /**
     * For YOUTUBE (video) hits: only the channel name.
     * Prefers the run whose browseId starts with "UC" (a real channel).
     */
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

        val full = subtitleCol?.let { runsText(it) }.orEmpty()
        val metaRegex = Regex(
            "^(" +
                "\\d{4}" +
                "|\\d{1,2} de \\w+\\.? de \\d{4}" +
                "|T\\d+E\\d+" +
                "|Episódio \\d+" +
                "|Temporada \\d+" +
                "|\\d[\\d.,]* (views|visualizaç|reproduç).*" +
                ")$",
            RegexOption.IGNORE_CASE,
        )
        val segments = full.split("•").map { it.trim() }.filter { it.isNotBlank() }
        return segments.firstOrNull { !metaRegex.matches(it) && it.length < 80 }
    }

    // ================= COLUMN / TEXT HELPERS =================

    private fun flexText(columns: JSONArray, index: Int): String? {
        val col = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: return null
        return runsText(col).takeIf { it.isNotBlank() }
    }

    private fun fixedText(r: JSONObject): String? {
        val fixed = r.optJSONArray("fixedColumns") ?: return null
        for (i in 0 until fixed.length()) {
            val col = fixed.optJSONObject(i) ?: continue
            val text = runsText(
                col.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?: col.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            )
            if (DURATION_REGEX.matches(text)) return text
        }
        return null
    }

    private fun runsText(container: JSONObject?): String {
        val runs = container?.optJSONObject("text")?.optJSONArray("runs")
            ?: container?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
        }
        return sb.toString()
    }

    // ================= THUMBNAILS =================

    private fun thumbnailOf(root: JSONObject?): String? {
        if (root == null) return null
        root.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { pickLargest(it) }?.let { return it }
        root.optJSONObject("croppedSquareThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { pickLargest(it) }?.let { return it }
        return findBestCoverUrl(root)
    }

    private fun findBestCoverUrl(node: Any?): String? {
        var best: String? = null
        var bestW = 0
        walkSearch(node) { obj ->
            val thumbs = obj.optJSONArray("thumbnails") ?: return@walkSearch
            for (i in 0 until thumbs.length()) {
                val t = thumbs.optJSONObject(i) ?: continue
                val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
                val w = t.optInt("width", 0)
                if (w > bestW) {
                    bestW = w
                    best = url
                }
            }
        }
        return best
    }

    private fun pickLargest(thumbs: JSONArray): String? {
        if (thumbs.length() == 0) return null
        var best: String? = null
        var bestW = 0
        for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
            val w = t.optInt("width", 0)
            if (w >= bestW) {
                bestW = w
                best = url
            }
        }
        return best ?: thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
    }

    // ================= MISC =================

    private fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return try {
            text.split(":").map { it.trim().toLong() }
                .reversed()
                .foldIndexed(0L) { idx, acc, v ->
                    acc + v * when (idx) {
                        0 -> 1L
                        1 -> 60L
                        else -> 3600L
                    }
                }
        } catch (e: Exception) {
            0L
        }
    }

    private fun walkSearch(node: Any?, block: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                block(node)
                val keys = node.keys()
                while (keys.hasNext()) walkSearch(node.opt(keys.next()), block)
            }
            is JSONArray -> for (i in 0 until node.length()) walkSearch(node.opt(i), block)
        }
    }
}