# fix_channel_playlists_v5.py - Debug V5 NA TELA, cobre Path1 (InnerTube) + Path2 (NewPipe), debug NO COMECO
# Roda na raiz: python3 fix_channel_playlists_v5.py
# Por que V4 mostrou "tem nada": debug ia no FIM da descricao (cortado), e se o canal caia
# no Path2 (NewPipe), o debug do Path1 nem rodava. E se o browse de diagnostico falhava,
# o debug ficava vazio.
# V5: debug vai no COMECO da descricao (aparece no topo e no Sobre), cobre Path1 E Path2,
# e mesmo se falhar mostra "[DEBUG V5 ... falhou: mensagem]".
# Idempotente.
import os, sys
def fail(m):
    print(f"ABORTOU: {m}"); sys.exit(1)
ROOT=os.getcwd()
CP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
for p in [CP,RP]:
    if not os.path.isfile(p): fail(f"nao achei {p} - roda na raiz")
# 1) ChannelParser: garante debugLockups + whole
t=open(CP,encoding="utf-8").read()
if "debugLockups" not in t:
    if "parsePlaylistsWholeJson" not in t:
        if "    private fun collectPlaylists(" in t:
            t=t.replace("    private fun collectPlaylists(","    fun collectPlaylists(",1)
        a="    fun parsePlaylistsContinuation(json: JSONObject): Pair<List<Playlist>, String?> {"
        pos=t.find(a)
        if pos==-1: fail("ancora parsePlaylistsContinuation nao bateu")
        end=t.find("\n    }",pos)+len("\n    }")
        blk="\n\n    fun parsePlaylistsWholeJson(json: JSONObject, channelName: String): List<Playlist> {\n"
        blk+="        val all = collectPlaylists(json).distinctBy { it.id }\n"
        blk+="        return all.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }\n"
        blk+="    }\n"
        t=t[:end]+blk+t[end:]
    a2="    fun parsePlaylistsWholeJson"
    pos2=t.find(a2)
    if pos2==-1: fail("ancora whole nao bateu V5")
    end2=t.find("\n    }",pos2)+len("\n    }")
    blk2="\n\n    // FIX PLAYLISTS V5 - diagnostico\n"
    blk2+="    fun debugLockups(json: JSONObject): String {\n"
    blk2+="        val lockups = mutableListOf<JSONObject>()\n"
    blk2+="        collect(json, \"lockupViewModel\", lockups)\n"
    blk2+="        val renderers = mutableListOf<JSONObject>()\n"
    blk2+="        collect(json, \"playlistRenderer\", renderers)\n"
    blk2+="        collect(json, \"gridPlaylistRenderer\", renderers)\n"
    blk2+="        val samples = lockups.take(3).joinToString(\" | \") { lvm ->\n"
    blk2+="            val type = lvm.optString(\"contentType\").takeIf { it.isNotBlank() } ?: \"(sem tipo)\"\n"
    blk2+="            val rawId = lvm.optString(\"contentId\").takeIf { it.isNotBlank() } ?: \"(sem contentId)\"\n"
    blk2+="            val title = YouTubeSearchParser.lockupTitle(lvm) ?: \"(sem titulo)\"\n"
    blk2+="            val pid = YouTubeSearchParser.lockupPlaylistId(lvm) ?: \"(pid null)\"\n"
    blk2+="            \"type=$type raw=$rawId pid=$pid titulo=$title\"\n"
    blk2+="        }\n"
    blk2+="        return \"lockups=${lockups.size} renderers=${renderers.size} amostras=[$samples]\"\n"
    blk2+="    }\n"
    t=t[:end2]+blk2+t[end2:]
    open(CP,"w",encoding="utf-8").write(t)
    print("OK ChannelParser: debugLockups (V5)")
else:
    print("SKIP ChannelParser ja tem debugLockups")
# 2) Repository
t=open(RP,encoding="utf-8").read()
if "FIX PLAYLISTS V5" in t:
    print("SKIP Repo ja tem V5")
