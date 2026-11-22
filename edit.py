# fix_channel_playlists_v2.py - Playlists do canal sumidas (V2 - param fixo + Home + busca InnerTube)
# Roda na raiz: python3 fix_channel_playlists_v2.py
# O que faz (so playlists do canal, nao mexe em dono dentro da playlist):
#  1) Se SearchParser ainda nao tem FIX PLAYLIST CANAL, aplica o minimo (lockup VL + tipo vazio).
#  2) Se ChannelParser ainda nao tem FIX PLAYLIST CANAL, aplica tipo vazio + owner = canal.
#  3) Repository Path 1 (UC): depois do tabParams, tenta aba com param FIXO do NewPipe
#     EglwbGF5bGlzdHPyBgQKAkIA (nao depende de tabParams da Home), depois Home scan,
#     depois busca InnerTube por playlists filtrada pelo dono. Tudo com dedup + owner = canal.
#  4) Repository Path 2 (@handle / fallback NewPipe): tenta aba fixa InnerTube ANTES da
#     busca NewPipe, e a busca so roda se a aba vier vazia.
# Idempotente: se ja tem FIX PLAYLISTS V2, pula. Se ja tem FIX PLAYLIST CANAL, pula essa parte.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
SEARCH_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeSearchParser.kt")
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
for p in [SEARCH_PARSER, CHANNEL_PARSER, YT_REPO]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda na raiz do projeto")

# ---------- 1) SearchParser (so se ainda nao tem o fix anterior) ----------
t = open(SEARCH_PARSER, encoding="utf-8").read()
if "FIX PLAYLIST CANAL" not in t:
    old_type = '            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue'
    if old_type in t:
        t = t.replace(old_type, '            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue // FIX PLAYLIST CANAL')
    # lockupPlaylistId: troca a funcao inteira por versao que normaliza VL + tenta browseEndpoint
    s = t.find("    fun lockupPlaylistId")
    e = t.find("\n    fun lockupTitle", s)
    if s != -1 and e != -1:
        new_id = "    fun lockupPlaylistId(lvm: JSONObject): String? {\n"
        new_id += "        // FIX PLAYLIST CANAL - aba usa browseEndpoint (VLPL...), busca usa watchEndpoint (PL...).\n"
        new_id += "        fun normalize(raw: String?): String? {\n"
        new_id += "            var v = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null\n"
        new_id += "            if (v.startsWith(\"VL\")) v = v.removePrefix(\"VL\")\n"
        new_id += "            if (v.startsWith(\"PL\") || v.startsWith(\"UU\") || v.startsWith(\"OL\") || v.startsWith(\"RD\") || v.startsWith(\"FL\")) return v\n"
        new_id += "            return null\n"
        new_id += "        }\n"
        new_id += "        val cmd = lvm.optJSONObject(\"rendererContext\")?.optJSONObject(\"commandContext\")?.optJSONObject(\"onTap\")?.optJSONObject(\"innertubeCommand\")\n"
        new_id += "        normalize(cmd?.optJSONObject(\"watchEndpoint\")?.optString(\"playlistId\"))?.let { return it }\n"
        new_id += "        normalize(cmd?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\"))?.let { return it }\n"
        new_id += "        normalize(cmd?.optJSONObject(\"browseEndpoint\")?.optString(\"playlistId\"))?.let { return it }\n"
        new_id += "        var found: String? = null\n"
        new_id += "        fun walk(n: Any?) {\n"
        new_id += "            if (found != null) return\n"
        new_id += "            when (n) {\n"
        new_id += "                is JSONObject -> {\n"
        new_id += "                    val browse = n.optJSONObject(\"browseEndpoint\")\n"
        new_id += "                    if (browse != null) {\n"
        new_id += "                        normalize(browse.optString(\"browseId\"))?.let { found = it; return }\n"
        new_id += "                        normalize(browse.optString(\"playlistId\"))?.let { found = it; return }\n"
        new_id += "                    }\n"
        new_id += "                    val watch = n.optJSONObject(\"watchEndpoint\")\n"
        new_id += "                    if (watch != null) { normalize(watch.optString(\"playlistId\"))?.let { found = it; return } }\n"
        new_id += "                    val keys = n.keys()\n"
        new_id += "                    while (keys.hasNext()) walk(n.opt(keys.next()))\n"
        new_id += "                }\n"
        new_id += "                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))\n"
        new_id += "            }\n"
        new_id += "        }\n"
        new_id += "        walk(lvm)\n"
        new_id += "        if (found != null) return found\n"
        new_id += "        return normalize(lvm.optString(\"contentId\"))\n"
        new_id += "    }\n"
        t = t[:s] + new_id + t[e:]
    open(SEARCH_PARSER, "w", encoding="utf-8").write(t)
    print("OK SearchParser: lockup VL + tipo vazio (FIX PLAYLIST CANAL)")
