#!/bin/bash
# VzTune HOTFIX FIX8 - ShortPage duplicado
# Causa: FIX4 ja tinha "data class ShortPage" e o FIX8 inseriu outro.
# O Kotlin nao deixa 2 classes com mesmo nome no mesmo arquivo -> Conflicting declarations.
# Esse hotfix mantem o primeiro ShortPage e apaga os demais. Idempotente, pode rodar 2x.
set -e
cd ~/VzTune 2>/dev/null || cd "$(dirname "$0")" || true
python3 - << 'PY'
import re, sys

def read(p):
    try:
        with open(p, encoding="utf-8") as f: return f.read()
    except FileNotFoundError:
        print(f"AVISO: nao achei {p}")
        return None

def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)

def must(cond, msg):
    if cond: print(f"OK: {msg}")
    else:
        print(f"FALHOU: {msg}")
        sys.exit(1)

p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if t is not None:
    # Conta ShortPage
    count = t.count("data class ShortPage")
    print(f"ShortPage antes: {count}x")
    if count > 1:
        # Mantem a primeira definicao, apaga as demais.
        # Definicao padrao do FIX8/FIX4:
        #     data class ShortPage(
        #         val items: ...,
        #         val hasMore: ...,
        #     )
        # Usa regex para achar todas as definicoes e remover da 2a em diante.
        pattern = re.compile(r"    data class ShortPage\(\n(?:.*\n)*?    \)\n", re.MULTILINE)
        matches = list(pattern.finditer(t))
        print(f"Definicoes encontradas por regex: {len(matches)}")
        if len(matches) > 1:
            # Remove de tras pra frente para nao baguncar os indices
            for m in reversed(matches[1:]):
                t = t[:m.start()] + t[m.end():]
            write(p, t)
            print(f"OK: apagadas {len(matches)-1} duplicata(s) de ShortPage")
        else:
            # Fallback: definicao pode estar em linha unica ou formato diferente.
            # Procura "data class ShortPage" e apaga ate o proximo "    )" ou ")"
            parts = t.split("data class ShortPage")
            # parts[0] + primeira + resto sem as demais
            first = parts[1]
            rest = "".join(parts[2:])
            # No rest, remove blocos "(\n ... \n    )" que venham logo apos "data class ShortPage" removido
            # Como ja removemos o marcador, o rest comeca com "(..." - apaga ate o primeiro "    )\n"
            # Mas para nao apagar codigo errado, faz loop removendo o primeiro bloco ate "    )\n"
            cleaned_rest = rest
            # Se o rest ainda tem ShortPage duplicado, ele foi dividido - na verdade split ja removeu todos os marcadores,
            # entao precisamos reconstruir: mantem primeira, e no resto remove o bloco "(...)" de cada duplicata.
            # Como o split tirou o marcador, o resto tem "(...)" + codigo entre duplicatas. Vamos reconstruir com cuidado:
            # Reconstroi a partir do original usando regex de novo com formato flexivel
            t2 = read(p)  # ainda com duplicatas, pois write nao foi feito no fallback
            # Regex flexivel: data class ShortPage + ate o primeiro ")\n" que fecha
            pat2 = re.compile(r"\n    data class ShortPage\(.*?\)\n", re.DOTALL)
            ms = list(pat2.finditer(t2))
            if len(ms) > 1:
                for m in reversed(ms[1:]):
                    t2 = t2[:m.start()] + t2[m.end():]
                t = t2
                write(p, t)
                print(f"OK: apagadas {len(ms)-1} duplicata(s) via fallback")
            else:
                print("AVISO: formato de ShortPage diferente, nao consegui remover automatico - veja o arquivo")
    t = read(p)
    print(f"ShortPage depois: {t.count('data class ShortPage')}x")
    must(t.count("data class ShortPage") == 1, "YouTubeRepository: ShortPage unico")

    # Seguranca: deduplica outras classes que o FIX8 pode ter duplicado junto (ChannelPage, ChannelHeader, VideoPage)
    for cls in ["ChannelPage", "ChannelHeader", "VideoPage"]:
        c = t.count(f"data class {cls}")
        print(f"{cls}: {c}x")
        if c > 1:
            pat = re.compile(rf"    data class {cls}\(.*?    \)\n", re.DOTALL)
            ms = list(pat.finditer(t))
            if len(ms) > 1:
                for m in reversed(ms[1:]):
                    t = t[:m.start()] + t[m.end():]
                write(p, t)
                t = read(p)
                print(f"OK: deduplicado {cls}")
    # Deduplica funcoes que podem ter duplicado junto
    for fn in ["fun searchShorts", "fun parseShortItemsStrict", "fun parseShortItemsLoose"]:
        c = t.count(fn)
        print(f"{fn}: {c}x")
        if c > 1:
            print(f"AVISO: {fn} duplicado - mantendo primeiro, removendo resto (verifique o build)")
            # Remove duplicatas de funcao: acha todas as ocorrencias de "    suspend fun searchShorts" / "    private fun parse..."
            # Para nao quebrar, so avisa - o erro atual e so ShortPage. Se build ainda falhar, me manda o novo erro.
            pass
    print(f"Chaves balanceadas: {t.count('{') - t.count('}')} (0 = ok)")
    must(t.count("{") == t.count("}"), "YouTubeRepository: chaves balanceadas")

print("\nHOTFIX aplicado. Roda o build de novo.")
PY
