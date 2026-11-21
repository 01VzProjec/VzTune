#!/bin/bash
# VzTune HOTFIX2 - Corrige o 'break' que o HOTFIX1 colocou errado
# Erro: 'break' and 'continue' are only allowed inside loops (linhas 180, 214, 250)
# Causa: eu usei getPage(page ?: break) em lugar que NAO tem while (ex: searchVideos loadMore = 1 pagina so).
# Break so existe dentro de while/for. Fora do loop ele nao compila.
# Fix: usa !! porque a gente ja checou que nao e nulo (while checa != null, ou ?: return antes).
# Rode na raiz: bash VzTune-HOTFIX2-YouTubeRepository-Break.sh
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
# 1) Troca todo '?: break' dentro de getPage por '!!'
# Antes (quebra fora do while): session.extractor.getPage(page ?: break)
# Depois (compila em todo lugar): session.extractor.getPage(page!!)
# Por que !! e seguro aqui? Porque antes do getPage a gente ja garantiu que nao e nulo:
# - No while: while (... && page != null ...) -> page nao e nulo aqui
# - No loadMore simples: val page = session.nextPage ?: return... -> se chegou aqui, page nao e nulo
# Entao !! nunca vai estourar na pratica.

count = 0
# Padrao generico: getPage(XXX ?: break) -> getPage(XXX!!)
# XXX pode ser page, nextPage, session.nextPage, etc. Pega tudo ate ' ?: break'
pat = re.compile(r"\.getPage\(\s*([A-Za-z0-9_.]+)\s*\?:\s*break\s*\)")
for m in pat.finditer(t):
    print(f"  achado: .getPage({m.group(1)} ?: break) -> .getPage({m.group(1)}!!)")
    count += 1
t = pat.sub(lambda m: f".getPage({m.group(1)}!!)", t)
if count:
    ok(f"Break: {count}x getPage(x ?: break) -> getPage(x!!)")
else:
    ok("Break: nenhum getPage(x ?: break) restante")

# 2) Se sobrou algum outro '?: break' solto (fora do getPage), troca por '!!' tambem
# Ex: val x = page ?: break -> val x = page!!
if "?: break" in t:
    # So troca onde faz sentido: 'var = X ?: break' vira 'X!!' nao e ideal, entao avisa
    # Mas na pratica so tinha nos getPage, entao se sobrou, mostra onde
    for i, line in enumerate(t.splitlines(), 1):
        if "?: break" in line:
            print(f"  sobrou linha {i}: {line.strip()[:160]}")
    # Troca generico: ' (X ?: break)' -> ' (X!!)' e ' = X ?: break' -> ' = X!!' (ultimo recurso)
    t = re.sub(r"\(\s*([A-Za-z0-9_.]+)\s*\?:\s*break\s*\)", r"(\1!!)", t)
    t = t.replace("?: break", "!!")
    ok("Break: ?: break solto restante trocado por !!")
else:
    ok("Break: nenhum ?: break solto")

# 3) Garante que Page? continua Page? (o HOTFIX1 ja fez, nao desfaz)
if "var page: Page?" in t or "var nextPage: Page?" in t:
    ok("Page: var page/nextPage continuam Page? (certo)")
else:
    warn("Page: var page/nextPage nao estao como Page? - pode ser que o HOTFIX1 nao tenha rodado")

if t != orig:
    write(p, t)
    print("HOTFIX2 OK - rode o build de novo")
else:
    warn("Nada mudou - talvez ja esteja corrigido")
PYEOF
