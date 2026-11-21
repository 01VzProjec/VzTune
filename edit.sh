#!/bin/bash
# VzTune FIX16-HOTFIX - corrige o erro do FIX16 em ArtistScreen.kt
# Erro: This annotation is not repeatable / Repeated 'private' / Functions which invoke @Composable must be marked
# Causa: o FIX16 inseriu o ChannelHeader no meio do bloco do SongsSlide, entre o @Composable/private dele
# e o "fun SongsSlide". Sobrou um @Composable + private orfao antes do ChannelHeader e o SongsSlide ficou sem anotacao.
# Esse hotfix: tira o orfao e devolve o cabecalho do SongsSlide pra cima dele. Nao mexe em mais nada.
# Roda na raiz do projeto: bash VzTune-FIX16-HOTFIX.sh
set -e
python3 - << 'PY'
import pathlib, sys, re

p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
t = pathlib.Path(p).read_text(encoding="utf-8")

# 1) Tira o orfao: comentario do Slide + @Composable + private soltos antes do comentario do ChannelHeader
# So casa quando o proximo bloco e o ChannelHeader, entao nao apaga um SongsSlide certo.
orphan = re.compile(
    r"/\*\* Slide de Musicas populares.*?\*/\s*@Composable\s*private\s*\n(?=/\*\*\s*\n\s*\* Cabecalho)",
    re.DOTALL,
)
if orphan.search(t):
    t = orphan.sub("", t, count=1)
    print("OK tirou @Composable/private orfao antes do ChannelHeader")
else:
    print("Sem orfao (ja corrigido ou outro formato)")

# 2) Devolve o cabecalho do SongsSlide pra cima dele, se ele estiver sem anotacao
if "fun SongsSlide(" in t and "@Composable\nprivate fun SongsSlide(" not in t:
    t = t.replace(
        "fun SongsSlide(",
        "/** Slide de Musicas populares: 4 faixas por pagina, arrasta da direita pra esquerda pra proxima fileira de 4. */\n@Composable\nprivate fun SongsSlide(",
        1,
    )
    print("OK devolveu @Composable/private pro SongsSlide")
else:
    print("SongsSlide ja esta com @Composable, nada a fazer")

# 3) Checagem: tem que ter 1 ChannelHeader e 1 SongsSlide, cada um com 1 @Composable
print("ChannelHeader:", t.count("private fun ChannelHeader("), "| SongsSlide:", t.count("private fun SongsSlide("))
assert t.count("private fun ChannelHeader(") == 1, "ChannelHeader duplicado ou faltando"
assert t.count("private fun SongsSlide(") == 1, "SongsSlide duplicado ou faltando"
assert "@Composable\nprivate \n/**" not in t, "ainda tem orfao"

pathlib.Path(p).write_text(t, encoding="utf-8")
print("FIX16-HOTFIX OK - pode buildar de novo")
PY
