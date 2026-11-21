#!/bin/bash
# VzTune FIX19 - Icones da fila trocados + canal so com conteudo DELE + separacao total NewPipe / InnerTube
# Aplica sobre FIX18. Rode na raiz do projeto: bash VzTune-FIX19.sh
# 1) TrackOptionsSheet: troca os icones de "Reproduzir como proxima faixa" <-> "Adicionar a fila" um com o outro
# 2) YouTubeRepository.browseChannel: Videos / Shorts / Playlists do fallback de busca so aceitam o MESMO criador (isSameCreator, "TH" == "TH - Topic"). Antes o contains() deixava passar canal com nome parecido e a aba Playlists vinha com playlists de canais aleatorios
# 3) ArtistViewModel.loadContent: respeita route.isYouTubeChannel. true = SO NewPipe, false = SO InnerTube. Antes carregava os dois e se o YT Music tivesse qualquer faixa (mesmo de outro artista com nome parecido) mostrava o YT Music e descartava o canal
# 4) TrackMenu + Search onOpenChannelForTrack: remove o fallback pro InnerTube getArtistFromVideo. Conteudo NewPipe fica SO no YouTube. InnerTube player com cliente YTMUSIC devolve a MUSICA do video, nao quem postou (video do canal 'th' com musica do artista 'TH' -> UC do TH)
# 5) Search NavigationEvent.OpenArtist ganha isYouTubeChannel + AppNavHost passa a flag. Antes a busca perdia a flag e todo canal aberto pela busca caia no YT Music
# 6) MediaList onOpenArtist: playlist do YouTube abre como canal (Source.YOUTUBE), playlist do YT Music continua como artista. Antes tudo ia como YOUTUBE_MUSIC
# 7) ArtistViewModel.loadChannelPlaylistTracks: playlist do canal YouTube -> NewPipe getYouTubePlaylistTracks, YT Music -> InnerTube. Nunca cruza
set -e
python3 - << 'PY'
import re, pathlib
ROOT = pathlib.Path(".")
def read(p):
    try:
        return pathlib.Path(p).read_text(encoding="utf-8")
    except Exception:
        return None
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
def ok(m): print("OK", m)
def warn(m): print("WARN", m)

# ============ 1) TrackOptionsSheet - troca os icones ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt"
t = read(p)
if t is None:
    warn("TrackOptionsSheet nao achado")
else:
    m_next = re.search(r"SheetOptionRow\(icon = VzIcons\.(\w+)[^\n]*onPlayNext\(track\)", t)
    m_queue = re.search(r"SheetOptionRow\(icon = VzIcons\.(\w+)[^\n]*onAddToQueue\(track\)", t)
    if not m_next or not m_queue:
        warn("TrackOptionsSheet: linhas PlayNext/AddToQueue nao achadas")
    else:
        icon_next, icon_queue = m_next.group(1), m_queue.group(1)
        if icon_next == icon_queue:
            ok(f"TrackOptionsSheet: icones ja iguais ({icon_next}), nada a trocar")
        else:
            t = re.sub(r"(SheetOptionRow\(icon = VzIcons\.)\w+([^\n]*onPlayNext\(track\))", r"\1__FIX19_TMP__\2", t, count=1)
            t = re.sub(r"(SheetOptionRow\(icon = VzIcons\.)\w+([^\n]*onAddToQueue\(track\))", r"\1" + icon_next + r"\2", t, count=1)
            t = t.replace("__FIX19_TMP__", icon_queue, 1)
            write(p, t)
            ok(f"TrackOptionsSheet: icones trocados {icon_next} <-> {icon_queue} (Proxima agora {icon_queue}, Fila agora {icon_next})")

# ============ 2) YouTubeRepository - isSameCreator + fallbacks estritos ============
p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if t is None:
    warn("YouTubeRepository nao achado")
