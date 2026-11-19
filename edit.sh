#!/bin/bash
# ============================================================
# VzTune FIX7 - Canal certo (estilo SimpMusic)
# Bug 1: Video Jazzghost -> Moldrin
#   onOpenChannelForTrack tentava InnerTube primeiro. InnerTube.player
#   com cliente YTMUSIC pra um video do YouTube devolve a MUSICA do video,
#   nao quem postou o video. Ex: video do gamer Jazzghost que usa a musica
#   "Jazzghost" do Moldrin -> player devolve UC do Moldrin.
#   SimpMusic: pra video do YouTube, quem postou = uploader da pagina do
#   video (NewPipe StreamExtractor.uploaderUrl), NUNCA o artista da musica.
#   Fix: NewPipe PRIMEIRO, InnerTube so como fallback. E getArtistFromVideo
#   nunca mais converte canal em artista do YT Music (sempre YOUTUBE).
# Bug 2: Playlist Jazzghost (Sanary Xenon) -> 360View Lab
#   2a) MediaListViewModel.onOpenArtist usava state.artistId (UC do header
#       do YT Music) pra playlist do YouTube. Header do YT Music pra playlist
#       do YouTube pode trazer UC de outro canal (ex: 360View Lab, que tem
#       video "SANARY SUR MER"). Fix: playlist do YouTube usa
#       getChannelForPlaylist (NewPipe PlaylistExtractor.uploaderUrl) como
#       fonte da verdade, nao o header do YT Music.
#   2b) resolveArtist() devolvia ranked.first() mesmo quando NENHUM candidato
#       tinha nome parecido (ex: "Sanary Xenon" -> "360View Lab", distancia 11).
#       SimpMusic tem threshold: sem candidato perto, nao navega.
#       Fix: se melhor distancia > max(3, len/3) e sem exato e sem catalogo,
#       retorna null em vez de chutar um canal aleatorio.
# Roda na raiz: cd ~/VzTune && bash VzTune-FIX7-CANAL-CERTO-SIMPMUSIC.sh
# Requer FIX4 (getChannelForPlaylist) + FIX5/6.
# ============================================================
set -e
echo "== FIX7 Canal certo (SimpMusic) =="

python3 - <<'PY'
import sys
def read(p):
    with open(p, encoding="utf-8") as f: return f.read()
def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)
def must(cond, msg):
    if not cond:
        print(f"ERRO {msg}"); sys.exit(1)
    print(f"OK {msg}")

