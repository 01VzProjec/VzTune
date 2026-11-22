# fix_playlists_v2.py - Fix REAL busca Playlists YouTube + dono do canal
# Roda na raiz: python3 fix_playlists_v2.py
# Causa raiz: YouTube mudou o JSON em 2026 - playlistRenderer sumiu, agora e lockupViewModel
# com contentType LOCKUP_CONTENT_TYPE_PLAYLIST. O parser antigo procurava so playlistRenderer,
# entao achava 0. NewPipe PR #1240 e invio confirmam o novo formato.
# 1) YouTubeSearchParser.parsePlaylists entende lockupViewModel + playlistRenderer antigo
# 2) YouTubeChannelParser.collectPlaylists idem (aba Playlists do canal)
# 3) Dono: lockup usa metadataRows -> parte cujo tap vai pra UC... (nao "Playlist" / "View full playlist")
# 4) YTMusicEnricher.fillPlaylistStats nao apaga dono de playlist YouTube (YT Music nao carrega ela)
# 5) Garante EgIQAw + validatePlaylists pulando YouTube (caso o fix v1 nao tenha rodado)
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
SEARCH_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeSearchParser.kt")
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
MR = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt")
ENRICHER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicEnricher.kt")
for p in [SEARCH_PARSER, CHANNEL_PARSER, YT_REPO, MR, ENRICHER]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda parte2_3 antes")

# ---- 1) YouTubeSearchParser.parsePlaylists - lockup ----
t = open(SEARCH_PARSER, encoding="utf-8").read()
if "LOCKUP_CONTENT_TYPE_PLAYLIST" not in t:
    start = t.find("fun parsePlaylists")
    if start == -1:
        fail("ancora parsePlaylists nao bateu")
    # fim da funcao: proxima "    fun " depois do start
    end = t.find("\n    fun ", start + 10)
    if end == -1:
        fail("fim parsePlaylists nao bateu")
    new_fn = """    fun parsePlaylists(json: JSONObject): List<SearchHit.PlaylistHit> {
        val out = mutableListOf<SearchHit.PlaylistHit>()
        // Layout antigo: playlistRenderer / gridPlaylistRenderer (ainda usado em algumas abas)
        val renderers = mutableListOf<JSONObject>()
        collect(json, "playlistRenderer", renderers)
        collect(json, "gridPlaylistRenderer", renderers)
        for (r in renderers) {
            val pid = r.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            val title = textOf(r.optJSONObject("title")) ?: continue
            val owner = textOf(r.optJSONObject("shortBylineText"))
                ?: textOf(r.optJSONObject("longBylineText"))
                ?: textOf(r.optJSONObject("ownerText")) ?: ""
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
            val countText = r.optString("videoCount").takeIf { it.isNotBlank() } ?: textOf(r.optJSONObject("videoCountText"))
            val count = countText?.filter { it.isDigit() }?.toIntOrNull()
            out += SearchHit.PlaylistHit(Playlist(id = pid, title = title, owner = owner.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE))
        }
        // Layout 2026: lockupViewModel com LOCKUP_CONTENT_TYPE_PLAYLIST / PODCAST
        // Ex: contentId=PL..., title=metadata.lockupMetadataViewModel.title.content,
        // dono=metadataRows -> parte com tap pra UC..., thumb=sources[] maior width, count=badge "11 videos"
        val lockups = mutableListOf<JSONObject>()
        collect(json, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue
            val pid = lockupPlaylistId(lvm) ?: continue
            if (!pid.startsWith("PL") && !pid.startsWith("UU") && !pid.startsWith("OL") && !pid.startsWith("RD") && !pid.startsWith("FL")) continue
            val title = lockupTitle(lvm) ?: continue
            val owner = lockupOwner(lvm)
            val thumb = lockupThumbnail(lvm)
            val count = lockupCount(lvm)
            out += SearchHit.PlaylistHit(Playlist(id = pid, title = title, owner = owner?.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE))
        }
        return out.distinctBy { it.playlist.id }
    }

    fun lockupPlaylistId(lvm: JSONObject): String? {
        val pid = lvm.optJSONObject("rendererContext")?.optJSONObject("commandContext")?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("watchEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() }
        if (pid != null) return pid
        return lvm.optString("contentId").takeIf { it.isNotBlank() }
    }

    fun lockupTitle(lvm: JSONObject): String? {
        val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val titleObj = meta.optJSONObject("title") ?: return null
        return titleObj.optString("content").takeIf { it.isNotBlank() } ?: textOf(titleObj)
    }

    fun lockupOwner(lvm: JSONObject): String? {
        val meta = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val rows = meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows") ?: return null
        var fallback: String? = null
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val parts = row.optJSONArray("metadataParts") ?: continue
            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j) ?: continue
                val textObj = part.optJSONObject("text") ?: continue
                val name = textObj.optString("content").takeIf { it.isNotBlank() } ?: textOf(textObj) ?: continue
                val trimmed = name.trim()
                if (trimmed.isBlank()) continue
                if (isLockupTypeWord(trimmed)) continue
                if (trimmed.contains("View full", ignoreCase = true)) continue
                if (trimmed.contains("Ver playlist", ignoreCase = true)) continue
                if (trimmed.contains("video", ignoreCase = true) || trimmed.contains("episode", ignoreCase = true)) continue
                val runs = textObj.optJSONArray("commandRuns")
                var isChannel = false
                if (runs != null) {
                    for (k in 0 until runs.length()) {
                        val browseId = runs.optJSONObject(k)?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: continue
                        if (browseId.startsWith("UC")) isChannel = true
                    }
                }
                if (isChannel) return trimmed
                if (fallback == null && (runs == null || runs.length() == 0)) fallback = trimmed
            }
        }
        return fallback
    }

    fun lockupThumbnail(lvm: JSONObject): String? {
        var bestUrl: String? = null
        var bestWidth = -1
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val arr = node.optJSONArray("sources")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            val url = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                            val w = s.optInt("width", 0)
                            if (w >= bestWidth) { bestWidth = w; bestUrl = url }
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(lvm.optJSONObject("contentImage"))
        if (bestUrl != null) return bestUrl
        return thumbnailOf(lvm.optJSONObject("contentImage")) ?: thumbnailOf(lvm.optJSONObject("thumbnail"))
    }

    fun lockupCount(lvm: JSONObject): Int? {
        var found: Int? = null
        fun walk(node: Any?) {
            if (found != null) return
            when (node) {
                is JSONObject -> {
                    val badge = node.optJSONObject("thumbnailBadgeViewModel")
                    if (badge != null) {
                        val txt = badge.optString("text").takeIf { it.isNotBlank() } ?: textOf(badge.optJSONObject("text"))
                        if (txt != null && (txt.contains("video", ignoreCase = true) || txt.contains("episode", ignoreCase = true) || txt.contains("vídeo", ignoreCase = true))) {
                            found = txt.filter { it.isDigit() }.toIntOrNull()
                            if (found != null) return
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(lvm.optJSONObject("contentImage"))
        if (found != null) return found
        walk(lvm.optJSONObject("metadata"))
        return found
    }

    private fun isLockupTypeWord(text: String): Boolean {
        val low = text.trim().lowercase()
        return low in setOf("playlist", "podcast", "mix", "video", "videos", "episode", "episodes", "vídeo", "vídeos", "álbum", "album", "música", "music")
    }
"""
    t = t[:start] + new_fn + t[end:]
    open(SEARCH_PARSER, "w", encoding="utf-8").write(t)
    print("1) YouTubeSearchParser.parsePlaylists agora entende lockupViewModel + dono via UC")
