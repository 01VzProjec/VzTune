#!/bin/bash
# VzTune FIX14 - REBUILD Artista/Canal do zero
# Regra: tem faixa de musica (topSongs/albums/singles) = ARTISTA (so InnerTube, sem abas, so Home)
#        sem musica = CANAL (NewPipe, abas Videos | Playlists)
# Home Artista: Musicas (slide 4 por pagina + Tocar tudo + >) | Singles e EPs (fileira + >) | Playlists (fileira + >) | Videos 16:9 (fileira + >)
# Canal: abas Videos | Playlists, sem Shorts
# Search: tira aba Shorts (remove SHORTS do SearchFilter + branch do MusicRepository + grid do SearchResultCards)
# Cria icone ArrowForward (>) que faltava
# Rode na raiz: bash VzTune-FIX14-REBUILD-ARTISTA-CANAL.sh
set -e
python3 - << 'PY'
import pathlib, re

# ---------- 0) Cria icone ArrowForward (>) ----------
arrow_path = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ArrowForward.kt")
if not arrow_path.exists():
    arrow_path.parent.mkdir(parents=True, exist_ok=True)
    arrow_path.write_text('''package com.vztechcore.vztune.ui.icons.actions

import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.ui.icons.VzIcons

val VzIcons.ArrowForward: ImageVector
    get() = VzIcons.icon(
        name = "ArrowForward",
        pathData = "M16.18,13H4q-0.43,0 -0.71-0.29T3,12t0.29-0.71T4,11h8.18l-4.9-4.9q-0.28-0.28 -0.28-0.7t0.28-0.7q0.28-0.28 0.71-0.28t0.71,0.28l6.59,6.6q0.3,0.3 0.3,0.7t-0.3,0.7l-6.6,6.6q-0.28,0.28 -0.7,0.28t-0.7-0.28q-0.28-0.28 -0.28-0.7t0.28-0.7l4.9-4.9L16.18,13Z"
    )
''', encoding="utf-8")
    print("OK ArrowForward criado")
else:
    print("ArrowForward ja existe")

# ---------- 1) ArtistViewModel: regra tem-musica = artista (so InnerTube) ----------
vm_path = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt")
vm = vm_path.read_text(encoding="utf-8")
# Substitui loadContent inteiro: do "private fun loadContent" ate "fun onRandomClick"
start = vm.find("    private fun loadContent()")
end = vm.find("    fun onRandomClick()")
if start == -1 or end == -1:
    print("AVISO: nao achei loadContent no ViewModel, pulando ViewModel")
else:
    new_load = """    // FIX14 REBUILD - Regra: tem faixa de musica = ARTISTA (so InnerTube). Sem musica = CANAL (NewPipe).
    // Antes: juntava os dois mundos e o artista caia como canal. Agora nao mistura mais.
    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isChannelId = route.id.startsWith("UC")
            val channel = if (isChannelId) runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull() else null
            val music = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            val hasMusic = music != null && (music.topSongs.isNotEmpty() || music.albums.isNotEmpty() || music.singles.isNotEmpty())
            if (hasMusic && music != null) {
                // ARTISTA: somente InnerTube. NewPipe NAO entra, mesmo que tenha canal com mesmo nome.
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
            if (channel != null) {
                // CANAL: sem musica -> NewPipe entra (Videos + Playlists).
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
                        shorts = emptyList(), channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
        }
    }

    /** Tocar tudo: toca todas as musicas do artista em ordem (nao embaralhado). */
    fun onPlayAllClick() {
        val songs = _uiState.value.topSongs
        if (songs.isEmpty()) return
        playerController.playQueue(songs, 0)
    }

"""
    vm = vm[:start] + new_load + vm[end:]
    # Remove onShortClick do ViewModel (Shorts removido). Mantem compat se alguem chamar? Remove mesmo.
    # Se onShortClick existir, remove o bloco dele.
    m = re.search(r"    fun onShortClick\(track: Track\) \{.*?\n    \}\n", vm, flags=re.DOTALL)
    if m:
        vm = vm[:m.start()] + vm[m.end():]
        print("OK ViewModel: onShortClick removido")
    vm_path.write_text(vm, encoding="utf-8")
    print("OK ViewModel regra tem-musica")

