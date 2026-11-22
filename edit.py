FACADE = '\n    // PART 6 - Facade unificada: ViewModels usam so MusicRepository, nao YouTubeRepository direto.\n    suspend fun browseChannel(channelIdOrHandle: String) = runCatching { youTubeRepository.browseChannel(channelIdOrHandle) }.getOrNull()\n    suspend fun browseYouTubePlaylist(playlistId: String) = runCatching { youTubeRepository.browseYouTubePlaylist(playlistId) }.getOrNull()\n    suspend fun getYouTubePlaylistTracks(playlistId: String): List<Track> = runCatching { youTubeRepository.getYouTubePlaylistTracks(playlistId) }.getOrDefault(emptyList())\n    fun hasMoreChannelVideos(channelId: String): Boolean = youTubeRepository.hasMoreChannelVideos(channelId)\n    fun hasMoreChannelPlaylists(channelId: String): Boolean = youTubeRepository.hasMoreChannelPlaylists(channelId)\n    suspend fun loadMoreChannelVideos(channelId: String, pageSize: Int = 500): List<Track> = runCatching { youTubeRepository.loadMoreChannelVideos(channelId, pageSize) }.getOrDefault(emptyList())\n    suspend fun loadMoreChannelPlaylists(channelId: String, pageSize: Int = 500): List<com.vztechcore.vztune.core.domain.music.Playlist> = runCatching { youTubeRepository.loadMoreChannelPlaylists(channelId, pageSize) }.getOrDefault(emptyList())\n'
# parte6.py - Parte 6: Unificacao - MusicRepository vira facade unica
# Roda na raiz: python3 parte6.py
# Nao apaga YouTubeRepository, so faz ViewModels usarem MusicRepository.
import os, sys
def fail(msg):
    print(f"ABORTOU: {msg}"); sys.exit(1)
ROOT=os.getcwd()
MR=os.path.join(ROOT,"data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt")
ARTIST_VM=os.path.join(ROOT,"app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt")
MEDIALIST_VM=os.path.join(ROOT,"app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt")
for p in [MR, ARTIST_VM, MEDIALIST_VM]:
    if not os.path.isfile(p): fail(f"nao achei {p}")
# 1 - Facade no MusicRepository
mr_text=open(MR,encoding="utf-8").read()
if "PART 6 - Facade unificada" not in mr_text:
    anchor="    private companion object { const val TAG = \"VzTuneMusic\" }"
    if anchor not in mr_text: fail("ancora MusicRepository companion nao bateu")
    mr_text=mr_text.replace(anchor, FACADE + "\n" + anchor, 1)
    print("Facade adicionada no MusicRepository")
    open(MR,"w",encoding="utf-8").write(mr_text)
else:
    print("Facade ja existe - pulando")

def patch_vm(path, vm_name):
    t=open(path,encoding="utf-8").read()
    changed=False
    # import MusicRepository
    if "import com.vztechcore.vztune.data.music.MusicRepository" not in t:
        # acha ultimo import de data.*
        # insere depois do import de YTMusicRepository
        anchor2="import com.vztechcore.vztune.data.ytmusic.YTMusicRepository"
        if anchor2 not in t: fail(f"ancora import YTMusic nao bateu em {vm_name}")
        t=t.replace(anchor2, anchor2 + "\nimport com.vztechcore.vztune.data.music.MusicRepository", 1)
        changed=True
        print(f"{vm_name}: import MusicRepository adicionado")
    # constructor param - insere depois de youTubeRepository line
    if "private val musicRepository: MusicRepository" not in t:
        # procura linha youTubeRepository
        lines=t.splitlines()
        new_lines=[]
        inserted=False
        for line in lines:
            new_lines.append(line)
            if not inserted and "private val youTubeRepository: YouTubeRepository" in line:
                indent=line[:len(line)-len(line.lstrip())]
                new_lines.append(f"{indent}private val musicRepository: MusicRepository,")
                inserted=True
        if not inserted: fail(f"ancora constructor youTubeRepository nao bateu em {vm_name}")
        t="\n".join(new_lines)
        # garante newline final
        if not t.endswith("\n"): t+="\n"
        changed=True
        print(f"{vm_name}: param musicRepository adicionado")
    # troca chamadas youTubeRepository. -> musicRepository. (todas tem facade)
    if "youTubeRepository." in t:
        count=t.count("youTubeRepository.")
        t=t.replace("youTubeRepository.", "musicRepository.")
        changed=True
        print(f"{vm_name}: {count} chamadas migradas para musicRepository")
    if changed:
        open(path,"w",encoding="utf-8").write(t)
    else:
        print(f"{vm_name}: nada para mudar")
    return

patch_vm(ARTIST_VM, "ArtistViewModel")
patch_vm(MEDIALIST_VM, "MediaListViewModel")
print("Parte 6 OK - ViewModels agora usam MusicRepository como facade unica.")