else:
    print("1) YouTubeSearchParser ja entende lockup - pulando")

# ---- 2) YouTubeChannelParser.collectPlaylists - lockup ----
c = open(CHANNEL_PARSER, encoding="utf-8").read()
if "LOCKUP_CONTENT_TYPE_PLAYLIST" not in c:
    start = c.find("private fun collectPlaylists")
    if start == -1:
        fail("ancora collectPlaylists canal nao bateu")
    end = c.find("\n    private fun collect", start + 10)
    if end == -1:
        end = c.find("\n}", start)
    if end == -1:
        fail("fim collectPlaylists canal nao bateu")
    new_fn2 = """    private fun collectPlaylists(node: Any?): List<Playlist> {
        val out = mutableListOf<Playlist>()
        val renderers = mutableListOf<JSONObject>()
        collect(node, "playlistRenderer", renderers)
        collect(node, "gridPlaylistRenderer", renderers)
        for (r in renderers) {
            val pid = r.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val owner = YouTubeSearchParser.textOf(r.optJSONObject("shortBylineText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("longBylineText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("ownerText")) ?: ""
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            val countText = r.optString("videoCount").takeIf { it.isNotBlank() }
                ?: YouTubeSearchParser.textOf(r.optJSONObject("videoCountText"))
            val count = countText?.filter { it.isDigit() }?.toIntOrNull()
            out += Playlist(id = pid, title = title, owner = owner.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE)
        }
        // Layout 2026: lockupViewModel (mesma logica da busca)
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue
            val pid = YouTubeSearchParser.lockupPlaylistId(lvm) ?: continue
            if (!pid.startsWith("PL") && !pid.startsWith("UU") && !pid.startsWith("OL") && !pid.startsWith("RD") && !pid.startsWith("FL")) continue
            val title = YouTubeSearchParser.lockupTitle(lvm) ?: continue
            val owner = YouTubeSearchParser.lockupOwner(lvm)
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)
            val count = YouTubeSearchParser.lockupCount(lvm)
            out += Playlist(id = pid, title = title, owner = owner?.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE)
        }
        return out
    }
"""
    c = c[:start] + new_fn2 + c[end:]
    open(CHANNEL_PARSER, "w", encoding="utf-8").write(c)
    print("2) YouTubeChannelParser.collectPlaylists agora entende lockup")
