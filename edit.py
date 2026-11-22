# fix_playlists_canal_owner.py - Playlists do canal sumidas + dono sumido dentro da playlist
# Roda na raiz: python3 fix_playlists_canal_owner.py
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
SEARCH_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeSearchParser.kt")
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
PLAYLIST_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubePlaylistParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
YTM_PLAYLIST_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicPlaylistParser.kt")
for p in [SEARCH_PARSER, CHANNEL_PARSER, PLAYLIST_PARSER, YT_REPO, YTM_PLAYLIST_PARSER]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda na raiz do projeto")

# ---------- 1) YouTubeSearchParser ----------
t = open(SEARCH_PARSER, encoding="utf-8").read()
if "FIX PLAYLIST CANAL" not in t:
    s = t.find("    fun lockupPlaylistId")
    e = t.find("\n    fun lockupTitle", s)
    if s == -1 or e == -1: fail("ancora lockupPlaylistId/lockupTitle nao bateu em YouTubeSearchParser.kt")
    new_id = "    fun lockupPlaylistId(lvm: JSONObject): String? {\n"
    new_id += "        // FIX PLAYLIST CANAL - aba Playlists usa browseEndpoint (VLPL...), nao watchEndpoint.\n"
    new_id += "        // Busca usa watchEndpoint (PL...). Tenta os dois + contentId, normaliza VL.\n"
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
    new_id += "                    if (watch != null) {\n"
    new_id += "                        normalize(watch.optString(\"playlistId\"))?.let { found = it; return }\n"
    new_id += "                    }\n"
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
    s = t.find("    fun lockupOwner")
    e = t.find("\n    fun lockupThumbnail", s)
    if s == -1 or e == -1: fail("ancora lockupOwner/lockupThumbnail nao bateu")
    new_owner = "    fun lockupOwner(lvm: JSONObject): String? {\n"
    new_owner += "        // FIX OWNER LOCKUP - metadataRows pode estar em 3 lugares, runs pode ser commandRuns OU runs com navigationEndpoint.\n"
    new_owner += "        val meta = lvm.optJSONObject(\"metadata\")?.optJSONObject(\"lockupMetadataViewModel\") ?: return null\n"
    new_owner += "        val rowSets = mutableListOf<JSONArray>()\n"
    new_owner += "        meta.optJSONObject(\"metadata\")?.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\")?.let { rowSets += it }\n"
    new_owner += "        meta.optJSONObject(\"metadata\")?.optJSONArray(\"metadataRows\")?.let { rowSets += it }\n"
    new_owner += "        meta.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\")?.let { rowSets += it }\n"
    new_owner += "        meta.optJSONArray(\"metadataRows\")?.let { rowSets += it }\n"
    new_owner += "        fun walkRows(n: Any?) {\n"
    new_owner += "            when (n) {\n"
    new_owner += "                is JSONObject -> {\n"
    new_owner += "                    val arr = n.optJSONArray(\"metadataRows\")\n"
    new_owner += "                    if (arr != null && !rowSets.contains(arr)) rowSets += arr\n"
    new_owner += "                    val keys = n.keys()\n"
    new_owner += "                    while (keys.hasNext()) walkRows(n.opt(keys.next()))\n"
    new_owner += "                }\n"
    new_owner += "                is JSONArray -> for (i in 0 until n.length()) walkRows(n.opt(i))\n"
    new_owner += "            }\n"
    new_owner += "        }\n"
    new_owner += "        walkRows(meta)\n"
    new_owner += "        var fallback: String? = null\n"
    new_owner += "        for (rows in rowSets) {\n"
    new_owner += "            for (i in 0 until rows.length()) {\n"
    new_owner += "                val row = rows.optJSONObject(i) ?: continue\n"
    new_owner += "                val parts = row.optJSONArray(\"metadataParts\") ?: continue\n"
    new_owner += "                for (j in 0 until parts.length()) {\n"
    new_owner += "                    val part = parts.optJSONObject(j) ?: continue\n"
    new_owner += "                    val textObj = part.optJSONObject(\"text\") ?: continue\n"
    new_owner += "                    val name = textObj.optString(\"content\").takeIf { it.isNotBlank() } ?: textOf(textObj) ?: continue\n"
    new_owner += "                    val trimmed = name.trim()\n"
    new_owner += "                    if (trimmed.isBlank()) continue\n"
    new_owner += "                    if (isLockupTypeWord(trimmed)) continue\n"
    new_owner += "                    if (trimmed.contains(\"View full\", ignoreCase = true)) continue\n"
    new_owner += "                    if (trimmed.contains(\"Ver playlist\", ignoreCase = true)) continue\n"
    new_owner += "                    if (trimmed.contains(\"video\", ignoreCase = true) || trimmed.contains(\"episode\", ignoreCase = true)) continue\n"
    new_owner += "                    var isChannel = false\n"
    new_owner += "                    val cmdRuns = textObj.optJSONArray(\"commandRuns\")\n"
    new_owner += "                    if (cmdRuns != null) {\n"
    new_owner += "                        for (k in 0 until cmdRuns.length()) {\n"
    new_owner += "                            val run = cmdRuns.optJSONObject(k) ?: continue\n"
    new_owner += "                            val b1 = run.optJSONObject(\"onTap\")?.optJSONObject(\"innertubeCommand\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_owner += "                            val b2 = run.optJSONObject(\"navigationEndpoint\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_owner += "                            if (b1?.startsWith(\"UC\") == true || b2?.startsWith(\"UC\") == true) isChannel = true\n"
    new_owner += "                        }\n"
    new_owner += "                    }\n"
    new_owner += "                    val runs = textObj.optJSONArray(\"runs\")\n"
    new_owner += "                    if (runs != null) {\n"
    new_owner += "                        for (k in 0 until runs.length()) {\n"
    new_owner += "                            val run = runs.optJSONObject(k) ?: continue\n"
    new_owner += "                            val b = run.optJSONObject(\"navigationEndpoint\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_owner += "                            if (b?.startsWith(\"UC\") == true) isChannel = true\n"
    new_owner += "                        }\n"
    new_owner += "                    }\n"
    new_owner += "                    if (isChannel) return trimmed\n"
    new_owner += "                    if (fallback == null) fallback = trimmed\n"
    new_owner += "                }\n"
    new_owner += "            }\n"
    new_owner += "        }\n"
    new_owner += "        return fallback\n"
    new_owner += "    }\n"
    t = t[:s] + new_owner + t[e:]
    old_type = '            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue'
    if old_type not in t: fail("ancora parsePlaylists type check nao bateu")
    t = t.replace(old_type, '            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue')
    open(SEARCH_PARSER, "w", encoding="utf-8").write(t)
    print("OK SearchParser: lockupPlaylistId browseEndpoint + lockupOwner multi-layout + parsePlaylists aceita tipo vazio")
else:
    print("SKIP SearchParser ja tem FIX PLAYLIST CANAL")

# ---------- 2) YouTubeChannelParser ----------
t = open(CHANNEL_PARSER, encoding="utf-8").read()
if "FIX PLAYLIST CANAL" not in t:
    old = '            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue'
    if old not in t: fail("ancora collectPlaylists type check nao bateu")
    t = t.replace(old, '            if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type == "LOCKUP_CONTENT_TYPE_SHORTS" || type == "LOCKUP_CONTENT_TYPE_STREAM") continue')
    # 2 blocos em parseChannelPage: 1o com indent 16, 2o (fallback) com indent 12
    fill16 = "                videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }\n                shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }"
    fill12 = "            videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }\n            shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }"
    if t.count(fill16) != 1 or t.count(fill12) != 1: fail(f"ancora parseChannelPage owner fill: fill16={t.count(fill16)} fill12={t.count(fill12)} (esperava 1+1)")
    t = t.replace(fill16, fill16 + "\n                // FIX PLAYLIST CANAL - playlist sem dono mostra so Playlist no card; usa nome do canal.\n                playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }")
    t = t.replace(fill12, fill12 + "\n            // FIX PLAYLIST CANAL - playlist sem dono mostra so Playlist no card; usa nome do canal.\n            playlists = playlists.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }")
    t = t.replace("    fun collectPlaylists(", "    // FIX PLAYLIST CANAL - aceita contentType vazio + dono = canal\n    fun collectPlaylists(", 1)
    open(CHANNEL_PARSER, "w", encoding="utf-8").write(t)
    print("OK ChannelParser: collectPlaylists aceita tipo vazio + owner = nome do canal")
else:
    print("SKIP ChannelParser ja tem FIX PLAYLIST CANAL")

# ---------- 3) YouTubePlaylistParser ----------
t = open(PLAYLIST_PARSER, encoding="utf-8").read()
if "FIX OWNER PLAYLIST DETALHE" not in t:
    s = t.find("    private fun parseHeaderOwner(")
    e = t.find("\n    private fun parseHeaderOwnerId", s)
    if s == -1 or e == -1: fail("ancora parseHeaderOwner nao bateu")
    new_owner = "    private fun parseHeaderOwner(json: JSONObject): String? {\n"
    new_owner += "        // FIX OWNER PLAYLIST DETALHE - 2026 o header e pageHeaderRenderer, nao playlistHeaderRenderer.\n"
    new_owner += "        val headers = mutableListOf<JSONObject>()\n"
    new_owner += "        collect(json, \"playlistHeaderRenderer\", headers)\n"
    new_owner += "        collect(json, \"pageHeaderRenderer\", headers)\n"
    new_owner += "        for (h in headers) {\n"
    new_owner += "            YouTubeSearchParser.textOf(h.optJSONObject(\"ownerText\"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }\n"
    new_owner += "            YouTubeSearchParser.textOf(h.optJSONObject(\"byline\"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }\n"
    new_owner += "            YouTubeSearchParser.textOf(h.optJSONObject(\"shortBylineText\"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }\n"
    new_owner += "            YouTubeSearchParser.textOf(h.optJSONObject(\"straplineTextOne\"))?.takeIf { it.isNotBlank() && !isHeaderMetaWord(it) }?.let { return it }\n"
    new_owner += "            val rows = h.optJSONObject(\"content\")?.optJSONObject(\"pageHeaderViewModel\")?.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\")\n"
    new_owner += "                ?: h.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\")\n"
    new_owner += "                ?: h.optJSONObject(\"metadata\")?.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\")\n"
    new_owner += "            if (rows != null) {\n"
    new_owner += "                for (i in 0 until rows.length()) {\n"
    new_owner += "                    val parts = rows.optJSONObject(i)?.optJSONArray(\"metadataParts\") ?: continue\n"
    new_owner += "                    for (j in 0 until parts.length()) {\n"
    new_owner += "                        val txtObj = parts.optJSONObject(j)?.optJSONObject(\"text\") ?: continue\n"
    new_owner += "                        val txt = txtObj.optString(\"content\").takeIf { it.isNotBlank() } ?: YouTubeSearchParser.textOf(txtObj) ?: continue\n"
    new_owner += "                        val clean = txt.trim()\n"
    new_owner += "                        if (clean.isBlank() || isHeaderMetaWord(clean)) continue\n"
    new_owner += "                        return clean\n"
    new_owner += "                    }\n"
    new_owner += "                }\n"
    new_owner += "            }\n"
    new_owner += "            val sub = YouTubeSearchParser.textOf(h.optJSONObject(\"subtitle\")) ?: YouTubeSearchParser.textOf(h.optJSONObject(\"straplineTextOne\"))\n"
    new_owner += "            if (!sub.isNullOrBlank()) {\n"
    new_owner += "                val parts = sub.split(\"\\u2022\", \"\\u00b7\", \"|\").map { it.trim() }.filter { it.isNotBlank() && !isHeaderMetaWord(it) }\n"
    new_owner += "                if (parts.isNotEmpty()) return parts.first()\n"
    new_owner += "            }\n"
    new_owner += "        }\n"
    new_owner += "        return null\n"
    new_owner += "    }\n"
    new_owner += "\n"
    new_owner += "    private fun isHeaderMetaWord(text: String): Boolean {\n"
    new_owner += "        val low = text.trim().lowercase()\n"
    new_owner += "        if (low in setOf(\"playlist\", \"podcast\", \"mix\", \"\\u00e1lbum\", \"album\", \"video\", \"v\\u00eddeo\", \"v\\u00eddeos\", \"videos\")) return true\n"
    new_owner += "        if (low.contains(\"video\") || low.contains(\"v\\u00eddeo\") || low.contains(\"episode\") || low.contains(\"epis\\u00f3dio\")) return true\n"
    new_owner += "        if (low.contains(\"view\") || low.contains(\"visualiza\") || low.contains(\"exibi\")) return true\n"
    new_owner += "        if (low.contains(\"song\") || low.contains(\"faixa\") || low.contains(\"m\\u00fasica\") || low.contains(\"musica\")) return true\n"
    new_owner += "        if (low.matches(Regex(\"^\\\\d+\\\\s*(songs|faixas|videos|v\\u00eddeos|views|visualiza\\u00e7\\u00f5es).*\"))) return true\n"
    new_owner += "        return false\n"
    new_owner += "    }\n"
    t = t[:s] + new_owner + t[e:]
    s = t.find("    private fun parseHeaderOwnerId(")
    e = t.find("\n    private fun parseHeaderDescription", s)
    if s == -1 or e == -1: fail("ancora parseHeaderOwnerId nao bateu")
    new_id = "    private fun parseHeaderOwnerId(json: JSONObject): String? {\n"
    new_id += "        // FIX OWNER PLAYLIST DETALHE - UC pode estar em pageHeaderRenderer.contentMetadataViewModel.\n"
    new_id += "        val headers = mutableListOf<JSONObject>()\n"
    new_id += "        collect(json, \"playlistHeaderRenderer\", headers)\n"
    new_id += "        collect(json, \"pageHeaderRenderer\", headers)\n"
    new_id += "        for (h in headers) {\n"
    new_id += "            val runs = h.optJSONObject(\"ownerText\")?.optJSONArray(\"runs\")\n"
    new_id += "            if (runs != null) {\n"
    new_id += "                for (i in 0 until runs.length()) {\n"
    new_id += "                    val cid = runs.optJSONObject(i)?.optJSONObject(\"navigationEndpoint\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_id += "                    if (!cid.isNullOrBlank() && cid.startsWith(\"UC\")) return cid\n"
    new_id += "                }\n"
    new_id += "            }\n"
    new_id += "            val nav = h.optJSONObject(\"ownerText\")?.optJSONArray(\"runs\")?.optJSONObject(0)?.optJSONObject(\"navigationEndpoint\") ?: h.optJSONObject(\"ownerEndpoint\")\n"
    new_id += "            val cid0 = nav?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_id += "            if (!cid0.isNullOrBlank() && cid0.startsWith(\"UC\")) return cid0\n"
    new_id += "            val rows = h.optJSONObject(\"content\")?.optJSONObject(\"pageHeaderViewModel\")?.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\") ?: h.optJSONObject(\"contentMetadataViewModel\")?.optJSONArray(\"metadataRows\")\n"
    new_id += "            if (rows != null) {\n"
    new_id += "                for (i in 0 until rows.length()) {\n"
    new_id += "                    val parts = rows.optJSONObject(i)?.optJSONArray(\"metadataParts\") ?: continue\n"
    new_id += "                    for (j in 0 until parts.length()) {\n"
    new_id += "                        val txtObj = parts.optJSONObject(j)?.optJSONObject(\"text\") ?: continue\n"
    new_id += "                        val cmdRuns = txtObj.optJSONArray(\"commandRuns\")\n"
    new_id += "                        if (cmdRuns != null) {\n"
    new_id += "                            for (k in 0 until cmdRuns.length()) {\n"
    new_id += "                                val cid = cmdRuns.optJSONObject(k)?.optJSONObject(\"onTap\")?.optJSONObject(\"innertubeCommand\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\") ?: cmdRuns.optJSONObject(k)?.optJSONObject(\"navigationEndpoint\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_id += "                                if (!cid.isNullOrBlank() && cid.startsWith(\"UC\")) return cid\n"
    new_id += "                            }\n"
    new_id += "                        }\n"
    new_id += "                        val r = txtObj.optJSONArray(\"runs\")\n"
    new_id += "                        if (r != null) {\n"
    new_id += "                            for (k in 0 until r.length()) {\n"
    new_id += "                                val cid = r.optJSONObject(k)?.optJSONObject(\"navigationEndpoint\")?.optJSONObject(\"browseEndpoint\")?.optString(\"browseId\")\n"
    new_id += "                                if (!cid.isNullOrBlank() && cid.startsWith(\"UC\")) return cid\n"
    new_id += "                            }\n"
    new_id += "                        }\n"
    new_id += "                    }\n"
    new_id += "                }\n"
    new_id += "            }\n"
    new_id += "        }\n"
    new_id += "        return null\n"
    new_id += "    }\n"
    t = t[:s] + new_id + t[e:]
    open(PLAYLIST_PARSER, "w", encoding="utf-8").write(t)
    print("OK PlaylistParser: parseHeaderOwner/Id com pageHeaderRenderer + metadataRows")
else:
    print("SKIP PlaylistParser ja tem FIX OWNER PLAYLIST DETALHE")

# ---------- 4) YTMusicPlaylistParser ----------
t = open(YTM_PLAYLIST_PARSER, encoding="utf-8").read()
if "FIX OWNER PLAYLIST DETALHE" not in t:
    old_header = "        val header = YTMusicTextUtils.findFirst(json, \"musicDetailHeaderRenderer\")\n            ?: YTMusicTextUtils.findFirst(json, \"musicResponsiveHeaderRenderer\")\n            ?: YTMusicTextUtils.findFirst(json, \"musicEditablePlaylistDetailHeaderRenderer\")\n            ?: YTMusicTextUtils.findFirst(json, \"playlistHeaderRenderer\")"
    if old_header not in t: fail("ancora YTMusic header nao bateu")
    t = t.replace(old_header, old_header + "\n            // FIX OWNER PLAYLIST DETALHE - 2026 pode vir pageHeaderRenderer / pageHeaderViewModel\n            ?: YTMusicTextUtils.findFirst(json, \"pageHeaderRenderer\")\n            ?: YTMusicTextUtils.findFirst(json, \"pageHeaderViewModel\")")
    anchor = "        // ---- 4. VIEWS FALLBACK ----"
    if anchor not in t: fail("ancora YTMusic VIEWS FALLBACK nao bateu")
    insert = "        // ---- 3b. METADATA ROWS (pageHeaderRenderer) - dono em metadataParts[].text.content ----\n"
    insert += "        if (owner == null) {\n"
    insert += "            YTMusicTextUtils.walk(header) { node ->\n"
    insert += "                if (owner != null) return@walk\n"
    insert += "                val rows = node.optJSONArray(\"metadataRows\") ?: return@walk\n"
    insert += "                for (i in 0 until rows.length()) {\n"
    insert += "                    val parts = rows.optJSONObject(i)?.optJSONArray(\"metadataParts\") ?: continue\n"
    insert += "                    for (j in 0 until parts.length()) {\n"
    insert += "                        val txtObj = parts.optJSONObject(j)?.optJSONObject(\"text\") ?: continue\n"
    insert += "                        val cand = txtObj.optString(\"content\").takeIf { it.isNotBlank() } ?: YTMusicTextUtils.runsText(txtObj)\n"
    insert += "                        val cleaned = cleanOwner(cand)\n"
    insert += "                        if (cleaned != null) { owner = cleaned; break }\n"
    insert += "                    }\n"
    insert += "                }\n"
    insert += "            }\n"
    insert += "        }\n"
    insert += "\n"
    t = t.replace(anchor, insert + anchor, 1)
    open(YTM_PLAYLIST_PARSER, "w", encoding="utf-8").write(t)
    print("OK YTMusicPlaylistParser: pageHeaderRenderer + metadataRows fallback")
else:
    print("SKIP YTMusicPlaylistParser ja tem FIX OWNER PLAYLIST DETALHE")

# ---------- 5) YouTubeRepository.browseChannel fallback ----------
t = open(YT_REPO, encoding="utf-8").read()
if "FIX PLAYLIST CANAL VAZIA" not in t:
    anchor = "                // Guarda token restante pro loadMore (se bateu o teto, ainda tem mais)"
    if anchor not in t: fail("ancora browseChannel guarda token nao bateu")
    fallback = "                // FIX PLAYLIST CANAL VAZIA - se aba Playlists voltou 0, usa Home + busca NewPipe antes de devolver vazio.\n"
    fallback += "                if (playlists.isEmpty()) {\n"
    fallback += "                    if (homeParsed.playlists.isNotEmpty()) {\n"
    fallback += "                        playlists += homeParsed.playlists\n"
    fallback += "                        if (playlistsCont == null) playlistsCont = homeParsed.playlistsContinuation\n"
    fallback += "                    }\n"
    fallback += "                }\n"
    fallback += "                if (playlists.isEmpty() && channelName.isNotBlank()) {\n"
    fallback += "                    runCatching {\n"
    fallback += "                        val q = channelName.ifBlank { input.takeIf { it.startsWith(\"@\") }.orEmpty() }\n"
    fallback += "                        if (q.isNotBlank()) {\n"
    fallback += "                            val ex = ServiceList.YouTube.getSearchExtractor(q, listOf(\"playlists\"), null)\n"
    fallback += "                            ex.fetchPage()\n"
    fallback += "                            val collected = parseChannelPlaylistItems(ex.initialPage.items, channelName).toMutableList()\n"
    fallback += "                            var np: Page? = ex.initialPage.nextPage\n"
    fallback += "                            var pg = 0\n"
    fallback += "                            while (collected.size < 100 && np != null && pg < 2) {\n"
    fallback += "                                val nxt = ex.getPage(np!!)\n"
    fallback += "                                collected += parseChannelPlaylistItems(nxt.items, channelName)\n"
    fallback += "                                np = nxt.nextPage\n"
    fallback += "                                pg++\n"
    fallback += "                            }\n"
    fallback += "                            if (collected.isNotEmpty()) {\n"
    fallback += "                                playlists += collected\n"
    fallback += "                                val sess = ChannelPlaylistsSession(ex, np, channelName)\n"
    fallback += "                                channelPlaylistSessions[uc] = sess\n"
    fallback += "                                channelPlaylistSessions[input] = sess\n"
    fallback += "                            }\n"
    fallback += "                        }\n"
    fallback += "                    }.onFailure { Log.w(TAG, \"Playlists NewPipe fallback failed: ${it.message}\") }\n"
    fallback += "                }\n"
    t = t.replace(anchor, fallback + anchor, 1)
    open(YT_REPO, "w", encoding="utf-8").write(t)
    print("OK YouTubeRepository: browseChannel fallback playlists Home + NewPipe")
else:
    print("SKIP YouTubeRepository ja tem FIX PLAYLIST CANAL VAZIA")

print("PRONTO - playlists do canal + dono dentro da playlist")