# ---------- 2) Search: tira aba Shorts ----------
# 2a) SearchFilter: remove SHORTS
sf_path = pathlib.Path("core/src/main/kotlin/com/vztechcore/vztune/core/domain/search/SearchFilter.kt")
if sf_path.exists():
    sf = sf_path.read_text(encoding="utf-8")
    sf = sf.replace('    SHORTS("Shorts", null),\n', '')
    sf = sf.replace('    SHORTS("Shorts", null)', '')
    sf_path.write_text(sf, encoding="utf-8")
    print("OK SearchFilter sem SHORTS")
# 2b) MusicRepository: remove branch SHORTS -> searchShorts (mantem a funcao searchShorts como codigo morto, nao quebra)
mr_path = pathlib.Path("data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt")
if mr_path.exists():
    mr = mr_path.read_text(encoding="utf-8")
    mr = mr.replace("            SearchFilter.SHORTS -> searchShorts(query, loadMore, state)\n", "")
    mr_path.write_text(mr, encoding="utf-8")
    print("OK MusicRepository sem branch SHORTS")
# 2c) SearchResultCards: remove o branch isShortsMode que mostrava ShortsGridContent
src_path = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchResultCards.kt")
if src_path.exists():
    src = src_path.read_text(encoding="utf-8")
    # Remove o bloco isShortsMode (de "val isShortsMode" ate o "return" do if)
    pat = re.compile(r"    val isShortsMode = .*?\n    if \(isShortsMode\) \{.*?\n    \}\n", flags=re.DOTALL)
    src2, n = pat.subn("", src, count=1)
    if n > 0:
        src = src2
        print("OK SearchResultCards sem ShortsGrid branch")
    else:
        print("AVISO: nao achei isShortsMode em SearchResultCards, pulando")
    src_path.write_text(src, encoding="utf-8")

# ---------- 3) ArtistScreen: rebuild ----------
as_path = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")
t = as_path.read_text(encoding="utf-8")

# 3a) Imports: garante os que o novo layout usa, remove ShortsOptionsSheet/ShortsGridRows se ainda estiverem (vira warning, nao erro, mas limpa)
for imp in [
    "import androidx.compose.foundation.layout.BoxWithConstraints",
    "import androidx.compose.foundation.shape.CircleShape",
    "import androidx.compose.material3.Icon",
    "import com.vztechcore.vztune.ui.icons.VzIcons",
    "import com.vztechcore.vztune.ui.icons.actions.ArrowForward",
    "import com.vztechcore.vztune.ui.icons.player.Play",
]:
    if imp not in t:
        # insere apos o ultimo import
        lines = t.splitlines()
        last = -1
        for idx, ln in enumerate(lines):
            if ln.startswith("import "):
                last = idx
        lines.insert(last + 1, imp)
        t = "\n".join(lines) + "\n"
        print("Add import", imp)

# 3b) Param onPlayAllClick (com default = onRandomClick, pra nao quebrar chamadores antigos)
if "onPlayAllClick:" not in t:
    if "onRandomClick: () -> Unit," in t:
        t = t.replace("    onRandomClick: () -> Unit,", "    onRandomClick: () -> Unit,\n    onPlayAllClick: () -> Unit = onRandomClick,", 1)
        print("Add param onPlayAllClick")

# 3c) Remove Shorts do ArtistScreen: var shortMenuTrack + sheet ShortsOptionsSheet
# Remove a linha da var
t = t.replace("    var shortMenuTrack by remember { mutableStateOf<Track?>(null) }\n", "")
# Remove o bloco do sheet de Shorts (shortMenuTrack?.let ... ShortsOptionsSheet ...)
pat_sheet = re.compile(r"    shortMenuTrack\?\.let \{.*?\n    \}\n", flags=re.DOTALL)
t2, n2 = pat_sheet.subn("", t, count=1)
if n2 > 0:
    t = t2
    print("OK ArtistScreen: sheet Shorts removido")
# Remove onShortClick da assinatura? Mantem o param pra compat com AppNavHost antigo, mas nao usa. Se quiser, mantem.

