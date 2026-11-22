# fix_channel_playlists_v3.py - Playlists do canal sumidas (V3 - whole-JSON, ignora aba selecionada)
# Roda na raiz: python3 fix_channel_playlists_v3.py
# Por que V2 nao resolveu em todo canal (ex: jazzghost):
#  parseChannelPage so le a aba com selected=true. Se o YouTube marcar a aba errada
#  (Home em vez de Playlists), ele volta 0 mesmo com param fixo certo.
# V3: collectPlaylists vira publico + parsePlaylistsWholeJson varre o JSON INTEIRO,
#  sem olhar aba selecionada. Repository usa ele quando ainda ta vazio.
# Idempotente: se ja tem FIX PLAYLISTS V3, pula.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
SEARCH_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeSearchParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
for p in [CHANNEL_PARSER, SEARCH_PARSER, YT_REPO]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda na raiz do projeto")

# ---------- 1) SearchParser (so se ainda nao tem fix anterior) ----------
t = open(SEARCH_PARSER, encoding="utf-8").read()
if "FIX PLAYLIST CANAL" not in t:
    old_type = '            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue'
    if old_type in t:
        t = t.replace(old_type, '            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue // FIX PLAYLIST CANAL')
    open(SEARCH_PARSER, "w", encoding="utf-8").write(t)
    print("OK SearchParser: tipo vazio")
else:
    print("SKIP SearchParser ja tem FIX PLAYLIST CANAL")

# ---------- 2) ChannelParser ----------
t = open(CHANNEL_PARSER, encoding="utf-8").read()
if "FIX PLAYLISTS V3" not in t:
    if "FIX PLAYLIST CANAL" not in t:
        old = '            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue'
        if old in t:
            t = t.replace(old, '            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue // FIX PLAYLIST CANAL')
        fill16 = "                videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }\n                shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }"
        fill12 = "            videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }\n            shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }"
        if t.count(fill16) == 1 and t.count(fill12) == 1:
            t = t.replace(fill16, fill16 + "\n                // FIX PLAYLIST CANAL - playlist sem dono usa nome do canal.\n                playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }")
            t = t.replace(fill12, fill12 + "\n            // FIX PLAYLIST CANAL - playlist sem dono usa nome do canal.\n            playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }")
    # collectPlaylists vira publico pra Repository usar direto
    if "    private fun collectPlaylists(" in t:
        t = t.replace("    private fun collectPlaylists(", "    fun collectPlaylists(", 1)
    # parsePlaylistsWholeJson depois de parsePlaylistsContinuation
    anchor = "    fun parsePlaylistsContinuation(json: JSONObject): Pair<List<Playlist>, String?> {"
    pos = t.find(anchor)
    if pos == -1:
        fail("ancora parsePlaylistsContinuation nao bateu em YouTubeChannelParser.kt")
    eol = t.find("\n", pos)
    # acha o fim da funcao: procura o proximo "\n    }" depois de pos (fim de parsePlaylistsContinuation)
    end = t.find("\n    }", pos)
    if end == -1:
        fail("fim de parsePlaylistsContinuation nao achado")
    end = end + len("\n    }")
    block = ""
    block += "\n\n    // FIX PLAYLISTS V3 - varre o JSON INTEIRO, ignora aba selecionada.\n"
    block += "    // parseChannelPage so le a aba selected=true. Se o YouTube marcar a errada, volta 0.\n"
    block += "    fun parsePlaylistsWholeJson(json: JSONObject, channelName: String): List<Playlist> {\n"
    block += "        val all = collectPlaylists(json).distinctBy { it.id }\n"
    block += "        return all.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }\n"
    block += "    }\n"
    t = t[:end] + block + t[end:]
    open(CHANNEL_PARSER, "w", encoding="utf-8").write(t)
    print("OK ChannelParser: collectPlaylists publico + parsePlaylistsWholeJson (FIX PLAYLISTS V3)")
else:
    print("SKIP ChannelParser ja tem FIX PLAYLISTS V3")

# ---------- 3) Repository ----------
t = open(YT_REPO, encoding="utf-8").read()
if "FIX PLAYLISTS V3" in t:
    print("SKIP Repo ja tem FIX PLAYLISTS V3")
