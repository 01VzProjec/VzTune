#!/bin/bash
# VzTune HOTFIX - Routes.kt cortado pelo FIX20
# Causa: no FIX20 eu fiz t = t[:end] + "val sourceName..." e esqueci o + t[end:]
# Ou seja, apaguei tudo que vinha depois de MediaList: o ) do MediaList, Artist, ArtistList, LibraryList, TrackInfo e o } final.
# Por isso o erro: Routes.kt:18:113 Parameter name expected - ele esperava o proximo parametro ou ) e achou fim do arquivo.
# Esse script restaura a parte apagada sem duplicar se voce ja rodou outro fix.
# Rode na raiz: bash VzTune-HOTFIX-Routes-Cortado.sh
set -e
python3 - << 'PYEOF'
import pathlib
def read(p):
    try: return pathlib.Path(p).read_text(encoding="utf-8")
    except Exception: return None
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
def ok(m): print("OK", m)
def warn(m): print("AVISO", m)

p = "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/Routes.kt"
t = read(p)
if t is None:
    warn("Routes nao achado - rode na raiz do projeto")
    raise SystemExit(0)

print(f"Routes atual: {len(t.splitlines())} linhas, tem Artist? {'data class Artist' in t}, tem TrackInfo? {'data class TrackInfo' in t}, tem sourceName? {'sourceName' in t}")

# Caso 1: arquivo cortado (tem sourceName mas nao tem Artist/TrackInfo) -> restaura o rabo
if "sourceName" in t and "data class Artist" not in t:
    # Garante que termina com quebra de linha, tira espaco sobrando
    t = t.rstrip() + "\n"
    # Se a ultima linha ja e a do sourceName com comentario, o comentario ja termina na quebra de linha, pode fechar
    # Fecha o MediaList e recoloca tudo que o FIX20 apagou (igual ao dump + sem duplicar nada)
    tail = """    )

    /** Artist profile page. [id] is the YouTube Music channel/browse id (UC...). */
    @Serializable data class Artist(
        val id: String,
        val name: String,
        val thumbnailUrl: String? = null,
        // true = canal do YouTube (videos/shorts/playlists via NewPipe).
        // false = artista do YT Music (musicas/albuns via InnerTube).
        // Sem essa flag, o AppNavHost perdia o source e todo mundo caia no YT Music.
        val isYouTubeChannel: Boolean = false,
    )

    /** "See all" list of an artist. [kind] is an ArtistListKind name. */
    @Serializable data class ArtistList(
        val artistId: String,
        val artistName: String,
        val kind: String,
        val browseId: String,
        val params: String? = null,
    )

    /** List opened from a Library shortcut card. [kind] is a LibraryShortcut name. */
    @Serializable data class LibraryList(val kind: String)

    @Serializable data class TrackInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val thumbnailUrl: String?,
        val sourceName: String, // "YOUTUBE" or "YOUTUBE_MUSIC"
        val durationSeconds: Long,
    )
}
"""
    t = t + tail
    write(p, t)
    ok("Routes: rabo restaurado ( ) + Artist + ArtistList + LibraryList + TrackInfo + })")
    print("Routes agora tem", len(t.splitlines()), "linhas")
    raise SystemExit(0)

# Caso 1b: ja esta completo (tem Artist + TrackInfo) -> nada a fazer, mesmo que o comentario esteja na mesma linha do sourceName
# O comentario so come o que esta DEPOIS dele NA MESMA LINHA. Se o ) esta na linha de baixo, ele nao come nada.
if "data class Artist(" in t and "data class TrackInfo" in t:
    ok("Routes: ja esta completo (tem Artist + TrackInfo), nada a fazer")
    raise SystemExit(0)

# Caso 2: arquivo ja tem Artist, mas a linha do sourceName tem comentario que comeu o ) ou o proximo parametro
# Ex: val sourceName: String? = null, // FIX20 ... )  -> o ) ficou comentado
# Fix: tira o comentario da mesma linha e bota ele numa linha propria acima
if "val sourceName" in t and "// FIX20" in t:
    lines = t.splitlines()
    fixed = False
    for i, line in enumerate(lines):
        if "val sourceName" in line and "// FIX20" in line:
            # Separa: parte antes do // e comentario
            before = line.split("// FIX20")[0].rstrip()
            # Se depois do // tinha codigo (ex: " )" ou " val ..."), ele esta perdido no comentario - tenta recuperar
            # O comentario original era "// FIX20 - YOUTUBE ou YOUTUBE_MUSIC. Sem isso a playlist perdia o mundo."
            # Qualquer coisa depois desse texto era codigo comido. Procura por ")" ou "val " depois do texto.
            after_comment = line.split("perdia o mundo.", 1)
            resto = ""
            if len(after_comment) > 1:
                resto = after_comment[1].strip()
                if resto:
                    print(f"  recuperado depois do comentario: {resto[:120]}")
            # Reescreve: comentario em linha propria + val sourceName limpo + resto na proxima linha se houver
            lines[i] = "        // FIX20 - YOUTUBE ou YOUTUBE_MUSIC. Sem isso a playlist perdia o mundo."
            lines.insert(i+1, before)
            if resto:
                lines.insert(i+2, "        " + resto)
            fixed = True
            break
    if fixed:
        t = "\n".join(lines) + "\n"
        write(p, t)
        ok("Routes: comentario do FIX20 tirado da mesma linha do sourceName (ele estava comendo o ) )")
        raise SystemExit(0)

# Caso 3: nao tem sourceName e nao esta cortado -> adiciona sourceName do jeito certo (com + resto)
if "sourceName" not in t or "data class MediaList" not in t:
    warn("Routes: sem sourceName ou sem MediaList - mande seu Routes.kt")
    raise SystemExit(0)

# Caso 4: ja esta certo (tem sourceName, tem Artist, sem comentario comendo codigo)
if "data class Artist" in t and "data class TrackInfo" in t:
    ok("Routes: ja esta completo (tem Artist + TrackInfo), nada a fazer")
else:
    warn("Routes: formato diferente, mande seu Routes.kt que eu gero o patch exato")
PYEOF
