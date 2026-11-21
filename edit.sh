#!/bin/bash
# VzTune HOTFIX - YouTubeRepository.kt: Page? vs Page + Description? vs String?
# Corrige os 4 erros do build:
# 124 / 896: Assignment type mismatch Page? vs Page
# 922: Unresolved reference 'isNotBlank' (Description nao tem isNotBlank, String tem)
# 933: Argument type mismatch Description? vs String?
# Rode na raiz: bash VzTune-HOTFIX-YouTubeRepository-Page-Description.sh
set -e
python3 - << 'PYEOF'
import re, pathlib
def read(p):
    try: return pathlib.Path(p).read_text(encoding="utf-8")
    except Exception: return None
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
def ok(m): print("OK", m)
def warn(m): print("AVISO", m)

p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if t is None:
    warn("YouTubeRepository nao achado - rode na raiz do projeto")
    raise SystemExit(0)

orig = t
# --- 1) Page? vs Page: var page = session.nextPage ?: return ... ---
# Problema: 'var page = session.nextPage ?: return' faz o Kotlin inferir page como Page (sem ?),
# ai 'page = next.nextPage' (que e Page?) nao compila.
# Fix: declara como Page? e usa 'page ?: break' no getPage.
# Antes (quebra):
#   var page = session.nextPage ?: return@withContext emptyList()
#   while (... && page != null ...) { val next = session.extractor.getPage(page); ...; page = next.nextPage }
# Depois (compila):
#   var page: Page? = session.nextPage
#   while (... && page != null ...) { val next = session.extractor.getPage(page ?: break); ...; page = next.nextPage }

# 1a) loadMoreChannelVideos / loadMoreChannelPlaylists - padrao com session.nextPage
pat_page_var = re.compile(r"var page = session\.nextPage \?: return@withContext emptyList\(\)")
if pat_page_var.search(t):
    t = pat_page_var.sub("var page: Page? = session.nextPage ?: return@withContext emptyList()", t)
    ok("Page: var page = session.nextPage -> var page: Page? = session.nextPage")
else:
    if "var page: Page?" in t:
        ok("Page: var page ja e Page?")
    else:
        warn("Page: var page = session.nextPage nao achado (pode ja estar como Page? ou com outro nome)")

# 1b) getPage(page) com page agora nullable -> getPage(page ?: break)
# So troca onde page e a variavel do loop (session.extractor.getPage(page))
if "session.extractor.getPage(page)" in t:
    t = t.replace("session.extractor.getPage(page)", "session.extractor.getPage(page ?: break)")
    ok("Page: getPage(page) -> getPage(page ?: break)")
elif "session.extractor.getPage(page ?: break)" in t:
    ok("Page: getPage(page ?: break) ja aplicado")
else:
    warn("Page: session.extractor.getPage(page) nao achado")

# 1c) var nextPage = pl.initialPage.nextPage -> var nextPage: Page? = ...
# Tem 2 lugares: browseYouTubePlaylist e browseChannel playlists. Corrige todos.
pat_next = re.compile(r"var nextPage = pl\.initialPage\.nextPage")
n = len(pat_next.findall(t))
if n:
    t = pat_next.sub("var nextPage: Page? = pl.initialPage.nextPage", t)
    ok(f"Page: var nextPage = pl.initialPage.nextPage -> var nextPage: Page? (x{n})")
else:
    if "var nextPage: Page?" in t:
        ok("Page: var nextPage ja e Page?")
    else:
        warn("Page: var nextPage = pl.initialPage.nextPage nao achado")

# 1d) pl.getPage(nextPage) com nextPage nullable -> pl.getPage(nextPage ?: break)
# Tem varios: browseYouTubePlaylist + browseChannel playlists. Corrige todos que ainda estao sem ?:
if "pl.getPage(nextPage)" in t:
    t = t.replace("pl.getPage(nextPage)", "pl.getPage(nextPage ?: break)")
    ok("Page: pl.getPage(nextPage) -> pl.getPage(nextPage ?: break)")
elif "pl.getPage(nextPage ?: break)" in t:
    ok("Page: pl.getPage(nextPage ?: break) ja aplicado")
else:
    warn("Page: pl.getPage(nextPage) nao achado")

