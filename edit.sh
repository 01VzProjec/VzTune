#!/bin/bash
# VzTune FIX16 - Playlist: InnerTube -> YT Music / NewPipe -> YouTube (carrega source na navegacao)
# Canal: abas Inicio | Videos | Playlists, Inicio com video mais famoso + Para voce + expandir 4 em 4,
# Videos/Playlists com paginacao real (Buscar mais) em vez de so 300 de uma vez.
# Rode na raiz do projeto: bash edit.sh
set -e
python3 - << 'PY'
import pathlib, re, sys
def read(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8"); print("OK", p)
def edit(p, fn):
    t = read(p); nt = fn(t)
    if nt != t: write(p, nt)
    else: print("SEM MUDANCA", p)
    return nt
def must(cond, msg):
    if not cond: print(f"ERRO {msg}"); sys.exit(1)
    print(f"OK {msg}")
def replace_balanced(t, marker, new_block):
    i = t.find(marker)
    if i < 0: return t
    j = t.find("{", i)
    if j < 0: return t
    depth = 0; k = j
    while k < len(t):
        if t[k] == "{": depth += 1
        elif t[k] == "}":
            depth -= 1
            if depth == 0: break
        k += 1
    return t[:i] + new_block + t[k+1:]

# ============ 1) Icone ExpandMore (o SVG que voce mandou) ============
write("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ExpandMore.kt", """package com.vztechcore.vztune.ui.icons.actions

import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.ui.icons.VzIcons

// Seta para baixo (expand_more) - usada no "mostrar mais 4" do Inicio do canal.
val VzIcons.ExpandMore: ImageVector
    get() = VzIcons.icon(
        name = "ExpandMore",
        pathData = "M11.63,14.89q-0.17-0.06-0.32-0.21l-4.6-4.6Q6.43,9.8 6.41,9.39T6.7,8.67Q6.98,8.4 7.4,8.4T8.1,8.67L12,12.55l3.9-3.88q0.28-0.28 0.69-0.29T17.3,8.67q0.27,0.28 0.27,0.7t-0.27,0.7l-4.6,4.6q-0.15,0.15-0.33,0.21T12,14.95t-0.38-0.06Z"
    )
""")

# ============ 2) Routes.MediaList ganha sourceName ============
def fix_routes(t):
    if "sourceName" in t: return t
    return t.replace(
        "        val thumbnailUrl: String? = null,\n    )\n\n    /** Artist profile page.",
        "        val thumbnailUrl: String? = null,\n        // De onde a playlist veio: YOUTUBE (NewPipe) ou YOUTUBE_MUSIC (InnerTube).\n        // Sem isso a playlist perdia a origem e o dono caia no mundo errado.\n        val sourceName: String? = null,\n    )\n\n    /** Artist profile page.")
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/Routes.kt", fix_routes)

# ============ 3) NavExtensions.openPlaylist carrega o source ============
def fix_nav_ext(t):
    if "sourceName = playlist.source.name" in t: return t
    return t.replace(
        "            thumbnailUrl = playlist.thumbnailUrl,\n        )\n    )\n}\n\nfun NavController.navigateToTrackInfo",
        "            thumbnailUrl = playlist.thumbnailUrl,\n            sourceName = playlist.source.name,\n        )\n    )\n}\n\nfun NavController.navigateToTrackInfo")
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/NavExtensions.kt", fix_nav_ext)

# ============ 4) Search: OpenPlaylist carrega source ============
def fix_search_vm(t):
    if "val sourceName: String" in t: return t
    t = t.replace(
        "            val id: String, val title: String, val owner: String?,\n            val thumbnailUrl: String?, val mosaic: List<String>,",
        "            val id: String, val title: String, val owner: String?,\n            val thumbnailUrl: String?, val mosaic: List<String>,\n            val sourceName: String,")
    t = t.replace(
        "                            hit.playlist.thumbnailUrl,\n                            hit.playlist.mosaicThumbnails,",
        "                            hit.playlist.thumbnailUrl,\n                            hit.playlist.mosaicThumbnails,\n                            hit.playlist.source.name,")
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt", fix_search_vm)

# ============ 5) AppNavHost: Search -> MediaList passa source + Artist passa loadMore do canal ============
def fix_navhost(t):
    t = t.replace(
        "                                            thumbnailUrl = event.thumbnailUrl,\n                                        )\n                                    )\n                                    is SearchViewModel.NavigationEvent.OpenArtist",
        "                                            thumbnailUrl = event.thumbnailUrl,\n                                            sourceName = event.sourceName,\n                                        )\n                                    )\n                                    is SearchViewModel.NavigationEvent.OpenArtist")
    # Artist: passa os callbacks de carregar mais do canal
    if "onLoadMoreChannelVideos" not in t:
        t = t.replace(
            "                            onFollowClick = vm::onFollowClick,\n                            onSongClick = vm::onSongClick,",
            "                            onFollowClick = vm::onFollowClick,\n                            onLoadMoreChannelVideos = vm::onLoadMoreChannelVideos,\n                            onLoadMoreChannelPlaylists = vm::onLoadMoreChannelPlaylists,\n                            onSongClick = vm::onSongClick,")
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt", fix_navhost)

# ============ 6) ArtistUiState: flags de paginacao do canal ============
def fix_artist_state(t):
    if "hasMoreVideos" in t: return t
    t = t.replace(
        "    val isFollowing: Boolean = false,\n)",
        "    val isFollowing: Boolean = false,\n    // Canal: ainda tem video/playlist la no YouTube que nao foi carregado.\n    val hasMoreVideos: Boolean = false,\n    val hasMorePlaylists: Boolean = false,\n    val isLoadingMoreVideos: Boolean = false,\n    val isLoadingMorePlaylists: Boolean = false,\n)")
    t += """
/** Video mais famoso do canal (maior viewCount). Null quando nao tem viewCount em nenhum video. */
val ArtistUiState.mostViewedVideo: com.vztechcore.vztune.core.domain.music.Track?
    get() = videos.maxByOrNull { it.viewCount ?: -1L }?.takeIf { (it.viewCount ?: 0L) > 0 }
        ?: videos.firstOrNull()
/** Videos do canal ordenados do mais visto para o menos visto. */
val ArtistUiState.videosByViews: List<com.vztechcore.vztune.core.domain.music.Track>
    get() = videos.sortedByDescending { it.viewCount ?: -1L }
"""
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt", fix_artist_state)

# ============ 7) YouTubeRepository: paginacao real do canal + playlist ============
def fix_yt_repo(t):
    if "ChannelListSession" not in t:
        t = t.replace(
            "    private val videoSearches = mutableMapOf<String, VideoSearchSession>()",
            "    // Sessao de paginacao do canal: guarda o extractor + a proxima pagina.\n    // Sem guardar o extractor nao da pra chamar getPage() depois.\n    private data class ChannelListSession(\n        val extractor: org.schabi.newpipe.extractor.ListExtractor,\n        var nextPage: Page?,\n        val channelName: String,\n    )\n    private val channelVideoSessions = mutableMapOf<String, ChannelListSession>()\n    private val channelPlaylistSessions = mutableMapOf<String, ChannelListSession>()\n\n    private val videoSearches = mutableMapOf<String, VideoSearchSession>()")
        t = t.replace(
            "        videoSearches.clear()",
            "        channelVideoSessions.clear()\n        channelPlaylistSessions.clear()\n        videoSearches.clear()")
    if "hasMoreVideos" not in t:
        t = t.replace(
            "        val subscriberCount: String?,\n    )",
            "        val subscriberCount: String?,\n        val hasMoreVideos: Boolean = false,\n        val hasMorePlaylists: Boolean = false,\n    )")
    # Helpers de parse (reaproveitam a mesma conversao em browse + loadMore)
    if "fun parseChannelStreams" not in t:
        t = t.replace(
            "    /** \"hoje\", \"ha 1 dia\"",
            """    private fun parseChannelStreams(items: List<*>, channelName: String): List<Track> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val vid = extractVideoId(stream.url) ?: return@mapNotNull null
            Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
        }
    }
    private fun parseChannelPlaylists(items: List<*>, channelName: String): List<Playlist> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
            val pid = Regex("list=([^&]+)").find(item.url ?: "")?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Playlist(id = pid, title = item.name ?: "Playlist", owner = channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount.takeIf { it > 0 }?.toInt(), source = Source.YOUTUBE)
        }
    }
    private fun resolveUc(input: String): String? {
        val raw = input.trim()
        if (raw.startsWith("UU") && raw.length >= 24) return "UC" + raw.drop(2)
        return Regex("UC[\\\\w-]{22}").find(raw)?.value
    }

    /** Carrega mais videos do canal: avanca ate 5 paginas da UU por chamada. Retorna SO os novos. */
    suspend fun loadMoreChannelVideos(channelId: String, pages: Int = 5): List<Track> = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext emptyList()
        val uc = resolveUc(channelId) ?: return@withContext emptyList()
        val session = channelVideoSessions[uc] ?: return@withContext emptyList()
        val page0 = session.nextPage ?: return@withContext emptyList()
        val out = mutableListOf<Track>()
        var next: Page? = page0
        var left = pages
        while (next != null && left > 0) {
            val page = runCatching { session.extractor.getPage(next) }.getOrNull() ?: break
            out += parseChannelStreams(page.items, session.channelName)
            next = page.nextPage
            left--
        }
        session.nextPage = next
        out.distinctBy { it.id }
    }

    /** Carrega mais playlists do canal: avanca ate 3 paginas por chamada. Retorna SO as novas. */
    suspend fun loadMoreChannelPlaylists(channelId: String, pages: Int = 3): List<Playlist> = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext emptyList()
        val uc = resolveUc(channelId) ?: return@withContext emptyList()
        val session = channelPlaylistSessions[uc] ?: return@withContext emptyList()
        val page0 = session.nextPage ?: return@withContext emptyList()
        val out = mutableListOf<Playlist>()
        var next: Page? = page0
        var left = pages
        while (next != null && left > 0) {
            val page = runCatching { session.extractor.getPage(next) }.getOrNull() ?: break
            out += parseChannelPlaylists(page.items, session.channelName)
            next = page.nextPage
            left--
        }
        session.nextPage = next
        out.distinctBy { it.id }
    }

    /** "hoje", "ha 1 dia\"""")
    # browseChannel: videos da UU passam a paginar (5 paginas na abertura, resto no Buscar mais)
    old_videos = """        var videos: List<Track> = emptyList()
        if (uc.startsWith("UC") && uc.length >= 24) {
            videos = runCatching {
                val uploadsId = "UU" + uc.drop(2)
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$uploadsId")
                pl.fetchPage()
                pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
                }
            }.getOrDefault(emptyList())
        }"""
    new_videos = """        var videos: List<Track> = emptyList()
        var hasMoreVideos = false
        if (uc.startsWith("UC") && uc.length >= 24) {
            videos = runCatching {
                val uploadsId = "UU" + uc.drop(2)
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$uploadsId")
                pl.fetchPage()
                val all = parseChannelStreams(pl.initialPage.items, channelName).toMutableList()
                var next = pl.initialPage.nextPage
                var pages = 1
                // Abertura: 5 paginas (~150 videos). O resto vem no "Buscar mais", pagina por pagina,
                // senao um canal com 30 mil videos travaria a tela e estouraria a memoria.
                while (next != null && pages < 5) {
                    val page = runCatching { pl.getPage(next) }.getOrNull() ?: break
                    all += parseChannelStreams(page.items, channelName)
                    next = page.nextPage
                    pages++
                }
                channelVideoSessions[uc] = ChannelListSession(pl, next, channelName)
                hasMoreVideos = next != null
                all.distinctBy { it.id }
            }.getOrDefault(emptyList())
        }"""
    if old_videos in t: t = t.replace(old_videos, new_videos)
    # browseChannel: playlists passam a paginar (3 paginas na abertura)
    old_pl = """        var channelPlaylists: List<Playlist> = emptyList()
        if (uc.startsWith("UC")) {
            channelPlaylists = runCatching {
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/channel/$uc/playlists")
                pl.fetchPage()
                pl.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                    val pid = Regex("list=([^&]+)").find(item.url ?: "")?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Playlist(id = pid, title = item.name ?: "Playlist", owner = channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount.takeIf { it > 0 }?.toInt(), source = Source.YOUTUBE)
                }
            }.getOrDefault(emptyList())
        }"""
    new_pl = """        var channelPlaylists: List<Playlist> = emptyList()
        var hasMorePlaylists = false
        if (uc.startsWith("UC")) {
            channelPlaylists = runCatching {
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/channel/$uc/playlists")
                pl.fetchPage()
                val all = parseChannelPlaylists(pl.initialPage.items, channelName).toMutableList()
                var next = pl.initialPage.nextPage
                var pages = 1
                while (next != null && pages < 3) {
                    val page = runCatching { pl.getPage(next) }.getOrNull() ?: break
                    all += parseChannelPlaylists(page.items, channelName)
                    next = page.nextPage
                    pages++
                }
                channelPlaylistSessions[uc] = ChannelListSession(pl, next, channelName)
                hasMorePlaylists = next != null
                all.distinctBy { it.id }
            }.getOrDefault(emptyList())
        }"""
    if old_pl in t: t = t.replace(old_pl, new_pl)
    if "hasMoreVideos = hasMoreVideos" not in t:
        t = t.replace(
            "            subscriberCount = header?.subs,\n        )",
            "            subscriberCount = header?.subs,\n            hasMoreVideos = hasMoreVideos,\n            hasMorePlaylists = hasMorePlaylists,\n        )")
    # getYouTubePlaylistTracks: pagina ate 10 paginas (~300 faixas) em vez de so a primeira
    if "getYouTubePlaylistTracks" in t and "var nextPl" not in t:
        t = t.replace(
            "            pl.fetchPage()\n            pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->\n                val vid = extractVideoId(stream.url) ?: return@mapNotNull null\n                Track(id = vid, title = stream.name ?: \"Untitled\", artist = stream.uploaderName ?: \"\", durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))\n            }",
            "            pl.fetchPage()\n            val all = pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->\n                val vid = extractVideoId(stream.url) ?: return@mapNotNull null\n                Track(id = vid, title = stream.name ?: \"Untitled\", artist = stream.uploaderName ?: \"\", durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))\n            }.toMutableList()\n            var nextPl = pl.initialPage.nextPage\n            var pagesPl = 1\n            while (nextPl != null && pagesPl < 10) {\n                val page = runCatching { pl.getPage(nextPl) }.getOrNull() ?: break\n                all += page.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->\n                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null\n                    Track(id = vid, title = stream.name ?: \"Untitled\", artist = stream.uploaderName ?: \"\", durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))\n                }\n                nextPl = page.nextPage\n                pagesPl++\n            }\n            all.distinctBy { it.id }")
    return t
edit("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt", fix_yt_repo)

# ============ 8) MediaListViewModel: playlist usa o source de origem + artista nao vira canal errado ============
def fix_media_vm(t):
    if "playlistSource" not in t:
        t = t.replace(
            "    private val route = savedStateHandle.toRoute<Routes.MediaList>()",
            "    private val route = savedStateHandle.toRoute<Routes.MediaList>()\n\n    // Origem real da playlist: YOUTUBE = veio do NewPipe -> dono e canal do YouTube.\n    // YOUTUBE_MUSIC = veio do InnerTube -> dono e artista do YT Music. Null = antigo/sem info -> cai no fallback.\n    private val playlistSource = runCatching { com.vztechcore.vztune.core.domain.music.Source.valueOf(route.sourceName ?: \"\") }.getOrNull()")
    # loadContent: playlist do YouTube carrega pelo NewPipe primeiro
    if "isYouTubeByRoute" not in t:
        t = t.replace(
            "                } else {\n                    var playlist = ytMusicRepository.browsePlaylist(route.id)",
            """                } else {
                    // Playlist do YouTube: NewPipe primeiro (faixas + canal real). Se vier vazia, cai pro InnerTube abaixo.
                    var loadedViaYouTube = false
                    if (playlistSource == Source.YOUTUBE) {
                        val ytChannel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                        val ytTracks = runCatching { youTubeRepository.getYouTubePlaylistTracks(route.id) }.getOrDefault(emptyList())
                        if (ytTracks.isNotEmpty()) {
                            loadedViaYouTube = true
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    title = _uiState.value.title,
                                    subtitle = ytChannel?.channelName?.takeIf { n -> n.isNotBlank() } ?: it.subtitle,
                                    owner = ytChannel?.channelName?.takeIf { n -> n.isNotBlank() } ?: it.owner,
                                    artistId = ytChannel?.channelId ?: it.artistId,
                                    ownerAvatarUrl = ytChannel?.avatarUrl ?: it.ownerAvatarUrl,
                                    allTracks = ytTracks.distinctBy { "${it.source}_${it.id}" },
                                )
                            }
                        }
                    }
                    if (!loadedViaYouTube) {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)""")
        # Fecha o if (!loadedViaYouTube) antes do onFailure: o bloco original termina com "                }\\n            }.onFailure"
        # O bloco else original tem 2 niveis de chave; fechamos 1 extra logo antes de }.onFailure
        t = t.replace(
            "                }\n            }.onFailure { e ->",
            "                }\n                    }\n            }.onFailure { e ->", 1)
    # onOpenArtist: source certo (YouTube = YOUTUBE, nunca YOUTUBE_MUSIC)
    t = t.replace(
        "                        Artist(id = directId, name = artistName, thumbnailUrl = state.ownerAvatarUrl, source = Source.YOUTUBE_MUSIC)",
        "                        Artist(id = directId, name = artistName, thumbnailUrl = state.ownerAvatarUrl, source = if (isYouTubePlaylist(state) || playlistSource == Source.YOUTUBE) Source.YOUTUBE else Source.YOUTUBE_MUSIC)")
    t = t.replace(
        "        if (isYouTubePlaylist(state)) {\n            viewModelScope.launch {\n                val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()",
        "        if (isYouTubePlaylist(state) || playlistSource == Source.YOUTUBE) {\n            viewModelScope.launch {\n                val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()")
    t = t.replace(
        "                            Artist(id = channel.channelId, name = channel.channelName.ifBlank { artistName ?: \"Canal\" }, thumbnailUrl = channel.avatarUrl ?: state.ownerAvatarUrl, source = Source.YOUTUBE_MUSIC)",
        "                            Artist(id = channel.channelId, name = channel.channelName.ifBlank { artistName ?: \"Canal\" }, thumbnailUrl = channel.avatarUrl ?: state.ownerAvatarUrl, source = Source.YOUTUBE)")
    # isYouTubePlaylist: considera o source da rota tambem
    t = t.replace(
        "        return state.allTracks.isNotEmpty() && state.allTracks.all { it.source == Source.YOUTUBE }",
        "        if (playlistSource == Source.YOUTUBE) return true\n        return state.allTracks.isNotEmpty() && state.allTracks.all { it.source == Source.YOUTUBE }")
    # onOpenArtistByName: playlist do YouTube nunca cai no resolveArtist do YT Music
    t = t.replace(
        "        if (isYouTubePlaylist(_uiState.value)) return",
        "        if (isYouTubePlaylist(_uiState.value) || playlistSource == Source.YOUTUBE) return")
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt", fix_media_vm)

# ============ 9) ArtistViewModel: hasMore + loadMore do canal + playlist do canal pelo NewPipe ============
def fix_artist_vm(t):
    if "hasMoreVideos = channel.hasMoreVideos" not in t:
        t = t.replace(
            "                        shorts = emptyList(), channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,",
            "                        shorts = emptyList(), channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true, hasMoreVideos = channel.hasMoreVideos, hasMorePlaylists = channel.hasMorePlaylists,")
    if "fun onLoadMoreChannelVideos" not in t:
        t = t.replace(
            "    /** Tocar tudo:",
            """    /** Canal: busca mais videos (proximas paginas da UU). So soma os novos. */
    fun onLoadMoreChannelVideos() {
        val state = _uiState.value
        if (!state.hasMoreVideos || state.isLoadingMoreVideos) return
        _uiState.update { it.copy(isLoadingMoreVideos = true) }
        viewModelScope.launch {
            val more = runCatching { youTubeRepository.loadMoreChannelVideos(state.artistId) }.getOrDefault(emptyList())
            _uiState.update { cur ->
                val merged = (cur.videos + more).distinctBy { it.id }
                cur.copy(videos = merged, isLoadingMoreVideos = false, hasMoreVideos = more.isNotEmpty())
            }
        }
    }

    /** Canal: busca mais playlists. So soma as novas. */
    fun onLoadMoreChannelPlaylists() {
        val state = _uiState.value
        if (!state.hasMorePlaylists || state.isLoadingMorePlaylists) return
        _uiState.update { it.copy(isLoadingMorePlaylists = true) }
        viewModelScope.launch {
            val more = runCatching { youTubeRepository.loadMoreChannelPlaylists(state.artistId) }.getOrDefault(emptyList())
            _uiState.update { cur ->
                val merged = (cur.channelPlaylists + more).distinctBy { it.id }
                cur.copy(channelPlaylists = merged, isLoadingMorePlaylists = false, hasMorePlaylists = more.isNotEmpty())
            }
        }
    }

    /** Tocar tudo:""")
    # Playlist do canal (YouTube) abre faixas pelo NewPipe, nao pelo InnerTube
    t = t.replace(
        "        return runCatching { ytMusicRepository.browsePlaylist(playlist.id).tracks }.getOrDefault(emptyList())",
        "        if (_uiState.value.isYouTubeChannel) return runCatching { youTubeRepository.getYouTubePlaylistTracks(playlist.id) }.getOrDefault(emptyList())\n        return runCatching { ytMusicRepository.browsePlaylist(playlist.id).tracks }.getOrDefault(emptyList())")
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt", fix_artist_vm)

# ============ 10) ArtistScreen: abas Inicio | Videos | Playlists + Inicio completo ============
def fix_artist_screen(t):
    # Parametros novos
    if "onLoadMoreChannelVideos" not in t:
        t = t.replace(
            "    onFollowClick: () -> Unit,\n    onSongClick: (Track) -> Unit,",
            "    onFollowClick: () -> Unit,\n    onLoadMoreChannelVideos: () -> Unit = {},\n    onLoadMoreChannelPlaylists: () -> Unit = {},\n    onSongClick: (Track) -> Unit,")
    # Import do icone ExpandMore
    if "ExpandMore" not in t:
        t = t.replace(
            "import com.vztechcore.vztune.ui.icons.actions.ChevronRight",
            "import com.vztechcore.vztune.ui.icons.actions.ChevronRight\nimport com.vztechcore.vztune.ui.icons.actions.ExpandMore")
    # 3 abas em vez de 2
    old_tabs = """                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 0) Color.White else Color.Transparent).clickable { selectedChannelTab = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Vídeos", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 1) Color.White else Color.Transparent).clickable { selectedChannelTab = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Playlists", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }"""
    new_tabs = """                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 0) Color.White else Color.Transparent).clickable { selectedChannelTab = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Início", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 1) Color.White else Color.Transparent).clickable { selectedChannelTab = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Vídeos", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 2) Color.White else Color.Transparent).clickable { selectedChannelTab = 2 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Playlists", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 2) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }"""
    if old_tabs in t: t = t.replace(old_tabs, new_tabs)
    # Troca o bloco inteiro do conteudo do canal (tab 0/1 antigo) pelo novo (0 = Inicio, 1 = Videos, 2 = Playlists)
    marker = "                        if (selectedChannelTab == 0) {"
    new_block = """                        if (selectedChannelTab == 0) {
                            // ---------- CANAL: INICIO ----------
                            item(key = "channel_home") {
                            ChannelHome(
                                state = state,
                                onVideoClick = onVideoClick,
                                onTrackInfoClick = onTrackInfoClick,
                                onPlaylistClick = onPlaylistClick,
                                onPlaylistMoreClick = { playlistMenu = it },
                                onGoVideos = { selectedChannelTab = 1; channelVideosSort = 0 },
                                onGoVideosTop = { selectedChannelTab = 1; channelVideosSort = 1 },
                                onGoPlaylists = { selectedChannelTab = 2 },
                            )
                            }
                        } else if (selectedChannelTab == 1) {
                            // ---------- CANAL: VIDEOS (Recentes | Em alta + Buscar mais) ----------
                            item(key = "channel_videos_sort") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Recentes" to 0, "Em alta" to 1).forEach { (label, sort) ->
                                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (channelVideosSort == sort) Color.White else Color.White.copy(alpha = 0.08f)).clickable { channelVideosSort = sort }.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(text = label, style = MaterialTheme.typography.labelLarge, color = if (channelVideosSort == sort) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            val shownVideos = if (channelVideosSort == 1) state.videosByViews else state.videos
                            if (shownVideos.isNotEmpty()) {
                                itemsIndexed(shownVideos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                            } else {
                                item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                            if (state.hasMoreVideos) {
                                item(key = "channel_videos_more") {
                                    ChannelLoadMoreButton(label = if (state.isLoadingMoreVideos) "Buscando mais..." else "Buscar mais", onClick = onLoadMoreChannelVideos)
                                }
                            }
                        } else {
                            // ---------- CANAL: PLAYLISTS (+ Buscar mais) ----------
                            if (state.channelPlaylists.isNotEmpty()) {
                                itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                            } else {
                                item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                            if (state.hasMorePlaylists) {
                                item(key = "channel_playlists_more") {
                                    ChannelLoadMoreButton(label = if (state.isLoadingMorePlaylists) "Buscando mais..." else "Buscar mais", onClick = onLoadMoreChannelPlaylists)
                                }
                            }
                        }"""
    if marker in t: t = replace_balanced(t, marker, new_block)
    # Estado do sort dos videos do canal (junto do selectedChannelTab)
    if "channelVideosSort" not in t:
        t = t.replace(
            "    var selectedChannelTab by remember { mutableStateOf(0) }",
            "    var selectedChannelTab by remember { mutableStateOf(0) }\n    var channelVideosSort by remember { mutableStateOf(0) } // 0 = Recentes, 1 = Em alta (mais vistos)")
    # Composables novos no fim do arquivo
    if "fun ChannelHome" not in t:
        t += """

/** Botao "Buscar mais" do canal: busca a proxima leva de videos/playlists no YouTube. */
@Composable
private fun ChannelLoadMoreButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Botao redondo com a seta para baixo (ExpandMore): mostra +4 itens da secao. */
@Composable
private fun ChannelShowMoreButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
            Icon(VzIcons.ExpandMore, contentDescription = "Mostrar mais", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

/** INICIO do canal: video mais famoso em destaque + Para voce + Videos + Mais acessados + Playlists. */
@Composable
private fun ChannelHome(
    state: ArtistUiState,
    onVideoClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistMoreClick: (Playlist) -> Unit,
    onGoVideos: () -> Unit,
    onGoVideosTop: () -> Unit,
    onGoPlaylists: () -> Unit,
) {
    var videosShown by remember { mutableStateOf(4) }
    var topShown by remember { mutableStateOf(4) }
    var playlistsShown by remember { mutableStateOf(4) }
    val nowPlaying = LocalNowPlaying.current
    Column(Modifier.fillMaxWidth()) {
        // 1) Video mais famoso: card 16:9 grande preenchido, igual ao da aba Videos do Search.
        val top = state.mostViewedVideo
        if (top != null) {
            val playback = nowPlaying.playbackOf(top)
            MediaListVideoCard(track = top, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(top) }, onMoreClick = { onTrackInfoClick(top) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalPadding = 0.dp)
            Spacer(Modifier.height(SectionGap))
        }
        // 2) Para voce: fileira 16:9 que arrasta pro lado, ate 50 videos do canal.
        if (state.videos.isNotEmpty()) {
            Text(text = "Para você", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.videos.take(50), key = { it.id }) { video ->
                    val playback = nowPlaying.playbackOf(video)
                    MediaListVideoCard(track = video, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, modifier = Modifier.width(300.dp), horizontalPadding = 0.dp)
                }
            }
            Spacer(Modifier.height(SectionGap))
        }
        // 3) Videos: 4 em lista + seta pra mostrar +4, infinitamente.
        if (state.videos.isNotEmpty()) {
            SectionHeaderArrow(title = "Vídeos", onArrowClick = onGoVideos)
            state.videos.take(videosShown).forEach { video ->
                ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) })
            }
            if (videosShown < state.videos.size || state.hasMoreVideos) {
                ChannelShowMoreButton(onClick = {
                    videosShown += 4
                    if (videosShown > state.videos.size && state.hasMoreVideos) { /* proxima leva vem pelo Buscar mais da aba Videos */ }
                })
            }
            Spacer(Modifier.height(SectionGap))
        }
        // 4) Videos mais acessados: os mais vistos em ordem, mesmo sistema 4 + 4.
        val byViews = state.videosByViews
        if (byViews.isNotEmpty()) {
            SectionHeaderArrow(title = "Vídeos mais acessados", onArrowClick = onGoVideosTop)
            byViews.take(topShown).forEach { video ->
                ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) })
            }
            if (topShown < byViews.size) ChannelShowMoreButton(onClick = { topShown += 4 })
            Spacer(Modifier.height(SectionGap))
        }
        // 5) Playlists: 4 em lista + seta pra mostrar +4.
        if (state.channelPlaylists.isNotEmpty()) {
            SectionHeaderArrow(title = "Playlists", onArrowClick = onGoPlaylists)
            state.channelPlaylists.take(playlistsShown).forEach { playlist ->
                ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { onPlaylistMoreClick(playlist) })
            }
            if (playlistsShown < state.channelPlaylists.size) ChannelShowMoreButton(onClick = { playlistsShown += 4 })
            Spacer(Modifier.height(SectionGap))
        }
        if (state.videos.isEmpty() && state.channelPlaylists.isEmpty()) {
            Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
        }
    }
}
"""
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt", fix_artist_screen)

must("onLoadMoreChannelVideos" in read("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"), "ArtistViewModel tem loadMore do canal")
must("ChannelHome" in read("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"), "ArtistScreen tem Início do canal")
must("sourceName = playlist.source.name" in read("app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/NavExtensions.kt"), "NavExtensions passa source da playlist")
must("loadMoreChannelVideos" in read("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"), "YouTubeRepository pagina o canal")
print("FIX16 OK")
PY