else:
    changed = False
    # 2a) helper isSameCreator
    if "fun isSameCreator" not in t:
        helper = r'''    // FIX19 - So aceita conteudo do MESMO criador. Normaliza: minusculo, sem " - Topic", sem espacos extras.
    // "TH" vs "TH - Topic" = mesmo criador. "th" vs "TH2" / "The ..." = criador diferente, descarta.
    private fun isSameCreator(owner: String?, channelName: String): Boolean {
        if (owner.isNullOrBlank() || channelName.isBlank()) return false
        fun norm(s: String): String {
            var v = s.trim().lowercase()
            if (v.endsWith(" - topic")) v = v.removeSuffix(" - topic").trim()
            if (v.endsWith("- topic")) v = v.removeSuffix("- topic").trim()
            return v.replace(Regex("\\s+"), " ").trim()
        }
        return norm(owner) == norm(channelName)
    }

'''
        if "suspend fun browseChannel" in t:
            t = t.replace("suspend fun browseChannel", helper + "suspend fun browseChannel", 1)
            changed = True
            ok("YouTubeRepository: isSameCreator criado")
        else:
            warn("YouTubeRepository: browseChannel nao achado p/ isSameCreator")
    # 2b) Videos fallback: contains() frouxo -> isSameCreator exato
    old_videos = ".filter { it.artist.contains(channelName, ignoreCase = true) || channelName.contains(it.artist, ignoreCase = true) }"
    if old_videos in t:
        t = t.replace(old_videos, ".filter { isSameCreator(it.artist, channelName) }")
        changed = True
        ok("YouTubeRepository: fallback Videos agora so mesmo criador")
    else:
        t2, n = re.subn(r"\.filter \{ it\.artist\.contains\(channelName[^}]*\}", ".filter { isSameCreator(it.artist, channelName) }", t)
        if n > 0:
            t = t2; changed = True
            ok("YouTubeRepository: fallback Videos (regex) agora so mesmo criador")
        else:
            warn("YouTubeRepository: fallback Videos nao achado (talvez ja esteja estrito)")
    # 2c) Shorts fallback: searchShorts(channelName ...) -> filtra dono + (se ja tem isRealShort, mantem)
    def patch_shorts_line(m):
        line = m.group(0)
        if "isSameCreator" in line:
            return line
        if "isRealShort(it)" in line:
            return line.replace(".filter { isRealShort(it) }", ".filter { isSameCreator(it.artist, channelName) && isRealShort(it) }")
        return line.replace(".items.map { it.track }", ".items.map { it.track }.filter { isSameCreator(it.artist, channelName) }")
    t2, n = re.subn(r"[^\n]*searchShorts\(channelName[^\n]*", patch_shorts_line, t)
    if n > 0 and t2 != t:
        t = t2; changed = True
        ok("YouTubeRepository: fallback Shorts agora so mesmo criador")
    elif n > 0:
        ok("YouTubeRepository: fallback Shorts ja estava estrito")
    else:
        warn("YouTubeRepository: fallback Shorts nao achado")
    # 2d) Playlists fallback: searchPlaylists(channelName ...) -> filtra dono
    def patch_pl_line(m):
        line = m.group(0)
        if "isSameCreator" in line:
            return line
        return line.replace(".map { it.playlist }", ".map { it.playlist }.filter { isSameCreator(it.owner, channelName) }")
    t2, n = re.subn(r"[^\n]*searchPlaylists\(channelName[^\n]*", patch_pl_line, t)
    if n > 0 and t2 != t:
        t = t2; changed = True
        ok("YouTubeRepository: fallback Playlists agora so do mesmo criador")
    elif n > 0:
        ok("YouTubeRepository: fallback Playlists ja estava estrito")
    else:
        warn("YouTubeRepository: fallback Playlists nao achado")
    # 2e) Playlists primaria (/playlists): usa uploaderName real e descarta se for de outro criador
    old_pl_primary = "Playlist(id = pid, title = item.name"
    if old_pl_primary in t and "isSameCreator(item.uploaderName" not in t:
        t = t.replace(old_pl_primary, "if (!item.uploaderName.isNullOrBlank() && !isSameCreator(item.uploaderName, channelName)) return@mapNotNull null\n                    Playlist(id = pid, title = item.name", 1)
        changed = True
        ok("YouTubeRepository: Playlists primaria descarta dono diferente")
    if "owner = channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount" in t:
        t = t.replace("owner = channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount", "owner = item.uploaderName?.takeIf { it.isNotBlank() } ?: channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount", 1)
        changed = True
        ok("YouTubeRepository: Playlists primaria usa uploaderName real")
    if changed:
        write(p, t)

