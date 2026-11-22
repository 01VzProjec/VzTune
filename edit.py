# fix_channels_ytmusic_owner.py - Canais vazios + dono playlist YT Music
# Roda na raiz: python3 fix_channels_ytmusic_owner.py
# CAUSAS PESQUISADAS:
# Canal:
#  1) browseChannel retornava cedo se channelName != "" mesmo com videos/playlists/shorts vazios (Home vazia = canal vazio)
#  2) browse(UC) sem params devolve Home selecionada; abas Videos/Shorts/Playlists vem como cascas vazias.
#     Tem que pedir cada aba com o params DELA, lido de tabRenderer.endpoint.browseEndpoint.params (invio tab_params).
#     Params hardcoded EgZ2aWRlb3MyAggA / EgZzaG9ydHMyAggJ so devolvem a lista de abas, nao conteudo (SKILL junho 2026).
#  3) Resposta do browse com params tem TODAS as abas, so a pedida vem selected:true. Ler tabs[0] pega Home vazia (OpenCLI PR #1164).
#  4) Videos tab 2026 nao manda videoRenderer, manda lockupViewModel LOCKUP_CONTENT_TYPE_VIDEO (invio lockup_to_video).
#     Shorts manda shortsLockupViewModel com onTap.reelWatchEndpoint.videoId + overlayMetadata.primaryText.content (invio shorts_lockup_to_video).
#     Parser antigo procurava contentId/title/thumbnail e achava 0.
#  5) Fallback UU: UULF = videos normais, UUSH = shorts, UU = tudo. Via browse VL+UU... (dev.to tabela UU/UULF/UUSH).
# YT Music playlist owner:
#  1) extractPlaylistOwner so aceitava run com browseId UC. Dono em texto puro (sem link) voltava null.
#  2) Fallback fazia subtitle.substringBefore("•") = "Playlist" (primeiro segmento e a palavra tipo). Dono e o SEGUNDO segmento: "Playlist • Dono • 20 songs".
#  3) fillPlaylistStats tratava "Playlist" como dono valido, entao nunca buscava o dono real no browse da playlist.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
PLAYLIST_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubePlaylistParser.kt")
YTM_SEARCH = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicSearchParser.kt")
ENRICHER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicEnricher.kt")
for p in [CHANNEL_PARSER, YT_REPO, PLAYLIST_PARSER, YTM_SEARCH, ENRICHER]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda parte2_3 + fix_playlists_v2 antes")

# ---------- 1) YouTubeChannelParser ----------
c = open(CHANNEL_PARSER, encoding="utf-8").read()

# 1a) parseChannelPage -> selected-tab aware
if "FIX CHANNEL SELECTED TAB" not in c:
    start = c.find("fun parseChannelPage")
    if start == -1: fail("ancora parseChannelPage nao bateu")
    end = c.find("fun parseVideosContinuation", start)
    if end == -1: fail("fim parseChannelPage nao bateu")
    new_parse = """fun parseChannelPage(json: JSONObject): ChannelData {
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

    """
    c = c[:start] + new_parse + c[end:]
    print("1a) parseChannelPage agora le so a aba selected:true")
else:
    print("1a) parseChannelPage ja e selected-tab - pulando")

# 1b) Helpers tabParams + selectedTab + bestSources (depois de findTabs)
if "fun tabParams" not in c:
    anchor = "private fun collectVideos"
    if anchor not in c: fail("ancora collectVideos nao bateu para helpers")
    helpers = """    // FIX CHANNEL TAB PARAMS - params vem da propria aba, nunca hardcoded (invio tab_params).
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

    """
    c = c.replace(anchor, helpers + anchor, 1)
    print("1b) Helpers tabParams / selectedTab / bestSources adicionados")
else:
    print("1b) Helpers tabParams ja existem - pulando")

# 1c) collectVideos -> lockup VIDEO
if "LOCKUP_CONTENT_TYPE_VIDEO" not in c:
    start = c.find("private fun collectVideos")
    if start == -1: fail("ancora collectVideos nao bateu")
    end = c.find("private fun collectShorts", start)
    if end == -1: fail("fim collectVideos nao bateu")
    new_collect_videos = """private fun collectVideos(node: Any?): List<Track> {
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

    """
    c = c[:start] + new_collect_videos + c[end:]
    print("1c) collectVideos agora entende lockupViewModel VIDEO")
