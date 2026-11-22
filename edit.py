# parte7.py - Parte 7: Limpeza final (sem mexer em logica)
# Roda na raiz: python3 parte7.py
# Remove param/import YouTubeRepository sem uso em ArtistViewModel e MediaListViewModel (depois da parte 6 eles usam MusicRepository)
# Nao apaga YouTubeRepository, NewPipeDownloader, nem nenhum arquivo.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
for rel in ["app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt",
            "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"]:
    p = os.path.join(ROOT, rel)
    if not os.path.isfile(p):
        fail(f"nao achei {p}")
    t = open(p, encoding="utf-8").read()
    if "youTubeRepository." in t:
        print(f"Pulando {rel} - ainda usa youTubeRepository, nao vou limpar")
        continue
    changed = False
    lines = t.splitlines()
    new_lines = []
    for line in lines:
        if "private val youTubeRepository: YouTubeRepository" in line:
            changed = True
            continue
        if "import com.vztechcore.vztune.data.youtube.YouTubeRepository" in line:
            changed = True
            continue
        new_lines.append(line)
    if changed:
        t = "\n".join(new_lines)
        if not t.endswith("\n"):
            t += "\n"
        open(p, "w", encoding="utf-8").write(t)
        print(f"Limpo: {rel} - param/import YouTubeRepository sem uso removido")
    else:
        print(f"Nada para limpar em {rel}")
print("Parte 7 OK - limpeza final.")