# ============ 3) ArtistViewModel - separacao total + loadChannelPlaylistTracks ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
t = read(p)
if t is None:
    warn("ArtistViewModel nao achado")
else:
    # Detecta FIX18 (channelVideosHasMore) p/ nao quebrar quem nao aplicou FIX18
    ui_state = read("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt") or ""
    yt_repo = read("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt") or ""
    has_more = ("channelVideosHasMore" in ui_state) and ("hasMoreChannelVideos" in yt_repo)
    more_line = "                            channelVideosHasMore = youTubeRepository.hasMoreChannelVideos(route.id), isLoadingMoreChannelVideos = false,\n" if has_more else ""
    # Import Source p/ loadChannelPlaylistTracks comparar playlist.source
    if "import com.vztechcore.vztune.core.domain.music.Source" not in t:
        if "import com.vztechcore.vztune.core.domain.music.Playlist" in t:
            t = t.replace("import com.vztechcore.vztune.core.domain.music.Playlist", "import com.vztechcore.vztune.core.domain.music.Playlist\nimport com.vztechcore.vztune.core.domain.music.Source", 1)
        elif "import com.vztechcore.vztune.core.domain.music.Track" in t:
            t = t.replace("import com.vztechcore.vztune.core.domain.music.Track", "import com.vztechcore.vztune.core.domain.music.Track\nimport com.vztechcore.vztune.core.domain.music.Source", 1)
    # 3a) Substitui loadContent inteiro (do "private fun loadContent" ate o "/** Tocar tudo")
    start = t.find("private fun loadContent()")
    end = t.find("/** Tocar tudo", start)
    if start != -1 and end != -1:
        new_load = '''    // FIX19 - Separacao total: quem veio do YouTube fica no YouTube, quem veio do YT Music fica no YT Music.
    // Antes: carregava os dois mundos e se o YT Music tivesse QUALQUER faixa (mesmo de outro artista com nome parecido, ex: musica "TH" dentro do video do canal "th") mostrava o YT Music e descartava o canal. O YT Music tinha preferencia.
    // Agora: route.isYouTubeChannel == true -> SO NewPipe browseChannel, sem InnerTube. false -> SO InnerTube browseArtist, sem NewPipe. Sem fallback pro outro mundo.
    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (route.isYouTubeChannel) {
                val channel = runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull()
                if (channel != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false, errorMessage = null,
                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                            subscribers = channel.subscriberCount ?: it.subscribers,
                            description = channel.description ?: it.description,
                            topSongs = emptyList(), songsMore = null,
                            albums = emptyList(), albumsMore = null,
                            singles = emptyList(), singlesMore = null,
                            videos = channel.videos, videosMore = null,
                            featured = emptyList(), featuredMore = null, relatedArtists = emptyList(),
                            shorts = channel.shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,
''' + more_line + '''                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
                return@launch
            }
            // YT Music: SO InnerTube, nunca NewPipe
            val music = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            if (music != null && (music.topSongs.isNotEmpty() || music.albums.isNotEmpty() || music.singles.isNotEmpty() || music.videos.isNotEmpty())) {
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
                        shorts = emptyList(), channelPlaylists = emptyList(), bannerUrl = null, isYouTubeChannel = false,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este artista.") }
        }
    }

'''
        t = t[:start] + new_load + t[end:]
        ok("ArtistViewModel: loadContent agora respeita isYouTubeChannel (sem preferencia pro YT Music)")
    else:
        warn("ArtistViewModel: loadContent nao achado p/ substituir")
    # 3b) loadChannelPlaylistTracks: YouTube -> NewPipe, YT Music -> InnerTube
    start2 = t.find("private suspend fun loadChannelPlaylistTracks")
    if start2 != -1:
        end2 = t.find("\n    }\n", start2)
        if end2 != -1:
            end2 = end2 + len("\n    }\n")
            new_tracks = '''    private suspend fun loadChannelPlaylistTracks(playlist: Playlist): List<Track> {
        // FIX19 - Playlist do canal: YouTube -> NewPipe, YT Music -> InnerTube. Nunca cruza.
        if (playlist.source == Source.YOUTUBE) {
            return runCatching { youTubeRepository.getYouTubePlaylistTracks(playlist.id) }.getOrDefault(emptyList())
        }
        return runCatching { ytMusicRepository.browsePlaylist(playlist.id).tracks }.getOrDefault(emptyList())
    }
'''
            t = t[:start2] + new_tracks + t[end2:]
            ok("ArtistViewModel: loadChannelPlaylistTracks agora separa NewPipe / InnerTube")
        else:
            warn("ArtistViewModel: fim de loadChannelPlaylistTracks nao achado")
    else:
        warn("ArtistViewModel: loadChannelPlaylistTracks nao achado")
    write(p, t)