else:
    print("1c) collectVideos ja entende lockup - pulando")

# 1d) collectShorts -> shortsLockup correto + lockup SHORTS
if "reelWatchEndpoint" not in c:
    start = c.find("private fun collectShorts")
    if start == -1: fail("ancora collectShorts nao bateu")
    end = c.find("private fun collectPlaylists", start)
    if end == -1: fail("fim collectShorts nao bateu")
    new_shorts = """private fun collectShorts(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val reels = mutableListOf<JSONObject>()
        collect(node, "reelItemRenderer", reels)
        for (r in reels) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("headline"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
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
            val thumb = bestSources(r.optJSONObject("thumbnail")) ?: bestSources(r.optJSONObject("thumbnailViewModel")) ?: YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
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
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        return out
    }

    """
    c = c[:start] + new_shorts + c[end:]
    print("1d) collectShorts agora entende shortsLockupViewModel correto")
else:
    print("1d) collectShorts ja entende reelWatchEndpoint - pulando")

# 1e) parseHeader -> fallback channelMetadataRenderer + pageHeaderViewModel
if "channelMetadataRenderer" not in c:
    start = c.find("private fun parseHeader")
    if start == -1: fail("ancora parseHeader nao bateu")
    end = c.find("private data class Quint", start)
    if end == -1: fail("fim parseHeader nao bateu")
    new_header = """private fun parseHeader(json: JSONObject): Quint<String, String?, String?, String?, String?> {
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

    """
    c = c[:start] + new_header + c[end:]
    print("1e) parseHeader agora entende layout 2026")
else:
    print("1e) parseHeader ja entende channelMetadataRenderer - pulando")

open(CHANNEL_PARSER, "w", encoding="utf-8").write(c)

