# fix_playlists_v7.py - Playlists via aba direta com UC resolvido + param certo + log SEMPRE
# Roda na raiz: python3 fix_playlists_v7.py
# O que estava errado:
#  - Busca por nome do canal ("Jazzghost" + playlists) NAO devolve as playlists do canal,
#    porque os titulos das playlists dele sao "Minecraft", "GTA", etc, nao tem "Jazzghost".
#    Por isso Path1 e Path2 voltavam 0.
#  - Param fixo estava com prefixo Eglw (funciona mas nao e o oficial). Troca pra EgZw oficial.
#  - Log so rodava se playlists vazio E UC valido, entao quando nao rodava vinha "Sem log".
# V7:
#  - Path2: ANTES da busca, tenta aba direta: browse(UC resolvido, EgZw playlists), parse whole JSON.
#    Se vier algo, usa como channelPlaylists (fonte direta, nao busca).
#  - Log V7 SEMPRE seta debugLog, mesmo se playlists ja veio algo, pra dialogo nunca ficar vazio.
# Idempotente.
import os, sys
def fail(m):
    print(f"ABORTOU: {m}"); sys.exit(1)
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
CP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt")
for p in [RP,CP]:
    if not os.path.isfile(p): fail(f"nao achei {p} - roda na raiz")
# 1) Param certo em todo lugar
for path in [RP,CP]:
    t=open(path,encoding="utf-8").read()
    if "EglwbGF5bGlzdHPyBgQKAkIA" in t:
        t=t.replace("EglwbGF5bGlzdHPyBgQKAkIA","EgZwbGF5bGlzdHPyBgQKAkIA")
        open(path,"w",encoding="utf-8").write(t)
        print(f"OK param EgZw em {os.path.basename(path)}")
    else:
        print(f"SKIP param ja ta EgZw em {os.path.basename(path)}")
# 2) Repo V7 Path2 + log sempre
t=open(RP,encoding="utf-8").read()
if "FIX PLAYLISTS V7" in t:
    print("SKIP Repo ja tem V7")
else:
    # Garante debugLog no ChannelPage (se V6 fix nao rodou)
    if "val debugLog" not in t:
        # Acha ChannelPage e insere apos description
        lines=t.splitlines()
        idx=None
        for i,l in enumerate(lines):
            if "data class ChannelPage(" in l:
                idx=i; break
        if idx is not None:
            for j in range(idx, min(len(lines), idx+30)):
                if "val description" in lines[j]:
                    indent=lines[j][:len(lines[j])-len(lines[j].lstrip())]
                    lines.insert(j+1, indent+"val debugLog: String? = null,")
                    break
            t="\n".join(lines)+"\n"
            print("OK Repo: ChannelPage.debugLog (V7)")
    # Path2: insere ANTES de val playlistQuery (antes da busca)
    anchor="        val playlistQuery = channelName.ifBlank"
    pos=t.find(anchor)
    if pos==-1: fail("ancora playlistQuery nao bateu V7")
    blk=""
    blk+="        // FIX PLAYLISTS V7 - aba direta com UC resolvido, ANTES da busca por nome.\n"
    blk+="        var debugV7Path2 = \"\"\n"
    blk+="        if (uc.startsWith(\"UC\") && uc.length >= 24 && channelName.isNotBlank()) {\n"
    blk+="            runCatching {\n"
    blk+="                val tabJsonV7 = InnerTubeYouTube.browse(uc, \"EgZwbGF5bGlzdHPyBgQKAkIA\")\n"
    blk+="                val wholeV7 = YouTubeChannelParser.parsePlaylistsWholeJson(tabJsonV7, channelName)\n"
    blk+="                val infoV7 = YouTubeChannelParser.debugLockups(tabJsonV7)\n"
    blk+="                debugV7Path2 = \"[V7 tab UC whole=\" + wholeV7.size + \" \" + infoV7 + \"]\"\n"
    blk+="                if (wholeV7.isNotEmpty()) channelPlaylists = wholeV7\n"
    blk+="                Log.w(TAG, \"Playlists V7 tab \" + debugV7Path2)\n"
    blk+="            }.onFailure { debugV7Path2 = \"[V7 tab falhou: \" + it.message + \"]\"; Log.w(TAG, \"Playlists V7 tab failed: \" + (it.message ?: \"\")) }\n"
    blk+="        } else {\n"
    blk+="            debugV7Path2 = \"[V7 tab pulado: uc=\" + uc + \" nome=\" + channelName + \"]\"\n"
    blk+="        }\n"
    t=t[:pos]+blk+t[pos:]
    # Path2 return: debugLog passa a incluir V7 SEMPRE (sem duplicar)
    _has_old_debug = ("debugLog = debugPlV5Path2" in t) or ("debugLog = debugPlV6Path2" in t)
    if _has_old_debug:
        _lines = t.splitlines()
        _out = []
        for _line in _lines:
            if "debugLog = debugPlV5Path2" in _line or "debugLog = debugPlV6Path2" in _line:
                _indent = _line[:len(_line)-len(_line.lstrip())]
                _old_var = "debugPlV5Path2" if "debugPlV5Path2" in _line else "debugPlV6Path2"
                _out.append(_indent + "debugLog = (debugV7Path2 + \" \" + " + _old_var + ".orEmpty()).trim().takeIf { it.isNotBlank() },")
            else:
                _out.append(_line)
        t = "\n".join(_out) + "\n"
        changed = True
    else:
        _old2 = "            description = header?.description?.takeIf { it.isNotBlank() },"
        if _old2 not in t:
            fail("nao achei return Path2 pra por debugLog V7")
        t = t.replace(_old2, _old2 + "\n            debugLog = debugV7Path2.takeIf { it.isNotBlank() },", 1)
        changed = True
    if not changed:
        fail("nao achei return Path2 pra por debugLog V7")
    # Path1: log SEMPRE - troca if (playlists.isEmpty()) do debug V5/V6 por if (true) so pro debug
    # Nao mexe na logica de playlists, so garante que debug roda. Procura debugPlV5 / debugPlV6 e tira a condicao isEmpty do debug.
    # Forma segura: se tem "if (playlists.isEmpty()) {" seguido de debug, troca pra if (true) { // V7 log sempre
    # Como ha varios if (playlists.isEmpty()) na logica de playlists, so troca o que esta perto de debugPlV5/V6
    # Faz por linhas: se linha e if (playlists.isEmpty()) e nas proximas 3 linhas tem debugPlV5/V6, troca.
    lines=t.splitlines()
    out2=[]
    for i,line in enumerate(lines):
        if line.strip()=="if (playlists.isEmpty()) {" and i+3 < len(lines) and ("debugPlV5" in "\n".join(lines[i:i+4]) or "debugPlV6" in "\n".join(lines[i:i+4]) or "dbgJson" in "\n".join(lines[i:i+4])):
            out2.append(line.replace("if (playlists.isEmpty())", "if (true) // V7 log sempre"))
        else:
            out2.append(line)
    t="\n".join(out2)+"\n"
    open(RP,"w",encoding="utf-8").write(t)
    print("OK Repo: V7 Path2 aba direta + log sempre")
print("PRONTO - limpa e builda, abre jazzghost, olha Playlists. Se ainda vier 0, 3 pontinhos, Ver Log temporario, print.")
