#!/usr/bin/env python3
"""Safe, idempotent VzTune channel UI + Shorts wiring patch.
Run from the VzTune project root: python edit.py
Creates .bak backups before any writes; validates every patch before modifying files.
"""
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parent
FILES = {
    "parser": ROOT / "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt",
    "repo": ROOT / "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt",
    """state""": ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt",
    "vm": ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt",
    "screen": ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt",
}


PATCHES = []


def add_patch(file_key, old, new, description, optional=False):
    PATCHES.append((file_key, old, new, description, optional))



# --- Shorts already fetched by InnerTube: pass them through to the screen state. ---
add_patch("vm", "shorts = emptyList(), // FIX20 - Canal sem Shorts", "shorts = channel.shorts, // Shorts do canal retornados pelo InnerTube", "Conectar Shorts do repositório ao estado da tela")

# Add Shorts filtering to the channel's existing in-memory search.
add_patch("screen",
'''        videos = state.videos.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
        channelPlaylists = state.channelPlaylists.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },''',
'''        videos = state.videos.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
        shorts = state.shorts.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
        channelPlaylists = state.channelPlaylists.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },''',
"Pesquisa interna também filtra Shorts")

# Channel tab: playlists become index 2; index 3 displays shorts. The Shorts grid component is already present in this file's imports.
add_patch("screen",
'''                            else -> {
                                // --- PLAYLISTS ---''',
'''                            2 -> {
                                // --- PLAYLISTS ---''',
"Fixar aba Playlists no índice 2 para abrir espaço a Shorts")
add_patch("screen",
'''                                }
                            }
                        }
                        item(key = "channel_space")''',
'''                                }
                            }
                            3 -> {
                                // --- SHORTS DO CANAL ---
                                if (displayState.shorts.isNotEmpty()) {
                                    item(key = "channel_shorts_grid") {
                                        ShortsGridRows(
                                            tracks = displayState.shorts,
                                            onClick = onVideoClick,
                                            onMoreClick = onTrackInfoClick,
                                        )
                                    }
                                } else {
                                    item(key = "channel_shorts_empty") {
                                        Text(
                                            text = "Nenhum Short encontrado",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "channel_space")''',
"Adicionar conteúdo da aba Shorts com grid reutilizado")

# Four channel tabs.
add_patch("screen", 'val tabs = listOf("Início", "Vídeos", "Playlists")', 'val tabs = listOf("Início", "Vídeos", "Playlists", "Shorts")', "Adicionar Shorts à navegação do canal")

# Share replaces overflow menu. Preserve temporary diagnostics as a small inline action instead of losing the dialog.
add_patch("screen",
'''                Box {
                    MediaListCircleButton(VzIcons.MoreVert, "Mais", { channelMenuOpen = true })
                    DropdownMenu(
                        expanded = channelMenuOpen,
n''',
'''                Box {
                    MediaListCircleButton(VzIcons.Share, "Compartilhar canal", onShareClick)
                    DropdownMenu(
                        expanded = channelMenuOpen,
''',
"Trocar o botão de overflow pelo botão de compartilhar", optional=True)
# The exact whole dropdown is safely replaced; this patch is preferred over the partial optional above.
menu_old = '''                Box {
                    MediaListCircleButton(VzIcons.MoreVert, "Mais", { channelMenuOpen = true })
                    DropdownMenu(
                        expanded = channelMenuOpen,
                        onDismissRequest = { channelMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Buscar mais faixas e playlists") },
                            onClick = { channelMenuOpen = false; onLoadMoreChannelClick() },
                        )
                        DropdownMenuItem(
                            text = { Text(text = "Compartilhar") },
                            onClick = { channelMenuOpen = false; onShareClick() },
                        )
                        DropdownMenuItem(
                            text = { Text(text = "Ver Log temporário") },
                            onClick = { channelMenuOpen = false; showTempLog = true },
                        )
                    }
                    if (showTempLog) {
                        AlertDialog(
                            onDismissRequest = { showTempLog = false },
                            title = { Text(text = "Log temporário") },
                            text = { Text(text = state.debugLog ?: "Sem log ainda. Reabre o canal.", modifier = Modifier.verticalScroll(rememberScrollState())) },
                            confirmButton = { TextButton(onClick = { showTempLog = false }) { Text(text = "Fechar") } },
                        )
                    }
                }'''
