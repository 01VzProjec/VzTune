#!/bin/bash
# VzTune FIX8 DEFINITIVO - Canal + Shorts
# O que faz:
#  1) Canal: NewPipe primeiro, sem converter canal em artista do YT Music.
#     - Video: StreamExtractor (watch + shorts) -> uploaderUrl -> UC. Se falhar, tenta de novo com URL curta.
#     - Playlist: distingue playlist de video (PL/UU/UC) de playlist de musica (OLAK/RD/VL) e radio. UU vira UC direto.
#     - Canal grande: browseChannel NAO retorna null se videos vierem vazios. Retorna cabecalho + tenta 3 fontes de video.
#  2) ArtistViewModel: se UC tem canal real, confere nome. Se YT Music devolver nome totalmente diferente (ex: JazzGhost -> Moldrin), ignora YT Music e mostra o canal real.
#  3) Shorts: 3 tentativas (filtro shorts -> busca normal -> busca "query shorts") + parser que aceita /shorts/ E watch curto (<=65s) como fallback. Antes o parser jogava tudo fora e a aba ficava vazia.
#  4) MediaList: onOpenArtist tenta resolver de novo se artistId estiver vazio, em vez de desistir.
# Idempotente: pode rodar 2x, nao duplica. Roda no ~/VzTune no Termux.
set -e
cd ~/VzTune 2>/dev/null || cd "$(dirname "$0")" || true
python3 - << 'PY'
import re, sys

def read(p):
    try:
        with open(p, encoding="utf-8") as f: return f.read()
    except FileNotFoundError:
        print(f"AVISO: nao achei {p} - pulando")
        return None

def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)

def must(cond, msg):
    if cond:
        print(f"OK: {msg}")
    else:
        print(f"FALHOU: {msg}")
        sys.exit(1)

def dedup_block(t, marker, block):
    # Se marker ja existe mais de 1x, mantem so a primeira ocorrencia do bloco inteiro
    if t.count(marker) <= 1:
        return t
    # Remove duplicatas: mantem primeira, apaga as demais ocorrencias do bloco
    parts = t.split(block)
    if len(parts) <= 2:
        return t
    return parts[0] + block + "".join(parts[1:]).replace(block, "")

