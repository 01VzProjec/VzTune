#!/usr/bin/env python3
"""Safe, idempotent VzTune patch script for artist/channel routing and playlist owner display.

Run from the VzTune project root (or place this script in that root) with:
    python edit.py
It creates a .bak backup for each Kotlin file before modifying it.
"""
from pathlib import Path
import shutil
import sys

FILES = {
    "artist_vm": Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt"),
    "track_menu_vm": Path("app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt"),
    "search_vm": Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt"),
    "media_list_vm": Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"),
    "media_list_header": Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt"),
}


# Previously identified fix: an inline // comment swallowed assignments on the same line.
ARTIST_VM_OLD = "shorts = emptyList(), // FIX20 - Canal sem Shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,"
ARTIST_VM_NEW = """shorts = emptyList(), // FIX20 - Canal sem Shorts
                            channelPlaylists = channel.playlists,
                            bannerUrl = channel.bannerUrl ?: it.bannerUrl,
                            isYouTubeChannel = true,"""


TRACK_MENU_OLD = """        // Vídeo do YouTube: o \"artista\" é o CANAL. Nunca resolve por nome no YT Music.
        if (track.source == Source.YOUTUBE) {
            onOpenChannelForTrack(track)
            return
        }
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            }
        }"""
TRACK_MENU_NEW = """        // FIX: vídeo também pode ser conteúdo de um artista musical.
        // Tenta resolver o artista no YT Music usando título/contexto; se não encontrar,
        // mantém o fallback para o canal real que publicou o vídeo.
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            } else if (track.source == Source.YOUTUBE) {
                val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
                if (channel != null) {
                    _navigationEvent.emit(
                        MenuNavigationEvent.OpenArtist(
                            Artist(id = channel.channelId, name = channel.channelName,
                                thumbnailUrl = channel.avatarUrl, source = Source.YOUTUBE)
                        )
                    )
                }
            }
        }"""

SEARCH_OLD = """        // Video do YouTube: o \"artista\" e o CANAL. Nunca resolve por nome no YT Music.
        if (track.source == Source.YOUTUBE) {
            onOpenChannelForTrack(track)
            return
        }
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }"""
SEARCH_NEW = """        // FIX: um vídeo pode ser clipe, performance ou música de um artista.
        // Primeiro tenta o artista musical com contexto; se falhar, abre o canal uploader.
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            } else if (track.source == Source.YOUTUBE) {
                val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
                if (channel != null) {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(channel.channelId, channel.channelName,
                            channel.avatarUrl, isYouTubeChannel = true)
                    )
                }
            }
        }"""

# Playlist page: channel lookup was already performed, but its channelName was not
# used as a fallback for owner/subtitle if NewPipe playlist owner text was missing.
PLAYLIST_OWNER_OLD = """                                subtitle = ytPage.owner ?: it.subtitle,
                                owner = ytPage.owner ?: it.owner,
                                artistId = ytPage.ownerChannelId ?: ch?.channelId ?: it.artistId,"""
PLAYLIST_OWNER_NEW = """                                subtitle = ytPage.owner?.takeIf { n -> n.isNotBlank() } ?: ch?.channelName?.takeIf { n -> n.isNotBlank() } ?: it.subtitle,
                                owner = ytPage.owner?.takeIf { n -> n.isNotBlank() } ?: ch?.channelName?.takeIf { n -> n.isNotBlank() } ?: it.owner,
                                artistId = ytPage.ownerChannelId ?: ch?.channelId ?: it.artistId,"""

# Header owner label fallback: route subtitle often carries playlist creator, but the
# UI was only reading state.owner, causing the creator line to disappear.
OWNER_LINE_OLD = "            val ownerName = state.owner?.takeIf { it.isNotBlank() }"
OWNER_LINE_NEW = "            val ownerName = (state.owner ?: state.subtitle)?.takeIf { it.isNotBlank() }"


def locate_project() -> Path:
    starts = [Path.cwd(), Path(__file__).resolve().parent]
    visited = set()
    for start in starts:
        start = start.resolve()
        for root in (start, *start.parents):
            if root in visited:
                continue
            visited.add(root)
            if all((root / rel).is_file() for rel in FILES.values()):
                return root
    print("ERRO: não encontrei a raiz VzTune com todos os arquivos Kotlin esperados.")
    print("Coloque edit.py na raiz do projeto ou execute-o de dentro dela.")
    sys.exit(1)


def apply_patch(root: Path, key: str, old: str, new: str, description: str) -> bool:
    path = root / FILES[key]
    content = path.read_text(encoding="utf-8")
    if new in content:
        print(f"[OK] Já aplicado: {description}")
        return False
    if old not in content:
        print(f"[ERRO] Trecho esperado não encontrado em {path.relative_to(root)}: {description}")
        print("      Nenhuma alteração feita neste arquivo por este patch.")
        return False
    updated = content.replace(old, new, 1)
    backup = path.with_suffix(path.suffix + ".bak")
    if not backup.exists():
        shutil.copy2(path, backup)
        print("Backup:", backup.relative_to(root))
    path.write_text(updated, encoding="utf-8")
    print(f"[PATCH] {description}")
    return True


def main() -> None:
    root = locate_project()
    print("Raiz do projeto:", root)
    print("\nAplicando correções...\n")

    # Check each patch before writing; exact snippets make this script conservative.
    patches = [
        ("artist_vm", ARTIST_VM_OLD, ARTIST_VM_NEW, "Corrige o comentário que ocultava o estado das playlists de canal"),
        ("track_menu_vm", TRACK_MENU_OLD, TRACK_MENU_NEW, "Ao abrir artista a partir de vídeo: tenta artista musical antes do canal"),
        ("search_vm", SEARCH_OLD, SEARCH_NEW, "Ao abrir artista a partir de vídeo no Search: tenta artista musical antes do canal"),
        ("media_list_vm", PLAYLIST_OWNER_OLD, PLAYLIST_OWNER_NEW, "Usa nome do canal como fallback para dono da playlist do YouTube"),
        ("media_list_header", OWNER_LINE_OLD, OWNER_LINE_NEW, "Mostra dono da playlist usando owner ou subtitle como fallback"),
    ]
    # Preflight all patches first. If a target no longer matches either the old
    # or new snippet, abort before making any changes (avoids partial patch runs).
    mismatches = []
    for key, old, new, description in patches:
        content = (root / FILES[key]).read_text(encoding="utf-8")
        if old not in content and new not in content:
            mismatches.append((FILES[key], description))
    if mismatches:
        print("\nERRO: alguns trechos não correspondem à versão esperada. Nada foi alterado.")
        for path, description in mismatches:
            print(f" - {path}: {description}")
        print("Envie esse resultado para adaptar o script à versão atual do projeto.")
        sys.exit(2)

    changed = 0
    for key, old, new, description in patches:
        changed += apply_patch(root, key, old, new, description)


    print("\nResumo:")
    print(f"  {changed} patch(es) aplicado(s) nesta execução.")
    print("  Backup .bak criado antes da primeira alteração de cada arquivo.")
    print("  Se algum [ERRO] aparecer, não compile ainda: envie o resultado para revisar.")
    print("\nDepois, compile o VzTune e teste:")
    print("  1. Abrir artista a partir de uma música.")
    print("  2. Abrir artista a partir de um vídeo que seja música/clipe de artista.")
    print("  3. Abrir um vídeo de canal comum; se não houver artista musical, deve cair no canal.")
    print("  4. Abrir uma playlist YouTube e conferir se o criador aparece abaixo do título.")

if __name__ == "__main__":
    main()
