#!/bin/bash
# VzTune FIX20 - Playlist no mundo certo + Canal com Inicio + Maximo de videos/playlists
# Aplica sobre FIX18 + FIX19. Rode na raiz do projeto: bash VzTune-FIX20.sh
# 1) PLAYLIST: Routes.MediaList ganha sourceName ("YOUTUBE" / "YOUTUBE_MUSIC"). openPlaylist passa playlist.source.name.
#    MediaList: se e YouTube -> SO NewPipe browseYouTubePlaylist, nunca InnerTube. Se e YT Music -> SO InnerTube.
# 2) CANAL: abas viram Inicio | Videos | Playlists. Shorts some da UI do canal (Search continua com Shorts).
#    Corrige bug do FIX18: Box de Playlists checava selectedChannelTab == 1 em vez de == 2.
# 3) INICIO: Hero = video mais famoso (mais views, VzVideoCard 16:9 igual Search) + "Para voce" (50 recentes, LazyRow 300dp)
#    + "Videos" (4+4, > vai p/ aba Videos / Mais recentes) + "Videos mais acessados" (4+4, > vai p/ aba Videos / Em alta)
#    + "Playlists" (4+4, > vai p/ aba Playlists). Expandir = icone ExpandMore (SVG que voce mandou).
# 4) MAXIMO: canal comeca com 500 videos (era 300) + 300 playlists. Buscar mais pega +500 videos / +200 playlists por clique.
#    30 mil videos NAO vem de uma vez (travaria e estouraria memoria) - vem em lotes de Buscar mais ate acabar.
set -e
python3 - << 'PYEOF'
import re, pathlib
def read(p):
    try: return pathlib.Path(p).read_text(encoding="utf-8")
    except Exception: return None
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
def ok(m): print("OK", m)
def warn(m): print("AVISO", m)
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ExpandMore.kt"
if pathlib.Path(p).exists():
    ok("ExpandMore: ja existe")
else:
    content = "package com.vztechcore.vztune.ui.icons.actions\n\nimport androidx.compose.ui.graphics.vector.ImageVector\nimport com.vztechcore.vztune.ui.icons.VzIcons\n\n// Seta pra baixo do Inicio (expandir +4). SVG que voce mandou.\nval VzIcons.ExpandMore: ImageVector\n    get() = VzIcons.icon(\n        name = \"ExpandMore\",\n        pathData = \"M11.63,14.89q-0.17-0.06-0.32-0.21l-4.6-4.6Q6.43,9.8 6.41,9.39T6.7,8.67Q6.98,8.4 7.4,8.4T8.1,8.67L12,12.55l3.9-3.88q0.28-0.28 0.69-0.29T17.3,8.67q0.27,0.28 0.27,0.7t-0.27,0.7l-4.6,4.6q-0.15,0.15-0.33,0.21T12,14.95t-0.38-0.06Z\"\n    )\n"
    write(p, content)
    ok("ExpandMore: criado")
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/Routes.kt"
t = read(p)
if t is None:
    warn("Routes nao achado")
elif "data class MediaList" in t and "sourceName" in t[t.find("data class MediaList"):t.find("data class MediaList")+800]:
    ok("Routes.MediaList: ja tem sourceName")
else:
    idx = t.find("data class MediaList")
    if idx != -1:
        j = t.find("val thumbnailUrl: String? = null,", idx)
        if j != -1:
            end = j + len("val thumbnailUrl: String? = null,")
            t = t[:end] + "\n        val sourceName: String? = null, // FIX20 - YOUTUBE ou YOUTUBE_MUSIC. Sem isso a playlist perdia o mundo."
            write(p, t)
            ok("Routes.MediaList: sourceName adicionado")
        else:
            warn("Routes.MediaList: thumbnailUrl nao achado")
    else:
        warn("Routes.MediaList: data class nao achada")
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/NavExtensions.kt"
t = read(p)
if t is None:
    warn("NavExtensions nao achado")
