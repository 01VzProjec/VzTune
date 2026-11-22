# passo1_fix.py — PASSO 1, sozinho. Roda na raiz do projeto: python3 passo1_fix.py
# Faz SO 6 trocas exatas, cada uma tem que aparecer EXATAMENTE 1 vez, senao aborta sem escrever nada.
# Faz backup .bak de cada arquivo antes de escrever. Nao apaga bloco nenhum.
import pathlib, sys

def replace_once(path, old, new, label):
    p = pathlib.Path(path)
    if not p.exists():
        print(f"ERRO [{label}]: nao achei {path}. Rode na raiz do projeto.")
        sys.exit(1)
    text = p.read_text(encoding="utf-8")
    n = text.count(old)
    if n != 1:
        print(f"ERRO [{label}]: achei {n}x (esperava 1x) em {path}. ABORTEI, nao mudei nada nesse arquivo.")
        print("Me manda o trecho atual desse arquivo que eu ajusto.")
        sys.exit(1)
    return text.replace(old, new)

jobs = []

# 1) Abas do canal: header mostrava Videos|Shorts|Playlists, conteudo ja era Inicio|Videos|Playlists
jobs.append((
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt",
    'val tabs = listOf("Vídeos", "Shorts", "Playlists")',
    'val tabs = listOf("Início", "Vídeos", "Playlists")',
    "abas-canal",
))

# 2) Inicio / Videos mais acessados: botao +4 agora chama Buscar mais quando acabam os carregados (igual a secao Videos)
jobs.append((
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt",
    'if (homeTopVideosVisible < topVideos.size) {',
    'if (homeTopVideosVisible < topVideos.size || state.channelVideosHasMore) {',
    "top-expand-cond",
))
jobs.append((
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt",
    '.clickable { homeTopVideosVisible += 4 }',
    '.clickable { if (homeTopVideosVisible < topVideos.size) homeTopVideosVisible += 4 else onLoadMoreChannelVideos() }',
    "top-expand-click",
))

# 3) browseChannel videos: 300 -> 500 de cara
jobs.append((
    "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt",
    'while (collected.size < 300 && nextPage != null) {',
    'while (collected.size < 500 && nextPage != null) {',
    "videos-500",
))

# 4) browseChannel playlists: 300 / 3 paginas -> 500 / 5 paginas
jobs.append((
    "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt",
    'while (collected.size < 300 && nextPage != null && pages < 3) {',
    'while (collected.size < 500 && nextPage != null && pages < 5) {',
    "playlists-500",
))
jobs.append((
    "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt",
    'collected.distinctBy { it.id }.take(300)',
    'collected.distinctBy { it.id }.take(500)',
    "playlists-take500",
))

# 5) Buscar mais playlists: 2 paginas -> 3 paginas por clique
jobs.append((
    "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt",
    'while (collected.size < pageSize && page != null && pages < 2) {',
    'while (collected.size < pageSize && page != null && pages < 3) {',
    "loadmore-playlists-3pag",
))

# 6) Playlist: VLPL... E YouTube + ID manda mais que sourceName
old_ml = """    private fun isYouTubePlaylistId(id: String): Boolean {
        val v = id.trim().removePrefix("YT:").trim()
        if (v.startsWith("OLAK")) return false
        if (v.startsWith("RD") || v.startsWith("VL")) return false
        return v.startsWith("PL") || v.startsWith("UU") || v.startsWith("FL") || v.startsWith("LL") || v.startsWith("OL")
    }
    private fun isYouTubePlaylistRoute(): Boolean {
        if (route.sourceName == "YOUTUBE") return true
        if (route.sourceName == "YOUTUBE_MUSIC") return false
        return isYouTubePlaylistId(route.id)
    }"""
new_ml = """    private fun isYouTubePlaylistId(id: String): Boolean {
        var v = id.trim().removePrefix("YT:").trim()
        if (v.startsWith("OLAK")) return false
        if (v.startsWith("RD")) return false
        // VLPL... = playlist do YouTube embrulhada pelo YT Music (VL = Video List). Tira o VL e testa o PL.
        if (v.startsWith("VL")) v = v.removePrefix("VL")
        return v.startsWith("PL") || v.startsWith("UU") || v.startsWith("FL") || v.startsWith("LL") || v.startsWith("OL")
    }
    private fun isYouTubePlaylistRoute(): Boolean {
        // ID manda mais que sourceName: busca do InnerTube devolve playlist do YouTube (PL... / VLPL...) com source YOUTUBE_MUSIC.
        // Se o ID e PL/UU/VLPL, e YouTube (NewPipe), mesmo que sourceName diga YOUTUBE_MUSIC.
        if (isYouTubePlaylistId(route.id)) return true
        if (route.sourceName == "YOUTUBE") return true
        if (route.sourceName == "YOUTUBE_MUSIC") return false
        return false
    }"""
jobs.append((
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt",
    old_ml, new_ml, "playlist-vl-id",
))

# Aplica por arquivo: le 1x, aplica todas as trocas dele, so escreve se todas deram 1x
from collections import defaultdict
by_file = defaultdict(list)
for path, old, new, label in jobs:
    by_file[path].append((old, new, label))

for path, lst in by_file.items():
    p = pathlib.Path(path)
    if not p.exists():
        print(f"ERRO: nao achei {path}. Rode na raiz.")
        sys.exit(1)
    text = p.read_text(encoding="utf-8")
    for old, new, label in lst:
        n = text.count(old)
        if n != 1:
            print(f"ERRO [{label}]: achei {n}x (esperava 1x) em {path}. ABORTEI, nao mudei NADA.")
            sys.exit(1)
        text = text.replace(old, new)
        print(f"OK [{label}]")
    bak = p.with_suffix(p.suffix + ".bak-passo1")
    bak.write_text(p.read_text(encoding="utf-8"), encoding="utf-8")
    p.write_text(text, encoding="utf-8")
    print(f"Salvo {path} (backup em {bak.name})")

print("\nPASSO 1 aplicado. Agora: build / testa e me fala.")
print("Testa: 1) canal mostra Inicio/Videos/Playlists  2) playlist PL/VLPL abre dono como canal  3) Inicio > Videos mais acessados > +4 ate acabar > Buscar mais")
