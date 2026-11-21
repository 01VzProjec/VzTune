#!/bin/bash
# VzTune FIX18-HOTFIX-NAV - Liga o Buscar mais na navegacao
# Corrige o AVISO do FIX18: AppNavHost nao tinha onSavePlaylistToLibrary,
# entao o FIX18 nao achou onde inserir onLoadMoreChannelVideos.
# Roda na raiz do projeto: bash VzTune-FIX18-HOTFIX-NAV.sh
set -e
python3 << 'PYEOF'
import pathlib
ROOT = pathlib.Path(".")
def read(p):
    return pathlib.Path(p).read_text(encoding="utf-8", errors="ignore") if pathlib.Path(p).exists() else None
def write(p, t):
    pp = pathlib.Path(p); pp.parent.mkdir(parents=True, exist_ok=True); pp.write_text(t, encoding="utf-8")
    print(f"OK {p}")

def insert_load_more(text):
    # Ja ligado?
    if "onLoadMoreChannelVideos" in text:
        return text, False
    idx = text.find("ArtistScreen(")
    if idx == -1:
        return text, False
    start = idx + len("ArtistScreen(")
    depth = 1
    j = start
    # Conta parenteses ate fechar o ArtistScreen( ... )
    # Ignora parenteses dentro de strings "..." pra nao confundir
    in_str = False
    esc = False
    while j < len(text) and depth > 0:
        c = text[j]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    break
        j += 1
    if depth != 0:
        return text, False
    # j = indice do ')' que fecha ArtistScreen(
    prefix = text[:j].rstrip()
    if not prefix.endswith(","):
        prefix += ","
    insertion = "\n                            onLoadMoreChannelVideos = vm::loadMoreChannelVideos,"
    new = prefix + insertion + "\n                        " + text[j:]
    return new, True

fixed = False
# 1) Tenta o AppNavHost direto (caso mais comum)
candidates = []
nav = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
if nav.exists():
    candidates.append(nav)
# 2) Fallback: qualquer .kt que tenha ArtistScreen( + ArtistViewModel (navegacao)
for pp in ROOT.rglob("*.kt"):
    if pp == nav:
        continue
    try:
        txt = pp.read_text(encoding="utf-8", errors="ignore")
    except:
        continue
    if "ArtistScreen(" in txt and "ArtistViewModel" in txt:
        candidates.append(pp)

for pp in candidates:
    t = read(str(pp))
    if t is None:
        continue
    nt, changed = insert_load_more(t)
    if changed:
        write(str(pp), nt)
        print(f"OK navegacao {pp} : onLoadMoreChannelVideos ligado (vm::loadMoreChannelVideos)")
        fixed = True
    else:
        if "onLoadMoreChannelVideos" in t:
            print(f"OK navegacao {pp} : ja tinha onLoadMoreChannelVideos")
            fixed = True
        else:
            print(f"AVISO navegacao {pp} : ArtistScreen( nao achado ou parenteses nao fecharam")

if not fixed:
    print("ERRO Nenhum arquivo de navegacao com ArtistScreen( achado. Me manda o AppNavHost.kt")
else:
    print("FIX18-HOTFIX-NAV OK - Buscar mais ligado")
PYEOF
