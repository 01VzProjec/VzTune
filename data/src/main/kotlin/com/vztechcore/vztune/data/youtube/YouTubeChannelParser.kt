package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.innertube.InnerTubeYouTube
import org.json.JSONArray
import org.json.JSONObject

/** Link de imagem (foto de perfil / banner) com o tamanho, ex: "72x72". */
data class ChannelImageLink(val label: String, val url: String)

/** Dados da aba "Sobre" do canal (InnerTube). */
data class ChannelAboutInfo(
    val avatars: List<ChannelImageLink> = emptyList(),
    val banners: List<ChannelImageLink> = emptyList(),
    val tags: List<String> = emptyList(),
    val videoCountText: String? = null,
)

/** Aba Lancamentos do canal: albuns e/ou singles (canal de artista nao tem playlists). */
data class ChannelReleases(
    val items: List<Playlist>,
    val hasAlbums: Boolean,
    val hasSingles: Boolean,
) {
    /** Nome da aba no lugar de "Playlists". */
    val label: String?
        get() = when {
            items.isEmpty() -> null
            hasAlbums && hasSingles -> "Álbuns e Singles"
            hasSingles -> "Singles"
            else -> "Álbuns"
        }
}

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
            candidates += metadata.optString("vanityChannelUrl")
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
        // Layout 2026: o @usuario aparece como texto solto nas linhas de metadata do header.
        for (text in leafStrings(json.optJSONObject("header"))) {
            val t = text.trim()
            if (t.length >= 4 && t.matches(Regex("^@[A-Za-z0-9._-]{3,}\$"))) candidates += t
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
            // Descricao COMPLETA: o header as vezes traz so um trecho; o channelMetadataRenderer traz inteira. Fica a maior.
            val headerDesc = YouTubeSearchParser.textOf(h.optJSONObject("description"))
            val fullDesc = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")?.optString("description")?.takeIf { it.isNotBlank() }
            val desc = listOfNotNull(headerDesc, fullDesc).maxByOrNull { it.length }
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
            val banner = pageHeader?.let { bestSources(it.optJSONObject("banner")) }
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

    // ===================== SOBRE / contagem de videos =====================

    private data class RawImage(val url: String, val width: Int, val height: Int)

    /** Texto do total de videos do canal, ex: "534 vídeos". Vem do header (InnerTube), nunca da contagem dos achados. */
    fun extractVideoCount(json: JSONObject): String? = findVideoCountText(json.optJSONObject("header"))

    private val VideoCountRegex = Regex("^\\s*[\\d.,]+\\s*(?:mil|mi|milh[õo]es|milhão|k|m|b)?\\s*v[ií]deos?\\s*\$", RegexOption.IGNORE_CASE)
    private val VideoCountInlineRegex = Regex("\\d[\\d.,]*\\s*(?:mil|mi|k|m|b)?\\s*v[ií]deos?\\b", RegexOption.IGNORE_CASE)

    /** Procura "N vídeos" nos textos de um header (canal ou playlist de uploads). */
    fun findVideoCountText(node: Any?): String? {
        if (node == null) return null
        val strings = leafStrings(node)
        strings.firstOrNull { VideoCountRegex.matches(it) }?.let { return it.trim() }
        // Fallback: tudo numa linha so, ex: "1,2 mi de inscritos • 534 vídeos"
        for (s in strings) {
            val low = s.lowercase()
            if (!low.contains("inscrit") && !low.contains("subscri")) continue
            VideoCountInlineRegex.find(s)?.let { return it.value.trim() }
        }
        return null
    }

    /** Fotos, banners, tags e total de videos do canal. */
    fun parseAbout(json: JSONObject): ChannelAboutInfo {
        val meta = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
        val headers = mutableListOf<JSONObject>()
        collect(json, "c4TabbedHeaderRenderer", headers)
        collect(json, "channelHeaderRenderer", headers)
        val header = headers.firstOrNull()
        val pageHeaderVm = json.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")
            ?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")

        val avatarRaw = rawImages(meta?.optJSONObject("avatar")) + rawImages(header?.optJSONObject("avatar")) + rawImages(pageHeaderVm?.optJSONObject("image"))
        val bannerRaw = rawImages(header?.optJSONObject("banner")) + rawImages(pageHeaderVm?.optJSONObject("banner"))

        val tags = mutableListOf<String>()
        val tagArray = json.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")?.optJSONArray("tags")
        if (tagArray != null) {
            for (i in 0 until tagArray.length()) {
                val tag = tagArray.optString(i).trim()
                if (tag.isNotBlank()) tags.add(tag)
            }
        }
        if (tags.isEmpty()) {
            // keywords vem numa string so: palavras soltas e "frases entre aspas"
            val keywords = meta?.optString("keywords").orEmpty()
            Regex("\"([^\"]+)\"|(\\S+)").findAll(keywords).forEach { m ->
                val tag = (m.groups[1]?.value ?: m.groups[2]?.value).orEmpty().trim()
                if (tag.isNotBlank()) tags.add(tag)
            }
        }
        return ChannelAboutInfo(
            avatars = toImageLinks(avatarRaw, synthesizeAvatarSizes = true),
            banners = toImageLinks(bannerRaw, synthesizeAvatarSizes = false),
            tags = tags.distinct(),
            videoCountText = extractVideoCount(json),
        )
    }

    /** Banner sempre na maior resolucao (as URLs do YouTube aceitam =wNNNN). */
    fun upscaleBanner(url: String): String = url.replace(Regex("=w\\d+"), "=w2560")

    private fun fixUrl(url: String): String = if (url.startsWith("//")) "https:$url" else url

    private fun rawImages(node: Any?): List<RawImage> {
        val out = mutableListOf<RawImage>()
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    for (key in listOf("thumbnails", "sources")) {
                        val arr = n.optJSONArray(key) ?: continue
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
                            out.add(RawImage(fixUrl(url), o.optInt("width", 0), o.optInt("height", 0)))
                        }
                    }
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(node)
        return out
    }

    private fun toImageLinks(raw: List<RawImage>, synthesizeAvatarSizes: Boolean): List<ChannelImageLink> {
        val sizeParam = Regex("=s\\d+")
        val bySize = linkedMapOf<String, String>()
        for (img in raw) {
            val side = sizeParam.find(img.url)?.value?.drop(2)?.toIntOrNull()
            val label = when {
                img.width > 0 && img.height > 0 -> "${img.width}x${img.height}"
                side != null -> "${side}x${side}"
                else -> "Original"
            }
            if (!bySize.containsKey(label)) bySize[label] = img.url
        }
        if (synthesizeAvatarSizes) {
            val base = raw.firstOrNull { sizeParam.containsMatchIn(it.url) }?.url
            if (base != null) {
                for (s in listOf(72, 120, 240, 512, 900)) {
                    val label = "${s}x${s}"
                    if (!bySize.containsKey(label)) bySize[label] = base.replace(sizeParam, "=s$s")
                }
            }
        }
        return bySize.entries
            .map { ChannelImageLink(it.key, it.value) }
            .sortedBy { it.label.substringBefore("x").toIntOrNull() ?: Int.MAX_VALUE }
    }

    /** Todos os textos (strings) de um trecho do JSON. */
    private fun leafStrings(node: Any?, limit: Int = 4000): List<String> {
        val out = ArrayList<String>()
        fun walk(n: Any?) {
            if (out.size >= limit) return
            when (n) {
                is JSONObject -> {
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
                is String -> if (n.isNotBlank()) out.add(n)
            }
        }
        walk(node)
        return out
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
                "releases" -> url.endsWith("/releases") || title.contains("lançamento") || title.contains("lancamento") || title.contains("release")
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
out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published, viewCount = YouTubeSearchParser.textOf(r.optJSONObject("viewCountText"))?.let { parseViewCount(it) }, channelAvatarUrl = YouTubeSearchParser.channelAvatarOf(r))
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
val (lv, ld) = lockupViewsDate(lvm)
out += Track(id = videoId, title = title, artist = "", durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published ?: ld, viewCount = lv)
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

    /** Shorts do canal (reelItemRenderer, shortsLockupViewModel e lockup SHORTS). Nunca descarta Short por falta de titulo/thumbnail. */
    fun collectShorts(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val reels = mutableListOf<JSONObject>()
        collect(node, "reelItemRenderer", reels)
        for (r in reels) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("headline"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: "Short"
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
                ?: shortThumbnail(videoId)
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        // Layout 2026: shortsLockupViewModel
        val slvms = mutableListOf<JSONObject>()
        collect(node, "shortsLockupViewModel", slvms)
        for (r in slvms) {
            shortFromLockup(r)?.let { out += it }
        }
        // Lockup generico de Shorts
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            if (lvm.optString("contentType") != "LOCKUP_CONTENT_TYPE_SHORTS") continue
            val videoId = lvm.optString("contentId").takeIf { it.isNotBlank() } ?: continue
            val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val titleObj = meta?.optJSONObject("title")
            val title = titleObj?.optString("content")?.takeIf { it.isNotBlank() }
                ?: YouTubeSearchParser.textOf(titleObj) ?: "Short"
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
                ?: shortThumbnail(videoId)
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        return out.distinctBy { it.id }
    }

    private fun shortThumbnail(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private fun shortFromLockup(r: JSONObject): Track? {
        val videoId = r.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("reelWatchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: r.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.isNotBlank() }
            ?: r.optString("contentId").takeIf { it.isNotBlank() }
            ?: r.optString("videoId").takeIf { it.isNotBlank() }
            ?: return null
        val title = r.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content")?.takeIf { it.isNotBlank() }
            ?: r.optString("title").takeIf { it.isNotBlank() }
            ?: titleFromAccessibility(r.optString("accessibilityText"))
            ?: "Short"
        val thumb = (bestSources(r.optJSONObject("thumbnail"))
            ?: bestSources(r.optJSONObject("thumbnailViewModel"))
            ?: bestSources(r)
            ?: YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail")))
            ?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
            ?: shortThumbnail(videoId)
        return Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
    }

    /** "Titulo do Short, 1,2 mil visualizações - reproduzir Short" -> "Titulo do Short". */
    private fun titleFromAccessibility(text: String): String? {
        if (text.isBlank()) return null
        val m = Regex("^(.*?),\\s*[\\d.,]+\\s*(?:mil|mi|M|K|B)?\\s*(?:visualiza|views).*\$", RegexOption.IGNORE_CASE).find(text)
        return (m?.groupValues?.get(1) ?: text.substringBefore(" - ")).trim().takeIf { it.isNotBlank() }
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

    /** Aba Lancamentos: albuns/singles. Detecta o tipo pelo texto "Álbum • 2020" / "Single • 2021" / "EP". */
    fun collectReleases(node: Any?): ChannelReleases {
        val items = collectPlaylists(node).distinctBy { it.id }
        var hasAlbums = false
        var hasSingles = false
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val rows = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.optJSONObject("metadata")
            val text = leafStrings(rows).joinToString(" ").lowercase()
            when {
                Regex("\\b(single|singles|ep)\\b").containsMatchIn(text) -> hasSingles = true
                text.contains("álbum") || text.contains("album") -> hasAlbums = true
            }
        }
        return ChannelReleases(items, hasAlbums, hasSingles)
    }

    /** Views + data dos metadataRows do lockup ("1,2 mi de visualizações • há 2 dias"). */
    fun lockupViewsDate(lvm: JSONObject): Pair<Long?, String?> {
        val rows = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            ?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows") ?: return null to null
        var views: Long? = null
        var date: String? = null
        for (i in 0 until rows.length()) {
            val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
            for (j in 0 until parts.length()) {
                val txt = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() } ?: continue
                for (seg in txt.split("•", "·")) {
                    val s = seg.trim()
                    val low = s.lowercase()
                    if (views == null && (low.contains("visualiza") || low.contains("view"))) views = parseViewCount(s)
                    if (date == null && (low.contains("há ") || low.contains("ago") || low.contains("atrás"))) date = s
                }
            }
        }
        return views to date
    }

    /** "1,2 mi de visualizações" -> 1200000; "534 mil" -> 534000; "1.234" -> 1234. */
    fun parseViewCount(text: String): Long? {
        val m = Regex("([\\d.,]+)\\s*(milh(?:ão|ões|oes)|mil|mi|bi|k|m|b)?", RegexOption.IGNORE_CASE).find(text) ?: return null
        val numRaw = m.groupValues[1]
        val suffix = m.groupValues[2].lowercase()
        val num = (if (numRaw.contains(",")) numRaw.replace(".", "").replace(",", ".").toDoubleOrNull() else numRaw.replace(".", "").toDoubleOrNull()) ?: return null
        val mult = when {
            suffix == "mil" || suffix == "k" -> 1_000.0
            suffix.startsWith("milh") || suffix == "mi" || suffix == "m" -> 1_000_000.0
            suffix == "bi" || suffix == "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (num * mult).toLong().takeIf { it > 0 }
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
