# fix_log_temporario_v6.py - Menu 3 pontinhos -> "Ver Log temporario" (dialogo, sem logcat, sem PC)
# Roda na raiz: python3 fix_log_temporario_v6.py
# O que faz:
#  - ChannelPage ganha campo debugLog (separado da descricao, nao corta, nao some)
#  - Path1 e Path2 passam o debug pra debugLog (reaproveita V5 se ja tiver, senao cria V6)
#  - ArtistUiState ganha debugLog, ViewModel repassa
#  - ChannelHeader (3 pontinhos) ganha item "Ver Log temporario" que abre dialogo com o log
# Idempotente.
import os, sys
def fail(m):
    print(f"ABORTOU: {m}"); sys.exit(1)
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
CP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
UI=os.path.join(ROOT,"app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt")
VM=os.path.join(ROOT,"app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt")
SC=os.path.join(ROOT,"app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")
for p in [RP,CP,UI,VM,SC]:
    if not os.path.isfile(p): fail(f"nao achei {p} - roda na raiz")
# 1) ChannelParser garante debugLockups + whole
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
    end2=t.find("\n    }",pos2)+len("\n    }")
    blk2="\n\n    fun debugLockups(json: JSONObject): String {\n"
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
    print("OK ChannelParser: debugLockups")
else:
    print("SKIP ChannelParser ja tem debugLockups")
# 2) ChannelPage ganha debugLog
t=open(RP,encoding="utf-8").read()
if "val debugLog" not in t:
    # Acha data class ChannelPage e dentro dela description + subscriberCount
    idx=t.find("data class ChannelPage(")
    if idx==-1: fail("data class ChannelPage nao achada")
    seg=t[idx:idx+1200]
    old="        val description: String? = null,\n        val subscriberCount"
    if old not in seg: fail("ancora ChannelPage description/subscriber nao bateu")
    t=t[:idx]+seg.replace(old, "        val description: String? = null,\n        val debugLog: String? = null,\n        val subscriberCount", 1)+t[idx+1200:]
    print("OK Repo: ChannelPage.debugLog")
else:
    print("SKIP Repo ja tem debugLog")
# 3) Repository Path1 + Path2 -> debugLog (reaproveita V5 se tiver)
t=open(RP,encoding="utf-8").read()
# Path1: se ja tem V5 (debugPlV5), troca descricao hack por descricao limpa + debugLog
if "debugLog = debugPlV6" in t or "debugLog = debugPlV5" in t:
    print("SKIP Repo Path1 ja tem debugLog")
elif "debugPlV5" in t and "description = if (playlists.isEmpty()" in t:
    lines=t.splitlines()
    out=[]
    changed=False
    for line in lines:
        if "description = if (playlists.isEmpty()" in line and "debugPlV5" in line:
            indent=line[:len(line)-len(line.lstrip())]
            out.append(indent+"description = homeParsed.description,")
            out.append(indent+"debugLog = debugPlV5.takeIf { it.isNotBlank() },")
            changed=True
        else:
            out.append(line)
    if not changed: fail("nao consegui trocar descricao Path1 V5")
    t="\n".join(out)+"\n"
    print("OK Repo Path1: descricao limpa + debugLog (reaproveitou V5)")
elif "debugPlV5" in t:
    # V5 existe mas descricao ja ta limpa? So garante debugLog no return Path1
    if "debugLog = debugPlV5" not in t:
        old1="                        description = homeParsed.description,"
        if old1 not in t: fail("ancora descricao Path1 limpa nao bateu")
        t=t.replace(old1, old1+"\n                        debugLog = debugPlV5.takeIf { it.isNotBlank() },", 1)
        print("OK Repo Path1: adicionou debugLog")
    else:
        print("SKIP Repo Path1 ja tem debugLog")
