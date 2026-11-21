#!/bin/bash
# VzTune FIX16 - Artista sem Playlists / 16 musicas / foto sem zoom / Canal novo estilo YouTube / Arrow normal / Rodape estilo YouTube
# Roda na raiz do projeto: bash VzTune-FIX16.sh
# 1) Artista YT Music: tira a fileira "Playlists" (a do canal). Destacado continua, pq e "onde ele aparece", nao playlist dele.
# 2) Musicas Populares: busca 16 (4 fileiras de 4). Antes so vinha 5 pq o YT Music manda so 5 na primeira pagina.
# 3) Foto do artista: Crop (da zoom / corta) -> FillBounds (esticada, sem zoom, sem cortar).
# 4) Canal: rebuild completo igual a foto (banner, avatar, nome, @, inscritos, descricao ...mais, botoes, abas, chips, lista) mas no tema VzTune (preto AMOLED).
# 5) Arrow / Chevron: pathData torto de banda grossa -> Material padrao, do jeito normal.
# 6) Rodape (AppBottomBar): pilula flutuante de vidro -> barra cheia preta igual YouTube, icone branco selecionado / cinza resto.
set -e
python3 - << 'PY'
import pathlib, sys, re

def read(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
def edit(p, fn):
    t = read(p)
    nt = fn(t)
    if nt != t: write(p, nt)
    else: print("SEM MUDANCA", p)
    return nt
def must(c, m):
    if not c: print("ERRO " + m); sys.exit(1)
    print("OK " + m)
def ensure_import(t, imp):
    if imp not in t:
        # coloca depois do ultimo import existente
        idx = t.rfind("import ")
        end = t.find("\n", idx)
        t = t[:end+1] + imp + "\n" + t[end+1:]
    return t

# ============ 1) YTMusicRepository - busca 16 musicas ============
def fix_repo(t):
    # O parse so traz 5. Se songsMore existe, busca a pagina "ver tudo" de musicas e completa ate 16.
    # browseArtistList ja sabe buscar por BrowseRef + continuation.
    old_parsed = "            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)"
    new_parsed = """            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)

            // FIX16: YT Music manda so 5 musicas na pagina do artista. O slide e 4 fileiras de 4 = 16.
            // Se ainda falta, busca a pagina "ver tudo" (songsMore) e completa ate 16, sem repetir.
            var topSongs16 = parsed.topSongs
            if (topSongs16.size < 16 && parsed.songsMore != null) {
                val more = runCatching { browseArtistList(ArtistListKind.SONGS, parsed.name, parsed.songsMore, null) }.getOrNull()
                if (more != null) {
                    topSongs16 = (topSongs16 + more.tracks).distinctBy { it.id }.take(16)
                    if (topSongs16.size < 16 && more.continuation != null) {
                        val more2 = runCatching { browseArtistList(ArtistListKind.SONGS, parsed.name, parsed.songsMore, more.continuation) }.getOrNull()
                        if (more2 != null) topSongs16 = (topSongs16 + more2.tracks).distinctBy { it.id }.take(16)
                    }
                }
            }"""
    if old_parsed in t and "topSongs16" not in t:
        t = t.replace(old_parsed, new_parsed, 1)
    # ArtistResult passa a usar topSongs16
    if "topSongs = parsed.topSongs," in t:
        t = t.replace("topSongs = parsed.topSongs,", "topSongs = topSongs16,", 1)
    return t
edit("data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt", fix_repo)

# ============ 2) ArtistHeader - foto sem zoom ============
def fix_header(t):
    # Crop = da zoom e corta as bordas. FillBounds = estica pra preencher o quadrado, sem zoom, sem cortar.
    if "contentScale = ContentScale.Crop," in t and "fun ArtistPhoto" in t:
        # So troca dentro de ArtistPhoto, nao em outro lugar do arquivo (se houver)
        head, tail = t.split("fun ArtistPhoto", 1)
        tail = tail.replace("contentScale = ContentScale.Crop,", "contentScale = ContentScale.FillBounds,", 1)
        t = head + "fun ArtistPhoto" + tail
    t = t.replace("Foto quadrada 1:1, Crop pra nunca esticar, com fade pra preto embaixo.", "Foto quadrada 1:1, FillBounds (esticada, sem zoom, sem cortar), com fade pra preto embaixo.")
    t = t.replace("Crop pra nunca esticar", "FillBounds pra nao dar zoom")
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt", fix_header)

# ============ 3) ArtistViewModel - artista sem Playlists + canal com Shorts ============
def fix_vm(t):
    # 3a) Se o FIX15-CORRECAO tinha ligado channelPlaylists no artista, desliga de novo.
    # Linha do artista: "shorts = emptyList(), channelPlaylists = channel?.playlists ?: emptyList(), bannerUrl = null, isYouTubeChannel = false,"
    old_artist = "shorts = emptyList(), channelPlaylists = channel?.playlists ?: emptyList(), bannerUrl = null, isYouTubeChannel = false,"
    new_artist = "shorts = emptyList(), channelPlaylists = emptyList(), bannerUrl = null, isYouTubeChannel = false,"
    if old_artist in t:
        t = t.replace(old_artist, new_artist, 1)
    # 3b) Canal: Shorts tava sempre emptyList() mesmo o browseChannel trazendo. Liga de verdade.
    # Linha do canal: "shorts = emptyList(), channelPlaylists = channel.playlists,"
    old_canal = "shorts = emptyList(), channelPlaylists = channel.playlists,"
    new_canal = "shorts = channel.shorts, channelPlaylists = channel.playlists,"
    if old_canal in t:
        t = t.replace(old_canal, new_canal, 1)
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt", fix_vm)

# ============ 4) Arrow / Chevron - Material padrao ============
def write_icon(path, name, pathdata, comment):
    content = f"""package com.vztechcore.vztune.ui.icons.actions

import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.ui.icons.VzIcons

// {comment}
val VzIcons.{name}: ImageVector
    get() = VzIcons.icon(
        name = "{name}",
        pathData = "{pathdata}"
    )
"""
    # Mantem o package original de cada arquivo (actions). ChevronRight tambem e actions.
    write(path, content)

# Material padrao, do jeito normal. Sem banda grossa, sem ponta torta.
write_icon("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ArrowBack.kt", "ArrowBack", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z", "Seta <- Material padrao (arrow_back)")
write_icon("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ArrowForward.kt", "ArrowForward", "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z", "Seta -> Material padrao (arrow_forward)")
write_icon("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ArrowBackIos.kt", "ArrowBackIos", "M17.77 3.77L16 2 6 12l10 10 1.77-1.77L9.54 12z", "Chevron < Material padrao (arrow_back_ios)")
write_icon("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ArrowForwardIos.kt", "ArrowForwardIos", "M6.23 20.23L8 22l10-10L8 2 6.23 3.77 14.46 12z", "Chevron > Material padrao (arrow_forward_ios)")
write_icon("app/src/main/kotlin/com/vztechcore/vztune/ui/icons/actions/ChevronRight.kt", "ChevronRight", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z", "Chevron > fino Material padrao (chevron_right) - usado no > dos cabecalhos")

# ============ 5) AppBottomBar - rodape estilo YouTube, tema VzTune ============
bottombar = """package com.vztechcore.vztune.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.navigation.AppTab

// Rodape estilo YouTube: barra cheia preta, sem pilula flutuante de vidro, sem label.
// Tema VzTune: fundo preto AMOLED, linha fina branca em cima, icone branco = selecionado, cinza = resto.
// As 4 abas continuam as mesmas (Home, Analytics, Library, Search), so mudou o visual.
@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
"""
write("app/src/main/kotlin/com/vztechcore/vztune/ui/components/layout/AppBottomBar.kt", bottombar)

# ============ 6) ArtistScreen - tira Playlists do artista + Canal novo ============
def fix_screen(t):
    # --- 6a) Estados novos do canal: ordenacao + descricao expandida ---
    old_tab_state = "    var selectedChannelTab by remember { mutableStateOf(0) }"
    new_tab_state = """    var selectedChannelTab by remember { mutableStateOf(0) }
    var channelSort by remember { mutableStateOf(0) } // 0 = Mais recentes, 1 = Em alta, 2 = Mais antigos
    var channelDescExpanded by remember { mutableStateOf(false) }"""
    if old_tab_state in t and "channelSort" not in t:
        t = t.replace(old_tab_state, new_tab_state, 1)

    # --- 6b) Imports que o cabecalho novo do canal precisa ---
    for imp in [
        "import androidx.compose.foundation.layout.aspectRatio",
        "import androidx.compose.foundation.border",
        "import androidx.compose.ui.layout.ContentScale",
        "import coil.compose.AsyncImage",
        "import com.vztechcore.vztune.ui.icons.actions.ArrowBackIos",
        "import com.vztechcore.vztune.ui.icons.actions.Check",
        "import com.vztechcore.vztune.ui.icons.actions.MoreVert",
        "import com.vztechcore.vztune.ui.icons.actions.StarFilled",
        "import com.vztechcore.vztune.ui.icons.media.Groups",
        "import com.vztechcore.vztune.ui.icons.navigation.Search",
        "import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton",
    ]:
        t = ensure_import(t, imp)

    # --- 6c) Tira a fileira "Playlists" do ARTISTA (so do artista, o canal continua com Playlists) ---
    # O bloco do artista tem key "channel_playlists_header". O do canal usa ChannelPlaylistCard, nao tem essa key.
    # Loop: remove TODOS os blocos do artista (se rodar 2x ou se houver duplicata, limpa tudo).
    for _ in range(3):
        artist_start = t.find("// ---------- ARTISTA")
        if artist_start == -1:
            break
        cp = t.find('item(key = "channel_playlists_header")', artist_start)
        if cp == -1:
            break
        # Volta ate o comentario da secao
        block_start = t.rfind("//", 0, cp)
        # Fim: item channel_playlists_space + fechamento do if
        space = t.find('item(key = "channel_playlists_space")', cp)
        if space == -1:
            break
        close = t.find("}", t.find("}", space) + 1)
        block_end = t.find("\n", close) + 1
        t = t[:block_start] + t[block_end:]

    # --- 6d) Header: artista usa ArtistHeader, canal usa ChannelHeader novo ---
    old_header = """            item(key = "header") {
                ArtistHeader(
                    state = state,
                    accent = Color.Black,
                    onBackClick = onBackClick,
                    onRandomClick = onRandomClick,
                    onFollowClick = onFollowClick,
                    onShareClick = onShareClick,
                )
            }"""
    new_header = """            item(key = "header") {
                if (state.isYouTubeChannel) {
                    ChannelHeader(
                        state = state,
                        selectedTab = selectedChannelTab,
                        onTabSelected = { selectedChannelTab = it },
                        descExpanded = channelDescExpanded,
                        onDescClick = { channelDescExpanded = !channelDescExpanded },
                        onBackClick = onBackClick,
                        onFollowClick = onFollowClick,
                        onShareClick = onShareClick,
                    )
                } else {
                    ArtistHeader(
                        state = state,
                        accent = Color.Black,
                        onBackClick = onBackClick,
                        onRandomClick = onRandomClick,
                        onFollowClick = onFollowClick,
                        onShareClick = onShareClick,
                    )
                }
            }"""
    if "ChannelHeader(" in t and 'item(key = "header")' in t:
        pass # Ja aplicado, nao duplica
    elif old_header in t:
        t = t.replace(old_header, new_header, 1)
    else:
        print("AVISO: header do ArtistScreen nao achado com esse formato, confere na mao")

    # --- 6e) Canal: rebuild completo estilo foto YouTube ---
    # Troca do "if (isChannel) {" ate o "} else {" que abre o ARTISTA.
    ch_start = t.find("if (isChannel) {")
    must(ch_start != -1, "ArtistScreen: if (isChannel) achado")
    artista_marker = t.find("// ---------- ARTISTA", ch_start)
    must(artista_marker != -1, "ArtistScreen: marcador ARTISTA achado")
    else_idx = t.rfind("} else {", ch_start, artista_marker)
    must(else_idx != -1, "ArtistScreen: else do canal achado")

    new_channel = """if (isChannel) {
                        // ---------- CANAL novo: igual a foto do YouTube, tema VzTune ----------
                        // Cabecalho (banner, avatar, botoes, abas) ja esta no ChannelHeader la em cima.
                        // Aqui so vai: chips (so na aba Videos) + conteudo da aba.
                        val sortedVideos = when (channelSort) {
                            1 -> state.videos.sortedByDescending { it.viewCount ?: 0 }
                            2 -> state.videos.reversed()
                            else -> state.videos
                        }
                        if (selectedChannelTab == 1) {
                            item(key = "channel_chips") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Sem forEach: weight() so existe direto dentro do Row.
                                    val chips = listOf("Mais recentes", "Em alta", "Mais antigos")
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 0) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = chips[0], style = MaterialTheme.typography.labelLarge, color = if (channelSort == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 1) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = chips[1], style = MaterialTheme.typography.labelLarge, color = if (channelSort == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 2) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 2 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = chips[2], style = MaterialTheme.typography.labelLarge, color = if (channelSort == 2) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            item(key = "channel_chips_space") { Spacer(Modifier.height(8.dp)) }
                        }
                        when (selectedChannelTab) {
                            0 -> {
                                // Inicio: lista de videos (igual a foto, thumb esquerda + titulo + views)
                                if (state.videos.isNotEmpty()) {
                                    itemsIndexed(state.videos, key = { _, v -> "cv_home_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                                } else {
                                    item(key = "channel_home_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            1 -> {
                                if (sortedVideos.isNotEmpty()) {
                                    itemsIndexed(sortedVideos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                                } else {
                                    item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            2 -> {
                                if (state.shorts.isNotEmpty()) {
                                    item(key = "channel_shorts_grid") { ShortsGridRows(tracks = state.shorts, onClick = { onVideoClick(it) }, onMoreClick = { onTrackInfoClick(it) }) }
                                } else {
                                    item(key = "channel_shorts_empty") { Text(text = "Nenhum Short ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            3 -> {
                                item(key = "channel_live_empty") { Text(text = "Nenhuma transmissão ao vivo", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                            4 -> {
                                if (state.channelPlaylists.isNotEmpty()) {
                                    itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                                } else {
                                    item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            else -> {
                                item(key = "channel_posts_empty") { Text(text = "Nenhum post ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                        }
                        item(key = "channel_space") { Spacer(Modifier.height(SectionGap)) }
                        item(key = "description") { ArtistDescription(description = state.description) }
                    } else {
                        """
    t = t[:ch_start] + new_channel + t[else_idx + len("} else {"):]

    # --- 6f) Adiciona o ChannelHeader no fim do arquivo (antes do SongsSlide, se existir) ---
    if "fun ChannelHeader" not in t:
        channel_header = '''
/**
 * Cabecalho do canal igual a foto do YouTube, mas no tema VzTune (preto AMOLED).
 * Banner 16:9 arredondado, avatar redondo, nome + selo, @handle, inscritos • videos,
 * descricao com ...mais, botoes Inscrever-se / Seja membro / Comunidade,
 * abas Inicio | Videos | Shorts | Ao vivo | Playlists | Posts.
 */
@Composable
private fun ChannelHeader(
    state: ArtistUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    descExpanded: Boolean,
    onDescClick: () -> Unit,
    onBackClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val handle = "@" + state.name.replace(" ", "").lowercase()
    val subsVideos = listOfNotNull(
        state.subscribers?.takeIf { it.isNotBlank() },
        if (state.videos.isNotEmpty()) state.videos.size.toString() + " vídeos" else null,
    ).joinToString(" • ")
    val tabs = listOf("Início", "Vídeos", "Shorts", "Ao vivo", "Playlists", "Posts")
    Column(
        Modifier.fillMaxWidth().background(Color.Black)
    ) {
        // Barra de cima: voltar | buscar | mais (igual a foto)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaListCircleButton(VzIcons.ArrowBackIos, "Voltar", onBackClick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Buscar dentro do canal ainda nao tem tela, deixa visual igual a foto por enquanto.
                MediaListCircleButton(VzIcons.Search, "Buscar", {})
                MediaListCircleButton(VzIcons.MoreVert, "Mais", onShareClick)
            }
        }
        // Banner 16:9 arredondado, FillBounds pra nao dar zoom. Sem banner = faixa preta.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))
        ) {
            if (!state.bannerUrl.isNullOrBlank()) {
                AsyncImage(model = state.bannerUrl, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        }
        // Avatar + nome + @ + inscritos
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                if (!state.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(model = state.thumbnailUrl, contentDescription = state.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = state.name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(imageVector = VzIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(text = handle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subsVideos.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subsVideos, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Descricao com ...mais (clica pra expandir)
        if (!state.description.isNullOrBlank()) {
            val shown = if (descExpanded) state.description else state.description.take(110) + if (state.description.length > 110) "...mais" else ""
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = if (descExpanded) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onDescClick() },
            )
            Spacer(Modifier.height(12.dp))
        }
        // Botoes: Inscrever-se (branco) | Seja membro (cinza) | Comunidade (cinza)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (state.isFollowing) Color.White.copy(alpha = 0.12f) else Color.White).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)).clickable { onFollowClick() }.padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (state.isFollowing) "Inscrito" else "Inscrever-se", style = MaterialTheme.typography.labelLarge, color = if (state.isFollowing) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { }.padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = VzIcons.StarFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(text = "Seja membro", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { }.padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = VzIcons.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(text = "Comunidade", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Abas igual a foto, com sublinhado na selecionada
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(tabs) { index, tab ->
                Column(Modifier.clickable { onTabSelected(index) }.padding(bottom = 8.dp)) {
                    Text(text = tab, style = MaterialTheme.typography.titleSmall, color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (selectedTab == index) Color.White else Color.Transparent))
                }
            }
        }
    }
}
'''
        marker = "fun SongsSlide"
        idx = t.find(marker)
        if idx != -1:
            t = t[:idx] + channel_header + "\n" + t[idx:]
        else:
            t = t + "\n" + channel_header
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt", fix_screen)

print("FIX16 OK")
PY