else:
    changed = False
    if "sourceName = playlist.source.name" not in t:
        if "thumbnailUrl = playlist.thumbnailUrl," in t:
            t = t.replace("thumbnailUrl = playlist.thumbnailUrl,", "thumbnailUrl = playlist.thumbnailUrl,\n            sourceName = playlist.source.name,", 1)
            changed = True
            ok("NavExtensions: openPlaylist agora passa sourceName")
        else:
            warn("NavExtensions: openPlaylist thumbnailUrl nao achado")
    else:
        ok("NavExtensions: openPlaylist ja passa sourceName")
    if 'sourceName = "YOUTUBE_MUSIC"' not in t:
        if "thumbnailUrl = album.thumbnailUrl," in t:
            t = t.replace("thumbnailUrl = album.thumbnailUrl,", 'thumbnailUrl = album.thumbnailUrl,\n            sourceName = "YOUTUBE_MUSIC",', 1)
            changed = True
            ok("NavExtensions: openAlbum agora passa YOUTUBE_MUSIC")
        else:
            warn("NavExtensions: openAlbum thumbnailUrl nao achado")
    if changed:
        write(p, t)

# ============ YouTubeRepository ============
p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if t is None:
    warn("YouTubeRepository nao achado")
else:
    if "ChannelPlaylistsSession" not in t:
        helper = '''    // FIX20 - Sessao de playlists do canal (/playlists). Igual a de videos: guarda extractor + nextPage pra Buscar mais nao recomecar.
    private data class ChannelPlaylistsSession(
        val extractor: PlaylistExtractor,
        var nextPage: Page?,
        val channelName: String,
    )
    private val channelPlaylistSessions = mutableMapOf<String, ChannelPlaylistsSession>()
    private fun parseChannelPlaylistItems(items: List<InfoItem>, channelName: String): List<Playlist> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
            val pid = Regex("list=([^&]+)").find(item.url ?: "")?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!item.uploaderName.isNullOrBlank() && !isSameCreator(item.uploaderName, channelName)) return@mapNotNull null
            Playlist(id = pid, title = item.name ?: "Playlist", owner = item.uploaderName?.takeIf { it.isNotBlank() } ?: channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount.takeIf { it > 0 }?.toInt(), source = Source.YOUTUBE)
        }
    }
    fun hasMoreChannelPlaylists(channelId: String): Boolean {
        if (channelPlaylistSessions[channelId]?.nextPage != null) return true
        return channelPlaylistSessions.entries.any { (k, v) -> v.nextPage != null && (k == channelId || channelId.contains(k) || k.contains(channelId)) }
    }
    suspend fun loadMoreChannelPlaylists(channelId: String, pageSize: Int = 200): List<Playlist> = withContext(Dispatchers.IO) {
        val session = channelPlaylistSessions[channelId]
            ?: channelPlaylistSessions.entries.firstOrNull { (k, v) -> v.nextPage != null && (channelId.contains(k) || k.contains(channelId)) }?.value
            ?: return@withContext emptyList()
        var page = session.nextPage ?: return@withContext emptyList()
        runCatching {
            val collected = mutableListOf<Playlist>()
            var pages = 0
            while (collected.size < pageSize && page != null && pages < 2) {
                val next = session.extractor.getPage(page)
                collected += parseChannelPlaylistItems(next.items, session.channelName)
                page = next.nextPage
                session.nextPage = page
                pages++
            }
            collected.distinctBy { it.id }.take(pageSize)
        }.getOrDefault(emptyList())
    }
    data class YouTubePlaylistPage(
        val title: String?,
        val owner: String?,
        val ownerChannelId: String?,
        val coverUrl: String?,
        val description: String?,
        val tracks: List<Track>,
    )
    suspend fun browseYouTubePlaylist(playlistId: String): YouTubePlaylistPage? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        runCatching {
            val raw = playlistId.trim().removePrefix("YT:").trim()
            val url = if (raw.startsWith("http")) raw else "https://www.youtube.com/playlist?list=$raw"
            val pl = ServiceList.YouTube.getPlaylistExtractor(url)
            pl.fetchPage()
            val ownerName = runCatching { pl.uploaderName?.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            val uploaderUrl = runCatching { pl.uploaderUrl?.trim() }.getOrNull().orEmpty()
            val ownerId = if (uploaderUrl.isNotBlank()) extractChannelId(uploaderUrl) else null
            val title = runCatching { pl.name?.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            val desc = runCatching { pl.description }.getOrNull()?.takeIf { it.isNotBlank() }
            val collected = parseChannelStreamItems(pl.initialPage.items, ownerName.orEmpty()).toMutableList()
            var nextPage = pl.initialPage.nextPage
            var pages = 0
            while (collected.size < 500 && nextPage != null && pages < 5) {
                val next = pl.getPage(nextPage)
                collected += parseChannelStreamItems(next.items, ownerName.orEmpty())
                nextPage = next.nextPage
                pages++
            }
            val cover = collected.firstOrNull()?.thumbnailUrl
            YouTubePlaylistPage(title, ownerName, ownerId, cover, desc, collected.distinctBy { it.id })
        }.getOrNull()
    }
'''
        if "suspend fun browseChannel" in t:
            t = t.replace("suspend fun browseChannel", helper + "suspend fun browseChannel", 1)
            ok("YouTubeRepository: sessoes playlists + browseYouTubePlaylist criados")
        else:
            warn("YouTubeRepository: browseChannel nao achado")
    else:
        ok("YouTubeRepository: helpers playlists ja existem")
    # Videos 300->500: so se ainda esta 300 no loop de videos. Se ja esta 500, pula pra nao encostar no loop de playlists (300).
    if "while (collected.size < 500" in t and "ChannelPlaylistsSession" in t:
        ok("YouTubeRepository: videos ja em 500 (pulado)")
    elif "while (collected.size < 300" in t:
        t = t.replace("while (collected.size < 300", "while (collected.size < 500", 1)
        ok("YouTubeRepository: videos iniciais 300 -> 500")
    if "collected.distinctBy { it.id }.take(500)" in t:
        ok("YouTubeRepository: videos take ja em 500")
    elif "collected.distinctBy { it.id }.take(300)" in t:
        t = t.replace("collected.distinctBy { it.id }.take(300)", "collected.distinctBy { it.id }.take(500)", 1)
        ok("YouTubeRepository: videos take 300 -> 500")
    old_load = "    suspend fun loadMoreChannelVideos(channelId: String, pageSize: Int = 100): List<Track> = withContext(Dispatchers.IO) {\n        val session = channelVideoSessions[channelId]\n            ?: channelVideoSessions.entries.firstOrNull { (k, v) -> v.nextPage != null && (channelId.contains(k) || k.contains(channelId)) }?.value\n            ?: return@withContext emptyList()\n        val page = session.nextPage ?: return@withContext emptyList()\n        runCatching {\n            val next = session.extractor.getPage(page)\n            session.nextPage = next.nextPage\n            parseChannelStreamItems(next.items, session.channelName).take(pageSize)\n        }.getOrDefault(emptyList())\n    }"
    new_load = "    // FIX20 - Buscar mais videos: +500 por clique (5 paginas). Antes era 1 pagina (100).\n    suspend fun loadMoreChannelVideos(channelId: String, pageSize: Int = 500): List<Track> = withContext(Dispatchers.IO) {\n        val session = channelVideoSessions[channelId]\n            ?: channelVideoSessions.entries.firstOrNull { (k, v) -> v.nextPage != null && (channelId.contains(k) || k.contains(channelId)) }?.value\n            ?: return@withContext emptyList()\n        var page = session.nextPage ?: return@withContext emptyList()\n        runCatching {\n            val collected = mutableListOf<Track>()\n            var pages = 0\n            while (collected.size < pageSize && page != null && pages < 5) {\n                val next = session.extractor.getPage(page)\n                collected += parseChannelStreamItems(next.items, session.channelName)\n                page = next.nextPage\n                session.nextPage = page\n                pages++\n            }\n            collected.distinctBy { it.id }.take(pageSize)\n        }.getOrDefault(emptyList())\n    }"
    if old_load in t:
        t = t.replace(old_load, new_load, 1)
        ok("YouTubeRepository: loadMoreChannelVideos agora pega 500 por clique")
    elif "pageSize: Int = 500" in t and "loadMoreChannelVideos" in t:
        ok("YouTubeRepository: loadMoreChannelVideos ja pega 500")
    else:
        t2, n = re.subn(r"suspend fun loadMoreChannelVideos\(channelId: String, pageSize: Int = 100\)", "suspend fun loadMoreChannelVideos(channelId: String, pageSize: Int = 500)", t)
        if n > 0:
            t = t2
            ok("YouTubeRepository: loadMoreChannelVideos default 100 -> 500")
        else:
            warn("YouTubeRepository: loadMoreChannelVideos nao achado")
    pat_pl = re.compile(r"if \(uc\.startsWith\(\"UC\"\)\) \{\s*channelPlaylists = runCatching \{\s*val pl = ServiceList\.YouTube\.getPlaylistExtractor\(\"https://www\.youtube\.com/channel/\$uc/playlists\"\)(.*?)\.getOrDefault\(emptyList\(\)\)\s*\}", re.DOTALL)
    m = pat_pl.search(t)
    if m:
        new_pl = '''if (uc.startsWith("UC")) {
            channelPlaylists = runCatching {
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/channel/$uc/playlists")
                pl.fetchPage()
                val collected = parseChannelPlaylistItems(pl.initialPage.items, channelName).toMutableList()
                var nextPage = pl.initialPage.nextPage
                var pages = 0
                while (collected.size < 300 && nextPage != null && pages < 3) {
                    val next = pl.getPage(nextPage)
                    collected += parseChannelPlaylistItems(next.items, channelName)
                    nextPage = next.nextPage
                    pages++
                }
                val session = ChannelPlaylistsSession(pl, nextPage, channelName)
                channelPlaylistSessions[uc] = session
                channelPlaylistSessions[input] = session
                channelPlaylistSessions[channelUrl] = session
                collected.distinctBy { it.id }.take(300)
            }.getOrDefault(emptyList())
        }'''
        t = t[:m.start()] + new_pl + t[m.end():]
        ok("YouTubeRepository: playlists canal paginam ate 300 + sessao")
    else:
        warn("YouTubeRepository: bloco primario playlists nao achado")
    pat_shorts1 = re.compile(r"if \(uc\.startsWith\(\"UC\"\)\) \{\s*shorts = runCatching \{\s*val pl = ServiceList\.YouTube\.getPlaylistExtractor\(\"https://www\.youtube\.com/channel/\$uc/shorts\"\)(.*?)\.getOrDefault\(emptyList\(\)\)\s*\}", re.DOTALL)
    m1 = pat_shorts1.search(t)
    if m1:
        t = t[:m1.start()] + "// FIX20 - Shorts fora do canal (UI sem aba Shorts). Nao busca /shorts aqui.\n        shorts = emptyList()" + t[m1.end():]
        ok("YouTubeRepository: Shorts via /shorts removido do browseChannel")
    if 'shorts = runCatching { searchShorts(channelName' in t:
        pat_shorts2 = re.compile(r"if \(shorts\.isEmpty\(\) && channelName\.isNotBlank\(\)\) \{\s*shorts = runCatching \{ searchShorts\(channelName.*?\.getOrDefault\(emptyList\(\)\)\s*\}", re.DOTALL)
        m2 = pat_shorts2.search(t)
        if m2:
            t = t[:m2.start()] + "// FIX20 - Fallback Shorts removido (canal sem Shorts)." + t[m2.end():]
            ok("YouTubeRepository: fallback Shorts removido")
        else:
            warn("YouTubeRepository: fallback Shorts nao achado")
    write(p, t)