else:
    print("SKIP SearchParser ja tem FIX PLAYLIST CANAL")

# ---------- 2) ChannelParser (so se ainda nao tem o fix anterior) ----------
t = open(CHANNEL_PARSER, encoding="utf-8").read()
if "FIX PLAYLIST CANAL" not in t:
    old = '            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue'
    if old in t:
        t = t.replace(old, '            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue // FIX PLAYLIST CANAL')
    fill16 = "                videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }\n                shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }"
    fill12 = "            videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }\n            shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }"
    if t.count(fill16) == 1 and t.count(fill12) == 1:
        t = t.replace(fill16, fill16 + "\n                // FIX PLAYLIST CANAL - playlist sem dono usa nome do canal.\n                playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }")
        t = t.replace(fill12, fill12 + "\n            // FIX PLAYLIST CANAL - playlist sem dono usa nome do canal.\n            playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }")
    open(CHANNEL_PARSER, "w", encoding="utf-8").write(t)
    print("OK ChannelParser: tipo vazio + owner = canal (FIX PLAYLIST CANAL)")
else:
    print("SKIP ChannelParser ja tem FIX PLAYLIST CANAL")

# ---------- 3) Repository ----------
t = open(YT_REPO, encoding="utf-8").read()
if "FIX PLAYLISTS V2" in t:
    print("SKIP Repo ja tem FIX PLAYLISTS V2")
