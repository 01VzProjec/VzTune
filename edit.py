# fix_playlists_busca_v8.py - tenta outra busca: ChannelTabExtractor do NewPipe + log singleton que nao se perde
# Roda na raiz: python3 fix_playlists_busca_v8.py
# Por que V7 nao mostrou log: debugLog viajava dentro do ChannelPage e se perdia se o Path1 retornava antes ou o ViewModel sobrescrevia.
# V8:
#  - object ChannelDebugLog { var last } no mesmo arquivo, qualquer lugar do Repo pode escrever, o dialogo le ele no return do Path2.
#  - helper fetchPlaylistsViaChannelTab() fora do browseChannel (mesmo padrao seguro do V7-compile):
#    usa ServiceList.YouTube.getChannelTabsLHFactory().fromUrl(channelUrl + "/playlists")
#    + getChannelTabExtractor(lh) -> e o que o app NewPipe usa pra aba Playlists. Se vier algo, usa.
#  - 1 linha no Path2 ANTES do helper InnerTube: tenta ChannelTab primeiro.
# Sem .bak, sem temp. Aborta sem escrever se ancora nao bater.
import os, sys
def fail(m):
    print(f"ABORTOU: {m}"); sys.exit(1)
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not os.path.isfile(RP): fail(f"nao achei {RP} - roda na raiz")
t=open(RP,encoding="utf-8").read()
_v8_exists = "fetchPlaylistsViaChannelTab" in t
if _v8_exists:
    print("SKIP helper V8 ja existe, vou so garantir o debugLog")
else:
    # 1) Singleton no topo do arquivo, antes da classe YouTubeRepository
    anchor_class="class YouTubeRepository"
    if anchor_class not in t: fail("ancora class YouTubeRepository nao bateu")
    singleton=""
    singleton+="// V8 - log que nao se perde: qualquer ponto do Repo escreve, o return do Path2 le.\n"
    singleton+="object ChannelDebugLog { @Volatile var last: String = \"\" }\n\n"
    t=t.replace(anchor_class, singleton+anchor_class, 1)
    print("OK: ChannelDebugLog")
    # 2) Helper fora do browseChannel, antes de parseChannelStreams (mesma ancora segura do V7-compile)
    anchor2="    private fun parseChannelStreams"
    if anchor2 not in t: fail("ancora parseChannelStreams nao bateu")
    helper=""
    helper+="    // V8 - Playlists via ChannelTabExtractor do NewPipe (o que o app NewPipe usa).\n"
    helper+="    private suspend fun fetchPlaylistsViaChannelTab(channelUrl: String, channelName: String): List<Playlist> {\n"
    helper+="        return runCatching {\n"
    helper+="            val lh = ServiceList.YouTube.getChannelTabsLHFactory().fromUrl(channelUrl + \"/playlists\")\n"
    helper+="            val ex = ServiceList.YouTube.getChannelTabExtractor(lh)\n"
    helper+="            ex.fetchPage()\n"
    helper+="            val items = ex.initialPage.items\n"
    helper+="            ChannelDebugLog.last = \"[V8 tabExtractor items=\" + items.size + \"]\"\n"
    helper+="            val all = parseChannelPlaylistItems(items, channelName).toMutableList()\n"
    helper+="            var next = ex.initialPage.nextPage\n"
    helper+="            var pages = 0\n"
    helper+="            while (all.size < 200 && next != null && pages < 2) {\n"
    helper+="                val p = ex.getPage(next!!)\n"
    helper+="                all += parseChannelPlaylistItems(p.items, channelName)\n"
    helper+="                next = p.nextPage\n"
    helper+="                pages++\n"
    helper+="            }\n"
    helper+="            ChannelDebugLog.last = ChannelDebugLog.last + \" parsed=\" + all.size\n"
    helper+="            all.distinctBy { it.id }\n"
    helper+="        }.onFailure { ChannelDebugLog.last = \"[V8 tabExtractor falhou: \" + (it.message ?: \"\") + \"]\" }.getOrDefault(emptyList())\n"
    helper+="    }\n\n"
    t=t.replace(anchor2, helper+anchor2, 1)
    print("OK: helper fetchPlaylistsViaChannelTab")
    # 3) 1 linha no Path2 ANTES do helper InnerTube (que ja existe do V7-compile) ou antes de val playlistQuery
    # Procura a linha do V7-compile; se nao existir, usa playlistQuery
    if "fetchPlaylistsTabV7(uc, channelName)" in t:
        t=t.replace("        if (channelPlaylists.isEmpty()) channelPlaylists = fetchPlaylistsTabV7(uc, channelName)",
                    "        if (channelPlaylists.isEmpty()) channelPlaylists = fetchPlaylistsViaChannelTab(channelUrl, channelName)\n        if (channelPlaylists.isEmpty()) channelPlaylists = fetchPlaylistsTabV7(uc, channelName)", 1)
        print("OK: chamada V8 antes do V7-compile")
    else:
        anchor3="        val playlistQuery = channelName.ifBlank"
        if anchor3 not in t: fail("ancora playlistQuery nao bateu V8")
        t=t.replace(anchor3, "        if (channelPlaylists.isEmpty()) channelPlaylists = fetchPlaylistsViaChannelTab(channelUrl, channelName)\n"+anchor3, 1)
        print("OK: chamada V8 antes da busca")
    # 4) Path2 return: debugLog inclui singleton - roda mesmo se helper ja existia
    pass
# --- Sempre garante debugLog Path2 com singleton (mesmo se V8 ja aplicado) ---
if "ChannelDebugLog.last" not in t or "debugLog = (ChannelDebugLog.last" not in t:
    _lines2 = t.splitlines()
    _out2 = []
    _fixed_dbg = False
    for _ln in _lines2:
        if "debugLog =" in _ln and "debugPlV5Path2" in _ln and "ChannelDebugLog" not in _ln:
            _ind = _ln[:len(_ln)-len(_ln.lstrip())]
            if "debugV7Path2" in _ln:
                _out2.append(_ind + "debugLog = (ChannelDebugLog.last + \" \" + debugV7Path2 + \" \" + debugPlV5Path2.orEmpty()).trim().takeIf { it.isNotBlank() },")
            else:
                _out2.append(_ind + "debugLog = (ChannelDebugLog.last + \" \" + debugPlV5Path2.orEmpty()).trim().takeIf { it.isNotBlank() },")
            _fixed_dbg = True
        else:
            _out2.append(_ln)
    t = "\n".join(_out2) + "\n"
    print("OK: debugLog Path2 le singleton" if _fixed_dbg else "SKIP debugLog Path2 ja tem singleton ou nao achado")
else:
    print("SKIP debugLog Path2 ja tem singleton")
opens=t.count("{"); closes=t.count("}")
print(f"CHAVES: abre={opens} fecha={closes} diff={opens-closes}")
if opens!=closes: fail(f"chaves desbalanceadas diff={opens-closes} - nao escrevi")
open(RP,"w",encoding="utf-8").write(t)
print("PRONTO - limpa e builda. Abre jazzghost, olha Playlists. Se vier 0, 3 pontinhos, Ver Log temporario, agora tem que aparecer [V8 ...].")
