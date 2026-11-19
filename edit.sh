#!/bin/bash
# VzTune HOTFIX FIX8 - ArtistViewModel Smart Cast
# Erro: "Smart cast to 'String' is impossible, because 'channelName' is a public API property declared in different module."
# Causa: channel.channelName vem do modulo :data (ChannelPage). O Kotlin NAO deixa fazer:
#   if (channel.channelName != null) { channel.channelName.lowercase() }
# porque o getter pode mudar entre a checagem e o uso. Tem que copiar pra variavel local primeiro.
# Fix: val chName = channel?.channelName  e usar chName em vez de channel.channelName.
# Idempotente: pode rodar 2x.
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

p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
t = read(p)
if t is not None:
    print(f"channel.channelName.lowercase antes: {t.count('channel.channelName.lowercase')}")
    print(f"val chName antes: {t.count('val chName')}")
    # 1) Cria variavel local antes do if, se ainda nao existe
    if "val chName" not in t:
        old_if = "            if (channel != null && channel.channelName != null) {"
        if old_if in t:
            t = t.replace(
                old_if,
                "            val chName = channel?.channelName\n            if (channel != null && chName != null) {",
                1
            )
            print("OK: criado val chName antes do if")
        else:
            print("AVISO: if do canal com formato diferente - tentando regex")
            t2 = re.sub(
                r"if \(channel != null && channel\.channelName != null\)",
                "val chName = channel?.channelName\n            if (channel != null && chName != null)",
                t, count=1
            )
            if t2 != t:
                t = t2
                print("OK: criado val chName via regex")
    # 2) Usa chName no lugar de channel.channelName onde era smart cast
    # 2a) lowercase - o erro do build
    if "channel.channelName.lowercase()" in t:
        t = t.replace("channel.channelName.lowercase()", "chName.lowercase()")
        print("OK: channel.channelName.lowercase() -> chName.lowercase()")
    # 2b) checagem dentro do if de videos
    if "channel.shorts.isNotEmpty() || channel.channelName != null" in t:
        t = t.replace(
            "channel.shorts.isNotEmpty() || channel.channelName != null",
            "channel.shorts.isNotEmpty() || chName != null"
        )
        print("OK: checagem shorts -> chName")
    # 2c) name = channel.channelName ?: it.name  -> usa local (seguro e evita novo smart cast)
    # So troca quando chName ja existe no escopo (ele existe, foi criado antes do primeiro if e fica visivel nos ifs seguintes)
    if "name = channel.channelName ?: it.name" in t:
        t = t.replace("name = channel.channelName ?: it.name", "name = chName ?: it.name")
        print("OK: name = channel.channelName -> chName")
    # 2d) qualquer outro channel.channelName que sobrou e ainda usa smart cast: troca por chName?. ou chName
    # Nao troca "channel?.channelName" (a definicao do chName)
    # Se sobrar "channel.channelName" solto, troca por "(chName ?: \"\")" para nao quebrar
    remaining = [m.start() for m in re.finditer(r"channel\.channelName", t)]
    # Remove a definicao "channel?.channelName" da contagem
    remaining = [s for s in remaining if "channel?.channelName" not in t[max(0,s-2):s+30]]
    if remaining:
        print(f"AVISO: ainda restam {len(remaining)} usos de channel.channelName - trocando por chName com Elvis")
        t = re.sub(r"channel\.channelName", "chName", t)
        # Corrige a definicao que virou "chName = chName?.chName" se aconteceu
        if "chName = chName?.chName" in t:
            t = t.replace("chName = chName?.chName", "chName = channel?.channelName")
        # Corrige "val chName = chName?.channelName" se aconteceu
        if "val chName = chName" in t:
            # volta para definicao correta
            t = t.replace("val chName = chName?.channelName", "val chName = channel?.channelName")
            t = t.replace("val chName = chName", "val chName = channel?.channelName")
        print("OK: restantes trocados para chName")
    write(p, t)
    t = read(p)
    print(f"channel.channelName.lowercase depois: {t.count('channel.channelName.lowercase')}")
    print(f"val chName depois: {t.count('val chName')}")
    print(f"channel.channelName restante (sem ?. ): {t.count('channel.channelName')}")
    must("channel.channelName.lowercase()" not in t, "ArtistViewModel: sem smart cast em channelName.lowercase")
    must(t.count("val chName") == 1, "ArtistViewModel: chName unico")
    must(t.count("{") == t.count("}"), "ArtistViewModel: chaves balanceadas")
    print(f"Chaves: {t.count('{')} / {t.count('}')}")

print("\nHOTFIX Smart Cast aplicado. Roda o build de novo.")
PY
