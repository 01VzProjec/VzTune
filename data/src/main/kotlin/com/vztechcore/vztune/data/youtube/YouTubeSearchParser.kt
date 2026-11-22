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
        // Layout antigo: playlistRenderer / gridPlaylistRenderer (ainda usado em algumas abas)
        val renderers = mutableListOf<JSONObject>()
        collect(json, "playlistRenderer", renderers)
        collect(json, "gridPlaylistRenderer", renderers)
        for (r in renderers) {
            val pid = r.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            val title = textOf(r.optJSONObject("title")) ?: continue
            val owner = textOf(r.optJSONObject("shortBylineText"))
                ?: textOf(r.optJSONObject("longBylineText"))
                ?: textOf(r.optJSONObject("ownerText")) ?: ""
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
            val countText = r.optString("videoCount").takeIf { it.isNotBlank() } ?: textOf(r.optJSONObject("videoCountText"))
            val count = countText?.filter { it.isDigit() }?.toIntOrNull()
            out += SearchHit.PlaylistHit(Playlist(id = pid, title = title, owner = owner.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE))
        }
        // Layout 2026: lockupViewModel com LOCKUP_CONTENT_TYPE_PLAYLIST / PODCAST
        // Ex: contentId=PL..., title=metadata.lockupMetadataViewModel.title.content,
        // dono=metadataRows -> parte com tap pra UC..., thumb=sources[] maior width, count=badge "11 videos"
        val lockups = mutableListOf<JSONObject>()
        collect(json, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue
            val pid = lockupPlaylistId(lvm) ?: continue
            if (!pid.startsWith("PL") && !pid.startsWith("UU") && !pid.startsWith("OL") && !pid.startsWith("RD") && !pid.startsWith("FL")) continue
            val title = lockupTitle(lvm) ?: continue
            val owner = lockupOwner(lvm)
            val thumb = lockupThumbnail(lvm)
            val count = lockupCount(lvm)
            out += SearchHit.PlaylistHit(Playlist(id = pid, title = title, owner = owner?.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE))
        }
        return out.distinctBy { it.playlist.id }
    }

    fun lockupPlaylistId(lvm: JSONObject): String? {
        // FIX PLAYLIST CANAL - aba Playlists usa browseEndpoint (VLPL...), nao watchEndpoint.
        // Busca usa watchEndpoint (PL...). Tenta os dois + contentId, normaliza VL.
        fun normalize(raw: String?): String? {
            var v = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (v.startsWith("VL")) v = v.removePrefix("VL")
            if (v.startsWith("PL") || v.startsWith("UU") || v.startsWith("OL") || v.startsWith("RD") || v.startsWith("FL")) return v
            return null
        }
        val cmd = lvm.optJSONObject("rendererContext")?.optJSONObject("commandContext")?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
        normalize(cmd?.optJSONObject("watchEndpoint")?.optString("playlistId"))?.let { return it }
        normalize(cmd?.optJSONObject("browseEndpoint")?.optString("browseId"))?.let { return it }
        normalize(cmd?.optJSONObject("browseEndpoint")?.optString("playlistId"))?.let { return it }
        var found: String? = null
        fun walk(n: Any?) {
            if (found != null) return
            when (n) {
                is JSONObject -> {
                    val browse = n.optJSONObject("browseEndpoint")
                    if (browse != null) {
                        normalize(browse.optString("browseId"))?.let { found = it; return }
                        normalize(browse.optString("playlistId"))?.let { found = it; return }
                    }
                    val watch = n.optJSONObject("watchEndpoint")
                    if (watch != null) {
                        normalize(watch.optString("playlistId"))?.let { found = it; return }
                    }
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(lvm)
        if (found != null) return found
        return normalize(lvm.optString("contentId"))
    }

    fun lockupTitle(lvm: JSONObject): String? {
        val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val titleObj = meta.optJSONObject("title") ?: return null
        return titleObj.optString("content").takeIf { it.isNotBlank() } ?: textOf(titleObj)
    }

    fun lockupOwner(lvm: JSONObject): String? {
        // FIX OWNER LOCKUP - metadataRows pode estar em 3 lugares, runs pode ser commandRuns OU runs com navigationEndpoint.
        val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val rowSets = mutableListOf<JSONArray>()
        meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rowSets += it }
        meta.optJSONObject("metadata")?.optJSONArray("metadataRows")?.let { rowSets += it }
        meta.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rowSets += it }
        meta.optJSONArray("metadataRows")?.let { rowSets += it }
        fun walkRows(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val arr = n.optJSONArray("metadataRows")
                    if (arr != null && !rowSets.contains(arr)) rowSets += arr
                    val keys = n.keys()
                    while (keys.hasNext()) walkRows(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walkRows(n.opt(i))
            }
        }
        walkRows(meta)
        var fallback: String? = null
        for (rows in rowSets) {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val parts = row.optJSONArray("metadataParts") ?: continue
                for (j in 0 until parts.length()) {
                    val part = parts.optJSONObject(j) ?: continue
                    val textObj = part.optJSONObject("text") ?: continue
                    val name = textObj.optString("content").takeIf { it.isNotBlank() } ?: textOf(textObj) ?: continue
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) continue
                    if (isLockupTypeWord(trimmed)) continue
                    if (trimmed.contains("View full", ignoreCase = true)) continue
                    if (trimmed.contains("Ver playlist", ignoreCase = true)) continue
                    if (trimmed.contains("video", ignoreCase = true) || trimmed.contains("episode", ignoreCase = true)) continue
                    var isChannel = false
                    val cmdRuns = textObj.optJSONArray("commandRuns")
                    if (cmdRuns != null) {
                        for (k in 0 until cmdRuns.length()) {
                            val run = cmdRuns.optJSONObject(k) ?: continue
                            val b1 = run.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("browseEndpoint")?.optString("browseId")
                            val b2 = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                            if (b1?.startsWith("UC") == true || b2?.startsWith("UC") == true) isChannel = true
                        }
                    }
                    val runs = textObj.optJSONArray("runs")
                    if (runs != null) {
                        for (k in 0 until runs.length()) {
                            val run = runs.optJSONObject(k) ?: continue
                            val b = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                            if (b?.startsWith("UC") == true) isChannel = true
                        }
                    }
                    if (isChannel) return trimmed
                    if (fallback == null) fallback = trimmed
                }
            }
        }
        return fallback
    }

    fun lockupThumbnail(lvm: JSONObject): String? {
        var bestUrl: String? = null
        var bestWidth = -1
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val arr = node.optJSONArray("sources")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            val url = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                            val w = s.optInt("width", 0)
                            if (w >= bestWidth) { bestWidth = w; bestUrl = url }
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(lvm.optJSONObject("contentImage"))
        if (bestUrl != null) return bestUrl
        return thumbnailOf(lvm.optJSONObject("contentImage")) ?: thumbnailOf(lvm.optJSONObject("thumbnail"))
    }

    fun lockupCount(lvm: JSONObject): Int? {
        var found: Int? = null
        fun walk(node: Any?) {
            if (found != null) return
            when (node) {
                is JSONObject -> {
                    val badge = node.optJSONObject("thumbnailBadgeViewModel")
                    if (badge != null) {
                        val txt = badge.optString("text").takeIf { it.isNotBlank() } ?: textOf(badge.optJSONObject("text"))
                        if (txt != null && (txt.contains("video", ignoreCase = true) || txt.contains("episode", ignoreCase = true) || txt.contains("vídeo", ignoreCase = true))) {
                            found = txt.filter { it.isDigit() }.toIntOrNull()
                            if (found != null) return
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(lvm.optJSONObject("contentImage"))
        if (found != null) return found
        walk(lvm.optJSONObject("metadata"))
        return found
    }

    private fun isLockupTypeWord(text: String): Boolean {
        val low = text.trim().lowercase()
        return low in setOf("playlist", "podcast", "mix", "video", "videos", "episode", "episodes", "vídeo", "vídeos", "álbum", "album", "música", "music")
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