else:
    # Path 1: insere ANTES de ytChannelPlaylistContinuations[uc] = playlistsCont (depois do V2)
    anchorB = "ytChannelPlaylistContinuations[uc] = playlistsCont"
    posB = t.find(anchorB)
    if posB == -1:
        fail("ancora Path1 ytChannelPlaylistContinuations nao bateu em YouTubeRepository.kt")
    solB = t.rfind("\n", 0, posB) + 1
    blockB = ""
    blockB += "                // FIX PLAYLISTS V3 - whole-JSON, ignora aba selecionada. So roda se ainda ta vazio.\n"
    blockB += "                if (playlists.isEmpty()) {\n"
    blockB += "                    runCatching {\n"
    blockB += "                        val jsonV3 = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blockB += "                        val wholeV3 = YouTubeChannelParser.parsePlaylistsWholeJson(jsonV3, channelName)\n"
    blockB += "                        val idsV3 = playlists.map { it.id }.toSet()\n"
    blockB += "                        playlists += wholeV3.filter { it.id !in idsV3 }\n"
    blockB += "                        Log.w(TAG, \"Playlists V3 whole fixed-param: ${wholeV3.size}, total agora ${playlists.size}\")\n"
    blockB += "                    }.onFailure { Log.w(TAG, \"Playlists V3 whole fixed failed: ${it.message}\") }\n"
    blockB += "                }\n"
    blockB += "                if (playlists.isEmpty()) {\n"
    blockB += "                    runCatching {\n"
    blockB += "                        val tpV3 = YouTubeChannelParser.tabParams(homeJson, \"playlists\")\n"
    blockB += "                        if (tpV3 != null) {\n"
    blockB += "                            val jsonTpV3 = InnerTubeYouTube.browse(uc, tpV3)\n"
    blockB += "                            val wholeTpV3 = YouTubeChannelParser.parsePlaylistsWholeJson(jsonTpV3, channelName)\n"
    blockB += "                            val idsTpV3 = playlists.map { it.id }.toSet()\n"
    blockB += "                            playlists += wholeTpV3.filter { it.id !in idsTpV3 }\n"
    blockB += "                            Log.w(TAG, \"Playlists V3 whole tabParams: ${wholeTpV3.size}, total agora ${playlists.size}\")\n"
    blockB += "                        } else {\n"
    blockB += "                            Log.w(TAG, \"Playlists V3 tabParams veio null pra $channelName\")\n"
    blockB += "                        }\n"
    blockB += "                    }.onFailure { Log.w(TAG, \"Playlists V3 whole tabParams failed: ${it.message}\") }\n"
    blockB += "                }\n"
    blockB += "                if (playlists.isEmpty()) {\n"
    blockB += "                    runCatching {\n"
    blockB += "                        val homeWholeV3 = YouTubeChannelParser.parsePlaylistsWholeJson(homeJson, channelName)\n"
    blockB += "                        val idsHomeV3 = playlists.map { it.id }.toSet()\n"
    blockB += "                        playlists += homeWholeV3.filter { it.id !in idsHomeV3 }\n"
    blockB += "                        Log.w(TAG, \"Playlists V3 whole home: ${homeWholeV3.size}, total agora ${playlists.size}\")\n"
    blockB += "                    }.onFailure { Log.w(TAG, \"Playlists V3 whole home failed: ${it.message}\") }\n"
    blockB += "                }\n"
    t = t[:solB] + blockB + t[solB:]
    # Path 2: insere DEPOIS do bloco V2 Path2, ANTES de val playlistQuery
    anchorQ = "        val playlistQuery = channelName.ifBlank"
    posQ = t.find(anchorQ)
    if posQ == -1:
        fail("ancora Path2 playlistQuery nao bateu em YouTubeRepository.kt")
    solQ = t.rfind("\n", 0, posQ) + 1
    blockQ = ""
    blockQ += "        // FIX PLAYLISTS V3 Path2 - whole-JSON, ignora aba selecionada.\n"
    blockQ += "        if (channelPlaylists.isEmpty() && uc.startsWith(\"UC\") && uc.length >= 24 && channelName.isNotBlank()) {\n"
    blockQ += "            runCatching {\n"
    blockQ += "                val tabJsonV3 = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blockQ += "                val wholeV3b = YouTubeChannelParser.parsePlaylistsWholeJson(tabJsonV3, channelName)\n"
    blockQ += "                if (wholeV3b.isNotEmpty()) channelPlaylists = wholeV3b\n"
    blockQ += "                Log.w(TAG, \"Playlists V3 Path2 whole: ${wholeV3b.size}\")\n"
    blockQ += "            }.onFailure { Log.w(TAG, \"Playlists V3 Path2 whole failed: ${it.message}\") }\n"
    blockQ += "        }\n"
    t = t[:solQ] + blockQ + t[solQ:]
    open(YT_REPO, "w", encoding="utf-8").write(t)
    print("OK Repo: Path1 + Path2 whole-JSON (FIX PLAYLISTS V3)")
print("PRONTO - limpa e builda, abre o jazzghost de novo.")
