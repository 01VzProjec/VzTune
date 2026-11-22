# passo3_fix.py — Corrige o erro de build do passo3_ui + apaga os .bak que meus scripts criaram.
# Roda na raiz do projeto: python3 passo3_fix.py
# NAO cria nenhum arquivo temporario nem backup.
import pathlib, sys

ROOT = pathlib.Path(".")

# 1) Corrige a declaracao do displayState: o passo3_ui trocou state.videos -> displayState.videos
#    DENTRO da propria declaracao, e val nao pode se referenciar na propria inicializacao.
p = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
if not p.exists():
    print("ERRO: nao achei ArtistScreen.kt. Rode na raiz do projeto.")
    sys.exit(1)
t = p.read_text(encoding="utf-8")
broken = """    val displayState = if (channelSearchQuery.isBlank()) state else state.copy(
        videos = displayState.videos.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
        channelPlaylists = displayState.channelPlaylists.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
    )"""
fixed = """    val displayState = if (channelSearchQuery.isBlank()) state else state.copy(
        videos = state.videos.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
        channelPlaylists = state.channelPlaylists.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
    )"""
if t.count(broken) == 1:
    t = t.replace(broken, fixed, 1)
    p.write_text(t, encoding="utf-8")
    print("OK [displayState]: declaracao corrigida (agora usa state.videos / state.channelPlaylists).")
elif t.count(fixed) == 1:
    print("OK [displayState]: ja estava certo, nao mudei nada.")
else:
    print(f"ERRO [displayState]: achei {t.count(broken)}x quebrado e {t.count(fixed)}x certo. Me manda o trecho da declaracao que eu ajusto.")
    sys.exit(1)

# 2) Apaga os .bak que meus scripts criaram (passo3_repo / passo3_ui). Sem backup novo.
removed = []
for rel in [
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt.bak-passo3ui",
    "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/ChannelCards.kt.bak-passo3ui",
    "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MediaThumbnail.kt.bak-passo3ui",
    "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt.bak-passo3repo",
]:
    f = ROOT / rel
    if f.exists():
        f.unlink()
        removed.append(f.name)
print(f"OK [limpeza]: apaguei {len(removed)} arquivo(s) .bak: {', '.join(removed) if removed else 'nenhum (ja nao existiam)'}")
print("Pronto. Da push de novo que o build passa.")