# 3d) Substitui o bloco else -> { ... } inteiro (Home + abas) pelo novo
new_else = """                else -> {
                    // FIX14 REBUILD - Regra: tem musica = ARTISTA (so Home, sem abas). Sem musica = CANAL (abas Videos | Playlists).
                    val hasMusic = state.topSongs.isNotEmpty() || state.albums.isNotEmpty() || state.singles.isNotEmpty()
                    val isChannel = !hasMusic

                    if (isChannel) {
                        // ---------- CANAL: abas Videos | Playlists (sem Shorts) ----------
                        item(key = "channel_tabs") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Vídeos", "Playlists").forEachIndexed { idx, label ->
                                    val sel = selectedChannelTab == idx
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.Transparent).clickable { selectedChannelTab = idx }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item(key = "channel_tabs_space") { Spacer(Modifier.height(16.dp)) }
                        if (selectedChannelTab == 0) {
                            if (state.videos.isNotEmpty()) {
                                itemsIndexed(state.videos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                            } else {
                                item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                        } else {
                            if (state.channelPlaylists.isNotEmpty()) {
                                itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                            } else {
                                item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                        }
                        item(key = "channel_space") { Spacer(Modifier.height(SectionGap)) }
                        item(key = "description") { ArtistDescription(description = state.description) }
                    } else {
                        // ---------- ARTISTA: so Home, sem abas ----------
                        // 1) Musicas populares: slide de 4 por pagina + Tocar tudo + >
                        if (state.topSongs.isNotEmpty()) {
                            item(key = "songs_header") {
                                SectionHeaderArrow(title = "Músicas populares", onPlayAllClick = onPlayAllClick, onArrowClick = seeAll(ArtistListKind.SONGS, state.songsMore))
                            }
                            item(key = "songs_slide") {
                                SongsSlide(tracks = state.topSongs, onSongClick = onSongClick, onTrackInfoClick = onTrackInfoClick)
                            }
                            item(key = "songs_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 2) Singles e EPs: fileira + > (se nao tem singles, mostra albuns pra nao perder os albuns)
                        val singlesEps = if (state.singles.isNotEmpty()) state.singles else state.albums
                        val singlesEpsMore = if (state.singles.isNotEmpty()) state.singlesMore else state.albumsMore
                        val singlesEpsKind = if (state.singles.isNotEmpty()) ArtistListKind.SINGLES else ArtistListKind.ALBUMS
                        if (singlesEps.isNotEmpty()) {
                            item(key = "singles_header") {
                                SectionHeaderArrow(title = "Singles e EPs", onArrowClick = seeAll(singlesEpsKind, singlesEpsMore))
                            }
                            item(key = "singles_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(singlesEps, key = { it.id }) { album ->
                                        val sub = if (state.singles.isNotEmpty()) "Single" else "Álbum"
                                        CoverCard(title = album.title, subtitle = sub, imageUrl = album.thumbnailUrl, onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth))
                                    }
                                }
                            }
                            item(key = "singles_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 3) Playlists: fileira + > (artista usa featured do YT Music)
                        val artistPlaylists = state.featured
                        if (artistPlaylists.isNotEmpty()) {
                            item(key = "playlists_header") {
                                SectionHeaderArrow(title = "Playlists", onArrowClick = seeAll(ArtistListKind.FEATURED, state.featuredMore))
                            }
                            item(key = "playlists_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(artistPlaylists, key = { it.id }) { playlist ->
                                        CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                                    }
                                }
                            }
                            item(key = "playlists_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 4) Videos: fileira 16:9 + > (so videos do artista, do InnerTube)
                        if (state.videos.isNotEmpty()) {
                            item(key = "videos_header") {
                                SectionHeaderArrow(title = "Vídeos", onArrowClick = seeAll(ArtistListKind.VIDEOS, state.videosMore))
                            }
                            item(key = "videos_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.videos, key = { it.id }) { video ->
                                        val playback = nowPlaying.playbackOf(video)
                                        MediaListVideoCard(track = video, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, modifier = Modifier.width(VideoWidth), horizontalPadding = 0.dp)
                                    }
                                }
                            }
                            item(key = "videos_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        item(key = "description") { ArtistDescription(description = state.description) }
                    }
                }
            }
"""

