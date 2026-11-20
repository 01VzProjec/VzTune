#!/bin/bash
# VzTune FIX11 - Canal YouTube: sem Aleatorio + abas Videos/Playlists/Shorts + sem secoes de artista
# Artista (YT Music) NAO mexe - fica como esta agora.
# Aplica sobre FIX10 + hotfixes. Rode na raiz: bash VzTune-FIX11-CANAL-SO-CANAL.sh
set -e
python3 - << 'PY'
import pathlib, re

# ---------- 1) Header: canal nao mostra Aleatorio, so Seguir ----------
for rel in [
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt",
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/components/ArtistHeader.kt",
]:
    p = pathlib.Path(rel)
    if not p.exists():
        print("PULA (nao existe)", rel)
        continue
    t = p.read_text(encoding="utf-8")
    orig = t
    # So no header usado (screens/artist): o botao Aleatorio esta em if (hasPlayableItems)
    # No header de components: mesmo padrao. Em ambos, canal esconde Aleatorio.
    if "hasPlayableItems && !state.isYouTubeChannel" not in t:
        t = t.replace("if (hasPlayableItems) {", "if (hasPlayableItems && !state.isYouTubeChannel) {")
    if t != orig:
        p.write_text(t, encoding="utf-8")
        print("OK", rel)
    else:
        print("SEM MUDANCA", rel)

# ---------- 2) ArtistScreen: so canal muda ----------
p2 = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")
t2 = p2.read_text(encoding="utf-8")

# 2a) Foto igual artista: remove banner extra que o FIX10 colocava antes do header no canal
old_banner = '            if (state.isYouTubeChannel && !state.bannerUrl.isNullOrBlank()) {\n                item(key = "channel_banner") {\n                    Box(Modifier.fillMaxWidth().height(110.dp).background(Color.Black)) { AsyncImage(model = state.bannerUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }\n                }\n            }\n'
if old_banner in t2:
    t2 = t2.replace(old_banner, "", 1)
else:
    # Fallback generico: remove o if do banner ate os 2 fechamentos (item + if)
    m = re.search(r"\n\s*if \(state\.isYouTubeChannel && !state\.bannerUrl\.isNullOrBlank\(\)\) \{.*?channel_banner.*?\n\s*\}\n\s*\}\n", t2, flags=re.DOTALL)
    if m:
        t2 = t2[:m.start()] + "\n" + t2[m.end():]
        print("Banner removido por fallback regex (2 fechamentos)")
    else:
        print("AVISO: bloco banner nao encontrado, nada removido")

# 2b) Artista: esconde secoes de YT Music quando for canal (songs/albums/singles/featured/related)
# Canal mostra SO abas + conteudo da aba + descricao. Artista continua igual.
if "!state.isYouTubeChannel && state.topSongs.isNotEmpty()" not in t2:
    t2 = t2.replace(
        "if (state.topSongs.isNotEmpty()) {",
        "if (!state.isYouTubeChannel && state.topSongs.isNotEmpty()) {",
        1,
    )
for key in ["albums", "singles", "featured", "related"]:
    # rowSection(key = "albums"... -> if (!canal) rowSection(...)
    # So nos que ainda nao tem o if
    pat = 'rowSection(\n                        key = "' + key + '"'
    if pat in t2 and ('if (!state.isYouTubeChannel) ' + pat) not in t2:
        t2 = t2.replace(pat, 'if (!state.isYouTubeChannel) ' + pat, 1)

# 2c) Abas do canal: ordem Videos / Playlists / Shorts + barrinha unica + estados vazios
# Troca a lista de abas (ordem antiga: Videos / Shorts / Playlists)
t2 = t2.replace('listOf("Vídeos", "Shorts", "Playlists")', 'listOf("Vídeos", "Playlists", "Shorts")')
# Troca o conteudo das abas 1 e 2 (antes 1=Shorts, 2=Playlists -> agora 1=Playlists, 2=Shorts)
# Fazemos pela chave dos itens para nao confundir
old_when_1 = '1 -> if (state.shorts.isNotEmpty()) { item(key = "channel_shorts") { ShortsGridRows(tracks = state.shorts, onClick = { onShortClick(it) }, onMoreClick = { shortMenuTrack = it }) } }'
new_when_1 = '1 -> if (state.channelPlaylists.isNotEmpty()) { itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) } } else { item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) } }'
old_when_2 = '2 -> itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }'
new_when_2 = '2 -> if (state.shorts.isNotEmpty()) { item(key = "channel_shorts") { ShortsGridRows(tracks = state.shorts, onClick = { onShortClick(it) }, onMoreClick = { shortMenuTrack = it }) } } else { item(key = "channel_shorts_empty") { Text(text = "Nenhum Short ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) } }'
if old_when_1 in t2:
    t2 = t2.replace(old_when_1, new_when_1)
if old_when_2 in t2:
    t2 = t2.replace(old_when_2, new_when_2)
# Estado vazio tambem para Videos (aba 0)
old_when_0 = '0 -> itemsIndexed(state.videos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }'
new_when_0 = '0 -> if (state.videos.isNotEmpty()) { itemsIndexed(state.videos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) } } else { item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) } }'
if old_when_0 in t2:
    t2 = t2.replace(old_when_0, new_when_0)

# 2d) Barrinha unica: um container so, 3 segmentos dentro (antes eram 3 caixas separadas com espaco)
old_bar = "Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {"
new_bar = "Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {"
if old_bar in t2:
    t2 = t2.replace(old_bar, new_bar, 1)
# Dentro da barrinha, segmento nao selecionado fica transparente (antes tinha fundo 0.08f em cada um)
t2 = t2.replace(
    "Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = 0.08f))",
    "Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.Transparent)",
    1,
)

p2.write_text(t2, encoding="utf-8")
print("OK", p2)
print("FIX11 OK - canal: sem Aleatorio, abas Videos/Playlists/Shorts, sem secoes de artista. Artista intacto.")
PY