# ============ ArtistUiState + ArtistViewModel + MediaListViewModel ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt"
t = read(p)
if t is None:
    warn("ArtistUiState nao achado")
elif "channelPlaylistsHasMore" in t:
    ok("ArtistUiState: ja tem channelPlaylistsHasMore")
else:
    if "val channelVideosHasMore: Boolean = false," in t:
        t = t.replace("val channelVideosHasMore: Boolean = false,", "val channelVideosHasMore: Boolean = false,\n    val channelPlaylistsHasMore: Boolean = false,\n    val isLoadingMoreChannelPlaylists: Boolean = false,", 1)
        write(p, t)
        ok("ArtistUiState: channelPlaylistsHasMore + isLoadingMore")
    else:
        warn("ArtistUiState: channelVideosHasMore nao achado")
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
t = read(p)
if t is None:
    warn("ArtistViewModel nao achado")
else:
    if "shorts = channel.shorts" in t:
        t = t.replace("shorts = channel.shorts", "shorts = emptyList(), // FIX20 - Canal sem Shorts", 1)
        ok("ArtistViewModel: shorts zerado")
    if "channelPlaylistsHasMore" in t:
        ok("ArtistViewModel: channelPlaylistsHasMore ja ligado")
    elif "channelVideosHasMore = youTubeRepository.hasMoreChannelVideos(route.id), isLoadingMoreChannelVideos = false," in t:
        t = t.replace("channelVideosHasMore = youTubeRepository.hasMoreChannelVideos(route.id), isLoadingMoreChannelVideos = false,", "channelVideosHasMore = youTubeRepository.hasMoreChannelVideos(route.id), isLoadingMoreChannelVideos = false,\n                            channelPlaylistsHasMore = youTubeRepository.hasMoreChannelPlaylists(route.id), isLoadingMoreChannelPlaylists = false,", 1)
        ok("ArtistViewModel: channelPlaylistsHasMore ligado")
    else:
        warn("ArtistViewModel: linha channelVideosHasMore nao achada")
    if "fun loadMoreChannelPlaylists" not in t:
        anchor = "    fun onVideoClick(track: Track) {"
        idx = t.find(anchor)
        if idx != -1:
            insert = '''    // FIX20 - Buscar mais playlists: proxima pagina do /playlists, soma na mesma lista.
    fun loadMoreChannelPlaylists() {
        val state = _uiState.value
        if (state.isLoadingMoreChannelPlaylists) return
        if (!state.channelPlaylistsHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreChannelPlaylists = true) }
            val more = runCatching { youTubeRepository.loadMoreChannelPlaylists(route.id) }.getOrDefault(emptyList())
            val current = _uiState.value.channelPlaylists
            val merged = (current + more).distinctBy { it.id }
            _uiState.update {
                it.copy(
                    channelPlaylists = merged,
                    channelPlaylistsHasMore = youTubeRepository.hasMoreChannelPlaylists(route.id),
                    isLoadingMoreChannelPlaylists = false,
                )
            }
        }
    }

'''
            t = t[:idx] + insert + t[idx:]
            ok("ArtistViewModel: loadMoreChannelPlaylists criado")
        else:
            warn("ArtistViewModel: onVideoClick nao achado")
    else:
        ok("ArtistViewModel: loadMoreChannelPlaylists ja existe")
    write(p, t)
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t = read(p)
if t is None:
    warn("MediaListViewModel nao achado")
