#!/bin/bash
# ============================================================
# VzTune HOTFIX - Desduplica youTubeRepository (FIX4 + FIX7)
# Causa: FIX4 injetou como nome completo:
#   private val youTubeRepository: com.vztechcore.vztune.data.youtube.YouTubeRepository,
# FIX7 procurava só pelo nome curto:
#   private val youTubeRepository: YouTubeRepository
# ...nao achou o completo, achou que nao existia e injetou DE NOVO.
# Resultado: 2x youTubeRepository no construtor (linhas 48-49) -> o
# Kotlin nao sabe qual usar, e os 2 lugares que chamam
# getChannelForPlaylist (linha 208 = FIX4 loadContent, 375 = FIX7 onOpenArtist)
# quebram com "Cannot infer type" + "Unresolved reference".
# Fix: mantem SÓ a primeira declaracao, apaga as repetidas.
# Roda na raiz: cd ~/VzTune && bash VzTune-HOTFIX-FIX7-DUPLICADO.sh
# ============================================================
set -e
echo "== HOTFIX desduplica youTubeRepository =="

python3 - <<'PY'
import sys, re
def read(p):
    with open(p, encoding="utf-8") as f: return f.read()
def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)
def must(c, m):
    if not c:
        print(f"ERRO {m}"); sys.exit(1)
    print(f"OK {m}")

p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t = read(p)

# 1) Desduplica declaracao no construtor: mantem a 1a, apaga as outras
# Casa as 2 formas: curta e completa
pat = re.compile(r"^[ \t]*private val youTubeRepository:[^\n]*,\s*\n", re.MULTILINE)
matches = list(pat.finditer(t))
print(f"Declaracoes youTubeRepository encontradas: {len(matches)}")
if len(matches) > 1:
    # Apaga de tras pra frente pra nao deslocar os indices
    for m in reversed(matches[1:]):
        t = t[:m.start()] + t[m.end():]
    write(p, t)
    print(f"OK removidas {len(matches)-1} declaracao(oes) duplicada(s), mantida a 1a")
else:
    print("OK sem duplicata no construtor (0 ou 1 declaracao)")

t = read(p)
# 2) Desduplica import, se houver 2x
imp = "import com.vztechcore.vztune.data.youtube.YouTubeRepository"
if t.count(imp) > 1:
    # Mantem a 1a, apaga as outras
    first = t.find(imp)
    rest = t[first+len(imp):].replace(imp + "\n", "", 1)
    # Se ainda houver, remove todas as restantes
    while imp in rest:
        rest = rest.replace(imp + "\n", "", 1).replace(imp, "", 1)
    t = t[:first+len(imp)] + rest
    write(p, t)
    print("OK import duplicado removido")
t = read(p)
# 3) Se a declaracao restante usa nome curto, garante que o import existe
decl_short = re.search(r"private val youTubeRepository:\s*YouTubeRepository", t)
decl_full = re.search(r"private val youTubeRepository:\s*com\.vztechcore", t)
if decl_short and imp not in t:
    must("import com.vztechcore.vztune.data.ytmusic.YTMusicRepository" in t, "ancora import YTMusicRepository")
    t = t.replace(
        "import com.vztechcore.vztune.data.ytmusic.YTMusicRepository",
        imp + "\nimport com.vztechcore.vztune.data.ytmusic.YTMusicRepository"
    )
    write(p, t)
    print("OK import adicionado (declaracao usa nome curto)")
t = read(p)
# 4) Verificacoes finais
pat2 = re.compile(r"private val youTubeRepository:")
must(len(pat2.findall(t)) == 1, f"so 1 youTubeRepository no construtor (achado {len(pat2.findall(t))})")
must("getChannelForPlaylist(route.id)" in t, "getChannelForPlaylist ainda presente (FIX4 loadContent + FIX7 onOpenArtist)")
must(t.count(imp) <= 1, "import sem duplicata")
print("\nHOTFIX aplicado. Roda o build de novo.")
PY
