# fix_v6_debuglog_field.py - Corrige V6: faltou o campo debugLog na ChannelPage
# Roda na raiz: python3 fix_v6_debuglog_field.py
# Causa: V6 adicionou debugLog = ... nos returns mas nao salvou o campo no data class.
# Este fix adiciona: val debugLog: String? = null, na ChannelPage.
# Idempotente.
import os, sys
ROOT=os.getcwd()
RP=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
if not os.path.isfile(RP):
    print(f"ABORTOU: nao achei {RP} - roda na raiz"); sys.exit(1)
lines=open(RP,encoding="utf-8").read().splitlines()
# Acha data class ChannelPage
idx=None
for i,l in enumerate(lines):
    if "data class ChannelPage(" in l:
        idx=i; break
if idx is None:
    print("ABORTOU: data class ChannelPage nao achada"); sys.exit(1)
# Verifica se ja tem campo dentro da ChannelPage (ate fechar com ')')
end=None
for j in range(idx, min(len(lines), idx+30)):
    if lines[j].strip()==")" or lines[j].strip().startswith(")"):
        end=j; break
    if "val debugLog" in lines[j]:
        print("SKIP: ChannelPage ja tem debugLog"); sys.exit(0)
if end is None:
    print("ABORTOU: fim da ChannelPage nao achado"); sys.exit(1)
# Acha val description dentro da ChannelPage
desc_idx=None
for j in range(idx, end):
    if "val description" in lines[j]:
        desc_idx=j; break
if desc_idx is None:
    print("ABORTOU: val description nao achado na ChannelPage"); sys.exit(1)
indent=lines[desc_idx][:len(lines[desc_idx])-len(lines[desc_idx].lstrip())]
lines.insert(desc_idx+1, indent+"val debugLog: String? = null,")
open(RP,"w",encoding="utf-8").write("\n".join(lines)+"\n")
print("OK: ChannelPage.debugLog adicionado. Builda de novo.")
