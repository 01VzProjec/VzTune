# fix_channel_completo.py - Canal COMPLETO: todos os videos + todas as playlists
# Roda na raiz: python3 fix_channel_completo.py
# POR QUE VINHA INCOMPLETO (pesquisado):
#  1) browseChannel so pegava a 1a pagina de cada aba (30 na aba, 100 no UU) e guardava o token pro loadMore.
#     Se a tela nao chama loadMore, o usuario so ve 100. Numero redondo = bug de paginacao (dev.to 15/nov/2026).
#     Playlist responde com os 100 primeiros + token, tem que mandar o token de volta pro browse pra pegar os proximos 100, e assim por diante.
#  2) Home com 5 videos em destaque fazia o codigo PULAR a aba Videos inteira (if videos.isEmpty()).
#     Destaque != lista completa. Tem que buscar a aba cheia SEMPRE, mesmo que a Home ja tenha alguma coisa.
#  3) Token novo e continuationItemViewModel com token aninhado (continuationCommand.innertubeCommand.continuationCommand.token).
#     E quando tem varias prateleiras, o 1o token do DFS pode ser de uma prateleira pequena, nao da grade principal.
#     Regra do dev.to / invio: o ULTIMO token vence.
#  4) UUSH (shorts via UU) vinha 0 porque YouTubePlaylistParser.collectTracks nao entendia shortsLockupViewModel.
#     Shorts usa shortsLockupViewModel, id em onTap.reelWatchEndpoint.videoId, titulo em overlayMetadata.primaryText.content.
#  5) Shorts via aba nao tinha parseShortsContinuation, entao nem dava pra paginar shorts da aba.
# SNAP TUBE:
#  SnapTube e proprietario, nao open source (Uptodown: License Proprietary, Wikipedia: app proprietario).
#  Nao tem codigo pra copiar. O que se sabe publicamente: ele usa WebView / servidor proprio + youtubei (mesmo InnerTube),
#  e parece completo porque pagina UU + continuation ate o fim, nao porque usa um API secreta.
#  O equivalente aberto disso e o que esse fix faz: UU (UULF/UUSH) + loop de continuation ate acabar ou bater o teto.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
INNER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/innertube/Innertube.kt")
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
PLAYLIST_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubePlaylistParser.kt")
for p in [INNER, CHANNEL_PARSER, YT_REPO, PLAYLIST_PARSER]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda parte1 + parte2_3 + fix_channels_ytmusic_owner antes")

# ---------- 1) Innertube.findContinuationToken -> ULTIMO token vence + ViewModel ----------
t = open(INNER, encoding="utf-8").read()
if "FIX CONTINUATION LAST WINS" not in t:
    start = t.find("fun findContinuationToken")
    if start == -1: fail("ancora findContinuationToken nao bateu em Innertube.kt")
    # fim: proxima "    fun " ou fim do object ( "}\n}" ) - procura "    }\n}" apos start ou proximo fun
    end = t.find("\n    fun ", start + 10)
    if end == -1:
        # ultimo fun do arquivo, vai ate o penultimo } (fecha object)
        end = t.rfind("\n}")
        if end == -1: fail("fim findContinuationToken nao bateu")
    new_fn = """    fun findContinuationToken(node: Any?): String? {
        // FIX CONTINUATION LAST WINS - playlist responde 100 + token, tem que paginar ate o fim.
        // Token novo e continuationItemViewModel com token aninhado:
        // continuationCommand.innertubeCommand.continuationCommand.token (dev.to 15/nov/2026).
        // E quando tem varias prateleiras, o 1o token do DFS pode ser de prateleira pequena, nao da grade principal.
        // Regra: coleta TODOS os tokens e devolve o ULTIMO (grade principal vem por ultimo).
        val tokens = mutableListOf<String>()
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    n.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() && it != "null" && it.length > 10 }?.let { tokens += it }
                    n.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() && it != "null" && it.length > 10 }?.let { tokens += it }
                    // nextContinuationData tambem pode vir como { nextContinuationData: { continuation: ... } } dentro de continuationItemRenderer
                    n.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() && it != "null" && it.length > 10 }?.let { tokens += it }
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(node)
        return tokens.lastOrNull()
    }
"""
    t = t[:start] + new_fn + t[end:]
    open(INNER, "w", encoding="utf-8").write(t)
    print("1) findContinuationToken agora entende ViewModel aninhado e devolve o ULTIMO token")