# ---------- 2) YouTubeRepository.browseChannel - nao retorna cedo, busca cada aba + UU ----------
yt = open(YT_REPO, encoding="utf-8").read()
if "FIX CHANNEL TABS" not in yt:
    start = yt.find("// PART 2+3 - InnerTube first for browseChannel")
    if start == -1: fail("ancora browseChannel InnerTube nao bateu")
    onf = yt.find(".onFailure", start)
    if onf == -1: fail("fim browseChannel InnerTube nao bateu")
    end = yt.find("\n", onf)
    if end == -1: end = len(yt)
    new_block = """        // FIX CHANNEL TABS - InnerTube first for browseChannel: nao retorna cedo so por ter nome.
        // browse(UC) sem params = Home (quase sempre vazia). Cada aba tem que ser pedida com o params DELA, lido do tabRenderer.
        // Se tudo vier vazio, NAO retorna - cai pro NewPipe abaixo. So retorna via InnerTube se vier algum conteudo.
        runCatching {
            val uc = Regex("UC[\\\\w-]{22}").find(input)?.value
            if (uc != null) {
                val homeJson = InnerTubeYouTube.browse(uc)
                val homeParsed = YouTubeChannelParser.parseChannelPage(homeJson)
                var videos = homeParsed.videos.toMutableList()
                var shorts = homeParsed.shorts.toMutableList()
                var playlists = homeParsed.playlists.toMutableList()
                var videosCont = homeParsed.videosContinuation
                var playlistsCont = homeParsed.playlistsContinuation
                var shortsCont = homeParsed.shortsContinuation
                // Videos aba com params dela
                val videosParams = YouTubeChannelParser.tabParams(homeJson, "videos")
                if (videos.isEmpty() && videosParams != null) {
                    runCatching {
                        val tabJson = InnerTubeYouTube.browse(uc, videosParams)
                        val parsed = YouTubeChannelParser.parseChannelPage(tabJson)
                        if (parsed.videos.isNotEmpty()) { videos = parsed.videos.toMutableList(); videosCont = parsed.videosContinuation }
                    }
                }
                // Shorts aba com params dela
                val shortsParams = YouTubeChannelParser.tabParams(homeJson, "shorts")
                if (shorts.isEmpty() && shortsParams != null) {
                    runCatching {
                        val tabJson = InnerTubeYouTube.browse(uc, shortsParams)
                        val parsed = YouTubeChannelParser.parseChannelPage(tabJson)
                        if (parsed.shorts.isNotEmpty()) { shorts = parsed.shorts.toMutableList(); shortsCont = parsed.shortsContinuation }
                        if (parsed.videos.isNotEmpty() && videos.isEmpty()) { videos = parsed.videos.toMutableList(); videosCont = parsed.videosContinuation }
                    }
                }
                // Playlists aba com params dela
                val playlistsParams = YouTubeChannelParser.tabParams(homeJson, "playlists")
                if (playlists.isEmpty() && playlistsParams != null) {
                    runCatching {
                        val tabJson = InnerTubeYouTube.browse(uc, playlistsParams)
                        val parsed = YouTubeChannelParser.parseChannelPage(tabJson)
                        if (parsed.playlists.isNotEmpty()) { playlists = parsed.playlists.toMutableList(); playlistsCont = parsed.playlistsContinuation }
                    }
                }
                // Fallback UU: UULF = videos normais, UUSH = shorts, UU = tudo (dev.to). Via browse VL+...
                if (videos.isEmpty()) {
                    runCatching {
                        val uuJson = InnerTubeYouTube.browse("VL" + "UULF" + uc.drop(2))
                        val plParsed = YouTubePlaylistParser.parsePlaylistPage(uuJson, homeParsed.channelName)
                        if (plParsed.tracks.isNotEmpty()) { videos = plParsed.tracks.map { if (it.artist.isBlank()) it.copy(artist = homeParsed.channelName) else it }.toMutableList(); videosCont = plParsed.continuation }
                    }
                    if (videos.isEmpty()) {
                        runCatching {
                            val uuJson2 = InnerTubeYouTube.browse("VL" + "UU" + uc.drop(2))
                            val plParsed2 = YouTubePlaylistParser.parsePlaylistPage(uuJson2, homeParsed.channelName)
                            if (plParsed2.tracks.isNotEmpty()) { videos = plParsed2.tracks.map { if (it.artist.isBlank()) it.copy(artist = homeParsed.channelName) else it }.toMutableList(); videosCont = plParsed2.continuation }
                        }
                    }
                }
                if (shorts.isEmpty()) {
                    runCatching {
                        val ushJson = InnerTubeYouTube.browse("VL" + "UUSH" + uc.drop(2))
                        val plParsed = YouTubePlaylistParser.parsePlaylistPage(ushJson, homeParsed.channelName)
                        if (plParsed.tracks.isNotEmpty()) { shorts = plParsed.tracks.map { if (it.artist.isBlank()) it.copy(artist = homeParsed.channelName) else it }.toMutableList(); shortsCont = plParsed.continuation }
                    }
                }
                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {
                    ytChannelVideoContinuations[uc] = videosCont
                    ytChannelVideoContinuations[input] = videosCont
                    ytChannelPlaylistContinuations[uc] = playlistsCont
                    ytChannelPlaylistContinuations[input] = playlistsCont
                    return@withContext ChannelPage(
                        videos = videos,
                        shorts = shorts,
                        playlists = playlists,
                        channelName = homeParsed.channelName.ifBlank { null },
                        avatarUrl = homeParsed.avatarUrl,
                        bannerUrl = homeParsed.bannerUrl,
                        description = homeParsed.description,
                        subscriberCount = homeParsed.subscriberCount,
                        hasMoreVideos = videosCont != null,
                        hasMorePlaylists = playlistsCont != null
                    )
                }
                // Tudo vazio via InnerTube: nao retorna, deixa o NewPipe abaixo tentar (ele pode achar via extractor).
            }
        }.onFailure { Log.w(TAG, "InnerTube browseChannel failed, fallback NewPipe: ${it.message}") }"""
    yt = yt[:start] + new_block + yt[end:]
    open(YT_REPO, "w", encoding="utf-8").write(yt)
    print("2) browseChannel agora busca cada aba com params dela + fallback UULF/UUSH/UU, sem retornar cedo")
else:
    print("2) browseChannel ja tem FIX CHANNEL TABS - pulando")

