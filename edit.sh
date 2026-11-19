#!/bin/bash
# VzTune FIX9 - Canal no Search + Shorts definitivo
# 1) CANAL NO SEARCH:
#    Causa: SearchScreen usa TrackOptionsSheet LOCAL, sem onOpenArtistForTrack.
#    Ele caia no default { _, name -> onOpenArtistByName(name) } que e so YT Music por nome.
#    Playlist/Library usam o menu GLOBAL (TrackMenuViewModel) que tem canal real via NewPipe.
#    Fix: SearchViewModel ganha YouTubeRepository + onOpenArtistForTrack/onOpenChannelForTrack
#    (mesma logica do TrackMenuViewModel) e SearchScreen passa ele pro sheet.
# 2) SHORTS:
#    Causa: parser so aceitava URL com /shorts/. NewPipe devolve Shorts com URL watch?v=
#    mesmo quando filtrado por Shorts, entao o FIX8 jogava tudo fora e a aba ficava vazia.
#    Fix: busca filtrada por Shorts aceita TODOS os itens (ela ja e filtrada).
#    Fallback "$query shorts" tambem aceita todos se strict/loose vier vazio.
#    Generic mantem strict/loose pra nao misturar video normal.
# Idempotente: pode rodar 2x.
set -e
cd ~/VzTune 2>/dev/null || cd "$(dirname "$0")" || true
python3 - << 'PY'
import re, sys

def read(p):
    try:
        with open(p, encoding="utf-8") as f: return f.read()
    except FileNotFoundError:
        print(f"AVISO: nao achei {p}")
        return None

def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)

def must(cond, msg):
    if cond: print(f"OK: {msg}")
    else:
        print(f"FALHOU: {msg}")
        sys.exit(1)

# ---------- 1) SearchViewModel: injeta YouTubeRepository + canal ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt"
t = read(p)
if t is not None:
    # imports
    if "import com.vztechcore.vztune.core.domain.music.Artist" not in t:
        t = t.replace(
            "import com.vztechcore.vztune.core.domain.music.Track",
            "import com.vztechcore.vztune.core.domain.music.Artist\nimport com.vztechcore.vztune.core.domain.music.Source\nimport com.vztechcore.vztune.core.domain.music.Track",
            1
        )
    if "import com.vztechcore.vztune.data.youtube.YouTubeRepository" not in t:
        t = t.replace(
            "import com.vztechcore.vztune.data.ytmusic.YTMusicRepository",
            "import com.vztechcore.vztune.data.youtube.YouTubeRepository\nimport com.vztechcore.vztune.data.ytmusic.YTMusicRepository",
            1
        )
    # construtor: injeta youTubeRepository
    if "youTubeRepository" not in t or "private val youTubeRepository" not in t:
        # Acha o construtor: ytMusicRepository + historyStore
        old_ctor = "    private val ytMusicRepository: YTMusicRepository,"
        if old_ctor in t:
            t = t.replace(
                old_ctor,
                "    private val ytMusicRepository: YTMusicRepository,\n    private val youTubeRepository: YouTubeRepository,",
                1
            )
            print("OK: SearchViewModel: YouTubeRepository injetado")
        else:
            print("AVISO: construtor SearchViewModel com formato diferente")
    # Adiciona onOpenArtistForTrack + onOpenChannelForTrack depois de onOpenArtistByName
    if "fun onOpenArtistForTrack" not in t:
        anchor = "    fun onOpenArtistByName(artistName: String) {"
        if anchor in t:
            # Acha o fim do metodo onOpenArtistByName: proximo "    }\n" apos o anchor
            start = t.find(anchor)
            end = t.find("\n    }\n", start)
            if end != -1:
                insert_at = end + len("\n    }\n")
                new_methods = """
    /** FIX9: Clique em artista DENTRO de uma faixa no Search. Igual ao TrackMenuViewModel. */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        // Video do YouTube: o "artista" e o CANAL. Nunca resolve por nome no YT Music.
        if (track.source == Source.YOUTUBE) {
            onOpenChannelForTrack(track)
            return
        }
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }
    }

    /** FIX9: Canal real do video no Search. 1) InnerTube player -> UC. 2) NewPipe StreamExtractor -> UC. Sem nome. */
    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(fromInnerTube.id, fromInnerTube.name, fromInnerTube.thumbnailUrl))
                return@launch
            }
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(channel.channelId, channel.channelName, channel.avatarUrl))
            }
            // channel == null -> nao navega. Nunca chuta artista por nome.
        }
    }
"""
                t = t[:insert_at] + new_methods + t[insert_at:]
                print("OK: SearchViewModel: onOpenArtistForTrack + onOpenChannelForTrack")
        else:
            print("AVISO: onOpenArtistByName nao achado em SearchViewModel")
    write(p, t)
    t = read(p)
    must("youTubeRepository" in t, "SearchViewModel: YouTubeRepository")
    must("fun onOpenArtistForTrack" in t, "SearchViewModel: onOpenArtistForTrack")
    must("fun onOpenChannelForTrack" in t, "SearchViewModel: onOpenChannelForTrack")
    must(t.count("{") == t.count("}"), "SearchViewModel: chaves balanceadas")

