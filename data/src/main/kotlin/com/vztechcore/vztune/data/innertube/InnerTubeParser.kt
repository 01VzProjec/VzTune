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

    private fun parseResponsiveListItem(r: JSONObject, out: MutableList<ParsedItem>) {
        val columns = r.optJSONArray("flexColumns") ?: return
        val title = flexText(columns, 0) ?: return
        val subtitleCol = columns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val subtitle = runsText(subtitleCol)
        val thumb = thumbnailOf(r.optJSONObject("thumbnail"))

        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
        if (!videoId.isNullOrBlank() && videoId != "null") {
            val uploader = extractArtistFromSubtitle(subtitleCol, subtitle) ?: "Desconhecido"
            val source = detectSource(videoId, r)
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

    private fun detectSource(videoId: String, renderer: JSONObject): Source {
        sourceCache[videoId]?.let { return it }

        findFirstMusicVideoType(renderer)?.let { type ->
            when {
                type == "MUSIC_VIDEO_TYPE_ATV" || type == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" ->
                    Source.YOUTUBE_MUSIC.also { sourceCache[videoId] = it }
                type.startsWith("MUSIC_VIDEO_TYPE_") ->
                    Source.YOUTUBE.also { sourceCache[videoId] = it }
                else -> Unit
            }?.let { return it }
        }

        detectSourceFromThumbnailUrl(thumbnailOf(renderer.optJSONObject("thumbnail")))?.let {
            sourceCache[videoId] = it
            return it
        }

        val result = Source.YOUTUBE_MUSIC
        sourceCache[videoId] = result
        return result
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
                val text = run.optString("text").trim().trim(' ', ',', '&', '·', '•', '-', '/', ';')
                if (text.isBlank()) continue
                if (text.matches(Regex("^\\d+:\\d+.*$"))) break
                val nav = run.optJSONObject("navigationEndpoint")
                val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                when {
                    browseId?.startsWith("MPREb_") == true -> break
                    browseId?.startsWith("VL") == true -> break
                }
                if (text.matches(Regex("^\\d{4}$"))) continue
                if (text.contains("views", true) || text.contains("visualizaç", true)) continue
                if (text.length > 80) continue
                if (text !in artists) artists += text
            }
            if (artists.isNotEmpty()) return artists.joinToString(", ")
        }
        if (subtitleText.contains("•")) {
            val before = subtitleText.substringBefore("•").trim().trim(',', '&', '·', '•', '-')
            if (before.isNotBlank()) return before
        }
        return subtitleText.trim().takeIf { it.isNotBlank() }
    }

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
            if (text.matches(Regex("^\\d+:\\d+.*$"))) return text
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
                if (w > bestW) { bestW = w; best = url }
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
            if (w >= bestW) { bestW = w; best = url }
        }
        return best ?: thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
    }

    private fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return try {
            text.split(":").map { it.trim().toLong() }
                .reversed()
                .foldIndexed(0L) { idx, acc, v ->
                    acc + v * when (idx) { 0 -> 1L; 1 -> 60L; else -> 3600L }
                }
        } catch (e: Exception) { 0L }
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