# Acha o else do when (loading/error/else). O else comeca com "                else -> {"
start_marker = "                else -> {"
start = t.find(start_marker)
if start == -1:
    print("ERRO: nao achei else da ArtistScreen")
    raise SystemExit(1)
end_marker = '            item(key = "credits")'
end = t.find(end_marker, start)
if end == -1:
    print("ERRO: nao achei credits")
    raise SystemExit(1)
t = t[:start] + new_else + "\n" + t[end:]

# 3e) Adiciona os novos composables no fim do arquivo (SectionHeaderArrow + SongsSlide) se ainda nao existirem
if "fun SectionHeaderArrow" not in t:
    t += """

/** Header de secao com balao "Tocar tudo" (so em Musicas) + icone > pra ver tudo. Substitui o "Mais". */
@Composable
private fun SectionHeaderArrow(
    title: String,
    onPlayAllClick: (() -> Unit)? = null,
    onArrowClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onPlayAllClick != null) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { onPlayAllClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(VzIcons.Play, contentDescription = "Tocar tudo", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text(text = "Tocar tudo", style = MaterialTheme.typography.labelLarge, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        if (onArrowClick != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { onArrowClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(VzIcons.ArrowForward, contentDescription = "Ver tudo", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Slide de Musicas populares: 4 faixas por pagina, arrasta da direita pra esquerda pra proxima fileira de 4. */
@Composable
private fun SongsSlide(
    tracks: List<Track>,
    onSongClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    val pages = tracks.chunked(4)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth - 32.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(pages, key = { _, page -> page.first().id }) { pageIndex, page ->
                Column(Modifier.width(pageWidth)) {
                    page.forEachIndexed { idx, track ->
                        val number = pageIndex * 4 + idx + 1
                        TrackListRow(track = track, number = number, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                    }
                }
            }
        }
    }
}
"""
    print("OK ArtistScreen: SectionHeaderArrow + SongsSlide adicionados")

if t.count("{") != t.count("}"):
    print("AVISO braces ArtistScreen:", t.count("{"), t.count("}"))
else:
    print("Braces ArtistScreen OK:", t.count("{"))
as_path.write_text(t, encoding="utf-8")
print("OK ArtistScreen rebuild")

# ---------- 4) AppNavHost: passa onPlayAllClick + remove onShortClick (ViewModel nao tem mais) ----------
nav_path = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt")
if nav_path.exists():
    nav = nav_path.read_text(encoding="utf-8")
    # Remove a linha onShortClick do ArtistScreen (o ViewModel nao tem mais esse metodo)
    if "onShortClick = vm::onShortClick" in nav:
        nav = nav.replace("                        onShortClick = vm::onShortClick,\n", "")
        print("OK AppNavHost onShortClick removido (ViewModel sem Shorts)")
    # Adiciona onPlayAllClick so no bloco do ArtistScreen (nao no primeiro onRandomClick do arquivo)
    idx_artist = nav.find("ArtistScreen(")
    if idx_artist != -1:
        # Procura o onRandomClick dentro do bloco ArtistScreen (ate 2000 chars depois)
        sub = nav[idx_artist:idx_artist+2500]
        if "onPlayAllClick = vm::onPlayAllClick" not in sub:
            if "onRandomClick = vm::onRandomClick," in sub:
                sub2 = sub.replace("onRandomClick = vm::onRandomClick,", "onRandomClick = vm::onRandomClick,\n                        onPlayAllClick = vm::onPlayAllClick,", 1)
                nav = nav[:idx_artist] + sub2 + nav[idx_artist+2500:]
                print("OK AppNavHost onPlayAllClick no ArtistScreen")
            else:
                print("AVISO AppNavHost: nao achei onRandomClick no ArtistScreen")
        else:
            print("AppNavHost ja tem onPlayAllClick no ArtistScreen")
    nav_path.write_text(nav, encoding="utf-8")

print("FIX14 OK")
PY