# ---------- 0) Pre-check ----------
t = read("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
must("fun getChannelForVideo" in t, "YouTubeRepository: getChannelForVideo existe")
must("fun getChannelForPlaylist" in t, "FIX4 aplicado? getChannelForPlaylist existe (precisa do FIX4)")

# ---------- 1) YTMusicRepository.getArtistFromVideo: nunca vira artista do YT Music ----------
p = "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt"
t = read(p)
if "val detail = runCatching { browseArtist(channelId) }" in t:
    # Remove o bloco que convertia canal em artista do YT Music.
    # Antes: se browseArtist(UC) tinha pagina, devolvia Artist(name=detail.name, source=YOUTUBE_MUSIC).
    # Era isso que trocava Jazzghost (gamer) por Moldrin (dono da musica "Jazzghost").
    # Agora: "Ir para o canal" sempre volta YOUTUBE com o author do video.
    old_start = t.find("            val detail = runCatching { browseArtist(channelId) }")
    old_end = t.find("        }\n\n    // ================= QUEUE =================", old_start)
    if old_start == -1 or old_end == -1:
        # Fallback: procura o fim do bloco pelo padrao do else
        print("AVISO getArtistFromVideo: formato diferente, tentando ancora alternativa")
        # Tenta achar o bloco isMusicArtist
        if "val isMusicArtist" in t:
            s = t.find("            val detail = runCatching { browseArtist(channelId) }")
            e = t.find("            }\n        }", s)
            must(s != -1 and e != -1, "getArtistFromVideo: acha bloco browseArtist")
            new_block = """            // FIX7: "Ir para o canal" NUNCA vira artista do YT Music.
            // O author do video e quem postou. Se o YT Music tiver pagina
            // pra esse UC, ela pode ser de OUTRO artista com nome parecido
            // (ex: musica "Jazzghost" do Moldrin dentro do video do gamer
            // Jazzghost) e era isso que abria o Moldrin. Sempre YOUTUBE.
            return@withContext Artist(
                id = channelId,
                name = cleanName,
                thumbnailUrl = null,
                source = Source.YOUTUBE,
            )"""
            t = t[:s] + new_block + t[e+len("            }\n        }"):]
            write(p, t)
    else:
        new_block = """            // FIX7: "Ir para o canal" NUNCA vira artista do YT Music.
            // O author do video e quem postou. Se o YT Music tiver pagina
            // pra esse UC, ela pode ser de OUTRO artista com nome parecido
            // (ex: musica "Jazzghost" do Moldrin dentro do video do gamer
            // Jazzghost) e era isso que abria o Moldrin. Sempre YOUTUBE.
            return@withContext Artist(
                id = channelId,
                name = cleanName,
                thumbnailUrl = null,
                source = Source.YOUTUBE,
            )
"""
        t = t[:old_start] + new_block + t[old_end:]
        write(p, t)
    print("OK getArtistFromVideo: sempre YOUTUBE, sem browseArtist")
t = read(p)
must("FIX7" in t and "getArtistFromVideo" in t, "getArtistFromVideo: FIX7 aplicado")
# Garante que nao sobrou o browseArtist dentro de getArtistFromVideo (pode ter em outros lugares, tudo bem)
seg = t[t.find("suspend fun getArtistFromVideo"):t.find("suspend fun getArtistFromVideo")+2500]
must("browseArtist(channelId)" not in seg, "getArtistFromVideo: sem browseArtist dentro")

# ---------- 2) YTMusicRepository.resolveArtist: threshold (nao chuta canal aleatorio) ----------
t = read(p)
if "FIX7 threshold" not in t:
    old = "        // 4. Mais parecido (SimpMusic: bestMatchingIndex por Levenshtein)\n        return@withContext ranked.first().first"
    if old in t:
        t = t.replace(old, """        // 4. Mais parecido (SimpMusic: bestMatchingIndex por Levenshtein)
        // FIX7 threshold: se NENHUM candidato tem nome parecido, nao navega.
        // Antes devolvia ranked.first() mesmo com distancia 11 (ex: "Sanary Xenon"
        // -> "360View Lab", que so tem "Sanary" num video "SANARY SUR MER").
        // Agora retorna null e a UI nao abre canal errado.
        // Threshold: max(3, len/3) - erro de digitacao pequeno passa, nome totalmente
        // diferente nao passa.
        val best = ranked.first()
        val maxDist = maxOf(3, cleanQuery.length / 3)
        if (best.second > maxDist) {
            return@withContext null
        }
        return@withContext best.first""")
        write(p, t)
        print("OK resolveArtist: threshold FIX7")
    else:
        print("AVISO resolveArtist: ancora '4. Mais parecido' nao achada, procurando fallback")
        if "return@withContext ranked.first().first" in t:
            t = t.replace(
                "return@withContext ranked.first().first",
                """// FIX7 threshold: sem candidato parecido, nao navega.
        val best = ranked.first()
        val maxDist = maxOf(3, cleanQuery.length / 3)
        if (best.second > maxDist) {
            return@withContext null
        }
        return@withContext best.first"""
            )
            write(p, t)
            print("OK resolveArtist: threshold FIX7 (fallback)")
t = read(p)
must("FIX7 threshold" in t, "resolveArtist: threshold aplicado")

# ---------- 3) TrackMenuViewModel.onOpenChannelForTrack: NewPipe PRIMEIRO ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt"
t = read(p)
if "FIX7: NewPipe PRIMEIRO" not in t:
    old = """    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(fromInnerTube))
                return@launch
            }
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                _navigationEvent.emit(
                    MenuNavigationEvent.OpenArtist(
                        Artist(
                            id = channel.channelId,
                            name = channel.channelName,
                            thumbnailUrl = channel.avatarUrl,
                            source = Source.YOUTUBE,
                        )
                    )
                )
            }"""
    if old in t:
        t = t.replace(old, """    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
            // FIX7: NewPipe PRIMEIRO (estilo SimpMusic).
            // StreamExtractor.uploaderUrl = quem POSTOU o video (fonte da verdade).
            // InnerTube.player com cliente YTMUSIC devolve a MUSICA do video,
            // nao quem postou (video do gamer Jazzghost com musica "Jazzghost"
            // do Moldrin -> devolvia UC do Moldrin). So usa InnerTube se o
            // NewPipe falhar.
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                _navigationEvent.emit(
                    MenuNavigationEvent.OpenArtist(
                        Artist(
                            id = channel.channelId,
                            name = channel.channelName,
                            thumbnailUrl = channel.avatarUrl,
                            source = Source.YOUTUBE,
                        )
                    )
                )
                return@launch
            }
            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(fromInnerTube))
                return@launch
            }""")
        write(p, t)
        print("OK TrackMenu: NewPipe primeiro")
    else:
        print("AVISO TrackMenu: formato onOpenChannelForTrack diferente, tentando troca por ordem")
        # Fallback: troca a ordem das duas linhas de chamada
        if "getArtistFromVideo" in t and "getChannelForVideo" in t:
            # Nao faz troca automatica insegura, falha pra revisar
            print("ERRO TrackMenu: nao consegui trocar a ordem automaticamente, revisa o arquivo")
            sys.exit(1)
t = read(p)
must("FIX7: NewPipe PRIMEIRO" in t, "TrackMenu: NewPipe primeiro")
# Verifica ordem: getChannelForVideo aparece ANTES de getArtistFromVideo dentro de onOpenChannelForTrack
seg = t[t.find("fun onOpenChannelForTrack"):t.find("fun onOpenChannelForTrack")+2000]
must(seg.find("getChannelForVideo") < seg.find("getArtistFromVideo"), "TrackMenu: ordem NewPipe antes de InnerTube")

# ---------- 4) MediaListViewModel: injeta YouTubeRepository + playlist do YouTube usa getChannelForPlaylist ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t = read(p)
if "import com.vztechcore.vztune.data.youtube.YouTubeRepository" not in t:
    must("import com.vztechcore.vztune.data.ytmusic.YTMusicRepository" in t, "MediaList: ancora import YTMusicRepository")
    t = t.replace(
        "import com.vztechcore.vztune.data.ytmusic.YTMusicRepository",
        "import com.vztechcore.vztune.data.youtube.YouTubeRepository\nimport com.vztechcore.vztune.data.ytmusic.YTMusicRepository"
    )
    write(p, t)
t = read(p)
if "private val youTubeRepository: YouTubeRepository" not in t:
    must("private val ytMusicRepository: YTMusicRepository," in t, "MediaList: ancora construtor")
    t = t.replace(
        "private val ytMusicRepository: YTMusicRepository,",
        "private val ytMusicRepository: YTMusicRepository,\n    private val youTubeRepository: YouTubeRepository,"
    )
    write(p, t)
    print("OK MediaList: injeta YouTubeRepository")
t = read(p)
must("private val youTubeRepository: YouTubeRepository" in t, "MediaList: YouTubeRepository injetado")
# 4b) onOpenArtist: playlist do YouTube usa getChannelForPlaylist (NewPipe), nao header do YT Music
if "FIX7: playlist do YouTube usa getChannelForPlaylist" not in t:
    old = """    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }"""
    if old in t:
        t = t.replace(old, """    fun onOpenArtist() {
        val state = _uiState.value
        // FIX7: playlist do YouTube usa getChannelForPlaylist (NewPipe
        // PlaylistExtractor.uploaderUrl) como fonte da verdade.
        // O header do YT Music pra playlist do YouTube pode trazer UC de
        // outro canal (ex: playlist "Jazzghost" da Sanary Xenon -> 360View Lab,
        // que tem video "SANARY SUR MER"). Se o NewPipe falhar, nao navega
        // em vez de abrir canal errado.
        if (isYouTubePlaylist(state)) {
            viewModelScope.launch {
                val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                if (channel != null) {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            Artist(
                                id = channel.channelId,
                                name = channel.channelName,
                                thumbnailUrl = channel.avatarUrl ?: state.ownerAvatarUrl,
                                source = Source.YOUTUBE,
                            )
                        )
                    )
                }
            }
            return
        }
        val directId = state.artistId?.takeIf { it.startsWith("UC") }""")
        write(p, t)
        print("OK MediaList: onOpenArtist usa getChannelForPlaylist pra playlist do YouTube")
    else:
        print("AVISO MediaList: ancora onOpenArtist diferente, tentando fallback")
        if "fun onOpenArtist()" in t:
            t = t.replace(
                "fun onOpenArtist() {",
                """fun onOpenArtist() {
        // FIX7: playlist do YouTube usa getChannelForPlaylist (NewPipe) como fonte da verdade.
        if (isYouTubePlaylist(_uiState.value)) {
            viewModelScope.launch {
                val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                if (channel != null) {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            Artist(
                                id = channel.channelId,
                                name = channel.channelName,
                                thumbnailUrl = channel.avatarUrl ?: _uiState.value.ownerAvatarUrl,
                                source = Source.YOUTUBE,
                            )
                        )
                    )
                }
            }
            return
        }""",
                1
            )
            write(p, t)
            print("OK MediaList: onOpenArtist FIX7 (fallback)")
t = read(p)
must("FIX7: playlist do YouTube usa getChannelForPlaylist" in t, "MediaList: FIX7 onOpenArtist")
must("getChannelForPlaylist(route.id)" in t, "MediaList: chama getChannelForPlaylist")

print("\nFIX7 aplicado. Build e teste: video Jazzghost -> canal Jazzghost (gamer), playlist Sanary Xenon -> canal dela (ou nao abre se nao achar), nunca Moldrin / 360View Lab.")
PY