# ================= 1) YouTubeRepository =================
p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if t is not None:
    # Garante mapa de sessao de Shorts
    if "shortSearches" not in t:
        if "private val playlistSearches" in t:
            t = t.replace("private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()",
                          "private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()\n    private val shortSearches = mutableMapOf<String, VideoSearchSession>()")
            write(p, t)
        else:
            print("AVISO: playlistSearches nao achado, shortSearches vai ser criado junto com ShortPage")
    t = read(p)

    # --- FIX8 marker: remove versao antiga de searchShorts / parseShortItems / browseChannel / getChannelForVideo / getChannelForPlaylist para reescrever limpo ---
    # Nao apagamos o arquivo inteiro, so substituimos as funcoes por versao definitiva usando ancora.
    # 1a) searchShorts + parseShortItems: substitui bloco antigo se existir
    if "FIX8_SHORTS" not in t:
        # Remove searchShorts antigo (do FIX4) se existir: do "suspend fun searchShorts" ate o proximo "\n    suspend fun" ou "\n    private fun" ou "\n    // =="
        if "suspend fun searchShorts" in t:
            start = t.find("    suspend fun searchShorts")
            # procura fim: proximo "    suspend fun" ou "    private fun parse" depois do start + 500
            nxt1 = t.find("\n    suspend fun", start + 500)
            nxt2 = t.find("\n    private fun parseShortItems", start + 500)
            nxt3 = t.find("\n    // ==", start + 500)
            cands = [x for x in [nxt1, nxt2, nxt3] if x != -1]
            end = min(cands) if cands else -1
            if end != -1:
                t = t[:start] + t[end:]
                print("OK: YouTubeRepository: searchShorts antigo removido para reescrever")
            else:
                print("AVISO: nao achei fim do searchShorts antigo")
        if "fun parseShortItems" in t:
            start = t.find("    private fun parseShortItems")
            if start == -1:
                start = t.find("    fun parseShortItems")
            if start != -1:
                nxt1 = t.find("\n    private fun", start + 500)
                nxt2 = t.find("\n    suspend fun", start + 500)
                nxt3 = t.find("\n    // ==", start + 500)
                cands = [x for x in [nxt1, nxt2, nxt3] if x != -1]
                end = min(cands) if cands else -1
                if end != -1:
                    t = t[:start] + t[end:]
                    print("OK: YouTubeRepository: parseShortItems antigo removido")
        # Insere searchShorts definitivo antes de SMART VIDEO MATCH ou antes de browseChannel
        anchor = "// ================= SMART VIDEO MATCH"
        if anchor not in t:
            anchor = "suspend fun browseChannel"
        must(anchor in t, "YouTubeRepository: ancora para searchShorts FIX8")
        shorts_code = """    // FIX8_SHORTS - Shorts definitivo: 3 tentativas + parser que nao joga tudo fora.
    // Antes: filtro "shorts" falhava e o parser so aceitava /shorts/, entao busca generica voltava vazia.
    // Agora: 1) filtro shorts, 2) busca normal, 3) busca "query shorts". Parser aceita /shorts/ e, como fallback, watch curto (<=65s).
    data class ShortPage(
        val items: List<SearchHit.ShortHit>,
        val hasMore: Boolean,
    )

    suspend fun searchShorts(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
        searchId: String = query,
    ): ShortPage = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchShorts: NewPipe not initialized")
            return@withContext ShortPage(emptyList(), false)
        }
        if (loadMore) {
            val session = shortSearches[searchId] ?: return@withContext ShortPage(emptyList(), false)
            val page = session.nextPage ?: return@withContext ShortPage(emptyList(), false)
            return@withContext runCatching {
                val next = session.extractor.getPage(page)
                session.nextPage = next.nextPage
                ShortPage(parseShortItemsLoose(next.items).take(pageSize), session.nextPage != null)
            }.getOrElse { e ->
                Log.e(TAG, "searchShorts loadMore failed for query=$query", e)
                ShortPage(emptyList(), false)
            }
        }
        // Tentativa 1: filtro shorts oficial
        val attempt1 = runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("shorts"), null)
            extractor.fetchPage()
            val items = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)
            if (items.isNotEmpty()) {
                shortSearches[searchId] = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                return@withContext ShortPage(items, extractor.initialPage.nextPage != null)
            }
            null
        }.getOrNull()
        if (attempt1 != null) return@withContext attempt1
        // Tentativa 2: busca normal, filtra so /shorts/
        val attempt2 = runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            val items = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)
            if (items.isNotEmpty()) {
                shortSearches[searchId] = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                return@withContext ShortPage(items, extractor.initialPage.nextPage != null)
            }
            null
        }.getOrNull()
        if (attempt2 != null) return@withContext attempt2
        // Tentativa 3: busca "query shorts" + parser frouxo (watch curto conta como Shorts)
        return@withContext runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor("$query shorts")
            extractor.fetchPage()
            val strict = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)
            val items = if (strict.isNotEmpty()) strict else parseShortItemsLoose(extractor.initialPage.items).take(pageSize)
            shortSearches[searchId] = VideoSearchSession(extractor, extractor.initialPage.nextPage)
            ShortPage(items, extractor.initialPage.nextPage != null)
        }.getOrElse { e ->
            Log.e(TAG, "searchShorts failed for query=$query", e)
            ShortPage(emptyList(), false)
        }
    }

    private fun parseShortItemsStrict(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            if (!rawUrl.contains("/shorts/")) return@mapNotNull null
            val videoId = extractVideoId(rawUrl) ?: return@mapNotNull null
            SearchHit.ShortHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parseShortItemsLoose(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            val isShortsUrl = rawUrl.contains("/shorts/")
            // Fallback: video curto (1s a 65s) com URL de watch pode ser Shorts que o YouTube devolveu como video normal.
            val isShortDuration = stream.duration in 1..65
            if (!isShortsUrl && !isShortDuration) return@mapNotNull null
            val videoId = extractVideoId(rawUrl) ?: return@mapNotNull null
            SearchHit.ShortHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    // Compat: nome antigo usado em outros lugares aponta para o parser estrito.
    private fun parseShortItems(items: List<InfoItem>): List<SearchHit.ShortHit> = parseShortItemsStrict(items)

"""
        if anchor.startswith("//"):
            t = t.replace(anchor, shorts_code + "    " + anchor)
        else:
            t = t.replace("    suspend fun browseChannel", shorts_code + "    suspend fun browseChannel")
        write(p, t)
        must("FIX8_SHORTS" in read(p), "YouTubeRepository: searchShorts FIX8")
    t = read(p)

    # 1b) extractVideoId: garante que entende /shorts/, watch, youtu.be, embed, v=
    if "substringAfter(\"/shorts/\")" not in t:
        # procura fun extractVideoId
        if "fun extractVideoId" in t:
            start = t.find("fun extractVideoId")
            # substitui funcao inteira ate proxima funcao
            nxt = t.find("\n    private fun", start + 200)
            nxt2 = t.find("\n    suspend fun", start + 200)
            nxt3 = t.find("\n    fun ", start + 200)
            cands = [x for x in [nxt, nxt2, nxt3] if x != -1]
            end = min(cands) if cands else -1
            if end != -1:
                new_fn = """    private fun extractVideoId(url: String): String? {
        val clean = url.trim()
        if (clean.contains("/shorts/")) {
            val id = clean.substringAfter("/shorts/").substringBefore("?").substringBefore("/").substringBefore("#").trim()
            if (id.length == 11) return id
            if (id.isNotBlank()) return id
        }
        if (clean.contains("youtu.be/")) {
            val id = clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("/").substringBefore("#").trim()
            if (id.isNotBlank()) return id
        }
        if (clean.contains("v=")) {
            val id = clean.substringAfter("v=").substringBefore("&").substringBefore("#").trim()
            if (id.isNotBlank()) return id
        }
        if (clean.contains("/embed/")) {
            val id = clean.substringAfter("/embed/").substringBefore("?").substringBefore("/").trim()
            if (id.isNotBlank()) return id
        }
        return null
    }
"""
                line_start = t.rfind("\n", 0, start) + 1
                t = t[:line_start] + new_fn + t[end:]
                write(p, t)
                print("OK: YouTubeRepository: extractVideoId entende shorts/watch/youtu.be")
    t = read(p)

    # 1c) getChannelForVideo DEFINITIVO: tenta watch, depois shorts, depois youtu.be. Nunca converte para artista do YT Music.
    if "FIX8_CHANNEL_VIDEO" not in t:
        if "fun getChannelForVideo" in t:
            start = t.find("fun getChannelForVideo")
            # volta ate o inicio da funcao (suspend fun ou fun)
            start = t.rfind("    suspend fun", 0, start + 10)
            if start == -1:
                start = t.rfind("    fun", 0, start + 10)
            nxt = t.find("\n    /**", start + 500)
            nxt2 = t.find("\n    private fun extractChannelId", start + 500)
            nxt3 = t.find("\n    suspend fun getChannelForPlaylist", start + 500)
            cands = [x for x in [nxt, nxt2, nxt3] if x != -1]
            end = min(cands) if cands else -1
            if start != -1 and end != -1:
                new_fn = """    // FIX8_CHANNEL_VIDEO - Canal real do video via NewPipe, 3 URLs. Sem busca por nome, sem YT Music.
    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        val cleanId = videoId.trim().substringAfter("v=").substringAfter("/shorts/").substringBefore("?").substringBefore("&").trim()
        if (cleanId.isBlank()) return@withContext null
        val urls = listOf(
            "https://www.youtube.com/watch?v=$cleanId",
            "https://www.youtube.com/shorts/$cleanId",
            "https://youtu.be/$cleanId",
        )
        for (url in urls) {
            val info = runCatching {
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                extractor.fetchPage()
                val uploaderName = extractor.uploaderName?.trim().orEmpty()
                val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
                if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
                val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
                val avatarUrl = runCatching { extractor.uploaderAvatars.lastOrNull()?.url }.getOrNull()
                    ?: runCatching { extractor.thumbnails?.lastOrNull()?.url }.getOrNull()
                YouTubeChannelInfo(
                    channelId = channelId,
                    channelName = cleanChannelName(uploaderName),
                    avatarUrl = avatarUrl,
                    channelUrl = uploaderUrl,
                )
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }

"""
                t = t[:start] + new_fn + t[end:]
                write(p, t)
                must("FIX8_CHANNEL_VIDEO" in read(p), "YouTubeRepository: getChannelForVideo FIX8")
    t = read(p)

    # 1d) getChannelForPlaylist DEFINITIVO: distingue tipos, UU vira UC direto, OLAK/RD nao sao playlist de canal
    if "FIX8_CHANNEL_PLAYLIST" not in t:
        if "fun getChannelForPlaylist" in t:
            start = t.find("fun getChannelForPlaylist")
            start = t.rfind("    suspend fun", 0, start + 10)
            if start == -1:
                start = t.rfind("    fun", 0, start + 10)
            # fim: proximo "    /**" ou "    private fun" ou "    suspend fun browseChannel" ou "    data class"
            nxts = []
            for pat in ["\n    /**", "\n    private fun", "\n    suspend fun browseChannel", "\n    data class ChannelHeader", "\n    suspend fun searchShorts"]:
                idx = t.find(pat, start + 800)
                if idx != -1: nxts.append(idx)
            end = min(nxts) if nxts else -1
            if start != -1 and end != -1:
                new_fn = """    // FIX8_CHANNEL_PLAYLIST - Dono real da playlist. Distingue video de musica.
    // PL/UU/UC = playlist de video -> NewPipe. OLAK/RD/RDAMVM = radio/mix do YT Music -> nao tem dono de canal, retorna null.
    // VL = prefixo do YT Music, tira. UU = playlist de uploads -> UC direto, sem precisar de extractor.
    suspend fun getChannelForPlaylist(playlistId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        var rawId = playlistId.trim().removePrefix("YT:").removePrefix("VL").trim()
        if (rawId.isBlank()) return@withContext null
        // Radio / mix nao tem dono de canal
        if (rawId.startsWith("OLAK") || rawId.startsWith("RD") || rawId.startsWith("RDAMVM") || rawId.startsWith("RDEM")) {
            return@withContext null
        }
        // UU... = uploads do canal -> UC... direto
        if (rawId.startsWith("UU") && rawId.length >= 24) {
            val uc = "UC" + rawId.drop(2)
            val header = runCatching {
                val ex = ServiceList.YouTube.getChannelExtractor("https://www.youtube.com/channel/$uc")
                ex.fetchPage()
                YouTubeChannelInfo(
                    channelId = uc,
                    channelName = cleanChannelName(ex.name ?: ""),
                    avatarUrl = runCatching { ex.avatars?.lastOrNull()?.url }.getOrNull(),
                    channelUrl = "https://www.youtube.com/channel/$uc",
                )
            }.getOrNull()
            if (header != null && header.channelName.isNotBlank()) return@withContext header
            return@withContext YouTubeChannelInfo(uc, "", null, "https://www.youtube.com/channel/$uc")
        }
        // UC direto = ja e o canal
        if (rawId.startsWith("UC") && rawId.length >= 24) {
            return@withContext YouTubeChannelInfo(rawId, "", null, "https://www.youtube.com/channel/$rawId")
        }
        // Playlist de video normal: PlaylistExtractor -> uploaderUrl -> UC
        return@withContext runCatching {
            val extractor = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$rawId")
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            YouTubeChannelInfo(
                channelId = channelId,
                channelName = cleanChannelName(uploaderName),
                avatarUrl = null,
                channelUrl = uploaderUrl,
            )
        }.getOrNull()
    }

"""
                t = t[:start] + new_fn + t[end:]
                write(p, t)
                must("FIX8_CHANNEL_PLAYLIST" in read(p), "YouTubeRepository: getChannelForPlaylist FIX8")
    t = read(p)

    # 1e) browseChannel DEFINITIVO: aceita UC, @handle, URL. Nunca retorna null se tem cabecalho. 3 fontes de video.
    if "FIX8_BROWSE_CHANNEL" not in t:
        if "suspend fun browseChannel" in t:
            start = t.find("    suspend fun browseChannel")
            # fim: proximo "    private fun formatSubscribers" ou "    private fun trimDecimal" ou "    suspend fun buildVideoQueue"
            nxts = []
            for pat in ["\n    private fun formatSubscribers", "\n    suspend fun buildVideoQueue", "\n    private fun trimDecimal"]:
                idx = t.find(pat, start + 800)
                if idx != -1: nxts.append(idx)
            end = min(nxts) if nxts else -1
            if start != -1 and end != -1:
                new_fn = """    // FIX8_BROWSE_CHANNEL - Canal grande abre mesmo se UU vier vazio. Aceita UC, @handle e URL.
    // Antes: se UU viesse vazio, retornava null e a tela mostrava erro. Agora: retorna cabecalho + tenta 3 fontes.
    suspend fun browseChannel(channelIdOrHandle: String): ChannelPage? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        val input = channelIdOrHandle.trim()
        if (input.isBlank()) return@withContext null
        // Normaliza: UC..., @handle, youtube.com/channel/UC, youtube.com/@handle, youtube.com/c/nome
        val channelUrl = when {
            input.startsWith("https://") || input.startsWith("http://") -> input
            input.startsWith("@") -> "https://www.youtube.com/$input"
            input.startsWith("UC") -> "https://www.youtube.com/channel/$input"
            input.startsWith("youtube.com/") -> "https://www.$input"
            else -> "https://www.youtube.com/@$input"
        }
        val header = runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
            extractor.fetchPage()
            ChannelHeader(
                name = runCatching { extractor.name }.getOrNull()?.trim().orEmpty(),
                avatar = runCatching { extractor.avatars?.lastOrNull()?.url }.getOrNull(),
                banner = runCatching { extractor.banners?.lastOrNull()?.url }.getOrNull(),
                description = runCatching { extractor.description }.getOrNull().orEmpty(),
                subs = runCatching { extractor.subscriberCount.takeIf { it > 0 }?.let { formatSubscribers(it) } }.getOrNull(),
            )
        }.getOrNull()
        // Resolve UC real: do header via ChannelExtractor.id, ou do input se ja for UC, ou via uploaderUrl
        val resolvedUc = runCatching {
            val ex = ServiceList.YouTube.getChannelExtractor(channelUrl)
            ex.fetchPage()
            ex.id?.takeIf { it.startsWith("UC") }
        }.getOrNull() ?: Regex("UC[\\\\w-]{22}").find(input)?.value
        val channelName = header?.name.orEmpty()
        if (header == null && resolvedUc == null) return@withContext null
        val uc = resolvedUc ?: "UC"
        // Videos: fonte 1 = UU, fonte 2 = /videos como playlist, fonte 3 = busca por nome do canal
        var videos: List<Track> = emptyList()
        if (uc.startsWith("UC") && uc.length >= 24) {
            videos = runCatching {
                val uploadsId = "UU" + uc.drop(2)
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$uploadsId")
                pl.fetchPage()
                pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE)
                }
            }.getOrDefault(emptyList())
        }
        if (videos.isEmpty() && uc.startsWith("UC")) {
            videos = runCatching {
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/channel/$uc/videos")
                pl.fetchPage()
                pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE)
                }
            }.getOrDefault(emptyList())
        }
        if (videos.isEmpty() && channelName.isNotBlank()) {
            videos = runCatching {
                val ex = ServiceList.YouTube.getSearchExtractor(channelName, listOf("videos"), null)
                ex.fetchPage()
                parseVideoItems(ex.initialPage.items).map { it.track }.filter { it.artist.contains(channelName, ignoreCase = true) || channelName.contains(it.artist, ignoreCase = true) }.take(20)
            }.getOrDefault(emptyList())
        }
        // Shorts do canal: tenta /shorts como playlist, se falhar busca Shorts pelo nome
        var shorts: List<Track> = emptyList()
        if (uc.startsWith("UC")) {
            shorts = runCatching {
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/channel/$uc/shorts")
                pl.fetchPage()
                pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE)
                }
            }.getOrDefault(emptyList())
        }
        if (shorts.isEmpty() && channelName.isNotBlank()) {
            shorts = runCatching { searchShorts(channelName, loadMore = false, pageSize = 12, searchId = "channel:$uc").items.map { it.track } }.getOrDefault(emptyList())
        }
        ChannelPage(
            videos = videos,
            shorts = shorts,
            playlists = emptyList(),
            channelName = channelName.takeIf { it.isNotBlank() },
            avatarUrl = header?.avatar,
            bannerUrl = header?.banner,
            description = header?.description?.takeIf { it.isNotBlank() },
            subscriberCount = header?.subs,
        )
    }

"""
                # Slice PRIMEIRO com os indices ja calculados, depois ajusta ChannelPage/ChannelHeader no resultado.
                t = t[:start] + new_fn + t[end:]
                # Garante ChannelHeader existe
                if "data class ChannelHeader" not in t:
                    t = t.replace("    // FIX8_BROWSE_CHANNEL", "    private data class ChannelHeader(\n        val name: String,\n        val avatar: String?,\n        val banner: String?,\n        val description: String,\n        val subs: String?,\n    )\n\n    // FIX8_BROWSE_CHANNEL")
                # Garante ChannelPage tem shorts/playlists/banner/descricao
                if "val shorts:" not in t:
                    t = t.replace("    data class ChannelPage(\n        val videos: List<Track>,",
                                  "    data class ChannelPage(\n        val videos: List<Track>,\n        val shorts: List<Track> = emptyList(),\n        val playlists: List<Playlist> = emptyList(),")
                    if "val bannerUrl" not in t:
                        t = t.replace("        val avatarUrl: String?,", "        val avatarUrl: String?,\n        val bannerUrl: String? = null,\n        val description: String? = null,")
                write(p, t)
                must("FIX8_BROWSE_CHANNEL" in read(p), "YouTubeRepository: browseChannel FIX8")
    t = read(p)
    # Garante Playlist import se ChannelPage usa Playlist
    if "val playlists: List<Playlist>" in t and "import com.vztechcore.vztune.core.domain" not in t:
        print("AVISO: verifica import Playlist em YouTubeRepository")
    # SEGURANCA FIX8: se algum parser sumiu por causa de substituicao anterior, reinsere antes de getChannelForVideo.
    t = read(p)
    if "fun parseVideoItems" not in t:
        if "suspend fun getChannelForVideo" in t:
            parsers = """    private fun parseVideoItems(items: List<InfoItem>): List<SearchHit.VideoHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
            SearchHit.VideoHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parsePlaylistItems(items: List<InfoItem>): List<SearchHit.PlaylistHit> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { playlist ->
            val listId = extractListId(playlist.url) ?: return@mapNotNull null
            SearchHit.PlaylistHit(
                Playlist(
                    id = listId,
                    title = playlist.name ?: "Playlist",
                    owner = playlist.uploaderName ?: "",
                    thumbnailUrl = playlist.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun extractVideoId(url: String): String? {
        val clean = url.trim()
        if (clean.contains("/shorts/")) {
            val id = clean.substringAfter("/shorts/").substringBefore("?").substringBefore("/").substringBefore("#").trim()
            if (id.isNotBlank()) return id
        }
        if (clean.contains("youtu.be/")) {
            val id = clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("/").substringBefore("#").trim()
            if (id.isNotBlank()) return id
        }
        if (clean.contains("v=")) {
            val id = clean.substringAfter("v=").substringBefore("&").substringBefore("#").trim()
            if (id.isNotBlank()) return id
        }
        return null
    }

    private fun extractListId(url: String): String? = try {
        android.net.Uri.parse(url).getQueryParameter("list")
    } catch (e: Exception) {
        null
    }

"""
            t = t.replace("    // FIX8_CHANNEL_VIDEO", parsers + "    // FIX8_CHANNEL_VIDEO")
            write(p, t)
            print("OK: YouTubeRepository: parsers restaurados (seguranca FIX8)")
    t = read(p)
    # Deduplica getChannelForVideo: mantem o FIX8 (primeiro), apaga o antigo (segundo) se existir
    if t.count("fun getChannelForVideo") > 1:
        first = t.find("fun getChannelForVideo")
        second = t.find("fun getChannelForVideo", first + 10)
        line_start = t.rfind("\n", 0, second) + 1
        # O bloco antigo vai ate o doc de extractChannelId
        end2 = t.find("private fun extractChannelId", second)
        if end2 != -1:
            # volta para o /** antes de extractChannelId, para manter o doc
            doc_start = t.rfind("/**", 0, end2)
            if doc_start != -1 and doc_start > line_start:
                end2 = doc_start
            # Mas mantem o doc de extractChannelId: corta de line_start (que inclui o doc antigo do getChannelForVideo) ate end2
            # line_start ja esta no doc antigo, entao apagar de line_start ate end2 remove o getChannelForVideo antigo + seu doc
            t = t[:line_start] + t[end2:]
            write(p, t)
            print("OK: YouTubeRepository: deduplicado getChannelForVideo antigo")
    t = read(p)
    # Se getChannelForPlaylist nao existe, insere depois de getChannelForVideo
    if "fun getChannelForPlaylist" not in t:
        if "FIX8_CHANNEL_VIDEO" in t:
            playlist_fn = """    // FIX8_CHANNEL_PLAYLIST - Dono real da playlist. Distingue video de musica.
    suspend fun getChannelForPlaylist(playlistId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        var rawId = playlistId.trim().removePrefix("YT:").removePrefix("VL").trim()
        if (rawId.isBlank()) return@withContext null
        if (rawId.startsWith("OLAK") || rawId.startsWith("RD") || rawId.startsWith("RDAMVM") || rawId.startsWith("RDEM")) {
            return@withContext null
        }
        if (rawId.startsWith("UU") && rawId.length >= 24) {
            val uc = "UC" + rawId.drop(2)
            return@withContext YouTubeChannelInfo(uc, "", null, "https://www.youtube.com/channel/$uc")
        }
        if (rawId.startsWith("UC") && rawId.length >= 24) {
            return@withContext YouTubeChannelInfo(rawId, "", null, "https://www.youtube.com/channel/$rawId")
        }
        return@withContext runCatching {
            val extractor = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$rawId")
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            YouTubeChannelInfo(channelId = channelId, channelName = cleanChannelName(uploaderName), avatarUrl = null, channelUrl = uploaderUrl)
        }.getOrNull()
    }

"""
            # Insere apos o fim do getChannelForVideo FIX8: procura o proximo "/**" apos FIX8_CHANNEL_VIDEO e insere antes dele
            idx = t.find("FIX8_CHANNEL_VIDEO")
            nxt = t.find("\n    /**", idx + 500)
            if nxt != -1:
                t = t[:nxt+1] + playlist_fn + t[nxt+1:]
                write(p, t)
                print("OK: YouTubeRepository: getChannelForPlaylist inserido (faltava)")
    print("YouTubeRepository FIX8 pronto")

