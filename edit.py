#!/usr/bin/env python3
"""Safely add the missing channelHandle field to VzTune's ChannelPage."""
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd().resolve()
TARGET = ROOT / "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"

OLD = '''        val channelName: String?,
        val avatarUrl: String?,
        val bannerUrl: String? = null,'''
NEW = '''        val channelName: String?,
        val avatarUrl: String?,
        val channelHandle: String? = null,
        val bannerUrl: String? = null,'''

if not TARGET.is_file():
    print("ERRO: não encontrei YouTubeRepository.kt neste caminho:")
    print(TARGET)
    print("Execute na raiz do projeto VzTune ou passe a raiz como argumento:")
    print("python fix_channelhandle.py /caminho/para/VzTune")
    sys.exit(2)

source = TARGET.read_text(encoding="utf-8")
if NEW in source:
    print("Nenhuma alteração: channelHandle já está declarado na ChannelPage.")
    sys.exit(0)
if OLD not in source:
    print("ERRO: o bloco esperado da ChannelPage não foi encontrado.")
    print("O arquivo pode ter sido alterado; nada foi modificado.")
    sys.exit(3)

updated = source.replace(OLD, NEW, 1)
backup = TARGET.with_suffix(TARGET.suffix + ".fix_channelhandle.bak")
if not backup.exists():
    shutil.copy2(TARGET, backup)
TARGET.write_text(updated, encoding="utf-8")
print("CORREÇÃO APLICADA:")
print(" - Adicionado channelHandle: String? = null na ChannelPage")
print(f" - Arquivo: {TARGET}")
print(f" - Backup: {backup}")
print("Agora compile novamente pelo GitHub Actions.")