# ---------- 3) YouTubePlaylistParser.collectTracks -> lockup VIDEO ----------
pp = open(PLAYLIST_PARSER, encoding="utf-8").read()
if "LOCKUP_CONTENT_TYPE_VIDEO" not in pp:
    start = pp.find("private fun collectTracks")
    if start == -1: fail("ancora collectTracks playlist nao bateu")
    end = pp.find("private fun parseHeaderTitle", start)
    if end == -1: fail("fim collectTracks playlist nao bateu")
    new_collect = """    private fun collectTracks(node: Any?): List<Track> {
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
        return out
    }

    """
    pp = pp[:start] + new_collect + pp[end:]
    open(PLAYLIST_PARSER, "w", encoding="utf-8").write(pp)
    print("3) YouTubePlaylistParser.collectTracks agora entende lockup VIDEO (fallback UU funciona)")
else:
    print("3) YouTubePlaylistParser ja entende lockup - pulando")

# ---------- 4) YTMusicSearchParser - dono playlist ----------
ys = open(YTM_SEARCH, encoding="utf-8").read()
if "FIX YTM PLAYLIST OWNER" not in ys:
    # 4a) Replace extractPlaylistOwner
    start = ys.find("private fun extractPlaylistOwner")
    if start == -1: fail("ancora extractPlaylistOwner nao bateu")
    end = ys.find("internal fun extractArtistFromSubtitle", start)
    if end == -1: fail("fim extractPlaylistOwner nao bateu")
    new_owner = """    // FIX YTM PLAYLIST OWNER - dono pode vir sem browseId UC (texto puro). E o fallback antigo pegava
    // substringBefore("•") = "Playlist". Subtitle real: "Playlist • Dono • 20 songs" -> dono e o 2o segmento.
    private fun extractPlaylistOwner(subtitleCol: JSONObject?): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs") ?: subtitleCol?.optJSONArray("runs") ?: return null
        // 1. Dono com link UC (mais confiavel)
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·") continue
            val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
            if (browseId?.startsWith("UC") == true) {
                if (text !in listOf("Playlist", "Podcast")) return text
            }
        }
        // 2. Dono em texto puro: primeiro segmento que nao e tipo, nem contagem, nem duracao, nem ano/data/views
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·") continue
            if (YTMusicTextUtils.isTypeWord(text)) continue
            if (YTMusicTextUtils.isCountOrDurationLabel(text)) continue
            if (YTMusicTextUtils.isDuration(text)) continue
            if (YTMusicTextUtils.isYear(text)) continue
            if (YTMusicTextUtils.isDate(text)) continue
            if (YTMusicTextUtils.isViewCount(text)) continue
            if (YTMusicTextUtils.isEpisode(text)) continue
            if (text.length > 80) continue
            val clean = YTMusicTextUtils.cleanArtistName(text).trim()
            if (clean.isNotBlank()) return clean
        }
        return null
    }

    internal fun playlistOwnerFromSubtitleText(subtitle: String): String? {
        // "Playlist • Dono • 20 songs" -> Dono. "Dono • Playlist • ..." -> Dono tambem.
        val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotBlank() }
        for (part in parts) {
            if (YTMusicTextUtils.isTypeWord(part)) continue
            if (YTMusicTextUtils.isCountOrDurationLabel(part)) continue
            if (YTMusicTextUtils.isDuration(part)) continue
            if (YTMusicTextUtils.isYear(part)) continue
            if (YTMusicTextUtils.isDate(part)) continue
            if (YTMusicTextUtils.isViewCount(part)) continue
            if (YTMusicTextUtils.isEpisode(part)) continue
            if (part.length > 80) continue
            val clean = YTMusicTextUtils.cleanArtistName(part).trim()
            if (clean.isNotBlank()) return clean
        }
        return null
    }

    """
    ys = ys[:start] + new_owner + ys[end:]
    # 4b) Fix fallbacks: substringBefore -> playlistOwnerFromSubtitleText
    old_fallback = 'extractPlaylistOwner(subtitleCol) ?: subtitle.substringBefore("•").trim().takeIf { it.isNotBlank() && !YTMusicTextUtils.isTypeWord(it) } ?: "Playlist"'
    new_fallback = 'extractPlaylistOwner(subtitleCol) ?: playlistOwnerFromSubtitleText(subtitle) ?: "Playlist"'
    count = ys.count(old_fallback)
    ys = ys.replace(old_fallback, new_fallback)
    print(f"4) YTMusicSearchParser dono playlist corrigido ({count} fallbacks trocados)")
    open(YTM_SEARCH, "w", encoding="utf-8").write(ys)