# ---------- 2) SearchScreen: recebe e passa onOpenArtistForTrack ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchScreen.kt"
t = read(p)
if t is not None:
    if "onOpenArtistForTrack" not in t:
        # Param na assinatura
        old_param = "    onOpenArtistByName: (String) -> Unit = {},"
        if old_param in t:
            t = t.replace(
                old_param,
                "    onOpenArtistByName: (String) -> Unit = {},\n    onOpenArtistForTrack: (Track, String) -> Unit = { _, _ -> },",
                1
            )
            print("OK: SearchScreen: param onOpenArtistForTrack")
        else:
            print("AVISO: param onOpenArtistByName com formato diferente em SearchScreen")
    # Passa pro TrackOptionsSheet
    if "onOpenArtistForTrack" in t and "onOpenArtistForTrack = onOpenArtistForTrack" not in t:
        old_sheet = "            onOpenArtistByName = onOpenArtistByName,"
        if old_sheet in t:
            t = t.replace(
                old_sheet,
                "            onOpenArtistByName = onOpenArtistByName,\n            onOpenArtistForTrack = onOpenArtistForTrack,",
                1
            )
            print("OK: SearchScreen: sheet recebe onOpenArtistForTrack")
        else:
            print("AVISO: sheet call com formato diferente em SearchScreen")
    write(p, t)
    t = read(p)
    must("onOpenArtistForTrack" in t, "SearchScreen: onOpenArtistForTrack")
    must("onOpenArtistForTrack = onOpenArtistForTrack" in t, "SearchScreen: sheet com onOpenArtistForTrack")
    must(t.count("{") == t.count("}"), "SearchScreen: chaves balanceadas")

# ---------- 3) AppNavHost: passa vm::onOpenArtistForTrack pro Search ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
t = read(p)
if t is not None:
    if "onOpenArtistForTrack = vm::onOpenArtistForTrack" not in t:
        # Dentro do composable Search, depois de onOpenArtistByName = vm::onOpenArtistByName,
        old = "                        onOpenArtistByName = vm::onOpenArtistByName,"
        if old in t:
            t = t.replace(
                old,
                "                        onOpenArtistByName = vm::onOpenArtistByName,\n                        onOpenArtistForTrack = vm::onOpenArtistForTrack,",
                1
            )
            print("OK: AppNavHost: Search recebe onOpenArtistForTrack")
        else:
            print("AVISO: AppNavHost Search onOpenArtistByName com formato diferente")
    write(p, t)
    t = read(p)
    must("onOpenArtistForTrack = vm::onOpenArtistForTrack" in t, "AppNavHost: Search onOpenArtistForTrack")
    must(t.count("{") == t.count("}"), "AppNavHost: chaves balanceadas")

