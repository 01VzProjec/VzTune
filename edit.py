# fix_v8_compile.py - corrige nome do metodo: getChannelTabsLHFactory -> getChannelTabLHFactory (singular)
# Roda na raiz: python3 fix_v8_compile.py
# Na v0.26.5 o Javadoc do StreamingService e: getChannelTabLHFactory (Tab singular) + getChannelTabExtractor
# O V8 tinha mandado com S a mais (Tabs), por isso Unresolved reference na 1661:42.
# Este script so troca o nome, nao mexe em mais nada. Aborta se nao achar.
import os, sys
def fail(m):
    print(f"ABORTOU: {m}"); sys.exit(1)
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not os.path.isfile(RP): fail(f"nao achei {RP} - roda na raiz")
t=open(RP,encoding="utf-8").read()
if "getChannelTabsLHFactory" not in t:
    if "getChannelTabLHFactory" in t:
        print("SKIP ja esta com nome certo (singular)")
    else:
        fail("nao achei getChannelTabsLHFactory nem getChannelTabLHFactory - me manda a linha 1661")
else:
    t=t.replace("getChannelTabsLHFactory", "getChannelTabLHFactory")
    print("OK: getChannelTabsLHFactory -> getChannelTabLHFactory")
if "getChannelTabsLHFactory" in t:
    fail("ainda tem getChannelTabsLHFactory")
opens=t.count("{"); closes=t.count("}")
print(f"CHAVES: abre={opens} fecha={closes} diff={opens-closes}")
if opens!=closes: fail("chaves desbalanceadas")
open(RP,"w",encoding="utf-8").write(t)
print("PRONTO - limpa e builda. Abre jazzghost, olha Playlists. Se vier 0, 3 pontinhos, Ver Log temporario, tem que aparecer [V8 ...].")