# ================= 2) MusicRepository - deduplica getChannelFor* =================
p2 = "data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt"
t2 = read(p2)
if t2 is not None:
    # Se tiver 2x getChannelForVideo, apaga duplicata mantendo 1
    count = t2.count("suspend fun getChannelForVideo")
    if count > 1:
        # Mantem primeira, remove demais blocos (ate proxima suspend fun)
        parts = t2.split("    suspend fun getChannelForVideo")
        # parts[0] + primeira + resto sem as duplicatas
        first = parts[1]
        # first vai ate proxima suspend fun getChannelForPlaylist
        rest = "".join(parts[2:])
        # Remove ocorrencias duplicadas de getChannelForVideo no rest
        rest = re.sub(r"\s*suspend fun getChannelForVideo.*?runCatching \{ youTubeRepository\.getChannelForVideo.*?\.getOrNull\(\)\s*", "", rest, flags=re.DOTALL)
        t2 = parts[0] + "    suspend fun getChannelForVideo" + first + rest
        write(p2, t2)
        print(f"OK: MusicRepository: deduplicado getChannelForVideo ({count} -> 1)")
    count2 = t2.count("suspend fun getChannelForPlaylist")
    if count2 > 1:
        parts = t2.split("    suspend fun getChannelForPlaylist")
        first = parts[1]
        rest = "".join(parts[2:])
        rest = re.sub(r"\s*suspend fun getChannelForPlaylist.*?runCatching \{ youTubeRepository\.getChannelForPlaylist.*?\.getOrNull\(\)\s*", "", rest, flags=re.DOTALL)
        t2 = parts[0] + "    suspend fun getChannelForPlaylist" + first + rest
        write(p2, t2)
        print(f"OK: MusicRepository: deduplicado getChannelForPlaylist ({count2} -> 1)")
    t2 = read(p2)
    if "fun getChannelForVideo" not in t2:
        if "import com.vztechcore.vztune.data.youtube.YouTubeRepository" in t2:
            t2 = t2.replace("import com.vztechcore.vztune.data.youtube.YouTubeRepository",
                            "import com.vztechcore.vztune.data.youtube.YouTubeChannelInfo\nimport com.vztechcore.vztune.data.youtube.YouTubeRepository")
        code = """    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? =
        runCatching { youTubeRepository.getChannelForVideo(videoId) }.getOrNull()

    suspend fun getChannelForPlaylist(playlistId: String): YouTubeChannelInfo? =
        runCatching { youTubeRepository.getChannelForPlaylist(playlistId) }.getOrNull()

"""
        if "    /** Extração de áudio" in t2:
            t2 = t2.replace("    /** Extração de áudio", code + "    /** Extração de áudio")
        else:
            t2 = t2.replace("    suspend fun getStreamUrl", code + "    suspend fun getStreamUrl")
        write(p2, t2)
        must("getChannelForPlaylist" in read(p2), "MusicRepository: getChannelForPlaylist existe")
    print("MusicRepository FIX8 pronto")

