package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.innertube.InnerTubeYouTube
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses YouTube InnerTube browse VL... (playlist) JSON.
 * Collects playlistVideoRenderer anywhere in tree.
 */
object YouTubePlaylistParser {

    data class PlaylistData(
        val title: String?,
        val owner: String?,
        val ownerChannelId: String?,
        val coverUrl: String?,
        val description: String?,
        val tracks: List<Track>,
        val continuation: String?,
    )

    fun parsePlaylistPage(json: JSONObject, ownerFallback: String = ""): PlaylistData {
        val title = parseHeaderTitle(json)
        val owner = parseHeaderOwner(json) ?: ownerFallback.ifBlank { null }
        val ownerId = parseHeaderOwnerId(json)
        val desc = parseHeaderDescription(json)
        val tracks = collectTracks(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        val cover = tracks.firstOrNull()?.thumbnailUrl
        return PlaylistData(title, owner, ownerId, cover, desc, tracks.distinctBy { it.id }, cont)
    }

    fun parsePlaylistContinuation(json: JSONObject): Pair<List<Track>, String?> {
        val tracks = collectTracks(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return tracks.distinctBy { it.id } to cont
    }

        private fun collectTracks(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val renderers = mutableListOf<JSONObject>()
        collect(node, "playlistVideoRenderer", renderers)
        for (r in renderers) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            if (title.contains("Deleted video") || title.contains("Vídeo excluído")) continue
            val channel = YouTubeSearchParser.textOf(r.optJSONObject("shortBylineText")) ?: ""
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            val duration = YouTubeSearchParser.parseDuration(
                YouTubeSearchParser.textOf(r.optJSONObject("lengthText")) ?: r.optString("lengthText")
            )
            out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        // Layout 2026: lockupViewModel VIDEO dentro da playlist (mesmo formato da aba Videos)
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type != "LOCKUP_CONTENT_TYPE_VIDEO" && type != "LOCKUP_CONTENT_TYPE_STREAM" && type != "") continue
            val videoId = lvm.optString("contentId").takeIf { it.isNotBlank() } ?: continue
            val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val titleObj = meta?.optJSONObject("title")
            val title = titleObj?.optString("content")?.takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(titleObj) ?: continue
            if (title.contains("Deleted video") || title.contains("Vídeo excluído")) continue
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)
            var duration = 0L
            var channel = ""
            // metadataRows: primeira parte que nao e views/ago e o canal; badge com ":" e a duracao
            val rows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                    for (j in 0 until parts.length()) {
                        val txt = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() } ?: continue
                        val low = txt.lowercase()
                        if (low.contains("view") || low.contains("visualiza") || low.contains("ago") || low.contains("há")) continue
                        if (channel.isBlank()) channel = txt.trim()
                    }
                }
            }
            // Duracao via badge
            fun walk(n: Any?) {
                if (duration != 0L) return
                when (n) {
                    is JSONObject -> {
                        val badge = n.optJSONObject("thumbnailBadgeViewModel")
                        if (badge != null) {
                            val txt = badge.optString("text").takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(badge.optJSONObject("text"))
                            if (txt != null && txt.contains(":")) { duration = YouTubeSearchParser.parseDuration(txt); return }
                        }
                        val keys = n.keys()
                        while (keys.hasNext()) walk(n.opt(keys.next()))
                    }
                    is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
                }
            }
            walk(lvm.optJSONObject("contentImage"))
            out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        // UUSH / Shorts via UU: shortsLockupViewModel (id em onTap.reelWatchEndpoint.videoId, titulo em overlayMetadata.primaryText.content)
        // Sem isso, UUSH vinha 0 e shorts do canal ficava incompleto.
        val slvms = mutableListOf<JSONObject>()
        collect(node, "shortsLockupViewModel", slvms)
        for (r in slvms) {
            val videoId = r.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("reelWatchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
                ?: r.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.isNotBlank() }
                ?: r.optString("contentId").takeIf { it.isNotBlank() } ?: continue
            val title = r.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content")?.takeIf { it.isNotBlank() }
                ?: r.optString("title").takeIf { it.isNotBlank() } ?: continue
            // Thumb: thumbnail.sources maior width (best effort via walk)
            var thumb: String? = null
            var bestW = -1
            fun walkThumb(n: Any?) {
                when (n) {
                    is JSONObject -> {
                        val arr = n.optJSONArray("sources")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val s = arr.optJSONObject(i) ?: continue
                                val url = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                                val w = s.optInt("width", 0)
                                if (w >= bestW) { bestW = w; thumb = url }
                            }
                        }
                        val keys = n.keys()
                        while (keys.hasNext()) walkThumb(n.opt(keys.next()))
                    }
                    is JSONArray -> for (i in 0 until n.length()) walkThumb(n.opt(i))
                }
            }
            walkThumb(r.optJSONObject("thumbnail"))
            if (thumb == null) thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        return out
    }

    private fun parseHeaderTitle(json: JSONObject): String? {
        val headers = mutableListOf<JSONObject>()
        collect(json, "playlistHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        val h = headers.firstOrNull() ?: return null
        return YouTubeSearchParser.textOf(h.optJSONObject("title"))
    }

    private fun parseHeaderOwner(json: JSONObject): String? {
        // FIX OWNER PLAYLIST DETALHE - 2026 o header e pageHeaderRenderer, nao playlistHeaderRenderer.
        val headers = mutableListOf<JSONObject>()
        collect(json, "playlistHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        for (h in headers) {
            YouTubeSearchParser.textOf(h.optJSONObject("ownerText"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }
            YouTubeSearchParser.textOf(h.optJSONObject("byline"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }
            YouTubeSearchParser.textOf(h.optJSONObject("shortBylineText"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }
            YouTubeSearchParser.textOf(h.optJSONObject("straplineTextOne"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }
            val rows = h.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                ?: h.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                ?: h.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                    for (j in 0 until parts.length()) {
                        val txtObj = parts.optJSONObject(j)?.optJSONObject("text") ?: continue
                        val txt = txtObj.optString("content").takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(txtObj) ?: continue
                        val clean = txt.trim()
                        if (clean.isBlank() || isHeaderMetaWord(clean)) continue
                        return clean
                    }
                }
            }
            val sub = YouTubeSearchParser.textOf(h.optJSONObject("subtitle")) ?: YouTubeSearchParser.textOf(h.optJSONObject("straplineTextOne"))
            if (!sub.isNullOrBlank()) {
                val parts = sub.split("\u2022", "\u00b7", "|").map { it.trim() }.filter { it.isNotBlank() && !isHeaderMetaWord(it) }
                if (parts.isNotEmpty()) return parts.first()
            }
        }
        return null
    }

    private fun isHeaderMetaWord(text: String): Boolean {
        val low = text.trim().lowercase()
        if (low in setOf("playlist", "podcast", "mix", "\u00e1lbum", "album", "video", "v\u00eddeo", "v\u00eddeos", "videos")) return true
        if (low.contains("video") || low.contains("v\u00eddeo") || low.contains("episode") || low.contains("epis\u00f3dio")) return true
        if (low.contains("view") || low.contains("visualiza") || low.contains("exibi")) return true
        if (low.contains("song") || low.contains("faixa") || low.contains("m\u00fasica") || low.contains("musica")) return true
        if (low.matches(Regex("^\\d+\\s*(songs|faixas|videos|v\u00eddeos|views|visualiza\u00e7\u00f5es).*"))) return true
        return false
    }

    private fun parseHeaderOwnerId(json: JSONObject): String? {
        // FIX OWNER PLAYLIST DETALHE - UC pode estar em pageHeaderRenderer.contentMetadataViewModel.
        val headers = mutableListOf<JSONObject>()
        collect(json, "playlistHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        for (h in headers) {
            val runs = h.optJSONObject("ownerText")?.optJSONArray("runs")
            if (runs != null) {
                for (i in 0 until runs.length()) {
                    val cid = runs.optJSONObject(i)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (!cid.isNullOrBlank() && cid.startsWith("UC")) return cid
                }
            }
            val nav = h.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint") ?: h.optJSONObject("ownerEndpoint")
            val cid0 = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
            if (!cid0.isNullOrBlank() && cid0.startsWith("UC")) return cid0
            val rows = h.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows") ?: h.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                    for (j in 0 until parts.length()) {
                        val txtObj = parts.optJSONObject(j)?.optJSONObject("text") ?: continue
                        val cmdRuns = txtObj.optJSONArray("commandRuns")
                        if (cmdRuns != null) {
                            for (k in 0 until cmdRuns.length()) {
                                val cid = cmdRuns.optJSONObject(k)?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: cmdRuns.optJSONObject(k)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                if (!cid.isNullOrBlank() && cid.startsWith("UC")) return cid
                            }
                        }
                        val r = txtObj.optJSONArray("runs")
                        if (r != null) {
                            for (k in 0 until r.length()) {
                                val cid = r.optJSONObject(k)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                if (!cid.isNullOrBlank() && cid.startsWith("UC")) return cid
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseHeaderDescription(json: JSONObject): String? {
        val headers = mutableListOf<JSONObject>()
        collect(json, "playlistHeaderRenderer", headers)
        val h = headers.firstOrNull() ?: return null
        return YouTubeSearchParser.textOf(h.optJSONObject("descriptionText"))
    }

    private fun extractMusicVideoId(r: JSONObject): String? {
        r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }?.let { return it }
        val flex = r.optJSONArray("flexColumns")
        if (flex != null) {
            for (i in 0 until flex.length()) {
                val col = flex.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: continue
                val nav = col.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                if (!nav.isNullOrBlank()) return nav
            }
        }
        return null
    }
    private fun extractMusicTitle(r: JSONObject): String? {
        val flex = r.optJSONArray("flexColumns") ?: return null
        val first = flex.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: return null
        return YouTubeSearchParser.textOf(first.optJSONObject("text"))
    }
    private fun extractMusicArtist(r: JSONObject): String {
        val flex = r.optJSONArray("flexColumns") ?: return ""
        if (flex.length() < 2) return ""
        val second = flex.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: return ""
        return YouTubeSearchParser.textOf(second.optJSONObject("text")) ?: ""
    }
    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
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
}
