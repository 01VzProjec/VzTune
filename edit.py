#!/usr/bin/env python3
"""Patch VzTune's YouTube channel state mapping safely."""
from pathlib import Path
import shutil
import sys

TARGET_RELATIVE = Path(
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"
)
BROKEN = "shorts = emptyList(), // FIX20 - Canal sem Shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,"
FIXED = """shorts = emptyList(), // FIX20 - Canal sem Shorts
                            channelPlaylists = channel.playlists,
                            bannerUrl = channel.bannerUrl ?: it.bannerUrl,
                            isYouTubeChannel = true,"""


def find_target() -> Path:
    # First check common launch locations (project root and script's folder).
    roots = [Path.cwd(), Path(__file__).resolve().parent]
    seen = set()
    for root in roots:
        root = root.resolve()
        if root in seen:
            continue
        seen.add(root)
        candidate = root / TARGET_RELATIVE
        if candidate.is_file():
            return candidate

    # If launched from a subfolder, search upward from cwd and script location.
    for start in roots:
        start = start.resolve()
        for parent in [start, *start.parents]:
            candidate = parent / TARGET_RELATIVE
            if candidate.is_file():
                return candidate

    # Last resort: search nearby for the exact filename, then validate its path.
    matches = []
    for root in seen:
        try:
            matches.extend(p for p in root.rglob("ArtistViewModel.kt")
                          if TARGET_RELATIVE.as_posix() in p.as_posix())
        except PermissionError:
            pass
    unique = list(dict.fromkeys(matches))
    if len(unique) == 1:
        return unique[0]
    if len(unique) > 1:
        print("ERRO: encontrei mais de um ArtistViewModel.kt. Rode o script na raiz correta do VzTune.")
        for p in unique:
            print(" -", p)
        sys.exit(2)
    print("ERRO: não encontrei o arquivo alvo:", TARGET_RELATIVE)
    print("Coloque edit.py na raiz do projeto VzTune ou execute-o de lá.")
    sys.exit(1)


def main() -> None:
    target = find_target()
    text = target.read_text(encoding="utf-8")



    if BROKEN in text:
        updated = text.replace(BROKEN, FIXED, 1)
        # Confirm that the bad inline-comment pattern is gone and all assignments exist.
        if BROKEN in updated or "channelPlaylists = channel.playlists," not in updated:
            print("ERRO: verificação pós-alteração falhou. Nenhum arquivo foi gravado.")
            sys.exit(3)
        backup = target.with_suffix(target.suffix + ".bak")
        if not backup.exists():
            shutil.copy2(target, backup)
            print("Backup criado:", backup)
        target.write_text(updated, encoding="utf-8")
        print("PATCH APLICADO COM SUCESSO!")
        print("Arquivo corrigido:", target)
        print("Correção: as atribuições channelPlaylists, bannerUrl e isYouTubeChannel agora não ficam dentro do comentário.")
        return


    # Idempotent behavior: if the fix already exists, do not modify the file again.
    if ("channelPlaylists = channel.playlists," in text
            and "bannerUrl = channel.bannerUrl ?: it.bannerUrl," in text
            and "isYouTubeChannel = true," in text):
        print("Nada a fazer: o patch parece já estar aplicado.")
        print("Arquivo:", target)
        return

    print("ERRO: não encontrei o trecho antigo esperado. Arquivo não foi alterado.")
    print("Isso pode significar que o código mudou; evitando sobrescrever algo diferente.")
    sys.exit(4)


if __name__ == "__main__":
    main()
