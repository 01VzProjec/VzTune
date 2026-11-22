# fix_v5_compile.py - Corrige erro de compilacao do V5 (string quebrada em 3 linhas)
# Roda na raiz: python3 fix_v5_compile.py
# Causa: no V5, o replace com regex transformou \n\n em quebra de linha real,
# deixando a string aberta. Este fix junta de novo em 1 linha.
# Idempotente: se ja ta certo, nao mexe.
import os, sys
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not os.path.isfile(RP):
    print(f"ABORTOU: nao achei {RP} - roda na raiz"); sys.exit(1)
t=open(RP,encoding="utf-8").read()
# Padrao quebrado: debugPlV5 + "  <quebra> <quebra> " + 
broken = 'debugPlV5 + "' + "\n\n" + '" +'
if broken in t:
    fixed = 'debugPlV5 + "\\n\\n" +'
    t=t.replace(broken, fixed)
    open(RP,"w",encoding="utf-8").write(t)
    print("OK: juntei a string quebrada do V5 Path1 (1 linha). Builda de novo.")
else:
    # Verifica se ainda tem linha quebrada de outro jeito: description com debugPlV5 e proxima linha vazia
    lines=t.splitlines()
    found=False
    for i,l in enumerate(lines):
        if "debugPlV5" in l and "description" in l and l.rstrip().endswith('"'):
            print(f"AVISO: linha {i+1} ainda parece quebrada: {l!r}")
            found=True
    if not found:
        print("SKIP: nada quebrado, arquivo ja ta certo.")