else:
    print("2) ChannelParser ja entende lockup - pulando")

# ---- 3) YTMusicEnricher.fillPlaylistStats nao apaga dono YouTube ----
e = open(ENRICHER, encoding="utf-8").read()
if "PART FIX PLAYLIST OWNER YOUTUBE" not in e:
    # Dentro do map, antes de hasViews/hasOwner, pula YouTube
    anchor = "val p = hit.playlist"
    if anchor not in e:
        fail("ancora fillPlaylistStats nao bateu")
    repl = "val p = hit.playlist\n                        // PART FIX PLAYLIST OWNER YOUTUBE - playlist YouTube ja vem com dono da busca; YT Music nao carrega ela, entao nao tenta enriquecer (apagava o dono)\n                        if (p.source == Source.YOUTUBE) return@withPermit hit"
    e = e.replace(anchor, repl, 1)
    open(ENRICHER, "w", encoding="utf-8").write(e)
    print("3) fillPlaylistStats nao mexe mais em playlist YouTube")
else:
    print("3) fillPlaylistStats ja pula YouTube - pulando")

# ---- 4) Garante EgIQAw em searchPlaylists ----
yt = open(YT_REPO, encoding="utf-8").read()
if "EgIQAw" not in yt:
    start = yt.find("suspend fun searchPlaylists")
    if start == -1:
        fail("ancora searchPlaylists repo nao bateu")
    nxt = yt.find("\n    suspend fun ", start + 10)
    if nxt == -1:
        nxt = len(yt)
    block = yt[start:nxt]
    if "InnerTubeYouTube.search(query)" not in block:
        fail("ancora InnerTube searchPlaylists nao bateu")
    block2 = block.replace("val json = InnerTubeYouTube.search(query)", 'val json = InnerTubeYouTube.search(query, "EgIQAw%3D%3D")', 1)
    anchor2 = "if (items.isNotEmpty() || cont != null) return@withContext items"
    if anchor2 in block2:
        fallback = anchor2 + "\n                // Fallback sem filtro\n                runCatching {\n                    val json2 = InnerTubeYouTube.search(query)\n                    val items2 = YouTubeSearchParser.parsePlaylists(json2).take(pageSize)\n                    if (items2.isNotEmpty()) return@withContext items2\n                }"
        block2 = block2.replace(anchor2, fallback, 1)
    yt = yt[:start] + block2 + yt[nxt:]
    open(YT_REPO, "w", encoding="utf-8").write(yt)
    print("4) searchPlaylists agora usa filtro EgIQAw + fallback")
else:
    print("4) searchPlaylists ja tem EgIQAw - pulando")

# ---- 5) Garante validatePlaylists pulando YouTube ----
mr = open(MR, encoding="utf-8").read()
if "PART FIX PLAYLIST VALIDATE" not in mr:
    start = mr.find("private suspend fun validatePlaylists")
    if start == -1:
        fail("ancora validatePlaylists nao bateu")
    old2 = "val count = hit.playlist.trackCount"
    if old2 not in mr[start:start+2000]:
        fail("ancora validatePlaylists count nao bateu")
    new2 = "// PART FIX PLAYLIST VALIDATE - Playlist YouTube nao passa pelo YT Music\n                    if (hit.playlist.source == Source.YOUTUBE) return@withPermit hit\n                    val count = hit.playlist.trackCount"
    mr = mr[:start] + mr[start:].replace(old2, new2, 1)
    open(MR, "w", encoding="utf-8").write(mr)
    print("5) validatePlaylists nao descarta mais playlist YouTube")
else:
    print("5) validatePlaylists ja corrigido - pulando")

print("Fix playlists v2 OK.")
