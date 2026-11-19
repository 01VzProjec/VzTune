#!/bin/bash
# VzTune HOTFIX - SearchResultCards: background + Text nullable
# Corrige:
#  SearchResultCards.kt:322 Unresolved reference 'background'
#  SearchResultCards.kt:334 Unresolved reference 'background'
#  SearchResultCards.kt:337 None of the following candidates is applicable: fun Text(...)
#  SearchResultCards.kt:345 Unresolved reference 'background'
# Roda na raiz: cd ~/VzTune && bash VzTune-HOTFIX-SearchResultCards-Background.sh
set -e
echo "== HOTFIX SearchResultCards background + Text =="

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

p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchResultCards.kt"
t = read(p)

# 1) Import do background - sem ele, Modifier.background() nao existe
if "import androidx.compose.foundation.background" not in t:
    if "import androidx.compose.foundation.clickable" in t:
        t = t.replace(
            "import androidx.compose.foundation.clickable",
            "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
        )
    elif "import androidx.compose.foundation.lazy.LazyColumn" in t:
        t = t.replace(
            "import androidx.compose.foundation.lazy.LazyColumn",
            "import androidx.compose.foundation.background\nimport androidx.compose.foundation.lazy.LazyColumn"
        )
    else:
        t = t.replace("import ", "import androidx.compose.foundation.background\nimport ", 1)
    write(p, t)
    print("OK import background adicionado")
else:
    print("OK import background ja existe")
t = read(p)

# 2) Text com String? - formatDuration() retorna String? (nullable)
# Text() so aceita String ou AnnotatedString, nao String?. Sem o ?: "" o
# compilador diz "None of the following candidates is applicable".
# ATENCAO: so corrige o Text() do ShortResultCard, NAO o durationLabel =
# formatDuration(...) que ja aceita String? e tem que continuar com null.
import re
pattern = re.compile(r"(Text\(\s*formatDuration\(hit\.track\.durationSeconds\)),")
t_new, n = pattern.subn(r'\1 ?: "",', t)
if n > 0:
    t = t_new
    write(p, t)
    print(f"OK Text(formatDuration) corrigido para ?: \"\" ({n} ocorrencia)")
else:
    print("OK Text(formatDuration) ja corrigido ou formato diferente")

# 3) Verificacao final
t2 = read(p)
must("import androidx.compose.foundation.background" in t2, "import background existe")
must(t2.count("import androidx.compose.foundation.background") == 1, "import background sem duplicar")
must("formatDuration(hit.track.durationSeconds) ?: \"\"" in t2, "Text usa String nao-nula")
must(".background(" in t2, "ShortResultCard usa .background()")
must(t2.count("{") == t2.count("}"), "chaves balanceadas")
print("\nHOTFIX aplicado. Pode rodar o build.")
PY
