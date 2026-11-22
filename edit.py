# fix_v7_compile.py - desfaz o V7 que quebrou o build e reaplica de forma minima e segura
# Roda na raiz: python3 fix_v7_compile.py
# O que o V7 fez que pode ter quebrado:
#  - inseriu bloco de 14 linhas antes de val playlistQuery
#  - trocou a linha debugLog do Path2 pra referenciar debugV7Path2
#  - trocou if (playlists.isEmpty()) do debug pra if (true)
# Este script desfaz os 3 e reaplica o fix de playlists de outro jeito:
#  - helper fetchPlaylistsTabV7() fora do browseChannel (nao mexe nas chaves dele)
#  - 1 linha so dentro do Path2: if (channelPlaylists.isEmpty()) channelPlaylists = fetchPlaylistsTabV7(uc, channelName)
# Sem .bak, sem arquivo temp. Aborta sem escrever se a ancora nao bater.
import os, sys
def fail(m):
    print(f"ABORTOU: {m}"); sys.exit(1)
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not os.path.isfile(RP): fail(f"nao achei {RP} - roda na raiz")
t=open(RP,encoding="utf-8").read()
orig=t
# 1) Remove TODOS os blocos V7 (14 linhas a partir do comentario)
# Bloco V7 tem marcadores: comeca em "// FIX PLAYLISTS V7" e termina no "}" do else, antes de val playlistQuery
# Remocao por linhas: se linha tem FIX PLAYLISTS V7, pula ela + proximas 13 linhas se elas forem do bloco
lines=t.splitlines()
out=[]
i=0
removed=0
while i < len(lines):
    if "FIX PLAYLISTS V7" in lines[i]:
        # Pula o bloco: comentario + 13 linhas (var, if, runCatching, 5 dentro, onFailure, } else {, debug else, })
        # Confere se as proximas linhas batem com o bloco V7, se nao, pula so o comentario pra nao apagar codigo alheio
        chunk="\n".join(lines[i:i+14])
        if "debugV7Path2" in chunk and "val playlistQuery" not in chunk:
            i+=14
            removed+=1
            continue
        else:
            # Bloco com formato diferente: pula ate a linha que tem "val playlistQuery" (exclusivo), max 30 linhas
            j=i
            while j < len(lines) and j < i+30 and "val playlistQuery" not in lines[j]:
                j+=1
            if j < len(lines) and "val playlistQuery" in lines[j]:
                i=j
                removed+=1
                continue
            else:
                out.append(lines[i]); i+=1; continue
    else:
        out.append(lines[i]); i+=1
t="\n".join(out)+"\n"
print(f"OK: removidos {removed} bloco(s) V7")
# 2) Reverte debugLog que referencia debugV7Path2
lines=t.splitlines()
out=[]
reverted_log=0
for line in lines:
    if "debugV7Path2" in line and "debugLog =" in line:
        indent=line[:len(line)-len(line.lstrip())]
        if "debugPlV5Path2" in line:
            out.append(indent+"debugLog = debugPlV5Path2.takeIf { it.isNotBlank() },")
            reverted_log+=1
        elif "debugPlV6Path2" in line:
            out.append(indent+"debugLog = debugPlV6Path2.takeIf { it.isNotBlank() },")
            reverted_log+=1
        else:
            # Era debugLog = debugV7Path2... sozinho (caso limpo): remove a linha, o ChannelPage ja tem debugLog default null
            reverted_log+=1
            continue
    elif "debugV7Path2" in line:
        # Qualquer outra linha solta com debugV7Path2 (nao deveria sobrar): remove
        reverted_log+=1
        continue
    else:
        out.append(line)
t="\n".join(out)+"\n"
print(f"OK: revertidas {reverted_log} linha(s) debugLog/debugV7")
# 3) Reverte if (true) // V7 log sempre -> if (playlists.isEmpty())
if "V7 log sempre" in t:
    t=t.replace("if (true) // V7 log sempre", "if (playlists.isEmpty())")
    print("OK: revertido if (true) V7")
# 4) Se ainda tem debugV7Path2, aborta (nao deixa referencia quebrada)
if "debugV7Path2" in t:
    fail("ainda tem debugV7Path2 no arquivo, nao vou escrever pra nao quebrar de novo. Me manda as linhas com debugV7Path2")
# 5) Reaplica de forma minima: helper fora do browseChannel + 1 linha no Path2
if "fetchPlaylistsTabV7" in t:
    print("SKIP helper ja existe")
else:
    anchor="    private fun parseChannelStreams"
    if anchor not in t: fail("ancora parseChannelStreams nao bateu")
    helper=""
    helper+="    // FIX V7-COMPILE - helper fora do browseChannel, nao mexe nas chaves dele.\n"
    helper+="    private suspend fun fetchPlaylistsTabV7(uc: String, channelName: String): List<Playlist> {\n"
    helper+="        if (!uc.startsWith(\"UC\") || uc.length < 24 || channelName.isBlank()) return emptyList()\n"
    helper+="        return runCatching {\n"
    helper+="            val tabJson = InnerTubeYouTube.browse(uc, \"EgZwbGF5bGlzdHPyBgQKAkIA\")\n"
    helper+="            val whole = YouTubeChannelParser.parsePlaylistsWholeJson(tabJson, channelName)\n"
    helper+="            Log.w(TAG, \"Playlists V7 helper whole=\" + whole.size)\n"
    helper+="            whole\n"
    helper+="        }.getOrDefault(emptyList())\n"
    helper+="    }\n\n"
    t=t.replace(anchor, helper+anchor, 1)
    print("OK: helper fetchPlaylistsTabV7 adicionado")
if "fetchPlaylistsTabV7(uc, channelName)" in t:
    print("SKIP chamada Path2 ja existe")
else:
    anchor2="        val playlistQuery = channelName.ifBlank"
    if anchor2 not in t: fail("ancora playlistQuery nao bateu (V7-compile)")
    call="        if (channelPlaylists.isEmpty()) channelPlaylists = fetchPlaylistsTabV7(uc, channelName)\n"
    t=t.replace(anchor2, call+anchor2, 1)
    print("OK: chamada de 1 linha no Path2")
# 6) Checagem basica de chaves (ignora strings simples): so avisa, nao aborta por causa de { em texto
opens=t.count("{"); closes=t.count("}")
print(f"CHAVES: abre={opens} fecha={closes} diff={opens-closes}")
if opens!=closes:
    fail(f"chaves desbalanceadas diff={opens-closes} - nao escrevi, me manda o log de novo")
if t==orig:
    print("NADA MUDOU")
else:
    open(RP,"w",encoding="utf-8").write(t)
    print("PRONTO - limpa e builda. Abre jazzghost, olha Playlists.")
