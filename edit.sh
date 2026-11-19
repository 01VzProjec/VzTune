#!/bin/bash
# ============================================================
# HOTFIX - LibraryListSearchOverlay.kt:157 Expecting top level declaration
# Causa: no FIX da unificacao, ao apagar EmptySearchResult + SearchInputBar
# sobraram 2 linhas "@Composable" orfas no fim do arquivo (linhas 155 e 157),
# sem nenhuma funcao depois delas. O Kotlin nao aceita anotacao solta.
# Fix: apaga so essas 2 linhas orfas do fim. Nao mexe em mais nada.
# Roda na raiz do projeto VzTune.
# ============================================================
set -e
FILE="app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt"
if [ ! -f "$FILE" ]; then
  echo "❌ Nao achei: $FILE"
  echo "Roda dentro da pasta VzTune (onde tem app/build.gradle.kts)."
  exit 1
fi
echo "📄 Antes (fim do arquivo):"
tail -n 12 "$FILE"
cp "$FILE" "$FILE.bak157"
echo "💾 Backup: $FILE.bak157"
python3 - <<'PY'
path = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt"
lines = open(path, encoding="utf-8").read().splitlines()
# Apaga do fim: linhas vazias + @Composable orfao, ate achar a ultima linha de codigo real (})
removed = 0
while lines and lines[-1].strip() in ("", "@Composable"):
    lines.pop()
    removed += 1
open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(f"Removidas {removed} linhas orfas do fim.")
PY
echo ""
echo "📄 Depois (fim do arquivo):"
tail -n 12 "$FILE"
echo ""
echo "🔍 Verificacao:"
echo -n "@Composable orfao no fim (tem que ser 0): "
tail -n 5 "$FILE" | grep -c "@Composable" || true
echo -n "Total de linhas: "
wc -l < "$FILE"
echo -n "Ultima linha (tem que ser }): "
tail -n 1 "$FILE"
echo ""
echo "✅ Pronto! Roda o build de novo."
echo "Se der outro erro, manda o log que eu vejo."