# ============ 4) TrackMenuViewModel - tira fallback InnerTube ============
p_menu = None
for cand in ["app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt", "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/trackmenu/TrackMenuViewModel.kt"]:
    if read(cand) is not None:
        p_menu = cand
        break
if p_menu is None:
    warn("TrackMenuViewModel nao achado")
else:
    t = read(p_menu)
    old_fb = """            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(fromInnerTube))
                return@launch
            }"""
    if old_fb in t:
        t = t.replace(old_fb, """            // FIX19 - Sem fallback pro InnerTube: conteudo NewPipe fica SO no YouTube.
            // InnerTube player com cliente YTMUSIC devolve a MUSICA do video (ex: video do canal 'th' com musica do artista 'TH' -> UC do TH), nao quem postou. Sem canal NewPipe = nao navega.""")
        t = t.replace("So usa InnerTube se o\n            // NewPipe falhar.", "Nunca usa InnerTube aqui.")
        t = t.replace("So usa InnerTube se o NewPipe falhar.", "Nunca usa InnerTube aqui.")
        write(p_menu, t)
        ok("TrackMenuViewModel: fallback InnerTube removido")
    elif "getArtistFromVideo" not in t:
        ok("TrackMenuViewModel: ja sem fallback InnerTube")
    else:
        warn("TrackMenuViewModel: fallback InnerTube nao achado no formato esperado")

# ============ 5) SearchViewModel - tira fallback + ganha isYouTubeChannel ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt"
t = read(p)
if t is None:
    warn("SearchViewModel nao achado")
