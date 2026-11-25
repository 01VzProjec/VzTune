#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
edit.py - VzTune: views compactas nos cards + busca 100% Brasil + preload de Shorts.
Uso (na raiz do projeto):
    python3 edit.py --dry   # so confere, NAO grava
    python3 edit.py         # aplica (sem .bak)
Tudo-ou-nada + idempotente.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = "app/src/main/kotlin/com/vztechcore/vztune"
DATA = "data/src/main/kotlin/com/vztechcore/vztune/data"

class PatchError(Exception): pass

def _flex_regex(anchor):
    lines = anchor.strip("\n").split("\n")
    if not lines or lines[0].strip() == "":
        raise PatchError("ancora invalida")
    parts = [r"(?m)^[ \t]*" + re.escape(lines[0].strip())]
    for ln in lines[1:]:
        t = ln.strip()
        parts.append(r"\n[ \t]*" + (re.escape(t) if t else ""))
    return re.compile("".join(parts))

def _find_all(s, a): return list(_flex_regex(a).finditer(s))

class Replace:
    def __init__(self, old, new, marker=None):
        self.old, self.new = old, new
        self.marker = marker if marker is not None else new.strip("\n")
    def apply(self, s):
        if self.marker and self.marker in s: return s, "ja aplicado"
        ms = _find_all(s, self.old)
        if len(ms) != 1:
            raise PatchError("trecho %dx (esperado 1): %r" % (len(ms), self.old.strip()[:80]))
        return _flex_regex(self.old).sub(lambda m: self.new, s), "ok"

class InsertBefore:
    def __init__(self, anchor, text, marker=None):
        self.anchor, self.text = anchor, text
        self.marker = marker if marker is not None else text.strip("\n")
    def apply(self, s):
        if self.marker and self.marker in s: return s, "ja aplicado"
        ms = _find_all(s, self.anchor)
        if len(ms) != 1:
            raise PatchError("ancora %dx (esperado 1): %r" % (len(ms), self.anchor.strip()[:80]))
        i = ms[0].start()
        return s[:i] + self.text.rstrip("\n") + "\n" + s[i:], "ok"

PATCHES = {}

# ---------- 1) Views compactas: {icone} X mil / X mi / X bi ----------
_COMPACT_AND_SWAP = r'''/** Views compactas pros cards: "534", "1,2 mil", "1,2 mi", "1,2 bi" (sem a palavra visualizacoes). */
fun formatViewsCompact(count: Long?): String? {
    if (count == null || count <= 0) return null
    return when {
        count < 1_000 -> "$count"
        count < 1_000_000 -> formatOneDecimal(count / 1_000.0) + " mil"
        count < 1_000_000_000 -> formatOneDecimal(count / 1_000_000.0) + " mi"
        else -> formatOneDecimal(count / 1_000_000_000.0) + " bi"
    }
}

fun videoViewsDate(track: Track): String? {
    val views = formatViewsCompact(track.viewCount)'''
PATCHES[f"{APP}/ui/components/cards/ChannelCards.kt"] = [
    Replace(
        '''fun videoViewsDate(track: Track): String? {
val views = formatViewCount(track.viewCount)''',
        _COMPACT_AND_SWAP,
        marker="fun formatViewsCompact(",
    ),
]

# ---------- 2) Busca 100% Brasil: Accept-Language pt-BR nos 2 clients ----------
PATCHES[f"{DATA}/innertube/InnerTubeYouTube.kt"] = [
    Replace(
        '.addHeader("Content-Type", "application/json")',
        '.addHeader("Content-Type", "application/json")\n.addHeader("Accept-Language", "pt-BR,pt;q=0.9")',
        marker='"Accept-Language"',
    ),
]
PATCHES[f"{DATA}/innertube/Innertube.kt"] = [
    Replace(
        '.addHeader("Content-Type", "application/json")',
        '.addHeader("Content-Type", "application/json")\n.addHeader("Accept-Language", "pt-BR,pt;q=0.9")',
        marker='"Accept-Language"',
    ),
]
PATCHES["gradle.properties"] = [
    Replace("systemProp.user.country=US", "systemProp.user.country=BR", marker="systemProp.user.country=BR"),
    Replace("systemProp.user.language=en", "systemProp.user.language=pt", marker="systemProp.user.language=pt"),
]

# ---------- 3) Shorts: pre-carrega link do proximo/anterior ----------
_PREFETCH = r'''    private val prefetchJobs = mutableMapOf<String, Job>()

    /** Assim que assenta num Short, ja resolve o link do proximo, do anterior e do +2.
     *  Quando o usuario desliza, o cache ja tem tudo -> toca sem delay de busca. */
    private fun prefetchAround(index: Int) {
        for (i in listOf(index + 1, index - 1, index + 2)) {
            val t = items.getOrNull(i) ?: continue
            if (infoCache.containsKey(t.id)) continue
            if (prefetchJobs[t.id]?.isActive == true) continue
            prefetchJobs[t.id] = viewModelScope.launch {
                val info = musicRepository.getShortPlaybackInfo(t.id)
                if (info != null) infoCache[t.id] = info
            }
        }
    }
'''
PATCHES[f"{APP}/ui/screens/shorts/ShortsViewModel.kt"] = [
    InsertBefore("    private fun keyOf(index: Int): String =", _PREFETCH, marker="private fun prefetchAround("),
    Replace(
        '''loadJob?.cancel()
if (cached != null) {''',
        '''loadJob?.cancel()
prefetchAround(index)
if (cached != null) {''',
        marker="prefetchAround(index)",
    ),
]

def main():
    dry = "--dry" in sys.argv
    results, errors, staged = [], [], {}
    for rel, ops in PATCHES.items():
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            errors.append("[%s] arquivo nao existe" % rel); continue
        with open(path, encoding="utf-8") as f: s = f.read()
        for n, op in enumerate(ops, 1):
            try:
                s, st = op.apply(s)
                results.append("  %-12s %s #%d" % (st, rel.split("/")[-1], n))
            except PatchError as e:
                errors.append("[%s] op #%d: %s" % (rel, n, e))
        staged[rel] = s
    print("\n".join(results))
    if errors:
        print("\nERROS (nada foi gravado):")
        print("\n".join("  - " + e for e in errors))
        sys.exit(1)
    if dry:
        print("\nDRY-RUN ok. Rode sem --dry para aplicar.")
        return
    changed = 0
    for rel, s in staged.items():
        path = os.path.join(ROOT, rel)
        with open(path, encoding="utf-8") as f: old = f.read()
        if old != s:
            with open(path, "w", encoding="utf-8", newline="\n") as f: f.write(s)
            print("alterado:", rel); changed += 1
    print("\nPronto (%d arquivos). Compile o projeto." % changed)

if __name__ == "__main__":
    main()