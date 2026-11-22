# passo2_fix_build.py — PASSO 2, sozinho. Roda na raiz do projeto: python3 passo2_fix_build.py
# Corrige o erro do CI: YouTubeRepository.kt usa 5 coisas que nunca foram declaradas.
# So 2 trocas exatas, cada uma tem que aparecer EXATAMENTE 1 vez, senao aborta sem escrever nada.
# Faz backup .bak-passo2 antes de escrever. Nao apaga bloco nenhum.
import pathlib, sys

path = pathlib.Path("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not path.exists():
    print("ERRO: nao achei o arquivo. Rode na raiz do projeto.")
    sys.exit(1)

text = path.read_text(encoding="utf-8")

# 1) Restaura as declaracoes que faltam: ChannelVideosSession + os 4 maps de sessao.
#    Tipo do extractor: PlaylistExtractor (e o que o browseChannel passa: pl = PlaylistExtractor do UU).
#    shortSearches guarda VideoSearchSession (e o que o searchShorts salva).
old1 = """    // Sessao de paginacao do canal: guarda o extractor + a proxima pagina.
    // Sem guardar o extractor nao da pra chamar getPage() depois.
    private fun parseChannelStreamItems"""
new1 = """    // Sessao de paginacao dos videos do canal (UU): guarda o extractor + a proxima pagina.
    // Sem guardar o extractor nao da pra chamar getPage() depois.
    private data class ChannelVideosSession(
        val extractor: PlaylistExtractor,
        var nextPage: Page?,
        val channelName: String,
    )

    private val channelVideoSessions = mutableMapOf<String, ChannelVideosSession>()
    private val videoSearches = mutableMapOf<String, VideoSearchSession>()
    private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()
    private val shortSearches = mutableMapOf<String, VideoSearchSession>()

    // Sessao de paginacao do canal: guarda o extractor + a proxima pagina.
    // Sem guardar o extractor nao da pra chamar getPage() depois.
    private fun parseChannelStreamItems"""
n1 = text.count(old1)
if n1 != 1:
    print(f"ERRO [sessoes]: achei {n1}x (esperava 1x). ABORTEI, nao mudei nada.")
    sys.exit(1)
text = text.replace(old1, new1)
print("OK [sessoes]")

# 2) browseChannel: hasMoreVideos/hasMorePlaylists nao sao variaveis, sao as funcoes hasMoreChannelVideos(uc) / hasMoreChannelPlaylists(uc).
old2 = """            hasMoreVideos = hasMoreVideos,
            hasMorePlaylists = hasMorePlaylists,"""
new2 = """            hasMoreVideos = hasMoreChannelVideos(uc),
            hasMorePlaylists = hasMoreChannelPlaylists(uc),"""
n2 = text.count(old2)
if n2 != 1:
    print(f"ERRO [hasMore]: achei {n2}x (esperava 1x). ABORTEI, nao mudei nada.")
    sys.exit(1)
text = text.replace(old2, new2)
print("OK [hasMore]")

bak = path.with_suffix(path.suffix + ".bak-passo2")
bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")
path.write_text(text, encoding="utf-8")
print(f"Salvo {path} (backup em {bak.name})")
print("PASSO 2 aplicado. Agora: commita, da push e ve o CI rebuildar.")
