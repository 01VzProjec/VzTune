# fix_channel_playlists_v4.py - Playlists do canal sumidas (V4 - debug NA TELA, sem logcat)
# Roda na raiz: python3 fix_channel_playlists_v4.py
# Por que V3 nao resolveu: ainda nao sabemos o que o YouTube esta devolvendo.
# V4 nao tenta adivinhar de novo. Ele mostra o diagnostico DENTRO do VzTune,
# no Sobre do canal, pra tu so tirar print. Ex: [DEBUG playlists V4: whole=0 lockups=12 ...]
# Se lockups>0 e whole=0, o parser esta filtrando tudo. Se lockups=0, o YouTube nao mandou nada.
# Idempotente: se ja tem FIX PLAYLISTS V4, pula.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT = os.getcwd()
CHANNEL_PARSER = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
YT_REPO = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
for p in [CHANNEL_PARSER, YT_REPO]:
    if not os.path.isfile(p):
        fail(f"nao achei {p} - roda na raiz do projeto")

# ---------- 1) ChannelParser: debugLockups ----------
t = open(CHANNEL_PARSER, encoding="utf-8").read()
if "FIX PLAYLISTS V4" not in t:
    # garante que V3 existe (collect publico + whole). Se nao tiver, aplica o minimo.
    if "parsePlaylistsWholeJson" not in t:
        if "    private fun collectPlaylists(" in t:
            t = t.replace("    private fun collectPlaylists(", "    fun collectPlaylists(", 1)
        anchor = "    fun parsePlaylistsContinuation(json: JSONObject): Pair<List<Playlist>, String?> {"
        pos = t.find(anchor)
        if pos == -1:
            fail("ancora parsePlaylistsContinuation nao bateu")
        end = t.find("\n    }", pos)
        if end == -1:
            fail("fim parsePlaylistsContinuation nao achado")
        end = end + len("\n    }")
        block = ""
        block += "\n\n    fun parsePlaylistsWholeJson(json: JSONObject, channelName: String): List<Playlist> {\n"
        block += "        val all = collectPlaylists(json).distinctBy { it.id }\n"
        block += "        return all.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }\n"
        block += "    }\n"
        t = t[:end] + block + t[end:]
    # debugLockups depois de parsePlaylistsWholeJson
    anchor2 = "    fun parsePlaylistsWholeJson"
    pos2 = t.find(anchor2)
    if pos2 == -1:
        fail("ancora parsePlaylistsWholeJson nao bateu para V4")
    end2 = t.find("\n    }", pos2)
    if end2 == -1:
        fail("fim parsePlaylistsWholeJson nao achado")
    end2 = end2 + len("\n    }")
    block2 = ""
    block2 += "\n\n    // FIX PLAYLISTS V4 - diagnostico: quantos lockups vieram e como sao os 3 primeiros.\n"
    block2 += "    fun debugLockups(json: JSONObject): String {\n"
    block2 += "        val lockups = mutableListOf<JSONObject>()\n"
    block2 += "        collect(json, \"lockupViewModel\", lockups)\n"
    block2 += "        val renderers = mutableListOf<JSONObject>()\n"
    block2 += "        collect(json, \"playlistRenderer\", renderers)\n"
    block2 += "        collect(json, \"gridPlaylistRenderer\", renderers)\n"
    block2 += "        val samples = lockups.take(3).joinToString(\" | \") { lvm ->\n"
    block2 += "            val type = lvm.optString(\"contentType\").takeIf { it.isNotBlank() } ?: \"(sem tipo)\"\n"
    block2 += "            val rawId = lvm.optString(\"contentId\").takeIf { it.isNotBlank() } ?: \"(sem contentId)\"\n"
    block2 += "            val title = YouTubeSearchParser.lockupTitle(lvm) ?: \"(sem titulo)\"\n"
    block2 += "            val pid = YouTubeSearchParser.lockupPlaylistId(lvm) ?: \"(pid null)\"\n"
    block2 += "            \"type=$type raw=$rawId pid=$pid titulo=$title\"\n"
    block2 += "        }\n"
    block2 += "        return \"lockups=${lockups.size} renderers=${renderers.size} amostras=[$samples]\"\n"
    block2 += "    }\n"
    t = t[:end2] + block2 + t[end2:]
    open(CHANNEL_PARSER, "w", encoding="utf-8").write(t)
    print("OK ChannelParser: debugLockups (FIX PLAYLISTS V4)")
else:
    print("SKIP ChannelParser ja tem FIX PLAYLISTS V4")

# ---------- 2) Repository Path 1: debug na descricao ----------
t = open(YT_REPO, encoding="utf-8").read()
if "FIX PLAYLISTS V4" in t:
    print("SKIP Repo ja tem FIX PLAYLISTS V4")
else:
    # Acha o return do Path1: if (videos.isNotEmpty() ... ) { return@withContext ChannelPage(
    anchorRet = "                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {"
    posRet = t.find(anchorRet)
    if posRet == -1:
        fail("ancora return Path1 nao bateu em YouTubeRepository.kt")
    block = ""
    block += "                // FIX PLAYLISTS V4 - diagnostico NA TELA (Sobre), sem precisar de logcat.\n"
    block += "                var debugPlV4 = \"\"\n"
    block += "                if (playlists.isEmpty()) {\n"
    block += "                    runCatching {\n"
    block += "                        val dbgJson = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    block += "                        val dbgWhole = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson, channelName)\n"
    block += "                        val dbgInfo = YouTubeChannelParser.debugLockups(dbgJson)\n"
    block += "                        debugPlV4 = \"\\n\\n[DEBUG playlists V4: whole=${dbgWhole.size} $dbgInfo]\"\n"
    block += "                        Log.w(TAG, \"Playlists V4 debug $debugPlV4\")\n"
    block += "                    }.onFailure { Log.w(TAG, \"Playlists V4 debug failed: ${it.message}\") }\n"
    block += "                }\n"
    t = t[:posRet] + block + t[posRet:]
    # Agora troca a descricao do return Path1 para incluir o debug quando playlists vazio.
    # O return Path1 usa description = homeParsed.description, o Path2 usa header?.description, entao e unico.
    old_desc = "                        description = homeParsed.description,"
    if old_desc not in t:
        fail("ancora description Path1 nao bateu")
    new_desc = "                        description = (homeParsed.description ?: \"\") + if (playlists.isEmpty()) debugPlV4 else \"\","
    t = t.replace(old_desc, new_desc, 1)
    open(YT_REPO, "w", encoding="utf-8").write(t)
    print("OK Repo: debug no Sobre quando playlists vazio (FIX PLAYLISTS V4)")
print("PRONTO - limpa e builda, abre jazzghost, desce ate Sobre e tira print.")
