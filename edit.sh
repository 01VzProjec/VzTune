#!/bin/bash
# VzTune FIX16b - limpa a duplicacao do YouTubeRepository.kt que quebrou o CI
# Causa: o GitHub ja tinha ChannelVideosSession / ChannelPlaylistsSession /
# loadMoreChannelVideos(channelId, pageSize) e o edit.sh anterior adicionou
# uma segunda copia com o nome ChannelListSession / loadMore(..., pages).
# Resultado: "Conflicting declarations", "One type argument expected for ListExtractor", etc.
# Este fix APAGA a copia duplicada (ChannelListSession) e mantem a original.
# Rode na raiz do projeto: bash edit_fix.sh
set -e
python3 - << 'PY'
import pathlib, re, sys

P = pathlib.Path("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not P.exists():
    P = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not P.exists():
    print("ERRO: nao achei YouTubeRepository.kt. Rode na raiz do projeto.")
    sys.exit(1)

t = P.read_text(encoding="utf-8")
orig = t
log = []

def find_balanced(s, open_idx):
    depth = 0; k = open_idx
    while k < len(s):
        if s[k] == "{": depth += 1
        elif s[k] == "}":
            depth -= 1
            if depth == 0: return k
        k += 1
    return -1

def remove_block_containing(s, start_marker, must_contain=None):
    """Remove um bloco { ... } que comeca em start_marker. Retorna (novo, removeu)."""
    i = s.find(start_marker)
    if i < 0: return s, False
    if must_contain and must_contain not in s[i:i+2000]: return s, False
    j = s.find("{", i)
    if j < 0: return s, False
    k = find_balanced(s, j)
    if k < 0: return s, False
    # volta ate o inicio da linha para nao deixar linha vazia com comentario solto
    line_start = s.rfind("\n", 0, i) + 1
    return s[:line_start] + s[k+1:].lstrip("\n"), True

# 1) Apaga a data class duplicada ChannelListSession (a nossa). A original e ChannelVideosSession / ChannelPlaylistsSession.
while "ChannelListSession" in t and "data class ChannelListSession" in t:
    t, ok = remove_block_containing(t, "data class ChannelListSession")
    if not ok: break
    log.append("removido: data class ChannelListSession duplicada")

# 2) Apaga as declaracoes duplicadas dos maps que usavam ChannelListSession
lines = t.split("\n")
new_lines = []
for ln in lines:
    if "channelVideoSessions" in ln and "ChannelListSession" in ln and "private val" in ln:
        log.append(f"removido linha duplicada: {ln.strip()[:100]}"); continue
    if "channelPlaylistSessions" in ln and "ChannelListSession" in ln and "private val" in ln:
        log.append(f"removido linha duplicada: {ln.strip()[:100]}"); continue
    new_lines.append(ln)
t = "\n".join(new_lines)

# 3) Apaga as nossas funcoes loadMore duplicadas (as que usam pages: Int = 5 / 3).
# Mantem a original (pageSize). Se so existir a nossa, mantem a primeira e apaga as outras.
def collect_fns(s, fname):
    out = []
    idx = 0
    while True:
        i = s.find(fname, idx)
        if i < 0: break
        j = s.find("{", i)
        if j < 0: break
        k = find_balanced(s, j)
        if k < 0: break
        out.append((i, j, k, s[i:k+1]))
        idx = k + 1
    return out

for fname in ["loadMoreChannelVideos", "loadMoreChannelPlaylists"]:
    fns = collect_fns(t, f"suspend fun {fname}")
    if len(fns) > 1:
        # mantem a que tem pageSize (original), apaga as com pages:
        keep_idx = None
        for n, (_, _, _, body) in enumerate(fns):
            if "pageSize" in body[:400]:
                keep_idx = n; break
        if keep_idx is None: keep_idx = 0
        # apaga de tras pra frente para nao baguncar os indices
        for n in sorted([x for x in range(len(fns)) if x != keep_idx], reverse=True):
            i, j, k, body = fns[n]
            line_start = t.rfind("\n", 0, i) + 1
            t = t[:line_start] + t[k+1:].lstrip("\n")
            log.append(f"removido: funcao duplicada {fname} #{n} (pages)")
        # recalcula se ainda houver duplicata (ex: 3 copias)
        fns2 = collect_fns(t, f"suspend fun {fname}")
        while len(fns2) > 1:
            i, j, k, body = fns2[1]
            line_start = t.rfind("\n", 0, i) + 1
            t = t[:line_start] + t[k+1:].lstrip("\n")
            log.append(f"removido: funcao duplicada extra {fname}")
            fns2 = collect_fns(t, f"suspend fun {fname}")

# 4) Qualquer uso restante de ChannelListSession vira a classe original certa.
# Videos -> ChannelVideosSession, Playlists -> ChannelPlaylistsSession
out_lines = []
for ln in t.split("\n"):
    if "ChannelListSession" in ln:
        if "Playlist" in ln or "channelPlaylistSessions" in ln:
            ln = ln.replace("ChannelListSession", "ChannelPlaylistsSession")
        else:
            ln = ln.replace("ChannelListSession", "ChannelVideosSession")
        log.append(f"renomeado ChannelListSession -> original em: {ln.strip()[:110]}")
    out_lines.append(ln)
t = "\n".join(out_lines)

# 5) Deduplica declaracoes repetidas de channelVideoSessions / channelPlaylistSessions (mantem a 1a)
for var in ["channelVideoSessions", "channelPlaylistSessions"]:
    seen = 0
    out = []
    for ln in t.split("\n"):
        if f"private val {var}" in ln or f"val {var}:" in ln and "private" in ln:
            seen += 1
            if seen > 1:
                log.append(f"removido: declaracao duplicada {var}")
                continue
        out.append(ln)
    t = "\n".join(out)

# 6) Deduplica data class ChannelVideosSession / ChannelPlaylistsSession se houver 2 (mantem a 1a)
for cls in ["ChannelVideosSession", "ChannelPlaylistsSession"]:
    while t.count(f"data class {cls}") > 1:
        # remove a SEGUNDA ocorrencia
        first = t.find(f"data class {cls}")
        second = t.find(f"data class {cls}", first + 1)
        j = t.find("{", second)
        k = find_balanced(t, j)
        line_start = t.rfind("\n", 0, second) + 1
        t = t[:line_start] + t[k+1:].lstrip("\n")
        log.append(f"removido: data class duplicada {cls}")

# 7) Deduplica parseChannelStreams / parseChannelPlaylists / resolveUc (mantem a 1a)
for fname in ["parseChannelStreams", "parseChannelPlaylists", "resolveUc"]:
    fns = collect_fns(t, f"fun {fname}")
    # tambem pega private fun
    if not fns:
        fns = collect_fns(t, f"private fun {fname}")
    while len(fns) > 1:
        i, j, k, body = fns[1]
        line_start = t.rfind("\n", 0, i) + 1
        t = t[:line_start] + t[k+1:].lstrip("\n")
        log.append(f"removido: funcao duplicada {fname}")
        fns = collect_fns(t, f"fun {fname}")

# 8) ListExtractor sem tipo generico -> ListExtractor<InfoItem>
# NewPipe: ListExtractor<T : InfoItem>. Sem <InfoItem> da "One type argument expected".
t2 = re.sub(r"ListExtractor(?![<A-Za-z])", "ListExtractor<InfoItem>", t)
if t2 != t:
    log.append("corrigido: ListExtractor -> ListExtractor<InfoItem>")
    t = t2

# 9) ChannelPage precisa ter hasMoreVideos / hasMorePlaylists (o browseChannel ja passa eles)
if "hasMoreVideos" not in t.split("data class ChannelPage")[1].split(")")[0] if "data class ChannelPage" in t else True:
    # insere antes do fechamento da ChannelPage
    m = re.search(r"(data class ChannelPage\(.*?)(\n    \))", t, flags=re.DOTALL)
    if m and "hasMoreVideos" not in m.group(1):
        t = t[:m.start(2)] + ",\n        val hasMoreVideos: Boolean = false,\n        val hasMorePlaylists: Boolean = false" + t[m.start(2):]
        log.append("adicionado: hasMoreVideos/hasMorePlaylists na ChannelPage")

# 10) Deduplica clears no resetSession
seen_clear = set()
out = []
in_reset = False
for ln in t.split("\n"):
    if "fun resetSession" in ln: in_reset = True
    if in_reset and ".clear()" in ln:
        key = ln.strip()
        if key in seen_clear:
            log.append(f"removido: clear duplicado {key}")
            continue
        seen_clear.add(key)
    if in_reset and ln.strip() == "}" and seen_clear:
        # fim aproximado do resetSession (primeiro } depois dos clears) - mantem flag ate achar funcao seguinte
        pass
    out.append(ln)
t = "\n".join(out)

if t != orig:
    P.write_text(t, encoding="utf-8")
    print("FIX16b aplicado em", P)
else:
    print("Nenhuma mudanca - arquivo ja estava sem duplicata? Verifique se voce rodou no diretorio certo.")
for l in log:
    print(" -", l)
if "ChannelListSession" in t:
    print("AVISO: ainda existe ChannelListSession no arquivo. Me manda as linhas que eu ajusto.")
else:
    print("OK: nenhum ChannelListSession restante.")
print("Agora rode: ./gradlew :data:compileDebugKotlin --no-daemon  (ou o lint) e veja se o erro sumiu.")
PY