else:
    # --- Path 1: insere ANTES de ytChannelPlaylistContinuations[uc] = playlistsCont ---
    anchorB = "ytChannelPlaylistContinuations[uc] = playlistsCont"
    posB = t.find(anchorB)
    if posB == -1:
        fail("ancora Path1 ytChannelPlaylistContinuations nao bateu em YouTubeRepository.kt")
    solB = t.rfind("\n", 0, posB) + 1
    blockB = ""
    blockB += "                // FIX PLAYLISTS V2 - param FIXO NewPipe, nao depende de tabParams da Home.\n"
    blockB += "                // tabParams pode vir null / errado e ai a aba nunca era nem buscada.\n"
    blockB += "                runCatching {\n"
    blockB += "                    var jsonV2 = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blockB += "                    var parsedV2 = YouTubeChannelParser.parseChannelPage(jsonV2)\n"
    blockB += "                    var fixedAll = parsedV2.playlists.distinctBy { it.id }\n"
    blockB += "                    fixedAll = fixedAll.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }\n"
    blockB += "                    val idsV2 = playlists.map { it.id }.toSet()\n"
    blockB += "                    playlists += fixedAll.filter { it.id !in idsV2 }\n"
    blockB += "                    var fixedCont: String? = parsedV2.playlistsContinuation ?: InnerTubeYouTube.findContinuationToken(jsonV2)\n"
    blockB += "                    var seenV2 = mutableSetOf<String>()\n"
    blockB += "                    var pagesV2 = 1\n"
    blockB += "                    while (playlists.size < 200 && fixedCont != null && pagesV2 < 5) {\n"
    blockB += "                        val tokenV2 = fixedCont ?: break\n"
    blockB += "                        if (!seenV2.add(tokenV2)) break\n"
    blockB += "                        val cjV2 = InnerTubeYouTube.browseContinuation(tokenV2)\n"
    blockB += "                        val (moreV2, nextV2) = YouTubeChannelParser.parsePlaylistsContinuation(cjV2)\n"
    blockB += "                        if (moreV2.isEmpty() && nextV2 == null) { fixedCont = null; break }\n"
    blockB += "                        val moreFixed = moreV2.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }\n"
    blockB += "                        val idsV2b = playlists.map { it.id }.toSet()\n"
    blockB += "                        playlists += moreFixed.filter { it.id !in idsV2b }\n"
    blockB += "                        fixedCont = nextV2\n"
    blockB += "                        pagesV2++\n"
    blockB += "                    }\n"
    blockB += "                    if (fixedCont != null) playlistsCont = fixedCont\n"
    blockB += "                }.onFailure { Log.w(TAG, \"Playlists fixed-param failed: ${it.message}\") }\n"
    blockB += "                // FIX PLAYLISTS V2 - Home scan: Home as vezes tem prateleira Playlists com lockup.\n"
    blockB += "                if (playlists.isEmpty()) {\n"
    blockB += "                    runCatching {\n"
    blockB += "                        val homeAll = YouTubeChannelParser.parseChannelPage(homeJson).playlists\n"
    blockB += "                        val idsHome = playlists.map { it.id }.toSet()\n"
    blockB += "                        playlists += homeAll.filter { it.id !in idsHome }.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }\n"
    blockB += "                    }.onFailure { Log.w(TAG, \"Playlists home scan failed: ${it.message}\") }\n"
    blockB += "                }\n"
    blockB += "                // FIX PLAYLISTS V2 - busca InnerTube por playlists (usa nosso parser lockup, nao o NewPipe).\n"
    blockB += "                if (playlists.isEmpty() && channelName.isNotBlank()) {\n"
    blockB += "                    runCatching {\n"
    blockB += "                        val searchJson = InnerTubeYouTube.search(channelName, \"EgIQAw%3D%3D\")\n"
    blockB += "                        val hits = YouTubeSearchParser.parsePlaylists(searchJson)\n"
    blockB += "                        val filtered = hits.map { it.playlist }.filter { pl ->\n"
    blockB += "                            val owner = pl.owner\n"
    blockB += "                            owner.isNullOrBlank() || isSameCreator(owner, channelName)\n"
    blockB += "                        }.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }\n"
    blockB += "                        val idsSearch = playlists.map { it.id }.toSet()\n"
    blockB += "                        playlists += filtered.filter { it.id !in idsSearch }\n"
    blockB += "                    }.onFailure { Log.w(TAG, \"Playlists InnerTube search failed: ${it.message}\") }\n"
    blockB += "                }\n"
    t = t[:solB] + blockB + t[solB:]
    # --- Path 2: insere DEPOIS de var channelPlaylists = emptyList() ---
    anchorC = "var channelPlaylists: List<Playlist> = emptyList()"
    posC = t.find(anchorC)
    if posC == -1:
        fail("ancora Path2 channelPlaylists nao bateu em YouTubeRepository.kt")
    eolC = t.find("\n", posC)
    if eolC == -1:
        fail("fim da linha Path2 nao achado")
    blockC = ""
    blockC += "        // FIX PLAYLISTS V2 - Path 2: tenta aba fixa InnerTube ANTES da busca NewPipe.\n"
    blockC += "        if (uc.startsWith(\"UC\") && uc.length >= 24 && channelName.isNotBlank()) {\n"
    blockC += "            runCatching {\n"
    blockC += "                val tabJson = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blockC += "                val parsedTab = YouTubeChannelParser.parseChannelPage(tabJson)\n"
    blockC += "                var tabAll = parsedTab.playlists.distinctBy { it.id }\n"
    blockC += "                tabAll = tabAll.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }\n"
    blockC += "                if (tabAll.isNotEmpty()) channelPlaylists = tabAll\n"
    blockC += "                val tabCont = parsedTab.playlistsContinuation ?: InnerTubeYouTube.findContinuationToken(tabJson)\n"
    blockC += "                if (tabCont != null) {\n"
    blockC += "                    ytChannelPlaylistContinuations[uc] = tabCont\n"
    blockC += "                    ytChannelPlaylistContinuations[input] = tabCont\n"
    blockC += "                    ytChannelPlaylistContinuations[channelUrl] = tabCont\n"
    blockC += "                }\n"
    blockC += "            }.onFailure { Log.w(TAG, \"Playlists Path2 fixed-param failed: ${it.message}\") }\n"
    blockC += "        }\n"
    t = t[:eolC+1] + blockC + t[eolC+1:]
    # --- Path 2: busca NewPipe so roda se a aba veio vazia ---
    old_if = "        if (playlistQuery.isNotBlank()) {"
    if old_if in t:
        t = t.replace(old_if, "        if (channelPlaylists.isEmpty() && playlistQuery.isNotBlank()) {", 1)
    else:
        print("AVISO: nao achei if (playlistQuery.isNotBlank()) - busca Path2 pode rodar mesmo com aba cheia")
    open(YT_REPO, "w", encoding="utf-8").write(t)
    print("OK Repo: Path1 param fixo + Home + busca InnerTube, Path2 aba antes da busca (FIX PLAYLISTS V2)")
print("PRONTO - limpa e builda. Se ainda vier 0, me manda o logcat com 'Playlists fixed-param' / 'Playlists Path2'.")
