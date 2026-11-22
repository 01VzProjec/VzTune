package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.data.innertube.InnerTubeYouTube
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses YouTube InnerTube search JSON (youtube.com).
 * Generic walker: collects renderers anywhere in the tree.
 */
object YouTubeSearchParser {

    fun parseVideos(json: JSONObject): List<SearchHit.VideoHit> {
        val out = mutableListOf<SearchHit.VideoHit>()
        val renderers = mutableListOf<JSONObject>()
        collect(json, "videoRenderer", renderers)
        collect(json, "gridVideoRenderer", renderers)
        for (r in renderers) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = textOf(r.optJSONObject("title")) ?: continue
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
            val duration = parseDuration(textOf(r.optJSONObject("lengthText")) ?: r.optString("lengthText"))
            val channel = textOf(r.optJSONObject("ownerText")) ?: textOf(r.optJSONObject("shortBylineText")) ?: ""
            val published = textOf(r.optJSONObject("publishedTimeText"))
            out += SearchHit.VideoHit(
                Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published)
            )
        }
        return out.distinctBy { it.track.id }
    }

    fun parsePlaylists(json: JSONObject): List<SearchHit.PlaylistHit> {
        val out = mutableListOf<SearchHit.PlaylistHit>()
        val renderers = mutableListOf<JSONObject>()
        collect(json, "playlistRenderer", renderers)
        collect(json, "gridPlaylistRenderer", renderers)
        for (r in renderers) {
            val pid = r.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            val title = textOf(r.optJSONObject("title")) ?: continue
            val owner = textOf(r.optJSONObject("shortBylineText")) ?: ""
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
            val countText = r.optString("videoCount").takeIf { it.isNotBlank() } ?: textOf(r.optJSONObject("videoCountText"))
            val count = countText?.filter { it.isDigit() }?.toIntOrNull()
            out += SearchHit.PlaylistHit(Playlist(id = pid, title = title, owner = owner.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE))
        }
        return out.distinctBy { it.playlist.id }
    }

    fun parseShorts(json: JSONObject): List<SearchHit.ShortHit> {
        val out = mutableListOf<SearchHit.ShortHit>()
        val reels = mutableListOf<JSONObject>()
        collect(json, "reelItemRenderer", reels)
        for (r in reels) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = textOf(r.optJSONObject("headline")) ?: textOf(r.optJSONObject("title")) ?: continue
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
            val channel = textOf(r.optJSONObject("shortBylineText")) ?: ""
            out += SearchHit.ShortHit(Track(id = videoId, title = title, artist = channel, durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE))
        }
        val lockups = mutableListOf<JSONObject>()
        collect(json, "shortsLockupViewModel", lockups)
        for (r in lockups) {
            val videoId = r.optString("contentId").takeIf { it.isNotBlank() } ?: r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = r.optString("title").takeIf { it.isNotBlank() } ?: textOf(r.optJSONObject("headline")) ?: continue
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
            out += SearchHit.ShortHit(Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE))
        }
        if (out.isNotEmpty()) return out.distinctBy { it.track.id }
        val videos = parseVideos(json)
        return videos.filter { it.track.durationSeconds in 1..65 }.map { SearchHit.ShortHit(it.track) }.distinctBy { it.track.id }
    }

    fun parseContinuation(json: JSONObject): String? {
        return InnerTubeYouTube.findContinuationToken(json)
    }

    fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) {
                    val v = node.opt(key)
                    if (v is JSONObject) out += v
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == key) continue
                    collect(node.opt(k), key, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collect(node.opt(i), key, out)
            }
        }
    }

    fun textOf(obj: JSONObject?): String? {
        if (obj == null) return null
        val simple = obj.optString("simpleText").takeIf { it.isNotBlank() && it != "null" }
        if (simple != null) return simple
        val runs = obj.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            sb.append(run.optString("text"))
        }
        val s = sb.toString().trim()
        return s.ifBlank { null }
    }

    fun thumbnailOf(obj: JSONObject?): String? {
        if (obj == null) return null
        val thumbs = obj.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
        }
        val nested = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        if (nested != null && nested.length() > 0) {
            return nested.optJSONObject(nested.length() - 1)?.optString("url")
        }
        return null
    }

    fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return 0L
        var total = 0L
        for (p in parts) total = total * 60 + p
        return total
    }
}