menu_new = '''                MediaListCircleButton(VzIcons.Share, "Compartilhar canal", onShareClick)'''
# Replace prior menu patch entry with whole-block patch; mark optional if already removed.
PATCHES = [p for p in PATCHES if p[3] != "Trocar o botão de overflow pelo botão de compartilhar"]
add_patch("screen", menu_old, menu_new, "Substituir menu de três pontos por compartilhamento direto")
# Reintroduce temporary-log dialog in an accessible, explicit small text action below the follow button.
add_patch("screen",
'''        Spacer(Modifier.height(16.dp))
        // Abas igual a foto, com sublinhado na selecionada''',
'''        if (!state.debugLog.isNullOrBlank()) {
            TextButton(onClick = { showTempLog = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(text = "Ver log temporário", color = Color.White.copy(alpha = 0.65f))
            }
        }
        if (showTempLog) {
            AlertDialog(
                onDismissRequest = { showTempLog = false },
                title = { Text(text = "Log temporário") },
                text = { Text(text = state.debugLog ?: "Sem log ainda. Reabre o canal.", modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { showTempLog = false }) { Text(text = "Fechar") } },
            )
        }
        Spacer(Modifier.height(16.dp))
        // Abas igual a foto, com sublinhado na selecionada''',
"Preservar acesso ao log temporário após remover overflow")

# Real channel handle data model; only show it when InnerTube supplies a real vanity/canonical URL.
add_patch("parser",
'''    fun parseChannelPage(json: JSONObject): ChannelData {''',
'''    fun extractChannelHandle(json: JSONObject): String? {
        val candidates = mutableListOf<String>()
        val metadata = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
        if (metadata != null) {
            candidates += metadata.optString("vanityUrl")
            candidates += metadata.optString("canonicalUrl")
            candidates += metadata.optString("channelUrl")
        }
        val headers = mutableListOf<JSONObject>()
        collect(json, "c4TabbedHeaderRenderer", headers)
        collect(json, "channelHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        for (header in headers) {
            candidates += header.optString("vanityUrl")
            candidates += header.optString("channelHandle")
            candidates += header.optString("canonicalUrl")
            candidates += header.optString("channelUrl")
        }
        val pattern = Regex("@([A-Za-z0-9._-]{3,})")
        for (candidate in candidates) {
            val value = candidate.trim().takeIf { it.isNotBlank() && it != "null" } ?: continue
            val match = pattern.find(value) ?: continue
            return "@" + match.groupValues[1]
        }
        return null
    }

    fun extractChannelHandleFromUrl(url: String): String? {
        val match = Regex("/@([A-Za-z0-9._-]{3,})").find(url) ?: return null
        return "@" + match.groupValues[1]
    }

    fun parseChannelPage(json: JSONObject): ChannelData {''',
"Parser de handle real do canal (sem fabricar @ a partir do nome)")


# ChannelPage gains an optional handle. InnerTube and NewPipe channel routes supply it when known.
add_patch("repo",
'''        val channelName: String?,
        val avatarUrl: String?,''',
'''        val channelName: String?,
        val avatarUrl: String?,''',
"Adicionar handle opcional à resposta do canal")
add_patch("repo",
'''                        channelName = channelName.ifBlank { null },
                        avatarUrl = homeParsed.avatarUrl,''',
'''                        channelName = channelName.ifBlank { null },
                        channelHandle = YouTubeChannelParser.extractChannelHandle(homeJson),
                        avatarUrl = homeParsed.avatarUrl,''',
"Propagar handle extraído no caminho InnerTube")
add_patch("repo",
'''            channelName = channelName.takeIf { it.isNotBlank() },
            avatarUrl = header?.avatar,''',
'''            channelName = channelName.takeIf { it.isNotBlank() },
            channelHandle = YouTubeChannelParser.extractChannelHandleFromUrl(channelUrl),
            avatarUrl = header?.avatar,''',
"Propagar handle de URL no fallback NewPipe", optional=True)
add_patch("state",
'''    val name: String,
    val thumbnailUrl: String? = null,''',
'''    val name: String,
    val channelHandle: String? = null,
    val thumbnailUrl: String? = null,''',
"Adicionar handle no estado da tela")
add_patch("vm",
'''                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,''',
'''                            name = channel.channelName ?: it.name,
                            channelHandle = channel.channelHandle ?: it.channelHandle,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,''',
"Propagar handle do canal para estado UI")
add_patch("screen",
'''    val handle = "@" + state.name.replace(" ", "").lowercase()''',
'''    val handle = state.channelHandle?.takeIf { it.isNotBlank() }''',
"Remover handle falso derivado do nome do canal")
add_patch("screen",
'''                Text(text = handle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subsVideos.isNotBlank()) {''',
'''                if (!handle.isNullOrBlank()) {
                    Text(text = handle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (subsVideos.isNotBlank()) {''',
"Mostrar username apenas quando o YouTube fornecer um handle real")

