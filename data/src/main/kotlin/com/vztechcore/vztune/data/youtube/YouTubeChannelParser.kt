package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.innertube.InnerTubeYouTube
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses YouTube InnerTube browse JSON for a channel (youtube.com).
 * Header + tabs (Videos / Shorts / Playlists). Falls back to whole JSON if no tabs.
 */
object YouTubeChannelParser {

    data class ChannelData(
        val channelName: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val description: String?,
        val subscriberCount: String?,
        val videos: List<Track>,
        val shorts: List<Track>,
        val playlists: List<Playlist>,
        val videosContinuation: String?,
        val playlistsContinuation: String?,
        val shortsContinuation: String?,
    )

    fun extractChannelHandle(json: JSONObject): String? {
        val candidates = mutableListOf<String>()
        val metadata = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
        if (metadata != null) {
            candidates += metadata.optString("vanityUrl")
            candidates += metadata.optString("canonicalUrl")
            candidates += metadata.optString("channelUrl")
        }
        val headers = mutableListOf<JSONObject>()
        collect(json, "c4TabbedHeaderRenderer", headers)
        collect(json, "channelHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        for (header in headers) {
            candidates += header.optString("vanityUrl")
            candidates += header.optString("channelHandle")
            candidates += header.optString("canonicalUrl")
            candidates += header.optString("channelUrl")
        }
        val pattern = Regex("@([A-Za-z0-9._-]{3,})")
        for (candidate in candidates) {
            val value = candidate.trim().takeIf { it.isNotBlank() && it != "null" } ?: continue
            val match = pattern.find(value) ?: continue
            return "@" + match.groupValues[1]
        }
        return null
    }

    fun extractChannelHandleFromUrl(url: String): String? {
        val match = Regex("/@([A-Za-z0-9._-]{3,})").find(url) ?: return null
        return "@" + match.groupValues[1]
    }

