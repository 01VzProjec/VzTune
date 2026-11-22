package com.vztechcore.vztune.ui.components.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel that powers the three-dots context menu across all screens
 * (Search, Library, LibraryList, MediaList, Artist, ArtistList) with album resolution.
 */
@HiltViewModel
class TrackMenuViewModel @Inject constructor(
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val playHistoryStore: PlayHistoryStore,
    private val trackDownloader: TrackDownloader,
    private val playerController: PlayerController,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    sealed interface MenuNavigationEvent {
        data class OpenAlbum(val album: Album) : MenuNavigationEvent
        data class OpenArtist(val artist: Artist) : MenuNavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<MenuNavigationEvent>()
    val navigationEvent: SharedFlow<MenuNavigationEvent> = _navigationEvent.asSharedFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.downloadedKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val albumCache = mutableMapOf<String, Album?>()
    private var activeAlbumKey: String? = null

    private val _resolvedAlbum = MutableStateFlow<Album?>(null)
    val resolvedAlbum: StateFlow<Album?> = _resolvedAlbum.asStateFlow()

    fun resolveAlbumFor(track: Track) {
        val key = StoredTrack.from(track).key
        activeAlbumKey = key
        if (albumCache.containsKey(key)) {
            _resolvedAlbum.value = albumCache[key]
            return
        }
        _resolvedAlbum.value = null
        viewModelScope.launch {
            val found = runCatching { ytMusicRepository.findAlbumForTrack(track) }.getOrNull()
            albumCache[key] = found
            if (activeAlbumKey == key) {
                _resolvedAlbum.value = found
            }
        }
    }

    fun onOpenAlbumForTrack(track: Track) {
        val key = StoredTrack.from(track).key
        val cached = albumCache[key]
        if (cached != null) {
            viewModelScope.launch {
                _navigationEvent.emit(MenuNavigationEvent.OpenAlbum(cached))
            }
            return
        }
        viewModelScope.launch {
            val found = runCatching { ytMusicRepository.findAlbumForTrack(track) }.getOrNull()
            albumCache[key] = found
            if (activeAlbumKey == key) {
                _resolvedAlbum.value = found
            }
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenAlbum(found))
            }
        }
    }

    fun onToggleFavorite(track: Track) {
        viewModelScope.launch { favoriteTracksStore.toggle(track) }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.addTrack(playlistId, track) }
    }

    fun onRemoveFromLocalPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.removeTrack(playlistId, track) }
    }

    fun onRemoveFromRecentHistory(track: Track) {
        viewModelScope.launch {
            val key = StoredTrack.from(track).key
            playHistoryStore.remove(key)
        }
    }

    fun onPlayNext(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueue(track: Track) {
        playerController.addToQueueEnd(track)
    }

    fun onDownload(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Sem contexto: ainda usa Levenshtein (mais parecido), nunca mais firstOrNull
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            }
        }
    }

    /** Clique em artista DENTRO de uma faixa: verifica quem tem ESSA faixa / ESSE álbum no catálogo. */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        // FIX: vídeo também pode ser conteúdo de um artista musical.
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
        }
    }

    /**
     * FIX 6 (método do SimpMusic): abre o CANAL REAL que postou o vídeo.
     * Ordem igual à do SimpMusic:
     *  1. InnerTube player(videoId).videoDetails.channelId -> UC... canônico.
     *     É o mesmo ID que o browse() entende como artista/canal, sem fetch de página.
     *  2. Fallback: NewPipe StreamExtractor.uploaderUrl -> UC... (ou @handle resolvido).
     * Nunca busca por nome. Se nenhum dos dois expõe o canal, não navega.
     *
     * getArtistFromVideo() já marca source=YOUTUBE quando o canal NÃO tem página
     * de artista no YT Music (canal puro de vídeo), então a tela de artista sabe
     * que é canal e carrega os vídeos DELE via NewPipe, em vez de chutar um
     * artista qualquer com nome parecido.
     */
    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
            // FIX7: NewPipe PRIMEIRO (estilo SimpMusic).
            // StreamExtractor.uploaderUrl = quem POSTOU o video (fonte da verdade).
            // InnerTube.player com cliente YTMUSIC devolve a MUSICA do video,
            // nao quem postou (video do gamer Jazzghost com musica "Jazzghost"
            // do Moldrin -> devolvia UC do Moldrin). Nunca usa InnerTube aqui.
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                _navigationEvent.emit(
                    MenuNavigationEvent.OpenArtist(
                        Artist(
                            id = channel.channelId,
                            name = channel.channelName,
                            thumbnailUrl = channel.avatarUrl,
                            source = Source.YOUTUBE,
                        )
                    )
                )
                return@launch
            }
            // FIX19 - Sem fallback pro InnerTube: conteudo NewPipe fica SO no YouTube.
            // InnerTube player com cliente YTMUSIC devolve a MUSICA do video (ex: video do canal 'th' com musica do artista 'TH' -> UC do TH), nao quem postou. Sem canal NewPipe = nao navega.
            // channel == null -> não navega. A UI já mostra "Ir para o canal" só pra
            // vídeos do YouTube; sem canal real não há pra onde ir.
        }
    }

    /**
     * FIX 3: "Iniciar rádio" - rádio infinito a partir da faixa.
     * Delega pro PlayerController.startRadio(), que toca o 1º lote e vai
     * buscando mais lotes quando a fila está acabando (nunca termina).
     */
    fun onStartRadio(track: Track) {
        viewModelScope.launch {
            val first = runCatching { musicRepository.buildRadioQueue(track) }
                .getOrDefault(listOf(track))
            playerController.startRadio(track, first)
        }
    }
}
