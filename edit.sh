#!/bin/bash
# VzTune FIX17 - Canal: so Videos | Playlists / topo mais pra baixo / so Seguir / compartilhar / rodape volta como era
# Roda na raiz do projeto, DEPOIS do FIX16: bash VzTune-FIX17.sh
# A foto era referencia, nao copia. Entao permanece como ta, so muda:
# 1) Abas do canal: Início | Vídeos | Shorts | Ao vivo | Playlists | Posts -> so Vídeos | Playlists
# 2) Icones de cima descem: statusBarsPadding pra nao ficar em cima da barra de infos do celular
# 3) Botoes: tira Seja membro e Comunidade, fica so Seguir (mesmo sistema do artista: Seguir / Seguindo)
# 4) Topo: tira a lupa, troca os 3 pontinhos pelo icone de compartilhar
# 5) Rodape (AppBottomBar): volta como era antes do FIX16, a pilula de vidro do mesmo jeitinho, so um pouco menor
set -e
python3 - << 'PY'
import pathlib, sys, re

def read(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
def must(c, m):
    if not c: print("ERRO " + m); sys.exit(1)
    print("OK " + m)
def ensure_import(t, imp):
    if imp not in t:
        idx = t.rfind("import ")
        end = t.find("\n", idx)
        t = t[:end+1] + imp + "\n" + t[end+1:]
    return t
def drop_import_if_unused(t, imp, used_marker):
    # So tira o import se nao tiver mais uso no arquivo
    if used_marker not in t and imp in t:
        t = t.replace(imp + "\n", "", 1)
    return t

# ============ 1) AppBottomBar - volta como era antes, so um pouco menor ============
# Mesmo codigo de antes do FIX16: pilula de vidro, hover, spring, label embaixo do icone.
# Unica diferenca: altura 72 -> 64, padding horizontal 28 -> 24, bottom 24 -> 16. Do mesmo jeitinho, so menor.
bottombar = """package com.vztechcore.vztune.ui.components.layout

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.ui.navigation.AppTab

private val BarFill = Color.Black.copy(alpha = 0.75f) // Same as MiniPlayerBar
private val BarBorder = Color.White.copy(alpha = 0.15f) // Same as MiniPlayerBar
private val SelectedFill = Color.White.copy(alpha = 0.15f)

// FIX17: voltou como era antes do FIX16, a pilula de vidro do mesmo jeitinho, so um pouco menor
// (altura 72 -> 64, horizontal 28 -> 24, bottom 24 -> 16). Nao mexe mais aqui.
@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassShape = RoundedCornerShape(50)
    val tabs = AppTab.entries
    val currentOnSelect by rememberUpdatedState(onSelect)

    var hoveredTab by remember { mutableStateOf<AppTab?>(null) }
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 16.dp)
            .height(64.dp),
    ) {
        // Glass background — stays fixed, never overflows
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(glassShape)
                .background(BarFill)
                .border(BorderStroke(1.dp, BarBorder), glassShape),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tabs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true

                        val tabWidth = size.width.toFloat() / tabs.size
                        fun tabAt(x: Float) =
                            tabs[(x / tabWidth).toInt().coerceIn(0, tabs.size - 1)]

                        var finalTab = tabAt(down.position.x)
                        hoveredTab = finalTab
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            finalTab = tabAt(change.position.x)
                            hoveredTab = finalTab
                            change.consume()
                        }

                        isPressed = false
                        hoveredTab = null
                        currentOnSelect(finalTab)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isHighlighted = if (hoveredTab != null) hoveredTab == tab else tab == selected
                val isBeingPressed = hoveredTab == tab && isPressed

                // Only the icon scales — no marker growth, no overflow
                val iconScale by animateFloatAsState(
                    targetValue = if (isBeingPressed) 1.22f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "iconScale-${tab.name}",
                )

                val contentColor =
                    if (isHighlighted) Color.White else Color.White.copy(alpha = 0.55f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Static marker — never grows
                    if (isHighlighted) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(glassShape)
                                .background(SelectedFill),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isHighlighted) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
"""
write("app/src/main/kotlin/com/vztechcore/vztune/ui/components/layout/AppBottomBar.kt", bottombar)

# ============ 2) ArtistScreen - canal: so Videos | Playlists, topo mais pra baixo, so Seguir, compartilhar ============
p_screen = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
t = read(p_screen)
orig = t

# --- 2a) Imports novos, tira os que nao vao mais usar no ChannelHeader ---
for imp in [
    "import androidx.compose.foundation.layout.statusBarsPadding",
    "import com.vztechcore.vztune.ui.icons.actions.PersonAdd",
    "import com.vztechcore.vztune.ui.icons.actions.PersonCheck",
    "import com.vztechcore.vztune.ui.icons.actions.Share",
]:
    t = ensure_import(t, imp)

# --- 2b) Abas: 6 -> 2 ---
t = t.replace(
    'val tabs = listOf("Início", "Vídeos", "Shorts", "Ao vivo", "Playlists", "Posts")',
    'val tabs = listOf("Vídeos", "Playlists")',
)

# --- 2c) Comentario do ChannelHeader: atualiza abas e botoes ---
t = t.replace(
    " * descricao com ...mais, botoes Inscrever-se / Seja membro / Comunidade,\n * abas Inicio | Videos | Shorts | Ao vivo | Playlists | Posts.",
    " * descricao com ...mais, botao Seguir (mesmo sistema do artista),\n * abas Videos | Playlists.",
)

# --- 2d) Topo: tira lupa, troca 3 pontinhos por compartilhar, desce pra baixo da barra de status ---
# O bloco do topo vai de "// Barra de cima" ate "// Banner". Troca inteiro, funciona com qualquer versao antiga (Search/MoreVert).
top_start = t.find("// Barra de cima")
top_end = t.find("// Banner", top_start) if top_start != -1 else -1
if top_start != -1 and top_end != -1:
    new_top = """// Barra de cima: voltar | compartilhar. Desce pra nao ficar em cima da barra de infos do celular.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaListCircleButton(VzIcons.ArrowBackIos, "Voltar", onBackClick)
            MediaListCircleButton(VzIcons.Share, "Compartilhar", onShareClick)
        }
        """
    t = t[:top_start] + new_top + t[top_end:]
    print("OK topo do canal: sem lupa, compartilhar no lugar dos 3 pontinhos, mais pra baixo")
else:
    print("AVISO: bloco do topo do canal nao achado, confere na mao")

# --- 2e) Botoes: tira Seja membro e Comunidade, fica so Seguir (mesmo sistema do artista) ---
# O bloco dos botoes vai de "// Botoes:" ate o Spacer(16.dp) que vem antes de "// Abas".
# Se ja esta com "// So Seguir", ja foi aplicado, pula.
if "// So Seguir" in t:
    print("OK botoes do canal: so Seguir (ja aplicado)")
    btn_start = -1
    abas_marker = -1
else:
    btn_start = t.find("// Botoes:")
abas_marker = t.find("// Abas", btn_start) if btn_start != -1 else -1
if btn_start != -1 and abas_marker != -1:
    spacer_idx = t.find("Spacer(Modifier.height(16.dp))", btn_start)
    # spacer_idx e o Spacer depois dos botoes, antes das abas. Mantem ele.
    if spacer_idx != -1 and spacer_idx < abas_marker:
        new_btn = """// So Seguir, mesmo sistema do artista: Seguir / Seguindo, troca com onFollowClick (isFollowing).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val followShape = RoundedCornerShape(50)
            Row(
                Modifier.height(44.dp).clip(followShape).background(if (state.isFollowing) Color.White.copy(alpha = 0.12f) else Color.Transparent).border(1.dp, Color.White.copy(alpha = 0.35f), followShape).clickable { onFollowClick() }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = if (state.isFollowing) VzIcons.PersonCheck else VzIcons.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(text = if (state.isFollowing) "Seguindo" else "Seguir", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        """
        t = t[:btn_start] + new_btn + t[spacer_idx:]
        print("OK botoes do canal: so Seguir")
    else:
        print("AVISO: Spacer dos botoes nao achado")
else:
    if "// So Seguir" not in t:
        print("AVISO: bloco dos botoes do canal nao achado")

# --- 2f) Conteudo do canal: so 2 abas (0 = Videos com chips, 1 = Playlists) ---
ch_start = t.find("if (isChannel) {")
must(ch_start != -1, "ArtistScreen: if (isChannel) achado")
artista_marker = t.find("// ---------- ARTISTA", ch_start)
must(artista_marker != -1, "ArtistScreen: marcador ARTISTA achado")
else_idx = t.rfind("} else {", ch_start, artista_marker)
must(else_idx != -1, "ArtistScreen: else do canal achado")

new_channel = """if (isChannel) {
                        // ---------- CANAL: so Videos | Playlists ----------
                        val sortedVideos = when (channelSort) {
                            1 -> state.videos.sortedByDescending { it.viewCount ?: 0 }
                            2 -> state.videos.reversed()
                            else -> state.videos
                        }
                        if (selectedChannelTab == 0) {
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
                                if (sortedVideos.isNotEmpty()) {
                                    itemsIndexed(sortedVideos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                                } else {
                                    item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            else -> {
                                if (state.channelPlaylists.isNotEmpty()) {
                                    itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                                } else {
                                    item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                        }
                        item(key = "channel_space") { Spacer(Modifier.height(SectionGap)) }
                        item(key = "description") { ArtistDescription(description = state.description) }
                    } else {
                        """
t = t[:ch_start] + new_channel + t[else_idx + len("} else {"):]

# --- 2g) Limpa imports que nao usam mais no ChannelHeader (so se nao tiver mais uso) ---
for imp, marker in [
    ("import com.vztechcore.vztune.ui.icons.navigation.Search", "VzIcons.Search"),
    ("import com.vztechcore.vztune.ui.icons.actions.MoreVert", "VzIcons.MoreVert"),
    ("import com.vztechcore.vztune.ui.icons.actions.StarFilled", "VzIcons.StarFilled"),
    ("import com.vztechcore.vztune.ui.icons.media.Groups", "VzIcons.Groups"),
]:
    t = drop_import_if_unused(t, imp, marker)

if t != orig:
    write(p_screen, t)
else:
    print("SEM MUDANCA", p_screen)

print("FIX17 OK")
PY