else:
    if "fun isYouTubePlaylistRoute" not in t:
        anchor = "private fun loadContent()"
        idx = t.find(anchor)
        if idx != -1:
            helper = '''    // FIX20 - Playlist YouTube tem id PL/UU/FL/LL/OL (menos OLAK que e YT Music). Sem sourceName, infere pelo id.
    private fun isYouTubePlaylistId(id: String): Boolean {
        val v = id.trim().removePrefix("YT:").trim()
        if (v.startsWith("OLAK")) return false
        if (v.startsWith("RD") || v.startsWith("VL")) return false
        return v.startsWith("PL") || v.startsWith("UU") || v.startsWith("FL") || v.startsWith("LL") || v.startsWith("OL")
    }
    private fun isYouTubePlaylistRoute(): Boolean {
        if (route.sourceName == "YOUTUBE") return true
        if (route.sourceName == "YOUTUBE_MUSIC") return false
        return isYouTubePlaylistId(route.id)
    }

'''
            t = t[:idx] + helper + t[idx:]
            ok("MediaListViewModel: helpers isYouTubePlaylistId/Route criados")
        else:
            warn("MediaListViewModel: loadContent nao achado")
    else:
        ok("MediaListViewModel: helpers ja existem")
    if "browseYouTubePlaylist(route.id)" not in t:
        pat = re.compile(r"\} else \{\s*var playlist = ytMusicRepository\.browsePlaylist\(route\.id\)", re.DOTALL)
        m = pat.search(t)
        if m:
            branch = '''} else if (isYouTubePlaylistRoute()) {
                    // FIX20 - Playlist YouTube: SO NewPipe, nunca InnerTube. Antes caia no browsePlaylist do InnerTube e o dono vinha como artista do YT Music.
                    val ytPage = runCatching { youTubeRepository.browseYouTubePlaylist(route.id) }.getOrNull()
                    if (ytPage != null) {
                        val ch = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false, errorMessage = null,
                                title = ytPage.title?.takeIf { v -> v.isNotBlank() } ?: it.title,
                                subtitle = ytPage.owner ?: it.subtitle,
                                owner = ytPage.owner ?: it.owner,
                                artistId = ytPage.ownerChannelId ?: ch?.channelId ?: it.artistId,
                                ownerAvatarUrl = ch?.avatarUrl ?: it.ownerAvatarUrl,
                                thumbnailUrl = ytPage.coverUrl ?: it.thumbnailUrl,
                                description = ytPage.description ?: it.description,
                                allTracks = ytPage.tracks.distinctBy { tr -> "${tr.source}_${tr.id}" },
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar esta playlist.") }
                    }
                } else {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)'''
            t = t[:m.start()] + branch + t[m.end():]
            ok("MediaListViewModel: ramo YouTube (NewPipe) criado")
        else:
            warn("MediaListViewModel: bloco else browsePlaylist nao achado")
    else:
        ok("MediaListViewModel: ramo YouTube ja existe")
    if 'if (route.sourceName == "YOUTUBE") return true' not in t:
        pat2 = re.compile(r"private fun isYouTubePlaylist\(state: MediaListUiState\): Boolean \{\s*", re.DOTALL)
        m2 = pat2.search(t)
        if m2:
            t = t[:m2.end()] + 'if (route.sourceName == "YOUTUBE") return true\n        ' + t[m2.end():]
            ok("MediaListViewModel: isYouTubePlaylist respeita sourceName")
        else:
            warn("MediaListViewModel: isYouTubePlaylist nao achado")
    if 'route.sourceName == "YOUTUBE" || isYouTubePlaylist(state)' not in t:
        if "source = if (isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC" in t:
            t = t.replace("source = if (isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC", 'source = if (route.sourceName == "YOUTUBE" || isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC', 1)
            ok("MediaListViewModel: onOpenArtist direto respeita sourceName")
        idx_o = t.find("fun onOpenArtist()")
        if idx_o != -1:
            seg_end = t.find("fun onOpenArtistByName", idx_o)
            if seg_end == -1: seg_end = len(t)
            seg = t[idx_o:seg_end]
            seg2 = seg.replace("if (isYouTubePlaylist(state)) {", 'if (route.sourceName == "YOUTUBE" || isYouTubePlaylist(state)) {', 1)
            if seg2 != seg:
                t = t[:idx_o] + seg2 + t[seg_end:]
                ok("MediaListViewModel: onOpenArtist resolver via NewPipe respeita sourceName")
    write(p, t)