# ================= 3) ArtistViewModel - nao abre artista errado =================
p3 = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
t3 = read(p3)
if t3 is not None and "FIX8_ARTIST" not in t3:
    if "private fun loadContent" in t3:
        start = t3.find("    private fun loadContent")
        nxt = t3.find("\n    /** Plays a random", start)
        if nxt == -1:
            nxt = t3.find("\n    fun onRandomClick", start)
        if start != -1 and nxt != -1:
            new_fn = """    // FIX8_ARTIST - Se UC tem canal real, o canal e a verdade. Se YT Music devolver nome totalmente diferente, ignora YT Music.
    // Antes: se YT Music devolvesse qualquer artista nao-vazio (ex: Moldrin para JazzGhost), mostrava o errado e nunca tentava o canal.
    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isChannelId = route.id.startsWith("UC")
            val channel = if (isChannelId) runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull() else null
            val music = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            val musicEmpty = music == null || (music.topSongs.isEmpty() && music.albums.isEmpty() && music.singles.isEmpty() && music.videos.isEmpty() && music.featured.isEmpty() && music.relatedArtists.isEmpty())
            if (channel != null && channel.channelName != null) {
                val channelNameNorm = channel.channelName.lowercase().trim()
                val musicNameNorm = (music?.name ?: "").lowercase().trim()
                val namesMatch = !musicEmpty && (musicNameNorm.isNotBlank() && (channelNameNorm.contains(musicNameNorm) || musicNameNorm.contains(channelNameNorm) || channelNameNorm.split(" ").firstOrNull() == musicNameNorm.split(" ").firstOrNull()))
                if (!musicEmpty && namesMatch) {
                    // Mesmo artista nos dois mundos: mostra YT Music + videos do canal
                    _uiState.update {
                        it.copy(
                            isLoading = false, errorMessage = null,
                            name = music.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                            thumbnailUrl = music.thumbnailUrl ?: channel.avatarUrl ?: it.thumbnailUrl,
                            subscribers = music.subscribers ?: channel.subscriberCount ?: it.subscribers,
                            views = music.views, description = music.description,
                            topSongs = music.topSongs, songsMore = music.songsMore,
                            albums = music.albums, albumsMore = music.albumsMore,
                            singles = music.singles, singlesMore = music.singlesMore,
                            videos = if (channel.videos.isNotEmpty()) channel.videos else music.videos,
                            videosMore = music.videosMore, featured = music.featured, featuredMore = music.featuredMore,
                            relatedArtists = music.relatedArtists,
                        )
                    }
                    return@launch
                }
                if (channel.videos.isNotEmpty() || channel.shorts.isNotEmpty() || channel.channelName != null) {
                    // Canal real existe: mostra ele, mesmo que YT Music tenha devolvido artista diferente ou vazio.
                    _uiState.update {
                        it.copy(
                            isLoading = false, errorMessage = null,
                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                            subscribers = channel.subscriberCount ?: it.subscribers,
                            description = channel.description ?: it.description,
                            videos = channel.videos,
                        )
                    }
                    return@launch
                }
            }
            if (!musicEmpty && music != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false, errorMessage = null,
                        name = music.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                        thumbnailUrl = music.thumbnailUrl ?: it.thumbnailUrl,
                        subscribers = music.subscribers, views = music.views, description = music.description,
                        topSongs = music.topSongs, songsMore = music.songsMore,
                        albums = music.albums, albumsMore = music.albumsMore,
                        singles = music.singles, singlesMore = music.singlesMore,
                        videos = music.videos, videosMore = music.videosMore,
                        featured = music.featured, featuredMore = music.featuredMore,
                        relatedArtists = music.relatedArtists,
                    )
                }
                return@launch
            }
            if (channel != null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = null, name = channel.channelName ?: it.name, thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl, subscribers = channel.subscriberCount ?: it.subscribers, videos = channel.videos)
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
        }
    }

"""
            t3 = t3[:start] + new_fn + t3[nxt:]
            write(p3, t3)
            must("FIX8_ARTIST" in read(p3), "ArtistViewModel: FIX8_ARTIST")
    print("ArtistViewModel FIX8 pronto")