else:
    print("1) findContinuationToken ja e LAST WINS - pulando")

# ---------- 2) YouTubeChannelParser: parseShortsContinuation ----------
c = open(CHANNEL_PARSER, encoding="utf-8").read()
if "parseShortsContinuation" not in c:
    anchor = "fun parsePlaylistsContinuation"
    if anchor not in c: fail("ancora parsePlaylistsContinuation nao bateu")
    helper = """    fun parseShortsContinuation(json: JSONObject): Pair<List<Track>, String?> {
        // FIX SHORTS CONTINUATION - shorts via aba nao tinha como paginar, agora tem igual videos/playlists
        val shorts = collectShorts(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return shorts.distinctBy { it.id } to cont
    }

    """
    c = c.replace(anchor, helper + anchor, 1)
    open(CHANNEL_PARSER, "w", encoding="utf-8").write(c)
    print("2) parseShortsContinuation adicionado")
else:
    print("2) parseShortsContinuation ja existe - pulando")

# ---------- 3) YouTubePlaylistParser.collectTracks -> shortsLockup ----------
pp = open(PLAYLIST_PARSER, encoding="utf-8").read()
if "shortsLockupViewModel" not in pp:
    # Insere depois do loop de lockup VIDEO, antes do return out
    anchor = "        return out\n    }\n\n    private fun parseHeaderTitle"
    if anchor not in pp: fail("ancora collectTracks return nao bateu em YouTubePlaylistParser")
    shorts_block = """        // UUSH / Shorts via UU: shortsLockupViewModel (id em onTap.reelWatchEndpoint.videoId, titulo em overlayMetadata.primaryText.content)
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

    private fun parseHeaderTitle"""
    pp = pp.replace(anchor, shorts_block, 1)
    open(PLAYLIST_PARSER, "w", encoding="utf-8").write(pp)
    print("3) YouTubePlaylistParser.collectTracks agora entende shortsLockup (UUSH funciona)")
else:
    print("3) YouTubePlaylistParser ja entende shortsLockup - pulando")