# ============ ArtistScreen imports/params/estado ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
t = read(p)
if t is None:
    warn("ArtistScreen nao achado")
else:
    if "import com.vztechcore.vztune.ui.components.cards.VzVideoCard" not in t:
        t = t.replace("import com.vztechcore.vztune.ui.components.cards.ChannelVideoCard", "import com.vztechcore.vztune.ui.components.cards.ChannelVideoCard\nimport com.vztechcore.vztune.ui.components.cards.VzVideoCard", 1)
        ok("ArtistScreen: import VzVideoCard")
    if "import com.vztechcore.vztune.ui.icons.actions.ExpandMore" not in t:
        t = t.replace("import com.vztechcore.vztune.ui.icons.actions.ChevronRight", "import com.vztechcore.vztune.ui.icons.actions.ChevronRight\nimport com.vztechcore.vztune.ui.icons.actions.ExpandMore", 1)
        ok("ArtistScreen: import ExpandMore")
    if "import androidx.compose.runtime.rememberCoroutineScope" not in t:
        t = t.replace("import androidx.compose.runtime.remember", "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope", 1)
        ok("ArtistScreen: import rememberCoroutineScope")
    if "import kotlinx.coroutines.launch" not in t:
        if "import com.vztechcore.vztune.ui.icons.VzIcons" in t:
            t = t.replace("import com.vztechcore.vztune.ui.icons.VzIcons", "import com.vztechcore.vztune.ui.icons.VzIcons\nimport kotlinx.coroutines.launch", 1)
            ok("ArtistScreen: import launch")
    if "onLoadMoreChannelPlaylists" not in t:
        if "onLoadMoreChannelVideos: () -> Unit = {}," in t:
            t = t.replace("onLoadMoreChannelVideos: () -> Unit = {},", "onLoadMoreChannelVideos: () -> Unit = {},\n    onLoadMoreChannelPlaylists: () -> Unit = {},", 1)
            ok("ArtistScreen: parametro onLoadMoreChannelPlaylists")
        else:
            warn("ArtistScreen: onLoadMoreChannelVideos nao achado")
    if "homeVideosVisible" not in t:
        if "var playlistMenu by remember { mutableStateOf<Playlist?>(null) }" in t:
            t = t.replace("var playlistMenu by remember { mutableStateOf<Playlist?>(null) }", "var playlistMenu by remember { mutableStateOf<Playlist?>(null) }\n    val scope = rememberCoroutineScope()\n    // FIX20 - Inicio: quantos mostrar em cada secao (4+4 infinito). Comeca em 4.\n    var homeVideosVisible by remember { mutableStateOf(4) }\n    var homeTopVideosVisible by remember { mutableStateOf(4) }\n    var homePlaylistsVisible by remember { mutableStateOf(4) }", 1)
            ok("ArtistScreen: estado Inicio (4+4) + scope")
        else:
            warn("ArtistScreen: playlistMenu nao achado")
    write(p, t)