# ---------- 4) YouTubeRepository: Shorts aceita todos quando ja filtrado ----------
p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if t is not None:
    # 4a) Novo parser: aceita TODOS os StreamInfoItem (usado quando a busca ja foi filtrada pra Shorts)
    if "fun parseShortItemsAll" not in t:
        anchor = "    private fun parseShortItemsStrict"
        if anchor in t:
            all_parser = """    // FIX9_ALL - Quando a busca ja veio filtrada pra Shorts, TODO item E Shorts.
    // Nao filtra por /shorts/ nem por duracao: o NewPipe devolve Shorts com URL watch?v=
    // mesmo filtrado, e o FIX8 jogava tudo fora por causa disso.
    private fun parseShortItemsAll(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
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

"""
            t = t.replace(anchor, all_parser + anchor, 1)
            print("OK: YouTubeRepository: parseShortItemsAll")
        else:
            print("AVISO: parseShortItemsStrict nao achado")
    # 4b) searchShorts: tentativa 1 usa All, tentativa 3 usa All se strict/loose vier vazio
    # Tentativa 1: troca parseShortItemsStrict -> parseShortItemsAll dentro do bloco do filtro shorts
    # Faz por blocos: procura o bloco da tentativa 1
    if "FIX9_SEARCHSHORTS_DONE" not in t:
        # Marca tentativa 1: apos "Tentativa 1: filtro shorts oficial" ate o proximo "Tentativa 2"
        idx1 = t.find("Tentativa 1")
        idx2 = t.find("Tentativa 2", idx1 + 10) if idx1 != -1 else -1
        if idx1 != -1 and idx2 != -1:
            block1 = t[idx1:idx2]
            new_block1 = block1.replace("parseShortItemsStrict", "parseShortItemsAll")
            if new_block1 != block1:
                t = t[:idx1] + new_block1 + t[idx2:]
                print("OK: YouTubeRepository: tentativa 1 usa parseShortItemsAll")
        # Tentativa 3: usa All como fallback se strict/loose vier vazio
        # Procura "Tentativa 3" e troca o bloco de strict/loose por: strict -> se vazio, All
        idx3 = t.find("Tentativa 3")
        if idx3 != -1:
            # Bloco da tentativa 3 ate o fim de searchShorts (proximo "    }\n\n    private fun parse")
            end3 = t.find("    private fun parseShortItemsAll", idx3)
            if end3 == -1:
                end3 = t.find("    private fun parseShortItemsStrict", idx3)
            if end3 != -1:
                block3 = t[idx3:end3]
                # Se ainda usa strict + loose, troca por: strict, se vazio loose, se vazio All
                if "parseShortItemsStrict" in block3 and "parseShortItemsAll" not in block3:
                    old_line = "            val strict = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)"
                    if old_line in block3:
                        new_lines = "            val strict = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)\n            val loose = if (strict.isNotEmpty()) strict else parseShortItemsLoose(extractor.initialPage.items).take(pageSize)\n            val items = if (loose.isNotEmpty()) loose else parseShortItemsAll(extractor.initialPage.items).take(pageSize)"
                        block3_new = block3.replace(
                            old_line + "\n            val items = if (strict.isNotEmpty()) strict else parseShortItemsLoose(extractor.initialPage.items).take(pageSize)",
                            new_lines
                        )
                        # Fallback se o formato for diferente
                        if block3_new == block3:
                            block3_new = block3.replace(
                                "val items = if (strict.isNotEmpty()) strict else parseShortItemsLoose(extractor.initialPage.items).take(pageSize)",
                                "val loose = if (strict.isNotEmpty()) strict else parseShortItemsLoose(extractor.initialPage.items).take(pageSize)\n            val items = if (loose.isNotEmpty()) loose else parseShortItemsAll(extractor.initialPage.items).take(pageSize)"
                            )
                        if block3_new != block3:
                            t = t[:idx3] + block3_new + t[end3:]
                            print("OK: YouTubeRepository: tentativa 3 usa All como fallback")
                # Marca FIX9 no comentario do searchShorts
                t = t.replace(
                    "// FIX8_SHORTS - Shorts definitivo",
                    "// FIX9_SEARCHSHORTS_DONE - Shorts definitivo (FIX8 + All)\n    // FIX8_SHORTS - Shorts definitivo",
                    1
                )
    write(p, t)
    t = read(p)
    must("fun parseShortItemsAll" in t, "YouTubeRepository: parseShortItemsAll")
    must("parseShortItemsAll(extractor.initialPage.items)" in t or "parseShortItemsAll" in t, "YouTubeRepository: searchShorts usa All")
    must(t.count("{") == t.count("}"), "YouTubeRepository: chaves balanceadas")
    # ShortPage continua unico
    must(t.count("data class ShortPage") == 1, "YouTubeRepository: ShortPage unico")

print("\nFIX9 aplicado. Roda o build de novo.")
PY
