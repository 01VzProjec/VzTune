#!/bin/bash
# ============================================================
# VzTune FIX5 - Canal de verdade (YouTube, nao YT Music)
# + Shorts que funciona (busca com filtro shorts)
# Requer FIX4 + hotfixes ja aplicados.
# Roda na raiz: cd ~/VzTune && bash VzTune-FIX5-CANAL-DE-VERDADE-SHORTS.sh
# ============================================================
set -e
echo "== FIX5 Canal de verdade + Shorts =="

python3 - <<'PY'
import re, sys
def read(p):
    with open(p, encoding="utf-8") as f: return f.read()
def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)
def must(cond, msg):
    if not cond:
        print(f"ERRO {msg}"); sys.exit(1)
    print(f"OK {msg}")

# ---------- 0) Pre-check FIX4 ----------
t = read("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
must("fun searchShorts" in t, "FIX4 aplicado? searchShorts existe")
must("fun parseShortItems" in t, "FIX4 aplicado? parseShortItems existe")
must("val shorts:" in t, "FIX4 aplicado? ChannelPage com shorts")

# ---------- 1) Routes.Artist: flag isYouTubeChannel ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/Routes.kt"
t = read(p)
if "isYouTubeChannel" not in t:
    old = """    @Serializable data class Artist(
        val id: String,
        val name: String,
        val thumbnailUrl: String? = null,
    )"""
    must(old in t, "Routes.kt: ancora Artist")
    t = t.replace(old, """    @Serializable data class Artist(
        val id: String,
        val name: String,
        val thumbnailUrl: String? = null,
        // true = canal do YouTube (videos/shorts/playlists via NewPipe).
        // false = artista do YT Music (musicas/albuns via InnerTube).
        // Sem essa flag, o AppNavHost perdia o source e todo mundo caia no YT Music.
        val isYouTubeChannel: Boolean = false,
    )""")
    write(p, t)
must("isYouTubeChannel" in read(p), "Routes.kt: isYouTubeChannel")

# ---------- 2) AppNavHost: preservar o source ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
t = read(p)
if "import com.vztechcore.vztune.core.domain.music.Source" not in t:
    must("import com.vztechcore.vztune.core.domain.music.Track" in t, "AppNavHost: ancora import Track")
    t = t.replace(
        "import com.vztechcore.vztune.core.domain.music.Track",
        "import com.vztechcore.vztune.core.domain.music.Source\nimport com.vztechcore.vztune.core.domain.music.Track"
    )
    write(p, t)
t = read(p)
# TrackMenu + MediaList: event.artist.source == YOUTUBE => canal
old1 = "Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)"
if old1 in t:
    t = t.replace(old1, "Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl, isYouTubeChannel = event.artist.source == Source.YOUTUBE)")
    write(p, t)
    print("OK AppNavHost: TrackMenu/MediaList preservam source")
t = read(p)
# Related / Home / Library: artist.source == YOUTUBE => canal
old2 = "Routes.Artist(artist.id, artist.name, artist.thumbnailUrl)"
if old2 in t:
    t = t.replace(old2, "Routes.Artist(artist.id, artist.name, artist.thumbnailUrl, isYouTubeChannel = artist.source == Source.YOUTUBE)")
    write(p, t)
    print("OK AppNavHost: related/home preservam source")
t = read(p)
# Search: artista do YT Music, nunca canal
if "isYouTubeChannel = false" not in t:
    old3 = """                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )"""
    if old3 in t:
        t = t.replace(old3, """                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                        isYouTubeChannel = false,
                                    )""")
        write(p, t)
        print("OK AppNavHost: Search artista = YT Music")
t = read(p)
must("isYouTubeChannel = event.artist.source == Source.YOUTUBE" in t, "AppNavHost: preserva source TrackMenu/MediaList")
must("isYouTubeChannel = artist.source == Source.YOUTUBE" in t, "AppNavHost: preserva source related/home")

# ---------- 3) MediaListViewModel.onOpenArtist: source certo ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t = read(p)
if "source = Source.YOUTUBE_MUSIC," in t and "isYouTubePlaylist(state)" in t:
    # So o onOpenArtist tem esse trecho com directId. Troca para YOUTUBE quando for playlist do YouTube.
    old = """                        Artist(
                            id = directId,
                            name = artistName,
                            thumbnailUrl = state.ownerAvatarUrl,
                            source = Source.YOUTUBE_MUSIC,
                        )"""
    if old in t:
        t = t.replace(old, """                        Artist(
                            id = directId,
                            name = artistName,
                            thumbnailUrl = state.ownerAvatarUrl,
                            // Playlist do YouTube => canal. Antes era fixo YOUTUBE_MUSIC,
                            // entao o AppNavHost (agora com flag) caia no artista do YT Music.
                            source = if (isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC,
                        )""")
        write(p, t)
        print("OK MediaListViewModel: onOpenArtist usa YOUTUBE para playlist do YouTube")
t = read(p)
must("if (isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC" in t, "MediaListViewModel: source condicional")

# ---------- 4) ArtistUiState: shorts + playlists + banner + flag ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt"
t = read(p)
if "isYouTubeChannel" not in t:
    must("val videos: List<Track> = emptyList()," in t, "ArtistUiState: ancora videos")
    t = t.replace(
        "val videos: List<Track> = emptyList(),",
        "val videos: List<Track> = emptyList(),\n    // Canal do YouTube (FIX5): Shorts e playlists do canal, banner do header.\n    val shorts: List<Track> = emptyList(),\n    val channelPlaylists: List<Playlist> = emptyList(),\n    val bannerUrl: String? = null,\n    val isYouTubeChannel: Boolean = false,"
    )
    write(p, t)
must("isYouTubeChannel" in read(p), "ArtistUiState: isYouTubeChannel + shorts + playlists + banner")

# ---------- 5) ArtistViewModel: canal pula YT Music ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
t = read(p)
# 5a) estado inicial ja carrega a flag da rota
if "isYouTubeChannel = route.isYouTubeChannel" not in t:
    old = """        ArtistUiState(
            artistId = route.id,
            name = route.name,
            thumbnailUrl = route.thumbnailUrl,
        )"""
    if old in t:
        t = t.replace(old, """        ArtistUiState(
            artistId = route.id,
            name = route.name,
            thumbnailUrl = route.thumbnailUrl,
            isYouTubeChannel = route.isYouTubeChannel,
        )""")
        write(p, t)
t = read(p)
# 5b) no loadContent, se for canal, NEM TENTA o YT Music. Vai direto pro NewPipe.
if "FIX5: canal do YouTube pula o YT Music" not in t:
    must("private fun loadContent()" in t, "ArtistViewModel: ancora loadContent")
    old = """    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()"""
    if old in t:
        t = t.replace(old, """    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // FIX5: canal do YouTube pula o YT Music. Antes ele tentava o YT Music
            // primeiro e, se voltasse qualquer coisa, mostrava artista do YT Music
            // e nunca chegava no canal real. Agora canal vai direto pro NewPipe.
            if (route.isYouTubeChannel) {
                val channel = runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull()
                if (channel != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            isYouTubeChannel = true,
                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                            bannerUrl = channel.bannerUrl,
                            description = channel.description,
                            subscribers = channel.subscriberCount ?: it.subscribers,
                            videos = channel.videos,
                            shorts = channel.shorts,
                            channelPlaylists = channel.playlists,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
                }
                return@launch
            }
            val result = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()""")
        write(p, t)
        print("OK ArtistViewModel: canal pula YT Music")
    else:
        # Fallback: insere antes do browseArtist se o formato for diferente
        print("AVISO ArtistViewModel: formato de loadContent diferente, tentando ancora alternativa")
        if "val result = runCatching { ytMusicRepository.browseArtist(route.id) }" in t:
            t = t.replace(
                "val result = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()",
                """// FIX5: canal do YouTube pula o YT Music.
            if (route.isYouTubeChannel) {
                val channel = runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull()
                if (channel != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            isYouTubeChannel = true,
                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                            bannerUrl = channel.bannerUrl,
                            description = channel.description,
                            subscribers = channel.subscriberCount ?: it.subscribers,
                            videos = channel.videos,
                            shorts = channel.shorts,
                            channelPlaylists = channel.playlists,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
                }
                return@launch
            }
            val result = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()"""
            )
            write(p, t)
            print("OK ArtistViewModel: canal pula YT Music (fallback)")
t = read(p)
must("FIX5: canal do YouTube pula o YT Music" in t or "if (route.isYouTubeChannel)" in t, "ArtistViewModel: branch canal")
must("shorts = channel.shorts" in t, "ArtistViewModel: usa shorts do canal")

# ---------- 6) ArtistScreen: secoes Shorts + Playlists + share certo ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
t = read(p)
# 6a) Share: canal => youtube.com/channel/UC..., artista => music.youtube.com/browse/...
if "isYouTubeChannel" not in t:
    old = 'val url = "https://music.youtube.com/browse/${state.artistId}"'
    if old in t:
        t = t.replace(old, 'val url = if (state.isYouTubeChannel) "https://www.youtube.com/channel/${state.artistId}" else "https://music.youtube.com/browse/${state.artistId}"')
        write(p, t)
        print("OK ArtistScreen: share usa link do canal quando for canal")
t = read(p)
# 6b) Secoes Shorts + Playlists do canal, depois de Videos e antes de Destacado
if "key = \"shorts\"" not in t:
    must('rowSection(\n                        key = "featured"' in t or 'key = "featured"' in t, "ArtistScreen: ancora featured")
    shorts_section = """                    // FIX5: canal do YouTube mostra Shorts e playlists do canal.
                    rowSection(
                        key = "shorts", title = "Shorts", items = state.shorts,
                        onMoreClick = null,
                        itemKey = { it.id },
                    ) { short ->
                        val playback = nowPlaying.playbackOf(short)
                        MediaListVideoCard(
                            track = short,
                            isPlaying = playback.isPlaying,
                            isPaused = playback.isPaused,
                            onClick = { onVideoClick(short) },
                            onMoreClick = { onTrackInfoClick(short) },
                            modifier = Modifier.width(160.dp),
                            horizontalPadding = 0.dp,
                        )
                    }

                    rowSection(
                        key = "channelPlaylists", title = "Playlists", items = state.channelPlaylists,
                        onMoreClick = null,
                        itemKey = { it.id },
                    ) { playlist ->
                        CoverCard(
                            title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl,
                            onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

"""
    # Insere antes do rowSection featured
    if 'rowSection(\n                        key = "featured"' in t:
        t = t.replace('rowSection(\n                        key = "featured"', shorts_section + 'rowSection(\n                        key = "featured"')
        write(p, t)
    else:
        # fallback: procura a linha com key featured
        t = t.replace('key = "featured"', 'key = "shorts_placeholder"', 1)  # nao faz nada, so para nao falhar silencioso
        write(p, t)
        print("AVISO ArtistScreen: formato featured diferente, verificar manualmente")
t = read(p)
must('key = "shorts"' in t, "ArtistScreen: secao Shorts")
must('key = "channelPlaylists"' in t, "ArtistScreen: secao Playlists do canal")

# ---------- 7) YouTubeRepository.searchShorts: aceitar Shorts do filtro ----------
p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
# 7a) parseShortItems ganha parametro requireShortsUrl.
# Antes ele SEMPRE exigia /shorts/ na URL. Quando a busca ja usou o filtro
# "shorts", o NewPipe pode devolver Shorts com URL watch?v= normal, e o filtro
# jogava tudo fora => aba Shorts vazia. Agora: filtro shorts => aceita tudo,
# fallback generico => exige /shorts/.
if "requireShortsUrl" not in t:
    old = """    private fun parseShortItems(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            if (!rawUrl.contains("/shorts/")) return@mapNotNull null"""
    must(old in t, "YouTubeRepository: ancora parseShortItems")
    t = t.replace(old, """    private fun parseShortItems(items: List<InfoItem>, requireShortsUrl: Boolean = true): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            // Se a busca ja usou o filtro "shorts", todo resultado ja e Short.
            // Se foi busca generica, ai sim precisa filtrar por /shorts/.
            if (requireShortsUrl && !rawUrl.contains("/shorts/")) return@mapNotNull null""")
    write(p, t)
    print("OK YouTubeRepository: parseShortItems com requireShortsUrl")
t = read(p)
# 7b) searchShorts: primeira tentativa (filtro shorts) usa requireShortsUrl = false
if "parseShortItems(extractor.initialPage.items, requireShortsUrl = false)" not in t:
    old = "val items = parseShortItems(extractor.initialPage.items).take(pageSize)"
    if old in t:
        t = t.replace(old, "val items = parseShortItems(extractor.initialPage.items, requireShortsUrl = false).take(pageSize)")
        write(p, t)
        print("OK searchShorts: filtro shorts aceita tudo")
t = read(p)
# 7c) fallback generico continua exigindo /shorts/
if "parseShortItems(generic.initialPage.items, requireShortsUrl = true)" not in t:
    old = "val genericItems = parseShortItems(generic.initialPage.items).take(pageSize)"
    if old in t:
        t = t.replace(old, "val genericItems = parseShortItems(generic.initialPage.items, requireShortsUrl = true).take(pageSize)")
        write(p, t)
        print("OK searchShorts: fallback generico exige /shorts/")
t = read(p)
# 7d) loadMore tambem usa requireShortsUrl = false (a sessao ja e do filtro shorts)
if "parseShortItems(next.items, requireShortsUrl = false)" not in t:
    old = "ShortPage(parseShortItems(next.items).take(pageSize), session.nextPage != null)"
    if old in t:
        t = t.replace(old, "ShortPage(parseShortItems(next.items, requireShortsUrl = false).take(pageSize), session.nextPage != null)")
        write(p, t)
        print("OK searchShorts: loadMore aceita tudo")
t = read(p)
must("requireShortsUrl" in t, "YouTubeRepository: requireShortsUrl aplicado")
must(t.count("{") == t.count("}"), "YouTubeRepository: chaves balanceadas")

print("\nFIX5 aplicado. Build e teste o canal + Shorts.")
PY
