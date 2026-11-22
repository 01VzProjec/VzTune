# parte2_3_fix.py - Corrige o erro Unresolved reference cleanId / rawId / input
# Roda na raiz: python3 parte2_3_fix.py
# Nao cria .bak. Se ja estiver corrigido, nao faz nada.
import os, sys

def fail(msg):
    print(f"ABORTOU: {msg}")
    sys.exit(1)

ROOT = os.getcwd()
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not os.path.isfile(YT_REPO):
    fail(f"nao achei {YT_REPO} - roda na raiz do VzTune")

with open(YT_REPO, "r", encoding="utf-8") as f:
    text = f.read()

def remove_block(orig, marker):
    # marker = "InnerTube first for browseChannel" etc
    start = orig.find(f"// PART 2+3 - {marker}")
    if start == -1:
        return orig, None
    line_start = orig.rfind("\n", 0, start)
    if line_start == -1:
        line_start = 0
    onf = orig.find(".onFailure", start)
    if onf == -1:
        fail(f"nao achei fim do bloco {marker}")
    end = orig.find("\n", onf)
    if end == -1:
        end = len(orig)
    block = orig[line_start:end]  # inclui \n do inicio ate fim da linha onFailure
    new_text = orig[:line_start] + orig[end:]
    return new_text, block

# Lista: (marker, anchor_unico)
jobs = [
    ("InnerTube first for browseChannel", "if (input.isBlank()) return@withContext null"),
    ("InnerTube first for getChannelForVideo", "if (cleanId.isBlank()) return@withContext null"),
    ("InnerTube first for getChannelForPlaylist", "if (rawId.isBlank()) return@withContext null"),
]

fixed_any = False
for marker, anchor in jobs:
    snippet_pos = text.find(f"// PART 2+3 - {marker}")
    anchor_pos = text.find(anchor)
    if snippet_pos == -1:
        print(f"Pulando {marker} - bloco nao encontrado (talvez ja removido?)")
        continue
    if anchor_pos == -1:
        fail(f"ancora nao bateu: {anchor} para {marker}")
    if snippet_pos > anchor_pos:
        print(f"OK {marker} - ja esta depois da variavel, nao precisa mexer")
        continue
    # Esta antes da variavel = bug: remove e reinsere depois da ancora
    print(f"Corrigindo {marker} - estava antes da variavel, movendo pra depois")
    text, block = remove_block(text, marker)
    if block is None:
        fail(f"falhou ao remover {marker}")
    # Reacha ancora depois da remocao (posicao mudou)
    anchor_pos2 = text.find(anchor)
    if anchor_pos2 == -1:
        fail(f"ancora sumiu depois de remover: {anchor}")
    anchor_end = text.find("\n", anchor_pos2)
    if anchor_end == -1:
        anchor_end = len(text)
    # Insere bloco logo depois da linha da ancora
    text = text[:anchor_end] + block + text[anchor_end:]
    fixed_any = True
    print(f"  -> {marker} movido para depois de: {anchor}")

if not fixed_any:
    print("Nada para corrigir - ja estava certo.")
else:
    # So escreve se tudo ok
    with open(YT_REPO, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"Fix OK - {YT_REPO} corrigido. Roda o build de novo.")