# 1d2) Declaracao explicita sem ?: 'var page: Page =' / 'var nextPage: Page =' -> vira Page?
if "var page: Page =" in t:
    t = t.replace("var page: Page =", "var page: Page? =")
    ok("Page: var page: Page = -> var page: Page? =")
if "var nextPage: Page =" in t:
    t = t.replace("var nextPage: Page =", "var nextPage: Page? =")
    ok("Page: var nextPage: Page = -> var nextPage: Page? =")
if ": Page = pl.initialPage.nextPage" in t:
    t = t.replace(": Page = pl.initialPage.nextPage", ": Page? = pl.initialPage.nextPage")
    ok("Page: : Page = pl.initialPage.nextPage -> : Page? =")

# 1e) Generico: qualquer outro 'var nextPage = ...nextPage' sem tipo -> vira Page?
# Ex: var nextPage = extractor.initialPage.nextPage, var nextPage = session.nextPage
pat_generic = re.compile(r"var nextPage = ([^\n]*?\.nextPage[^\n]*)")
def fix_generic(m):
    expr = m.group(1).strip()
    if "Page?" in m.group(0):
        return m.group(0)
    return f"var nextPage: Page? = {expr}"
t2, n2 = pat_generic.subn(fix_generic, t)
if n2:
    # So conta os que realmente mudaram (evita recontar os ja corrigidos em 1c)
    if t2 != t:
        t = t2
        ok(f"Page: var nextPage generico -> Page? (x{n2})")
    else:
        ok("Page: var nextPage generico ja estava Page?")

# --- 2) Description? vs String? ---
# No NewPipe, PlaylistExtractor.description NAO e String, e um objeto Description { content: String?, type }.
# Por isso: 'pl.description.isNotBlank()' nao existe (Description nao tem isNotBlank)
# e 'YouTubePlaylistPage(..., desc: Description?, ...)' nao cabe onde espera String?.
# Fix: usa pl.description?.content (que e String?)
# ATENCAO: ChannelExtractor.description ainda e String, nao mexe nele. So mexe em 'pl.description' (PlaylistExtractor).

# 2a) O erro exato do FIX20: val desc = runCatching { pl.description }.getOrNull()?.takeIf { it.isNotBlank() }
if "runCatching { pl.description }.getOrNull()?.takeIf { it.isNotBlank() }" in t:
    t = t.replace(
        "runCatching { pl.description }.getOrNull()?.takeIf { it.isNotBlank() }",
        "runCatching { pl.description?.content }.getOrNull()?.takeIf { it.isNotBlank() }"
    )
    ok("Description: pl.description -> pl.description?.content (desc do FIX20)")
elif "pl.description?.content" in t and "val desc" in t:
    ok("Description: desc ja usa pl.description?.content")
else:
    warn("Description: linha 'val desc = runCatching { pl.description }' nao achada no formato exato")

# 2b) Qualquer outro 'pl.description' usado como String: pl.description?.takeIf, pl.description.orEmpty(), etc.
# Ex: val d = pl.description -> val d = pl.description?.content
# Nao mexe em 'extractor.description' (canal) nem em 'header?.description' (ja e String).
if re.search(r"\bpl\.description\b(?!\?\.)", t):
    # So onde ainda esta sem ?.content
    t = re.sub(r"\bpl\.description\b(?!\?\.\s*content)", "pl.description?.content", t)
    ok("Description: pl.description solto -> pl.description?.content")
else:
    ok("Description: nenhum pl.description solto restante")

# 2c) Se ficou 'pl.description?.content?.content' (duplo por rodar 2x), desfaz
if "pl.description?.content?.content" in t:
    t = t.replace("pl.description?.content?.content", "pl.description?.content")
    ok("Description: duplo ?.content corrigido")

# 2d) Se ficou 'pl.description?.content' dentro de runCatching que ja tem ?.content, ok. Garante que desc e String?
# Ex: YouTubePlaylistPage(..., desc, ...) - desc agora e String?, nao precisa mexer.

if t != orig:
    write(p, t)
    print("HOTFIX OK - YouTubeRepository.kt atualizado, rode o build de novo")
else:
    warn("Nada mudou - talvez o arquivo ja esteja corrigido ou esta com outro formato. Mande as linhas 120-130 e 890-935 do seu YouTubeRepository.kt")
PYEOF