# ============ ArtistScreen bloco do canal ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
t = read(p)
if t is not None:
    start = t.find('item(key = "channel_tabs")')
    end = t.find('item(key = "channel_space")', start) if start != -1 else -1
    if start == -1 or end == -1:
        warn("ArtistScreen: bloco channel_tabs / channel_space nao achado")
    else:
        new_block = '''item(key = "channel_tabs") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // FIX20 - Abas: Inicio | Videos | Playlists. Sem Shorts. (Corrige FIX18: Playlists checava == 1)
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 0) Color.White else Color.Transparent).clickable { selectedChannelTab = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Início", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 1) Color.White else Color.Transparent).clickable { selectedChannelTab = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Vídeos", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 2) Color.White else Color.Transparent).clickable { selectedChannelTab = 2 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Playlists", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 2) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        item(key = "channel_tabs_space") { Spacer(Modifier.height(16.dp)) }
                        // FIX20 - Mesmas listas do canal, sem buscar de novo. Inicio deriva delas.
                        val sortedVideos = when (channelSort) {
                            1 -> state.videos.sortedByDescending { it.viewCount ?: 0 }
                            2 -> state.videos.reversed()
                            else -> state.videos
                        }
                        val topVideosSorted = state.videos.sortedByDescending { it.viewCount ?: 0 }
                        val mostViewedVideo = topVideosSorted.firstOrNull()
                        val forYouVideos = state.videos.take(50)
                        if (selectedChannelTab == 0) {
                            // ===== INICIO =====
                            if (mostViewedVideo != null) {
                                item(key = "home_hero") {
                                    val heroPlayback = nowPlaying.playbackOf(mostViewedVideo)
                                    VzVideoCard(track = mostViewedVideo, isPlaying = heroPlayback.isPlaying, isPaused = heroPlayback.isPaused, onClick = { onVideoClick(mostViewedVideo) }, onMoreClick = { onTrackInfoClick(mostViewedVideo) }, horizontalPadding = 16.dp)
                                }
                            } else if (state.videos.isEmpty() && !state.isLoading) {
                                item(key = "home_hero_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                            if (forYouVideos.isNotEmpty()) {
                                item(key = "home_foryou_title") {
                                    Text(text = "Para você", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                                }
                                item(key = "home_foryou_row") {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(forYouVideos, key = { "fy_" + it.id }) { video ->
                                            // FIX20 - Box 300dp por fora: VzVideoCard faz fillMaxWidth() por dentro, entao width direto nele seria ignorado.
                                            Box(Modifier.width(300.dp)) {
                                                val pv = nowPlaying.playbackOf(video)
                                                VzVideoCard(track = video, isPlaying = pv.isPlaying, isPaused = pv.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, horizontalPadding = 0.dp)
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "home_videos_header") {
                                SectionHeaderArrow(title = "Vídeos", onArrowClick = { selectedChannelTab = 1; channelSort = 0; scope.launch { listState.scrollToItem(1) } })
                            }
                            if (state.videos.isNotEmpty()) {
                                itemsIndexed(state.videos.take(homeVideosVisible), key = { _, v -> "hv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                            } else {
                                item(key = "home_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
                            }
                            if (homeVideosVisible < state.videos.size || state.channelVideosHasMore) {
                                item(key = "home_videos_expand") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable {
                                            if (homeVideosVisible < state.videos.size) homeVideosVisible += 4 else onLoadMoreChannelVideos()
                                        }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(imageVector = VzIcons.ExpandMore, contentDescription = "Mostrar mais vídeos", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            item(key = "home_top_header") {
                                SectionHeaderArrow(title = "Vídeos mais acessados", onArrowClick = { selectedChannelTab = 1; channelSort = 1; scope.launch { listState.scrollToItem(1) } })
                            }
                            if (topVideosSorted.isNotEmpty()) {
                                itemsIndexed(topVideosSorted.take(homeTopVideosVisible), key = { _, v -> "ht_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                            } else {
                                item(key = "home_top_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
                            }
                            if (homeTopVideosVisible < topVideosSorted.size || state.channelVideosHasMore) {
                                item(key = "home_top_expand") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable {
                                            if (homeTopVideosVisible < topVideosSorted.size) homeTopVideosVisible += 4 else onLoadMoreChannelVideos()
                                        }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(imageVector = VzIcons.ExpandMore, contentDescription = "Mostrar mais vídeos em alta", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            item(key = "home_playlists_header") {
                                SectionHeaderArrow(title = "Playlists", onArrowClick = { selectedChannelTab = 2; scope.launch { listState.scrollToItem(1) } })
                            }
                            if (state.channelPlaylists.isNotEmpty()) {
                                itemsIndexed(state.channelPlaylists.take(homePlaylistsVisible), key = { _, pl -> "hp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                            } else {
                                item(key = "home_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
                            }
                            if (homePlaylistsVisible < state.channelPlaylists.size || state.channelPlaylistsHasMore) {
                                item(key = "home_playlists_expand") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable {
                                            if (homePlaylistsVisible < state.channelPlaylists.size) homePlaylistsVisible += 4 else onLoadMoreChannelPlaylists()
                                        }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(imageVector = VzIcons.ExpandMore, contentDescription = "Mostrar mais playlists", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        } else if (selectedChannelTab == 1) {
                            item(key = "channel_chips") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 0) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = "Mais recentes", style = MaterialTheme.typography.labelLarge, color = if (channelSort == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 1) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = "Em alta", style = MaterialTheme.typography.labelLarge, color = if (channelSort == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 2) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 2 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = "Mais antigos", style = MaterialTheme.typography.labelLarge, color = if (channelSort == 2) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            item(key = "channel_chips_space") { Spacer(Modifier.height(8.dp)) }
                            if (state.videos.isNotEmpty()) {
                                itemsIndexed(sortedVideos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                            } else {
                                item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                            if (state.channelVideosHasMore && channelSort != 2) {
                                item(key = "channel_load_more") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onLoadMoreChannelVideos() }.padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = if (state.isLoadingMoreChannelVideos) "Carregando..." else "Buscar mais", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            if (state.channelPlaylists.isNotEmpty()) {
                                itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                            } else {
                                item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                            if (state.channelPlaylistsHasMore) {
                                item(key = "channel_playlists_load_more") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onLoadMoreChannelPlaylists() }.padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = if (state.isLoadingMoreChannelPlaylists) "Carregando..." else "Buscar mais", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
'''
        t = t[:start] + new_block + t[end:]
        ok("ArtistScreen: bloco canal substituido (Inicio | Videos | Playlists, sem Shorts)")
    write(p, t)

