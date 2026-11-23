#!/usr/bin/env python3
"""Fix the undefined onSearchToggle reference in VzTune and disable .bak creation.
Run from the VzTune project root: python fix_searchtoggle_no_backups.py
Or pass the project root as the first argument.
This script intentionally creates no backup files.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd().resolve()
SCREEN = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
EDIT_SCRIPT = ROOT / "edit.py"
HANDLE_FIX_SCRIPT = ROOT / "fix_channelhandle.py"

OLD = '''                onSearchClick = if (state.isYouTubeChannel) ({ onSearchToggle(); scope.launch { listState.animateScrollToItem(0) } }) else null,'''
NEW = '''                onSearchClick = if (state.isYouTubeChannel) ({ channelSearchOpen = !channelSearchOpen; if (!channelSearchOpen) channelSearchQuery = ""; scope.launch { listState.animateScrollToItem(0) } }) else null,'''

if not SCREEN.is_file():
    print("ERRO: ArtistScreen.kt não encontrado em:", SCREEN)
    print("Execute na raiz do VzTune ou passe a raiz como argumento.")
    sys.exit(2)

screen_text = SCREEN.read_text(encoding="utf-8")
if NEW in screen_text:
    print("ArtistScreen.kt: correção já aplicada.")
elif OLD in screen_text:
    SCREEN.write_text(screen_text.replace(OLD, NEW, 1), encoding="utf-8")
    print("ArtistScreen.kt: referência inexistente onSearchToggle corrigida.")
else:
    print("ERRO: não encontrei a linha esperada nem a versão corrigida em ArtistScreen.kt.")
    print("Nada foi alterado neste arquivo; pode haver diferenças no código atual.")
    sys.exit(3)

# If edit.py is present in the project, remove its backup-creation logic so reruns
# don't create .edit.py.bak files. Exact patterns are checked before changing.
if EDIT_SCRIPT.is_file():
    t = EDIT_SCRIPT.read_text(encoding="utf-8")
    t2 = t.replace('Validates every patch before modifying files; does not create backup files.',
                   'Validates every patch before modifying files; does not create backup files.')
    t2 = t2.replace('import shutil\n', '')
    old_backup_block = '''    # Backups first, then writes. If any patch failed above, no writes occurred.\n    backups = []\n    for key in sorted(changed):\n        path = FILES[key]\n        backup = path.with_suffix(path.suffix + ".edit.py.bak")\n        if not backup.exists():\n            shutil.copy2(path, backup)\n        backups.append(backup)\n'''
    new_backup_block = '''    # Patches are validated before writes; intentionally no backup files are created.\n'''
    if old_backup_block in t2:
        t2 = t2.replace(old_backup_block, new_backup_block, 1)
        t2 = t2.replace('    print("Backups: .edit.py.bak (não sobrescreve backup existente")\n', '')
    if t2 != t:
        EDIT_SCRIPT.write_text(t2, encoding="utf-8")
        print("edit.py: desativada criação de backups .edit.py.bak.")
    else:
        print("edit.py: sem alterações (backup já desativado ou script diferente).")
else:
    print("edit.py não está na raiz; ignorado.")

# Disable backup creation in the previously delivered channelHandle repair script,
# if the user copied it into the project root.
if HANDLE_FIX_SCRIPT.is_file():
    t = HANDLE_FIX_SCRIPT.read_text(encoding="utf-8")
    t2 = t.replace('import shutil\n', '')
    t2 = t2.replace('backup = TARGET.with_suffix(TARGET.suffix + ".fix_channelhandle.bak")\nif not backup.exists():\n    shutil.copy2(TARGET, backup)\n', '')
    t2 = t2.replace('print(f" - Backup: {backup}")\n', '')
    if t2 != t:
        HANDLE_FIX_SCRIPT.write_text(t2, encoding="utf-8")
        print("fix_channelhandle.py: desativada criação de backup.")
    else:
        print("fix_channelhandle.py: sem alterações (backup já desativado ou script diferente).")
else:
    print("fix_channelhandle.py não está na raiz; ignorado.")

# Remove only backup artifacts produced by the earlier scripts for these known files.
backup_paths = [
    SCREEN.with_suffix(SCREEN.suffix + ".edit.py.bak"),
    (ROOT / "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt").with_suffix(".kt.fix_channelhandle.bak"),
]
for key in ("parser", "repo", "state", "vm", "screen"):
    rel = {
        "parser": "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeChannelParser.kt",
        "repo": "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt",
        "state": "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistUiState.kt",
        "vm": "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt",
        "screen": "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt",
    }[key]
    backup_paths.append((ROOT / rel).with_suffix(Path(rel).suffix + ".edit.py.bak"))
removed = 0
for backup in set(backup_paths):
    if backup.exists():
        try:
            backup.unlink()
            removed += 1
        except OSError as exc:
            print(f"AVISO: não consegui apagar {backup.name}: {exc}")
print(f"Backups antigos conhecidos removidos: {removed}.")
print("Concluído. Nenhum backup foi criado por este script.")
print("Agora rode o build no GitHub Actions.")
