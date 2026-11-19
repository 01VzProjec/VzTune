#!/bin/bash
# ============================================================
# VzTune FIX6 HOTFIX - AppNavHost.kt:255 Unresolved reference 'source'
# Causa (FIX5): AppNavHost trocou os 2 Routes.Artist(artist...)
# pelo mesmo codigo com artist.source, mas o da Biblioteca usa
# FollowedArtist (id, nome, foto) que NAO tem source.
# Fix: FollowedArtist ganha isYouTubeChannel, ArtistViewModel salva
# a flag ao seguir, e AppNavHost usa artist.isYouTubeChannel na
# Biblioteca e artist.source na tela de artista (que e Artist).
# Roda na raiz: cd ~/VzTune && bash VzTune-FIX6-HOTFIX-AppNavHost-source.sh
# ============================================================
set -e
echo "== FIX6 HOTFIX AppNavHost source =="

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

# ---------- 1) FollowedArtist ganha isYouTubeChannel ----------
p = "data/src/main/kotlin/com/vztechcore/vztune/data/prefs/models/FallowedArtist.kt"
t = read(p)
if "isYouTubeChannel" not in t:
    old = """data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)"""
    must(old in t, "FollowedArtist.kt: ancora")
    t = t.replace(old, """data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    // Canal do YouTube seguido abre como canal, nao como artista do YT Music.
    // Default false = compat com os salvos antigos (sem esse campo).
    val isYouTubeChannel: Boolean = false,
)""")
    write(p, t)
must("isYouTubeChannel" in read(p), "FollowedArtist.kt: isYouTubeChannel")

# ---------- 2) ArtistViewModel salva a flag ao seguir ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
t = read(p)
if "FollowedArtist(state.artistId, state.name, state.thumbnailUrl)" in t:
    t = t.replace(
        "FollowedArtist(state.artistId, state.name, state.thumbnailUrl)",
        "FollowedArtist(state.artistId, state.name, state.thumbnailUrl, isYouTubeChannel = state.isYouTubeChannel)"
    )
    write(p, t)
    print("OK ArtistViewModel: seguir salva isYouTubeChannel")
t = read(p)
must("isYouTubeChannel = state.isYouTubeChannel" in t, "ArtistViewModel: seguir com flag")

# ---------- 3) AppNavHost: Biblioteca usa isYouTubeChannel, artista usa source ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
t = read(p)
broken = "Routes.Artist(artist.id, artist.name, artist.thumbnailUrl, isYouTubeChannel = artist.source == Source.YOUTUBE)"
fixed_library = "Routes.Artist(artist.id, artist.name, artist.thumbnailUrl, isYouTubeChannel = artist.isYouTubeChannel)"
count = t.count(broken)
print(f"AppNavHost: ocorrencias quebradas (artist.source) = {count}")
must(count >= 1, "AppNavHost: acha a linha quebrada do FIX5")
if count == 2:
    # Primeira ocorrencia = Biblioteca (FollowedArtist, sem source).
    # Segunda = tela de artista (Artist, com source) - deixa como esta.
    first = t.find(broken)
    t = t[:first] + fixed_library + t[first + len(broken):]
    write(p, t)
    print("OK AppNavHost: 1a ocorrencia (Biblioteca) -> artist.isYouTubeChannel, 2a (artista) mantida com artist.source")
elif count == 1:
    # So tem 1: pode ser a da Biblioteca (se a outra ja foi corrigida) ou a do artista.
    # Decide pelo contexto: se antes dela tem LibraryList, e Biblioteca.
    idx = t.find(broken)
    before = t[max(0, idx-2000):idx]
    if "LibraryList" in before or "composable<Routes.LibraryList>" in before:
        t = t[:idx] + fixed_library + t[idx + len(broken):]
        write(p, t)
        print("OK AppNavHost: unica ocorrencia era Biblioteca -> artist.isYouTubeChannel")
    else:
        print("OK AppNavHost: unica ocorrencia era da tela de artista, nada a corrigir")
t = read(p)
# Depois do fix, nao pode sobrar artist.source na Biblioteca: verifica que a primeira ocorrencia agora e isYouTubeChannel
must("isYouTubeChannel = artist.isYouTubeChannel" in t, "AppNavHost: Biblioteca usa artist.isYouTubeChannel")
must("isYouTubeChannel = artist.source == Source.YOUTUBE" in t, "AppNavHost: tela de artista mantem artist.source")
# Nao pode ter duplicacao
must(t.count("isYouTubeChannel = artist.isYouTubeChannel") == 1, "AppNavHost: Biblioteca corrigida 1x, sem duplicar")
must(t.count("isYouTubeChannel = artist.source == Source.YOUTUBE") == 1, "AppNavHost: artista mantido 1x, sem duplicar")

print("\nFIX6 aplicado. Pode dar build de novo.")
PY