# ============ AppNavHost ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
t = read(p)
if t is None:
    warn("AppNavHost nao achado")
else:
    idx = t.find("composable<Routes.Artist>")
    if idx == -1:
        warn("AppNavHost: composable<Routes.Artist> nao achado")
    else:
        seg_end = t.find("composable<Routes.ArtistList>", idx)
        if seg_end == -1: seg_end = idx + 5000
        seg = t[idx:seg_end]
        has_videos = "onLoadMoreChannelVideos" in seg
        has_playlists = "onLoadMoreChannelPlaylists" in seg
        if has_videos and has_playlists:
            ok("AppNavHost: Buscar mais ja ligado")
        else:
            m = re.search(r"(onSeeAllClick[\s\S]*?\},\s*\n)(\s*\))", seg)
            if m:
                add = ""
                if not has_videos: add += "                            onLoadMoreChannelVideos = vm::loadMoreChannelVideos,\n"
                if not has_playlists: add += "                            onLoadMoreChannelPlaylists = vm::loadMoreChannelPlaylists,\n"
                seg2 = seg[:m.start(2)] + add + seg[m.start(2):]
                t = t[:idx] + seg2 + t[seg_end:]
                write(p, t)
                ok("AppNavHost: Buscar mais ligado (videos + playlists)")
            else:
                warn("AppNavHost: fechamento ArtistScreen nao achado")
print("FIX20 OK")
PYEOF