# Add a search icon to the sticky/collapsed channel bar.
add_patch("screen",
'''            MediaListCollapsedHeader(
                title = state.name,
                thumbnailUrl = state.thumbnailUrl,
                onBackClick = onBackClick,
                onShareClick = onShareClick,
                dominantColor = Color.Black,
                circularThumbnail = true,
            )''',
'''            MediaListCollapsedHeader(
                title = state.name,
                thumbnailUrl = state.thumbnailUrl,
                onBackClick = onBackClick,
                dominantColor = Color.Black,
                onSearchClick = if (state.isYouTubeChannel) ({ onSearchToggle(); scope.launch { listState.animateScrollToItem(0) } }) else null,
                onShareClick = if (state.isYouTubeChannel) onShareClick else null,
                circularThumbnail = true,
            )''',
"Adicionar lupa e compartilhar à barra compacta ao rolar")

# Thumbnail fallback for channel Shorts parsers, covering renderer types already recognized.
add_patch("parser",
'''            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
'''            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
"Fallback de thumbnail para reelItemRenderer")
add_patch("parser",
'''            val thumb = bestSources(r.optJSONObject("thumbnail")) ?: bestSources(r.optJSONObject("thumbnailViewModel")) ?: YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
'''            val thumb = (bestSources(r.optJSONObject("thumbnail")) ?: bestSources(r.optJSONObject("thumbnailViewModel")) ?: YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail")))?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
"Fallback de thumbnail em shortsLockupViewModel")
add_patch("parser",
'''            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
'''            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
"Fallback de thumbnail para lockup Shorts")

# Remove an unused state variable from header after the overflow menu is gone.
add_patch("screen", '    var channelMenuOpen by remember { mutableStateOf(false) }\n', '', "Remover estado do menu overflow", optional=True)


def patch_text(key, content, old, new, desc, optional=False):
    if new in content:
        return content, False
    if old in content:
        return content.replace(old, new, 1), True
    if optional:
        return content, False
    raise ValueError(f"Trecho não encontrado e patch não aplicado: {desc} [{key}]")


def main():
    missing = [k for k, p in FILES.items() if not p.exists()]
    if missing:
        print("ERRO: arquivos do projeto não encontrados:", ", ".join(missing))
        print("Execute este script na raiz do VzTune.")
        sys.exit(2)
    contents = {k: p.read_text(encoding="utf-8") for k, p in FILES.items()}
    changed = set()
    try:
        for key, old, new, desc, optional in PATCHES:
            updated, did_change = patch_text(key, contents[key], old, new, desc, optional)
            contents[key] = updated
            if did_change:
                changed.add(key)
    except Exception as exc:
        print("PATCH ABORTADO — nenhum arquivo foi escrito.")
        print(exc)
        sys.exit(3)
    if not changed:
        print("Nenhuma alteração necessária: patches já estão aplicados.")
        return
    # Backups first, then writes. If any patch failed above, no writes occurred.
    backups = []
    for key in sorted(changed):
        path = FILES[key]
        backup = path.with_suffix(path.suffix + ".edit.py.bak")
        if not backup.exists():
            shutil.copy2(path, backup)
        backups.append(backup)
    for key in sorted(changed):
        FILES[key].write_text(contents[key], encoding="utf-8")
    print("PATCH CONCLUÍDO:")
    for key in sorted(changed):
        print(f" - {FILES[key].relative_to(ROOT)}")
    print("Backups: .edit.py.bak (não sobrescreve backup existente)")
    print("Agora compile o projeto e confira a aba Shorts e os handles de canais.")

if __name__ == "__main__":
    main()