# ================= 4) MediaListViewModel - onOpenArtist tenta de novo =================
p4 = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t4 = read(p4)
if t4 is not None:
    # Garante injecao do YouTubeRepository (sem duplicar)
    if "youTubeRepository" not in t4:
        if "private val ytMusicRepository: YTMusicRepository" in t4:
            t4 = t4.replace("private val ytMusicRepository: YTMusicRepository,",
                            "private val ytMusicRepository: YTMusicRepository,\n    private val youTubeRepository: YouTubeRepository,")
            if "import com.vztechcore.vztune.data.ytmusic.YTMusicRepository" in t4:
                t4 = t4.replace("import com.vztechcore.vztune.data.ytmusic.YTMusicRepository",
                                "import com.vztechcore.vztune.data.youtube.YouTubeRepository\nimport com.vztechcore.vztune.data.ytmusic.YTMusicRepository")
            write(p4, t4)
            print("OK: MediaListViewModel: YouTubeRepository injetado")
        else:
            print("AVISO: MediaListViewModel construtor diferente - injecao manual necessaria")
    t4 = read(p4)
    # Deduplica injecao se rodou 2x
    if t4.count("private val youTubeRepository: YouTubeRepository") > 1:
        t4 = t4.replace("    private val youTubeRepository: YouTubeRepository,\n    private val youTubeRepository: YouTubeRepository,", "    private val youTubeRepository: YouTubeRepository,")
        write(p4, t4)
        print("OK: MediaListViewModel: deduplicada injecao")
    t4 = read(p4)
    # onOpenArtist FIX8: se e playlist do YouTube e nao tem UC, tenta resolver agora, em vez de desistir
    if "FIX8_OPEN_ARTIST" not in t4 and "fun onOpenArtist()" in t4:
        start = t4.find("    fun onOpenArtist()")
        nxt = t4.find("\n    private fun isYouTubePlaylist", start)
        if nxt == -1:
            nxt = t4.find("\n    fun onOpenArtistByName", start)
        if start != -1 and nxt != -1:
            new_fn = """    // FIX8_OPEN_ARTIST - Se nao tem UC ainda, tenta resolver na hora em vez de desistir. Canal grande precisava disso.
    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }
        val artistName = state.owner?.takeIf { it.isNotBlank() }
        if (directId != null && artistName != null) {
            viewModelScope.launch {
                _navigationEvent.emit(
                    NavigationEvent.OpenArtist(
                        Artist(id = directId, name = artistName, thumbnailUrl = state.ownerAvatarUrl, source = Source.YOUTUBE_MUSIC)
                    )
                )
            }
            return
        }
        // Playlist do YouTube sem UC ainda: tenta resolver o dono agora via NewPipe.
        if (isYouTubePlaylist(state)) {
            viewModelScope.launch {
                val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                if (channel != null) {
                    _uiState.update { it.copy(artistId = channel.channelId, ownerAvatarUrl = channel.avatarUrl ?: it.ownerAvatarUrl) }
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            Artist(id = channel.channelId, name = channel.channelName.ifBlank { artistName ?: "Canal" }, thumbnailUrl = channel.avatarUrl ?: state.ownerAvatarUrl, source = Source.YOUTUBE_MUSIC)
                        )
                    )
                }
            }
            return
        }
        if (artistName != null) {
            onOpenArtistByName(artistName)
        }
    }

"""
            t4 = t4[:start] + new_fn + t4[nxt:]
            write(p4, t4)
            must("FIX8_OPEN_ARTIST" in read(p4), "MediaListViewModel: FIX8_OPEN_ARTIST")
    print("MediaListViewModel FIX8 pronto")

print("\\nFIX8 DEFINITIVO aplicado. Roda o build.")
PY