else:
    # Sem V5: cria bloco V6 Path1 antes do return Path1
    anchorRet="                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {"
    posRet=t.find(anchorRet)
    if posRet==-1: fail("ancora return Path1 nao bateu V6")
    blk="                // FIX LOG TEMPORARIO V6 Path1\n"
    blk+="                var debugPlV6 = \"\"\n"
    blk+="                if (playlists.isEmpty()) {\n"
    blk+="                    runCatching {\n"
    blk+="                        val dbgJson = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blk+="                        val dbgWhole = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson, channelName)\n"
    blk+="                        val dbgInfo = YouTubeChannelParser.debugLockups(dbgJson)\n"
    blk+="                        debugPlV6 = \"[DEBUG V6 Path1 whole=\" + dbgWhole.size + \" \" + dbgInfo + \"]\"\n"
    blk+="                    }.onFailure { debugPlV6 = \"[DEBUG V6 Path1 browse falhou: \" + it.message + \"]\" }\n"
    blk+="                }\n"
    t=t[:posRet]+blk+t[posRet:]
    old1="                        description = homeParsed.description,"
    if old1 not in t: fail("ancora descricao Path1 V6 novo nao bateu")
    t=t.replace(old1, old1+"\n                        debugLog = debugPlV6.takeIf { it.isNotBlank() },", 1)
    print("OK Repo Path1: V6 novo + debugLog")
# Path2
if "debugLog = debugPlV6Path2" in t or "debugLog = debugPlV5Path2" in t:
    print("SKIP Repo Path2 ja tem debugLog")
elif "debugPlV5Path2" in t and "description = if (channelPlaylists.isEmpty()" in t:
    lines=t.splitlines()
    out=[]
    changed=False
    for line in lines:
        if "description = if (channelPlaylists.isEmpty()" in line and "debugPlV5Path2" in line:
            indent=line[:len(line)-len(line.lstrip())]
            out.append(indent+"description = header?.description?.takeIf { it.isNotBlank() },")
            out.append(indent+"debugLog = debugPlV5Path2.takeIf { it.isNotBlank() },")
            changed=True
        else:
            out.append(line)
    if not changed: fail("nao consegui trocar descricao Path2 V5")
    t="\n".join(out)+"\n"
    print("OK Repo Path2: descricao limpa + debugLog (reaproveitou V5)")
elif "debugPlV5Path2" in t:
    if "debugLog = debugPlV5Path2" not in t:
        old2="            description = header?.description?.takeIf { it.isNotBlank() },"
        if old2 not in t: fail("ancora descricao Path2 limpa nao bateu")
        t=t.replace(old2, old2+"\n            debugLog = debugPlV5Path2.takeIf { it.isNotBlank() },", 1)
        print("OK Repo Path2: adicionou debugLog")
    else:
        print("SKIP Repo Path2 ja tem debugLog")
else:
    anchorP2="        ChannelPage(\n            videos = videos,\n            shorts = shorts,\n            playlists = channelPlaylists,"
    posP2=t.find(anchorP2)
    if posP2==-1: fail("ancora return Path2 nao bateu V6")
    blk2="        // FIX LOG TEMPORARIO V6 Path2\n"
    blk2+="        var debugPlV6Path2 = \"\"\n"
    blk2+="        if (channelPlaylists.isEmpty() && uc.startsWith(\"UC\") && uc.length >= 24 && channelName.isNotBlank()) {\n"
    blk2+="            runCatching {\n"
    blk2+="                val dbgJson2 = InnerTubeYouTube.browse(uc, \"EglwbGF5bGlzdHPyBgQKAkIA\")\n"
    blk2+="                val dbgWhole2 = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson2, channelName)\n"
    blk2+="                val dbgInfo2 = YouTubeChannelParser.debugLockups(dbgJson2)\n"
    blk2+="                debugPlV6Path2 = \"[DEBUG V6 Path2 whole=\" + dbgWhole2.size + \" \" + dbgInfo2 + \"]\"\n"
    blk2+="            }.onFailure { debugPlV6Path2 = \"[DEBUG V6 Path2 browse falhou: \" + it.message + \"]\" }\n"
    blk2+="        }\n"
    t=t[:posP2]+blk2+t[posP2:]
    old2="            description = header?.description?.takeIf { it.isNotBlank() },"
    if old2 not in t: fail("ancora descricao Path2 V6 novo nao bateu")
    t=t.replace(old2, old2+"\n            debugLog = debugPlV6Path2.takeIf { it.isNotBlank() },", 1)
    print("OK Repo Path2: V6 novo + debugLog")