else:
    print("4) YTMusicSearchParser ja tem FIX YTM PLAYLIST OWNER - pulando")

# ---------- 5) YTMusicEnricher.fillPlaylistStats - "Playlist" nao e dono ----------
en = open(ENRICHER, encoding="utf-8").read()
 # Reparo primeiro: se uma versao anterior quebrou o suspend, conserta mesmo que o marcador ja exista
if "suspend     private fun isPlaceholderOwner" in en:
    en = en.replace("suspend     private fun isPlaceholderOwner", "private fun isPlaceholderOwner", 1)
    en = en.replace("\n    fun fillPlaylistStats(", "\n    suspend fun fillPlaylistStats(", 1)
    open(ENRICHER, "w", encoding="utf-8").write(en)
    print("5) Reparo: suspend do fillPlaylistStats restaurado")
    en = open(ENRICHER, encoding="utf-8").read()
if "FIX ENRICHER PLAYLIST PLACEHOLDER" not in en:
    anchor = "val p = hit.playlist"
    if anchor not in en: fail("ancora fillPlaylistStats nao bateu")
    # Tem o early-return YOUTUBE do fix v2: precisamos mexer no hasOwner logo depois dele.
    # Procurar o bloco fillPlaylistStats e trocar hasOwner + owner copy
    start = en.find("fun fillPlaylistStats")
    if start == -1: fail("ancora fillPlaylistStats fun nao bateu")
    segment = en[start:start+2000]
    old_has = "val hasOwner = !p.owner.isNullOrBlank()"
    if old_has not in segment: fail("ancora hasOwner nao bateu")
    new_has = """val hasOwner = !p.owner.isNullOrBlank() && !isPlaceholderOwner(p.owner)
                        // FIX ENRICHER PLAYLIST PLACEHOLDER - "Playlist" nao e dono, tem que buscar o real no browse"""
    en = en[:start] + en[start:].replace(old_has, new_has, 1)
    old_copy = "owner = p.owner?.takeIf { it.isNotBlank() } ?: meta.owner,"
    if old_copy not in en[start:start+2500]: fail("ancora owner copy nao bateu")
    new_copy = "owner = p.owner?.takeIf { it.isNotBlank() && !isPlaceholderOwner(it) } ?: meta.owner,"
    en = en[:start] + en[start:].replace(old_copy, new_copy, 1)
    # Repara caso versao anterior tenha quebrado o 'suspend' (suspend + helper colados)
    broken = "suspend     private fun isPlaceholderOwner"
    if broken in en:
        en = en.replace(broken, "private fun isPlaceholderOwner", 1)
        en = en.replace("\n    fun fillPlaylistStats(", "\n    suspend fun fillPlaylistStats(", 1)
        print("5) Reparo: suspend do fillPlaylistStats restaurado")
    # Helper ANTES do 'suspend fun fillPlaylistStats' (nao no meio do suspend)
    helper = """    private fun isPlaceholderOwner(owner: String?): Boolean {
        if (owner.isNullOrBlank()) return true
        val low = owner.trim().lowercase()
        return low in setOf("playlist", "playlists", "podcast", "podcasts", "mix", "album", "álbum", "albums", "álbuns")
    }

    """
    suspend_idx = en.find("suspend fun fillPlaylistStats")
    if suspend_idx != -1:
        en = en[:suspend_idx] + helper + en[suspend_idx:]
    else:
        en = en[:start] + helper + en[start:]
    open(ENRICHER, "w", encoding="utf-8").write(en)
    print("5) fillPlaylistStats agora trata 'Playlist' como dono ausente e busca o real")
else:
    print("5) fillPlaylistStats ja trata placeholder - pulando")

print("Fix canais + dono YT Music OK.")