# ---------- 4) YouTubeRepository.browseChannel COMPLETO ----------
yt = open(YT_REPO, encoding="utf-8").read()
if "FIX CHANNEL COMPLETO" not in yt:
    start = yt.find("// FIX CHANNEL TABS")
    if start == -1:
        start = yt.find("// PART 2+3 - InnerTube first for browseChannel")
    if start == -1: fail("ancora browseChannel InnerTube nao bateu (roda fix_channels_ytmusic_owner antes)")
    onf = yt.find(".onFailure", start)
    if onf == -1: fail("fim browseChannel InnerTube nao bateu")
    end = yt.find("\n", onf)
    if end == -1: end = len(yt)
    new_block = """        // FIX CHANNEL COMPLETO - busca COMPLETA, nao so a 1a pagina.
        // Antes: pegava 1 pagina (100 no UU, ~30 na aba) e guardava token pro loadMore. Se a tela nao chama loadMore, vinha incompleto.
        // Agora: pagina UU + aba ate acabar ou bater o teto (1000 videos, 500 shorts, 200 playlists, 10 paginas cada).
        // E SEMPRE busca a aba cheia, mesmo que a Home ja tenha 5 videos em destaque (destaque != lista completa).
        // Fonte primaria pra videos/shorts e UU (ordem estavel, 100/pagina): UULF = videos, UUSH = shorts. Aba e fallback.
        // Playlists nao tem UU, entao pagina a aba Playlists com continuation ate o fim.
        runCatching {
            val uc = Regex("UC[\\\\w-]{22}").find(input)?.value
            if (uc != null) {
                val homeJson = InnerTubeYouTube.browse(uc)
                val homeParsed = YouTubeChannelParser.parseChannelPage(homeJson)
                val channelName = homeParsed.channelName
                // ---- VIDEOS via UULF loop ----
                var videos = mutableListOf<com.vztechcore.vztune.core.domain.music.Track>()
                var videosCont: String? = null
                runCatching {
                    var seenTokens = mutableSetOf<String>()
                    var json = InnerTubeYouTube.browse("VL" + "UULF" + uc.drop(2))
                    var parsed = YouTubePlaylistParser.parsePlaylistPage(json, channelName)
                    videos += parsed.tracks
                    videosCont = parsed.continuation
                    var pages = 1
                    while (videos.size < 1000 && videosCont != null && pages < 10) {
                        val token = videosCont ?: break
                        if (!seenTokens.add(token)) break
                        val cj = InnerTubeYouTube.browseContinuation(token)
                        val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                        if (more.isEmpty() && next == null) { videosCont = null; break }
                        videos += more
                        videosCont = next
                        pages++
                    }
                }.onFailure { Log.w(TAG, "UULF loop failed: ${it.message}") }
                // Se UULF deu 0, tenta UU (tudo) e depois aba Videos
                if (videos.isEmpty()) {
                    runCatching {
                        var seenTokens = mutableSetOf<String>()
                        var json = InnerTubeYouTube.browse("VL" + "UU" + uc.drop(2))
                        var parsed = YouTubePlaylistParser.parsePlaylistPage(json, channelName)
                        videos += parsed.tracks
                        videosCont = parsed.continuation
                        var pages = 1
                        while (videos.size < 1000 && videosCont != null && pages < 10) {
                            val token = videosCont ?: break
                            if (!seenTokens.add(token)) break
                            val cj = InnerTubeYouTube.browseContinuation(token)
                            val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                            if (more.isEmpty() && next == null) { videosCont = null; break }
                            videos += more
                            videosCont = next
                            pages++
                        }
                    }.onFailure { Log.w(TAG, "UU loop failed: ${it.message}") }
                }
                if (videos.isEmpty()) {
                    val videosParams = YouTubeChannelParser.tabParams(homeJson, "videos")
                    if (videosParams != null) {
                        runCatching {
                            var seenTokens = mutableSetOf<String>()
                            var json = InnerTubeYouTube.browse(uc, videosParams)
                            var parsed = YouTubeChannelParser.parseChannelPage(json)
                            videos += parsed.videos
                            videosCont = parsed.videosContinuation
                            var pages = 1
                            while (videos.size < 1000 && videosCont != null && pages < 10) {
                                val token = videosCont ?: break
                                if (!seenTokens.add(token)) break
                                val cj = InnerTubeYouTube.browseContinuation(token)
                                val (more, next) = YouTubeChannelParser.parseVideosContinuation(cj)
                                if (more.isEmpty() && next == null) { videosCont = null; break }
                                videos += more
                                videosCont = next
                                pages++
                            }
                        }.onFailure { Log.w(TAG, "Videos tab loop failed: ${it.message}") }
                    }
                }
                // ---- SHORTS via UUSH loop ----
                var shorts = mutableListOf<com.vztechcore.vztune.core.domain.music.Track>()
                var shortsCont: String? = null
                runCatching {
                    var seenTokens = mutableSetOf<String>()
                    var json = InnerTubeYouTube.browse("VL" + "UUSH" + uc.drop(2))
                    var parsed = YouTubePlaylistParser.parsePlaylistPage(json, channelName)
                    shorts += parsed.tracks
                    shortsCont = parsed.continuation
                    var pages = 1
                    while (shorts.size < 500 && shortsCont != null && pages < 10) {
                        val token = shortsCont ?: break
                        if (!seenTokens.add(token)) break
                        val cj = InnerTubeYouTube.browseContinuation(token)
                        val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                        if (more.isEmpty() && next == null) { shortsCont = null; break }
                        shorts += more
                        shortsCont = next
                        pages++
                    }
                }.onFailure { Log.w(TAG, "UUSH loop failed: ${it.message}") }
                if (shorts.isEmpty()) {
                    val shortsParams = YouTubeChannelParser.tabParams(homeJson, "shorts")
                    if (shortsParams != null) {
                        runCatching {
                            var seenTokens = mutableSetOf<String>()
                            var json = InnerTubeYouTube.browse(uc, shortsParams)
                            var parsed = YouTubeChannelParser.parseChannelPage(json)
                            shorts += parsed.shorts
                            shortsCont = parsed.shortsContinuation
                            var pages = 1
                            while (shorts.size < 500 && shortsCont != null && pages < 10) {
                                val token = shortsCont ?: break
                                if (!seenTokens.add(token)) break
                                val cj = InnerTubeYouTube.browseContinuation(token)
                                val (more, next) = YouTubeChannelParser.parseShortsContinuation(cj)
                                if (more.isEmpty() && next == null) { shortsCont = null; break }
                                shorts += more
                                shortsCont = next
                                pages++
                            }
                            // Shorts tab as vezes devolve videos longos no campo videos - junta se shorts ainda vazio
                            if (shorts.isEmpty() && parsed.videos.isNotEmpty()) shorts += parsed.videos
                        }.onFailure { Log.w(TAG, "Shorts tab loop failed: ${it.message}") }
                    }
                }
                // Se videos vieram de UU (tudo), tira os que sao shorts
                if (shorts.isNotEmpty() && videos.isNotEmpty()) {
                    val shortIds = shorts.map { it.id }.toSet()
                    videos = videos.filter { it.id !in shortIds }.toMutableList()
                }
                // Preenche artista em branco com nome do canal (lockup nao traz autor)
                if (channelName.isNotBlank()) {
                    videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }.toMutableList()
                    shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }.toMutableList()
                }
                // ---- PLAYLISTS via aba Playlists loop (SEMPRE, mesmo que Home tenha destaque) ----
                var playlists = mutableListOf<com.vztechcore.vztune.core.domain.music.Playlist>()
                var playlistsCont: String? = null
                val playlistsParams = YouTubeChannelParser.tabParams(homeJson, "playlists")
                if (playlistsParams != null) {
                    runCatching {
                        var seenTokens = mutableSetOf<String>()
                        var json = InnerTubeYouTube.browse(uc, playlistsParams)
                        var parsed = YouTubeChannelParser.parseChannelPage(json)
                        playlists += parsed.playlists
                        playlistsCont = parsed.playlistsContinuation
                        var pages = 1
                        while (playlists.size < 200 && playlistsCont != null && pages < 5) {
                            val token = playlistsCont ?: break
                            if (!seenTokens.add(token)) break
                            val cj = InnerTubeYouTube.browseContinuation(token)
                            val (more, next) = YouTubeChannelParser.parsePlaylistsContinuation(cj)
                            if (more.isEmpty() && next == null) { playlistsCont = null; break }
                            playlists += more
                            playlistsCont = next
                            pages++
                        }
                    }.onFailure { Log.w(TAG, "Playlists tab loop failed: ${it.message}") }
                }
                // Guarda token restante pro loadMore (se bateu o teto, ainda tem mais)
                ytChannelVideoContinuations[uc] = videosCont
                ytChannelVideoContinuations[input] = videosCont
                ytChannelPlaylistContinuations[uc] = playlistsCont
                ytChannelPlaylistContinuations[input] = playlistsCont
                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {
                    return@withContext ChannelPage(
                        videos = videos.distinctBy { it.id },
                        shorts = shorts.distinctBy { it.id },
                        playlists = playlists.distinctBy { it.id },
                        channelName = channelName.ifBlank { null },
                        avatarUrl = homeParsed.avatarUrl,
                        bannerUrl = homeParsed.bannerUrl,
                        description = homeParsed.description,
                        subscriberCount = homeParsed.subscriberCount,
                        hasMoreVideos = videosCont != null,
                        hasMorePlaylists = playlistsCont != null
                    )
                }
                // Tudo vazio via InnerTube: nao retorna, deixa o NewPipe abaixo tentar.
            }
        }.onFailure { Log.w(TAG, "InnerTube browseChannel failed, fallback NewPipe: ${it.message}") }"""
    yt = yt[:start] + new_block + yt[end:]
    open(YT_REPO, "w", encoding="utf-8").write(yt)
    print("4) browseChannel agora pagina UULF/UUSH + aba Playlists ate o fim (1000/500/200), sempre busca aba cheia")
else:
    print("4) browseChannel ja e COMPLETO - pulando")

print("Fix canal completo OK.")