    fun parseChannelPage(json: JSONObject): ChannelData {
        val header = parseHeader(json)
        val channelName = header.first
        val selected = selectedTab(json)
        if (selected != null) {
            // FIX CHANNEL SELECTED TAB - browse com params devolve TODAS as abas, so a pedida vem selected:true (OpenCLI #1164).
            // Ler tabs[0] pega Home vazia. So a aba selecionada tem conteudo.
            val title = YouTubeSearchParser.textOf(selected.optJSONObject("title")) ?: ""
            val lowTitle = title.lowercase()
            val url = selected.optJSONObject("endpoint")?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")?.lowercase() ?: ""
            val isVideos = url.endsWith("/videos") || lowTitle.contains("vídeo") || lowTitle.contains("video")
            val isShorts = url.endsWith("/shorts") || lowTitle.contains("short")
            val isPlaylists = url.endsWith("/playlists") || lowTitle.contains("playlist")
            val isHome = url.isBlank() || url.endsWith("/featured") || lowTitle.contains("início") || lowTitle.contains("inicio") || lowTitle.contains("home")
            var videos = emptyList<Track>()
            var shorts = emptyList<Track>()
            var playlists = emptyList<Playlist>()
            var videosCont: String? = null
            var playlistsCont: String? = null
            var shortsCont: String? = null
            when {
                isVideos -> { videos = collectVideos(selected); videosCont = InnerTubeYouTube.findContinuationToken(selected) }
                isShorts -> { shorts = collectShorts(selected); shortsCont = InnerTubeYouTube.findContinuationToken(selected) }
                isPlaylists -> { playlists = collectPlaylists(selected); playlistsCont = InnerTubeYouTube.findContinuationToken(selected) }
                isHome -> {
                    videos = collectVideos(selected); shorts = collectShorts(selected); playlists = collectPlaylists(selected)
                    videosCont = InnerTubeYouTube.findContinuationToken(selected)
                }
                else -> { videos = collectVideos(selected); shorts = collectShorts(selected); playlists = collectPlaylists(selected) }
            }
            if (channelName.isNotBlank()) {
                videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }
                shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }
                // FIX PLAYLIST CANAL - playlist sem dono mostra so Playlist no card; usa nome do canal.
                playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }
            }
            return ChannelData(
                channelName = channelName,
                avatarUrl = header.second,
                bannerUrl = header.third,
                description = header.fourth,
                subscriberCount = header.fifth,
                videos = videos.distinctBy { it.id },
                shorts = shorts.distinctBy { it.id },
                playlists = playlists.distinctBy { it.id },
                videosContinuation = videosCont,
                playlistsContinuation = playlistsCont,
                shortsContinuation = shortsCont
            )
        }
        // Sem aba selecionada (layout antigo / resposta sem tabs) - loop antigo
        val tabs = findTabs(json)
        var videos = emptyList<Track>()
        var shorts = emptyList<Track>()
        var playlists = emptyList<Playlist>()
        var videosCont: String? = null
        var playlistsCont: String? = null
        var shortsCont: String? = null
        if (tabs.isEmpty()) {
            videos = collectVideos(json)
            shorts = collectShorts(json)
            playlists = collectPlaylists(json)
            videosCont = InnerTubeYouTube.findContinuationToken(json)
        } else {
            for ((title, tabJson) in tabs) {
                val low = title.lowercase()
                when {
                    low.contains("video") || low.contains("vídeo") -> {
                        videos = collectVideos(tabJson)
                        videosCont = InnerTubeYouTube.findContinuationToken(tabJson)
                    }
                    low.contains("short") -> {
                        shorts = collectShorts(tabJson)
                        shortsCont = InnerTubeYouTube.findContinuationToken(tabJson)
                    }
                    low.contains("playlist") -> {
                        playlists = collectPlaylists(tabJson)
                        playlistsCont = InnerTubeYouTube.findContinuationToken(tabJson)
                    }
                    low.contains("início") || low.contains("inicio") || low.contains("home") -> {
                        if (videos.isEmpty()) {
                            val homeVideos = collectVideos(tabJson)
                            if (homeVideos.isNotEmpty()) videos = homeVideos
                        }
                    }
                }
            }
            if (videos.isEmpty()) {
                val allVideos = collectVideos(json)
                val shortIds = shorts.map { it.id }.toSet()
                videos = allVideos.filter { it.id !in shortIds }
            }
        }
        if (channelName.isNotBlank()) {
            videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }
            shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }
            // FIX PLAYLIST CANAL - playlist sem dono mostra so Playlist no card; usa nome do canal.
            playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }
        }
        return ChannelData(
            channelName = channelName,
            avatarUrl = header.second,
            bannerUrl = header.third,
            description = header.fourth,
            subscriberCount = header.fifth,
            videos = videos.distinctBy { it.id },
            shorts = shorts.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
            videosContinuation = videosCont,
            playlistsContinuation = playlistsCont,
            shortsContinuation = shortsCont
        )
    }

    fun parseVideosContinuation(json: JSONObject): Pair<List<Track>, String?> {
        val videos = collectVideos(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return videos.distinctBy { it.id } to cont
    }

        fun parseShortsContinuation(json: JSONObject): Pair<List<Track>, String?> {
        // FIX SHORTS CONTINUATION - shorts via aba nao tinha como paginar, agora tem igual videos/playlists
        val shorts = collectShorts(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return shorts.distinctBy { it.id } to cont
    }

    fun parsePlaylistsContinuation(json: JSONObject): Pair<List<Playlist>, String?> {
        val playlists = collectPlaylists(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return playlists.distinctBy { it.id } to cont
    }

    // FIX PLAYLISTS V3 - varre o JSON INTEIRO, ignora aba selecionada.
    // parseChannelPage so le a aba selected=true. Se o YouTube marcar a errada, volta 0.
    fun parsePlaylistsWholeJson(json: JSONObject, channelName: String): List<Playlist> {
        val all = collectPlaylists(json).distinctBy { it.id }
        return all.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }
    }

    // FIX PLAYLISTS V4 - diagnostico: quantos lockups vieram e como sao os 3 primeiros.
    fun debugLockups(json: JSONObject): String {
        val lockups = mutableListOf<JSONObject>()
        collect(json, "lockupViewModel", lockups)
        val renderers = mutableListOf<JSONObject>()
        collect(json, "playlistRenderer", renderers)
        collect(json, "gridPlaylistRenderer", renderers)
        val samples = lockups.take(3).joinToString(" | ") { lvm ->
            val type = lvm.optString("contentType").takeIf { it.isNotBlank() } ?: "(sem tipo)"
            val rawId = lvm.optString("contentId").takeIf { it.isNotBlank() } ?: "(sem contentId)"
            val title = YouTubeSearchParser.lockupTitle(lvm) ?: "(sem titulo)"
            val pid = YouTubeSearchParser.lockupPlaylistId(lvm) ?: "(pid null)"
            "type=$type raw=$rawId pid=$pid titulo=$title"
        }
        return "lockups=${lockups.size} renderers=${renderers.size} amostras=[$samples]"
    }



    private fun parseHeader(json: JSONObject): Quint<String, String?, String?, String?, String?> {
        val headers = mutableListOf<JSONObject>()
        collect(json, "c4TabbedHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        collect(json, "channelHeaderRenderer", headers)
        val h = headers.firstOrNull()
        if (h != null) {
            val name = YouTubeSearchParser.textOf(h.optJSONObject("title"))
                ?: YouTubeSearchParser.textOf(h.optJSONObject("channelTitle")) ?: ""
            val avatar = YouTubeSearchParser.thumbnailOf(h.optJSONObject("avatar"))
            val banner = YouTubeSearchParser.thumbnailOf(h.optJSONObject("banner"))
            val desc = YouTubeSearchParser.textOf(h.optJSONObject("description"))
            val subs = YouTubeSearchParser.textOf(h.optJSONObject("subscriberCountText"))
                ?: YouTubeSearchParser.textOf(h.optJSONObject("subscriberCount"))
            if (name.isNotBlank()) return Quint(name, avatar, banner, desc, subs)
        }
        // Layout 2026: metadata.channelMetadataRenderer + pageHeaderViewModel (invio parse_channel_meta)
        val meta = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
        if (meta != null) {
            val name = meta.optString("title").takeIf { it.isNotBlank() } ?: ""
            val avatar = YouTubeSearchParser.thumbnailOf(meta.optJSONObject("avatar")) ?: bestSources(meta.optJSONObject("avatar"))
            val desc = meta.optString("description").takeIf { it.isNotBlank() }
            val pageHeader = json.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
            val banner = pageHeader?.let { bestSources(it.optJSONObject("banner")) } ?: bestSources(pageHeader)
            var subs: String? = null
            val rows = pageHeader?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                    for (j in 0 until parts.length()) {
                        val txt = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content") ?: continue
                        if (txt.lowercase().contains("subscriber") || txt.lowercase().contains("inscrit")) { subs = txt.trim(); break }
                    }
                    if (subs != null) break
                }
            }
            return Quint(name, avatar, banner, desc, subs)
        }
        return Quint("", null, null, null, null)
    }

    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    private fun findTabs(json: JSONObject): List<Pair<String, JSONObject>> {
        val tabs = mutableListOf<Pair<String, JSONObject>>()
        val tabRenderers = mutableListOf<JSONObject>()
        collect(json, "tabRenderer", tabRenderers)
        for (t in tabRenderers) {
            val title = YouTubeSearchParser.textOf(t.optJSONObject("title")) ?: continue
            tabs += title to t
        }
        return tabs
    }

        // FIX CHANNEL TAB PARAMS - params vem da propria aba, nunca hardcoded (invio tab_params).
    // Params antigos EgZ2aWRlb3MyAggA so devolvem a lista de abas, nao o conteudo.
    fun tabParams(json: JSONObject, segment: String): String? {
        val renderers = mutableListOf<JSONObject>()
        collect(json, "tabRenderer", renderers)
        val seg = segment.lowercase()
        for (t in renderers) {
            val url = t.optJSONObject("endpoint")?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")?.lowercase() ?: ""
            val title = (YouTubeSearchParser.textOf(t.optJSONObject("title")) ?: "").lowercase()
            val matches = when (seg) {
                "videos" -> url.endsWith("/videos") || title.contains("vídeo") || title.contains("video")
                "shorts" -> url.endsWith("/shorts") || title.contains("short")
                "playlists" -> url.endsWith("/playlists") || title.contains("playlist")
                "streams" -> url.endsWith("/streams") || url.endsWith("/live") || title.contains("ao vivo") || title.contains("live") || title.contains("stream")
                else -> url.endsWith("/$seg") || title.contains(seg)
            }
            if (!matches) continue
            val params = t.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("params")?.takeIf { it.isNotBlank() } ?: continue
            return params
        }
        return null
    }

    private fun selectedTab(json: JSONObject): JSONObject? {
        val renderers = mutableListOf<JSONObject>()
        collect(json, "tabRenderer", renderers)
        return renderers.firstOrNull { it.optBoolean("selected", false) }
    }

    private fun bestSources(node: Any?): String? {
        var bestUrl: String? = null
        var bestWidth = -1
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val arr = n.optJSONArray("sources")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            val url = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                            val w = s.optInt("width", 0)
                            if (w >= bestWidth) { bestWidth = w; bestUrl = url }
                        }
                    }
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(node)
        return bestUrl
    }

    private fun collectVideos(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val videoRenderers = mutableListOf<JSONObject>()
        collect(node, "videoRenderer", videoRenderers)
        collect(node, "gridVideoRenderer", videoRenderers)
        for (r in videoRenderers) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            val duration = YouTubeSearchParser.parseDuration(
                YouTubeSearchParser.textOf(r.optJSONObject("lengthText")) ?: r.optString("lengthText")
            )
            val channel = YouTubeSearchParser.textOf(r.optJSONObject("ownerText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("shortBylineText")) ?: ""
            val published = YouTubeSearchParser.textOf(r.optJSONObject("publishedTimeText"))
            out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published)
        }
        // Layout 2026: lockupViewModel LOCKUP_CONTENT_TYPE_VIDEO (invio lockup_to_video)
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type != "LOCKUP_CONTENT_TYPE_VIDEO" && type != "LOCKUP_CONTENT_TYPE_STREAM" && type != "") continue
            if (type == "LOCKUP_CONTENT_TYPE_SHORTS") continue
            val videoId = lvm.optString("contentId").takeIf { it.isNotBlank() } ?: continue
            val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val titleObj = meta?.optJSONObject("title")
            val title = titleObj?.optString("content")?.takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(titleObj) ?: continue
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm) ?: bestSources(lvm.optJSONObject("contentImage"))
            val duration = lockupVideoDuration(lvm)
            val published = lockupPublished(lvm)
            out += Track(id = videoId, title = title, artist = "", durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published)
        }
        return out
    }

    private fun lockupVideoDuration(lvm: JSONObject): Long {
        var found: String? = null
        fun walk(n: Any?) {
            if (found != null) return
            when (n) {
                is JSONObject -> {
                    val badge = n.optJSONObject("thumbnailBadgeViewModel")
                    if (badge != null) {
                        val txt = badge.optString("text").takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(badge.optJSONObject("text"))
                        if (txt != null && txt.contains(":")) { found = txt; return }
                    }
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(lvm.optJSONObject("contentImage"))
        if (found == null) return 0L
        if (found.equals("LIVE", ignoreCase = true)) return 0L
        return YouTubeSearchParser.parseDuration(found)
    }

    private fun lockupPublished(lvm: JSONObject): String? {
        val rows = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows") ?: return null
        for (i in 0 until rows.length()) {
            val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
            for (j in 0 until parts.length()) {
                val txt = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() } ?: continue
                val low = txt.lowercase()
                if (low.contains("ago") || low.contains("há") || low.contains("ano") || low.contains("mês") || low.contains("semana") || low.contains("dia") || low.contains("hora") || low.contains("minuto")) return txt.trim()
            }
        }
        return null
    }

    private fun collectShorts(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val reels = mutableListOf<JSONObject>()
        collect(node, "reelItemRenderer", reels)
        for (r in reels) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("headline"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        // Layout 2026: shortsLockupViewModel (invio shorts_lockup_to_video)
        // videoId = onTap.reelWatchEndpoint.videoId, titulo = overlayMetadata.primaryText.content, thumb = thumbnail.sources maior width
        val slvms = mutableListOf<JSONObject>()
        collect(node, "shortsLockupViewModel", slvms)
        for (r in slvms) {
            val videoId = r.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("reelWatchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
                ?: r.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.isNotBlank() }
                ?: r.optString("contentId").takeIf { it.isNotBlank() }
                ?: r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = r.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content")?.takeIf { it.isNotBlank() }
                ?: r.optString("title").takeIf { it.isNotBlank() } ?: continue
            val thumb = (bestSources(r.optJSONObject("thumbnail")) ?: bestSources(r.optJSONObject("thumbnailViewModel")) ?: YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail")))?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        // Lockup generico de Shorts na aba Shorts
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            if (lvm.optString("contentType") != "LOCKUP_CONTENT_TYPE_SHORTS") continue
            val videoId = lvm.optString("contentId").takeIf { it.isNotBlank() } ?: continue
            val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val titleObj = meta?.optJSONObject("title")
            val title = titleObj?.optString("content")?.takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(titleObj) ?: continue
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        return out
    }

    fun collectPlaylists(node: Any?): List<Playlist> {
        val out = mutableListOf<Playlist>()
        val renderers = mutableListOf<JSONObject>()
        collect(node, "playlistRenderer", renderers)
        collect(node, "gridPlaylistRenderer", renderers)
        for (r in renderers) {
            val pid = r.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val owner = YouTubeSearchParser.textOf(r.optJSONObject("shortBylineText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("longBylineText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("ownerText")) ?: ""
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            val countText = r.optString("videoCount").takeIf { it.isNotBlank() }
                ?: YouTubeSearchParser.textOf(r.optJSONObject("videoCountText"))
            val count = countText?.filter { it.isDigit() }?.toIntOrNull()
            out += Playlist(id = pid, title = title, owner = owner.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE)
        }
        // Layout 2026: lockupViewModel (mesma logica da busca)
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue
            val pid = YouTubeSearchParser.lockupPlaylistId(lvm) ?: continue
            if (!pid.startsWith("PL") && !pid.startsWith("UU") && !pid.startsWith("OL") && !pid.startsWith("RD") && !pid.startsWith("FL")) continue
            val title = YouTubeSearchParser.lockupTitle(lvm) ?: continue
            val owner = YouTubeSearchParser.lockupOwner(lvm)
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)
            val count = YouTubeSearchParser.lockupCount(lvm)
            out += Playlist(id = pid, title = title, owner = owner?.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE)
        }
        return out
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
