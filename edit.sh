#!/bin/bash
# VzTune HOTFIX - Conflicting import: imported name 'R' is ambiguous
# Roda na raiz do projeto: bash VzTune-HOTFIX-R-DUPLICADO.sh
# Motivo: MediaThumbnail.kt tinha "import com.vztechcore.vztune.R" 2x (linhas 40 e 41)
# + tambem tinha Image e painterResource duplicados.
# O Kotlin nao deixa 2 imports com o mesmo nome, ele nao sabe qual usar -> trava a compilacao.
set -e
python3 - << 'PY'
import pathlib

def dedup_imports(path: pathlib.Path):
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    seen = set()
    out = []
    removed = 0
    for line in lines:
        stripped = line.strip()
        # So dedupa linha que e import. Resto do codigo nao mexe.
        if stripped.startswith("import "):
            if stripped in seen:
                removed += 1
                continue  # pula a repetida, mantem a primeira
            seen.add(stripped)
        out.append(line)
    if removed:
        path.write_text("".join(out), encoding="utf-8")
        print(f"OK {path} - removeu {removed} import duplicado(s)")
    return removed

# 1) O arquivo do erro
target = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MediaThumbnail.kt")
total = 0
if target.exists():
    total += dedup_imports(target)
else:
    print("AVISO: nao achei", target)

# 2) Varredura de seguranca: mesmo bug pode estar em outro arquivo por copy-paste / script anterior.
# So remove linha de import EXATAMENTE igual, nunca apaga import diferente.
for root in ["app/src", "data/src", "core/src"]:
    p = pathlib.Path(root)
    if not p.exists():
        continue
    for f in p.rglob("*.kt"):
        if f == target:
            continue
        total += dedup_imports(f)

print(f"HOTFIX R-DUPLICADO OK - total removido: {total}")
PY