else:
    # --- Path1: se V4 existe, troca o bloco V4 por V5. Se nao, insere V5 novo.
    if "FIX PLAYLISTS V4" in t:
        # Remove bloco V4 antigo (de // FIX PLAYLISTS V4 ate if (videos.isNotEmpty...)
        start=t.find("                // FIX PLAYLISTS V4")
        if start==-1: fail("inicio V4 nao achado")
        end_anchor="                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {"
        end=t.find(end_anchor, start)
        if end==-1: fail("fim V4 nao achado")
        blk=""
        blk+="                // FIX PLAYLISTS V5 Path1 - debug NO COMECO, mesmo se falhar mostra o erro.\n"
        blk+="                var debugPlV5 = \"\"\n"
        blk+="                if (playlists.isEmpty()) {\n"
        blk+="                    runCatching {\n"
        blk+="                        val dbgJson = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
        blk+="                        val dbgWhole = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson, channelName)\n"
        blk+="                        val dbgInfo = YouTubeChannelParser.debugLockups(dbgJson)\n"
        blk+="                        debugPlV5 = \"[DEBUG V5 Path1 whole=${dbgWhole.size} $dbgInfo]\"\n"
        blk+="                        Log.w(TAG, \"Playlists V5 Path1 $debugPlV5\")\n"
        blk+="                    }.onFailure { debugPlV5 = \"[DEBUG V5 Path1 browse falhou: ${it.message}]\"; Log.w(TAG, \"Playlists V5 Path1 failed: ${it.message}\") }\n"
        blk+="                }\n"
        t=t[:start]+blk+t[end:]
        # Troca descricao Path1: de (homeParsed.description ?: "") + if (playlists.isEmpty()) debugPlV4 else ""  OU  homeParsed.description
        # Procura a linha que tem debugPlV4
        if "debugPlV4" in t:
            # acha a linha completa
            import re
            t=re.sub(r"description = \(homeParsed\.description \?: \"\"\) \+ if \(playlists\.isEmpty\(\)\) debugPlV4 else \"\",", "description = if (playlists.isEmpty() && debugPlV5.isNotBlank()) debugPlV5 + \"\\n\\n\" + (homeParsed.description ?: \"\") else homeParsed.description,", t, count=1)
            # se ainda tiver debugPlV4 em outro lugar (declaracao antiga removida ja), garante que nao sobrou
            if "debugPlV4" in t:
                print("AVISO: ainda tem debugPlV4, verifica manual")
        else:
            old="                        description = homeParsed.description,"
            if old in t:
                t=t.replace(old, "                        description = if (playlists.isEmpty() && debugPlV5.isNotBlank()) debugPlV5 + \"\\n\\n\" + (homeParsed.description ?: \"\") else homeParsed.description,", 1)
            else:
                fail("ancora descricao Path1 nao bateu V5")
        print("OK Repo Path1: V4 -> V5 (debug no comeco)")
    else:
        # Sem V4, insere V5 novo antes do return Path1
        anchorRet="                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {"
        posRet=t.find(anchorRet)
        if posRet==-1: fail("ancora return Path1 nao bateu V5")
        blk=""
        blk+="                // FIX PLAYLISTS V5 Path1 - debug NO COMECO, mesmo se falhar mostra o erro.\n"
        blk+="                var debugPlV5 = \"\"\n"
        blk+="                if (playlists.isEmpty()) {\n"
        blk+="                    runCatching {\n"
        blk+="                        val dbgJson = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
        blk+="                        val dbgWhole = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson, channelName)\n"
        blk+="                        val dbgInfo = YouTubeChannelParser.debugLockups(dbgJson)\n"
        blk+="                        debugPlV5 = \"[DEBUG V5 Path1 whole=${dbgWhole.size} $dbgInfo]\"\n"
        blk+="                        Log.w(TAG, \"Playlists V5 Path1 $debugPlV5\")\n"
        blk+="                    }.onFailure { debugPlV5 = \"[DEBUG V5 Path1 browse falhou: ${it.message}]\"; Log.w(TAG, \"Playlists V5 Path1 failed: ${it.message}\") }\n"
        blk+="                }\n"
        t=t[:posRet]+blk+t[posRet:]
        old="                        description = homeParsed.description,"
        if old in t:
            t=t.replace(old, "                        description = if (playlists.isEmpty() && debugPlV5.isNotBlank()) debugPlV5 + \"\\n\\n\" + (homeParsed.description ?: \"\") else homeParsed.description,", 1)
        else:
            fail("ancora descricao Path1 (sem V4) nao bateu")
        print("OK Repo Path1: V5 novo")
    # --- Path2: insere antes do ChannelPage com channelPlaylists
    anchorP2="        ChannelPage(\n            videos = videos,\n            shorts = shorts,\n            playlists = channelPlaylists,"
    posP2=t.find(anchorP2)
    if posP2==-1: fail("ancora return Path2 nao bateu V5")
    blk2=""
    blk2+="        // FIX PLAYLISTS V5 Path2 - debug NO COMECO, cobre quando cai no NewPipe.\n"
    blk2+="        var debugPlV5Path2 = \"\"\n"
    blk2+="        if (channelPlaylists.isEmpty() && uc.startsWith(\"UC\") && uc.length >= 24 && channelName.isNotBlank()) {\n"
    blk2+="            runCatching {\n"
    blk2+="                val dbgJson2 = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blk2+="                val dbgWhole2 = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson2, channelName)\n"
    blk2+="                val dbgInfo2 = YouTubeChannelParser.debugLockups(dbgJson2)\n"
    blk2+="                debugPlV5Path2 = \"[DEBUG V5 Path2 whole=${dbgWhole2.size} $dbgInfo2]\"\n"
    blk2+="                Log.w(TAG, \"Playlists V5 Path2 $debugPlV5Path2\")\n"
    blk2+="            }.onFailure { debugPlV5Path2 = \"[DEBUG V5 Path2 browse falhou: ${it.message}]\"; Log.w(TAG, \"Playlists V5 Path2 failed: ${it.message}\") }\n"
    blk2+="        }\n"
    t=t[:posP2]+blk2+t[posP2:]
    oldP2="            description = header?.description?.takeIf { it.isNotBlank() },"
    if oldP2 not in t: fail("ancora descricao Path2 nao bateu V5")
    t=t.replace(oldP2, "            description = if (channelPlaylists.isEmpty() && debugPlV5Path2.isNotBlank()) debugPlV5Path2 + \"\\n\\n\" + (header?.description ?: \"\") else header?.description?.takeIf { it.isNotBlank() },", 1)
    open(RP,"w",encoding="utf-8").write(t)
    print("OK Repo Path2: debug no comeco (V5)")
print("PRONTO - limpa e builda, abre jazzghost, olha o TOPO da descricao e o Sobre.")