open(RP,"w",encoding="utf-8").write(t)
# 4) ArtistUiState ganha debugLog
t=open(UI,encoding="utf-8").read()
if "val debugLog" not in t:
    old="    val description: String? = null,"
    if old not in t: fail("ancora UiState description nao bateu")
    t=t.replace(old, old+"\n    val debugLog: String? = null,", 1)
    open(UI,"w",encoding="utf-8").write(t)
    print("OK UiState: debugLog")
else:
    print("SKIP UiState ja tem debugLog")
# 5) ViewModel repassa
t=open(VM,encoding="utf-8").read()
if "debugLog = channel.debugLog" not in t:
    old="                            description = channel.description ?: it.description,"
    if old not in t: fail("ancora ViewModel description nao bateu")
    t=t.replace(old, old+"\n                            debugLog = channel.debugLog,", 1)
    open(VM,"w",encoding="utf-8").write(t)
    print("OK ViewModel: repassa debugLog")
else:
    print("SKIP ViewModel ja tem debugLog")
# 6) ArtistScreen: imports + menu + dialogo
t=open(SC,encoding="utf-8").read()
if "Ver Log tempor" not in t:
    if "import androidx.compose.material3.AlertDialog" not in t:
        t=t.replace("import androidx.compose.material3.DropdownMenu\n", "import androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.TextButton\n", 1)
    if "import androidx.compose.foundation.verticalScroll" not in t:
        t=t.replace("import androidx.compose.foundation.layout.width\n", "import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n", 1)
    old_state="    var channelMenuOpen by remember { mutableStateOf(false) }"
    if old_state not in t: fail("ancora channelMenuOpen nao bateu")
    t=t.replace(old_state, old_state+"\n    var showTempLog by remember { mutableStateOf(false) }", 1)
    old_share="                        DropdownMenuItem(\n                            text = { Text(text = \"Compartilhar\") },\n                            onClick = { channelMenuOpen = false; onShareClick() },\n                        )"
    if old_share not in t: fail("ancora menu Compartilhar nao bateu")
    new_share=old_share+"\n                        DropdownMenuItem(\n                            text = { Text(text = \"Ver Log temporário\") },\n                            onClick = { channelMenuOpen = false; showTempLog = true },\n                        )"
    t=t.replace(old_share, new_share, 1)
    # Dialogo depois do DropdownMenu (dentro do Box)
    old_close="                    }\n                }\n            }\n        }\n        // Lupa:"
    if old_close not in t: fail("ancora fechamento DropdownMenu nao bateu")
    dlg="                    }\n                    if (showTempLog) {\n"
    dlg+="                        AlertDialog(\n"
    dlg+="                            onDismissRequest = { showTempLog = false },\n"
    dlg+="                            title = { Text(text = \"Log temporário\") },\n"
    dlg+="                            text = { Text(text = state.debugLog ?: \"Sem log ainda. Reabre o canal.\", modifier = Modifier.verticalScroll(rememberScrollState())) },\n"
    dlg+="                            confirmButton = { TextButton(onClick = { showTempLog = false }) { Text(text = \"Fechar\") } },\n"
    dlg+="                        )\n"
    dlg+="                    }\n"
    dlg+="                }\n            }\n        }\n        // Lupa:"
    t=t.replace(old_close, dlg, 1)
    open(SC,"w",encoding="utf-8").write(t)
    print("OK Screen: menu Ver Log temporario + dialogo")
else:
    print("SKIP Screen ja tem Ver Log temporario")
print("PRONTO - limpa e builda, abre jazzghost, 3 pontinhos, Ver Log temporario, tira print.")