else:
    changed = False
    # 5a) NavigationEvent.OpenArtist ganha isYouTubeChannel = false (default mantem os outros 3 emits como YT Music)
    old_ev = """        data class OpenArtist(
            val id: String, val name: String, val thumbnailUrl: String?,
        ) : NavigationEvent"""
    if old_ev in t:
        t = t.replace(old_ev, """        data class OpenArtist(
            val id: String, val name: String, val thumbnailUrl: String?,
            val isYouTubeChannel: Boolean = false,
        ) : NavigationEvent""")
        changed = True
        ok("SearchViewModel: OpenArtist ganhou isYouTubeChannel")
    else:
        t2, n = re.subn(r"(data class OpenArtist\(\s*val id: String, val name: String, val thumbnailUrl: String\?,)(\s*\) : NavigationEvent)", r"\1\n            val isYouTubeChannel: Boolean = false,\2", t)
        if n > 0:
            t = t2; changed = True
            ok("SearchViewModel: OpenArtist ganhou isYouTubeChannel (regex)")
        else:
            warn("SearchViewModel: OpenArtist nao achado")
    # 5b) onOpenChannelForTrack: emite como canal + tira fallback InnerTube
    old_emit = "_navigationEvent.emit(NavigationEvent.OpenArtist(channel.channelId, channel.channelName, channel.avatarUrl))"
    if old_emit in t:
        t = t.replace(old_emit, "_navigationEvent.emit(NavigationEvent.OpenArtist(channel.channelId, channel.channelName, channel.avatarUrl, isYouTubeChannel = true))")
        changed = True
        ok("SearchViewModel: onOpenChannelForTrack agora emite como canal")
    old_fb2 = """            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(fromInnerTube.id, fromInnerTube.name, fromInnerTube.thumbnailUrl))
                return@launch
            }"""
    if old_fb2 in t:
        t = t.replace(old_fb2, """            // FIX19 - Sem fallback pro InnerTube: conteudo NewPipe fica SO no YouTube. Sem canal NewPipe = nao navega.""")
        changed = True
        ok("SearchViewModel: fallback InnerTube removido")
    if changed:
        write(p, t)

# ============ 6) AppNavHost - Search passa isYouTubeChannel ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
t = read(p)
if t is None:
    warn("AppNavHost nao achado")
else:
    # So o bloco da Search (id = event.id ...) ganha a flag. Os outros ja passam source == YOUTUBE.
    pattern = re.compile(r"(is SearchViewModel\.NavigationEvent\.OpenArtist -> navController\.navigate\(\s*Routes\.Artist\(\s*id = event\.id,\s*name = event\.name,\s*thumbnailUrl = event\.thumbnailUrl,)(\s*\))", re.DOTALL)
    t2, n = pattern.subn(r"\1\n                                            isYouTubeChannel = event.isYouTubeChannel,\2", t)
    if n > 0:
        t = t2
        write(p, t)
        ok("AppNavHost: Search agora passa isYouTubeChannel")
    elif "isYouTubeChannel = event.isYouTubeChannel" in t:
        ok("AppNavHost: Search ja passa isYouTubeChannel")
    else:
        warn("AppNavHost: bloco Search OpenArtist nao achado")

# ============ 7) MediaListViewModel - onOpenArtist respeita o mundo da playlist ============
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t = read(p)
if t is None:
    warn("MediaListViewModel nao achado")
else:
    start = t.find("fun onOpenArtist()")
    end = t.find("fun onOpenArtistByName", start)
    if start == -1 or end == -1:
        warn("MediaListViewModel: onOpenArtist nao achado")
    else:
        block = t[start:end]
        orig = block
        # 7a) directId: se a playlist e do YouTube -> canal, senao -> artista YT Music
        # So o primeiro emit dentro de onOpenArtist (directId) vira condicional
        block = block.replace("source = Source.YOUTUBE_MUSIC", "source = if (isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC", 1)
        # 7b) getChannelForPlaylist: sempre canal YouTube (ja esta dentro de if (isYouTubePlaylist))
        block = block.replace("source = Source.YOUTUBE_MUSIC", "source = Source.YOUTUBE", 1)
        if block != orig:
            t = t[:start] + block + t[end:]
            write(p, t)
            ok("MediaListViewModel: onOpenArtist agora abre playlist YouTube como canal")
        elif "isYouTubePlaylist(state)) Source.YOUTUBE" in block:
            ok("MediaListViewModel: onOpenArtist ja estava certo")
        else:
            warn("MediaListViewModel: emits de onOpenArtist nao achados")
print("== FIX19 pronto ==")
PY
