package com.vztechcore.vztune.ui.screens.medialist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.DownloadedPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.store.DownloadedPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.MediaListPreferencesStore
import com.vztechcore.vztune.data.ytmusic.YTMusicOwnerValidator
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MediaListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val playerController: PlayerController,
    private val trackDownloader: TrackDownloader,
    private val store: MediaListPreferencesStore,
    private val favoritesStore: FavoritePlaylistsStore,
    private val localStore: LocalPlaylistsStore,
    private val downloadedPlaylistsStore: DownloadedPlaylistsStore,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenArtist(val artist: Artist) : NavigationEvent
        data object CloseScreen : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val route = savedStateHandle.toRoute<Routes.MediaList>()

    // Origem real da playlist: YOUTUBE = veio do NewPipe -> dono e canal do YouTube.
    // YOUTUBE_MUSIC = veio do InnerTube -> dono e artista do YT Music. Null = antigo/sem info -> cai no fallback.
    private val playlistSource = runCatching { com.vztechcore.vztune.core.domain.music.Source.valueOf(route.sourceName ?: "") }.getOrNull()

    private val _uiState = MutableStateFlow(
        MediaListUiState(
            listId = route.id,
            title = route.title,
            subtitle = route.subtitle,
            thumbnailUrl = route.thumbnailUrl,
            isAlbum = route.isAlbum,
            isLocal = isLocalPlaylistId(route.id),
        )
    )
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    init {
        val savedSort = store.getSortOrder(route.id)
        val savedManual = store.getManualOrder(route.id)
        _uiState.update { it.copy(sortOrder = savedSort, manualOrder = savedManual) }
        loadContent()
        viewModelScope.launch {
            favoritesStore.contains(route.id).collect { favorite ->
                _uiState.update { it.copy(isFavorite = favorite) }
            }
        }
    }

    private fun observeLocalPlaylist() {
        viewModelScope.launch {
            localStore.ensureRandomDefaultCover(route.id)
            localStore.playlist(route.id).collect { playlist ->
                _uiState.update {
                    if (playlist == null) {
                        it.copy(isLoading = false, errorMessage = "Playlist não encontrada.")
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            title = playlist.name,
                            owner = "Você",
                            subtitle = "Você",
                            thumbnailUrl = playlist.coverPath,
                            mosaicThumbnails = playlist.mosaic,
                            allTracks = playlist.tracks.mapNotNull { stored -> stored.toTrack() },
                        )
                    }
                }
            }
        }
    }

        // FIX20 - Playlist YouTube tem id PL/UU/FL/LL/OL (menos OLAK que e YT Music). Sem sourceName, infere pelo id.
    private fun isYouTubePlaylistId(id: String): Boolean {
        var v = id.trim().removePrefix("YT:").trim()
        if (v.startsWith("OLAK")) return false
        if (v.startsWith("RD")) return false
        // VLPL... = playlist do YouTube embrulhada pelo YT Music (VL = Video List). Tira o VL e testa o PL.
        if (v.startsWith("VL")) v = v.removePrefix("VL")
        return v.startsWith("PL") || v.startsWith("UU") || v.startsWith("FL") || v.startsWith("LL") || v.startsWith("OL")
    }
    private fun isYouTubePlaylistRoute(): Boolean {
        // ID manda mais que sourceName: busca do InnerTube devolve playlist do YouTube (PL... / VLPL...) com source YOUTUBE_MUSIC.
        // Se o ID e PL/UU/VLPL, e YouTube (NewPipe), mesmo que sourceName diga YOUTUBE_MUSIC.
        if (isYouTubePlaylistId(route.id)) return true
        if (route.sourceName == "YOUTUBE") return true
        if (route.sourceName == "YOUTUBE_MUSIC") return false
        return false
    }

private fun loadContent() {
        if (_uiState.value.isLocal) {
            observeLocalPlaylist()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (route.isAlbum) {
                    val album = ytMusicRepository.browseAlbum(route.id)

                    val resolvedArtist = listOfNotNull(
                        album.artist,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    val resolvedTitle = album.title ?: _uiState.value.title

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = resolvedTitle,
                            subtitle = resolvedArtist ?: it.subtitle,
                            owner = resolvedArtist,
                            ownerAvatarUrl = null,
                            artistId = album.artistId,
                            year = album.year,
                            thumbnailUrl = album.coverUrl ?: it.thumbnailUrl,
                            description = album.description,
                            views = album.views,
                            // Tira duplicadas (mesmo source + id) pra LazyColumn nunca crashar com key repetida
                            allTracks = album.tracks.distinctBy { "${it.source}_${it.id}" },
                        )
                    }

                    loadMoreAlbumsByArtist(
                        artistName = resolvedArtist,
                        artistId = album.artistId,
                        excludeAlbumTitle = resolvedTitle,
                    )
                } else if (isYouTubePlaylistRoute()) {
                    // FIX20 - Playlist YouTube: SO NewPipe, nunca InnerTube. Antes caia no browsePlaylist do InnerTube e o dono vinha como artista do YT Music.
                    val ytPage = runCatching { youTubeRepository.browseYouTubePlaylist(route.id) }.getOrNull()
                    if (ytPage != null) {
                        val ch = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false, errorMessage = null,
                                title = ytPage.title?.takeIf { v -> v.isNotBlank() } ?: it.title,
                                subtitle = ytPage.owner ?: it.subtitle,
                                owner = ytPage.owner ?: it.owner,
                                artistId = ytPage.ownerChannelId ?: ch?.channelId ?: it.artistId,
                                ownerAvatarUrl = ch?.avatarUrl ?: it.ownerAvatarUrl,
                                thumbnailUrl = ytPage.coverUrl ?: it.thumbnailUrl,
                                description = ytPage.description ?: it.description,
                                allTracks = ytPage.tracks.distinctBy { tr -> "${tr.source}_${tr.id}" },
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar esta playlist.") }
                    }
                } else {
                    // Playlist do YouTube: NewPipe primeiro (faixas + canal real). Se vier vazia, cai pro InnerTube abaixo.
                    var loadedViaYouTube = false
                    if (playlistSource == Source.YOUTUBE) {
                        val ytChannel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                        val ytTracks = runCatching { youTubeRepository.getYouTubePlaylistTracks(route.id) }.getOrDefault(emptyList())
                        if (ytTracks.isNotEmpty()) {
                            loadedViaYouTube = true
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    title = _uiState.value.title,
                                    subtitle = ytChannel?.channelName?.takeIf { n -> n.isNotBlank() } ?: it.subtitle,
                                    owner = ytChannel?.channelName?.takeIf { n -> n.isNotBlank() } ?: it.owner,
                                    artistId = ytChannel?.channelId ?: it.artistId,
                                    ownerAvatarUrl = ytChannel?.avatarUrl ?: it.ownerAvatarUrl,
                                    allTracks = ytTracks.distinctBy { "${it.source}_${it.id}" },
                                )
                            }
                        }
                    }
                    if (!loadedViaYouTube) {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)
                    if (playlist.tracks.isEmpty()) {
                        delay(1000)
                        playlist = ytMusicRepository.browsePlaylist(route.id)
                    }

                    val resolvedOwner = listOfNotNull(
                        playlist.owner,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = playlist.title ?: it.title,
                            subtitle = resolvedOwner ?: it.subtitle,
                            owner = resolvedOwner,
                            // Canal real da playlist (UC... do header). Antes era null e
                            // loadPlaylistOwnerAvatar() buscava por NOME no YT Music -> artista errado.
                            artistId = playlist.ownerChannelId ?: it.artistId,
                            ownerAvatarUrl = playlist.ownerAvatarUrl,
                            year = playlist.year,
                            views = playlist.views,
                            originalTrackCount = playlist.trackCount,
                            thumbnailUrl = playlist.coverUrl ?: it.thumbnailUrl,
                            description = playlist.description,
                            mosaicThumbnails = playlist.tracks
                                .asSequence()
                                .mapNotNull { t -> t.thumbnailUrl }
                                .distinct()
                                .take(4)
                                .toList(),
                            allTracks = playlist.tracks.distinctBy { "${it.source}_${it.id}" },
                        )
                    }

                    // Só busca por nome quando NÃO temos o canal real E a playlist é YT Music.
                    // Playlist do YouTube sem channelId: não chuta artista do YT Music.
                    val isYouTubePlaylist = playlist.tracks.isNotEmpty() &&
                        playlist.tracks.all { it.source == Source.YOUTUBE }
                    if (playlist.ownerChannelId.isNullOrBlank() &&
                        playlist.ownerAvatarUrl.isNullOrBlank() &&
                        !resolvedOwner.isNullOrBlank() && !isYouTubePlaylist
                    ) {
                        loadPlaylistOwnerAvatar(resolvedOwner)
                    }

                    // Playlist do YouTube sem UC no InnerTube: resolve via NewPipe
                    if (isYouTubePlaylist && _uiState.value.artistId.isNullOrBlank()) {
                        val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                        if (channel != null) {
                            _uiState.update {
                                it.copy(
                                    artistId = channel.channelId,
                                    ownerAvatarUrl = channel.avatarUrl ?: it.ownerAvatarUrl,
                                )
                            }
                        }
                    }
                }
                    }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    private fun loadPlaylistOwnerAvatar(ownerName: String) {
        viewModelScope.launch {
            val primary = ownerName
                .substringBefore(",")
                .substringBefore("&")
                .trim()
            if (primary.isBlank() || primary.equals("YouTube Music", ignoreCase = true)) return@launch
            val resolved = runCatching {
                ytMusicRepository.findChannelOrArtist(primary)
            }.getOrNull()
            if (resolved != null) {
                _uiState.update { current ->
                    current.copy(
                        artistId = current.artistId ?: resolved.id,
                        ownerAvatarUrl = current.ownerAvatarUrl ?: resolved.thumbnailUrl,
                    )
                }
            }
        }
    }

    private fun loadMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumTitle: String?,
    ) {
        viewModelScope.launch {
            val (artist, moreAlbums) = ytMusicRepository.getMoreAlbumsByArtist(
                artistName = artistName,
                artistId = artistId,
                excludeAlbumId = route.id,
                excludeAlbumTitle = excludeAlbumTitle,
            )
            _uiState.update { current ->
                current.copy(
                    artistId = artist?.id ?: current.artistId,
                    ownerAvatarUrl = artist?.thumbnailUrl ?: current.ownerAvatarUrl,
                    moreAlbumsByArtist = moreAlbums,
                )
            }
        }
    }

    fun onTabSelected(tab: MediaListTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSortOrderSelected(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = order, isShuffling = false) }
        store.setSortOrder(route.id, order)
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentTracks = _uiState.value.getDisplayTracks()
        if (fromIndex !in currentTracks.indices) return
        if (toIndex !in currentTracks.indices) return
        val newList = currentTracks.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        val newOrderIds = newList.map { it.id }
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.MANUAL) }
        store.setManualOrder(route.id, newOrderIds)
        store.setSortOrder(route.id, SortOrder.MANUAL)
    }

    fun startShuffle() { _uiState.update { it.copy(isShuffling = true) } }

    fun shuffleOnce() {
        val currentTracks = _uiState.value.allTracks
        if (currentTracks.isEmpty()) return
        val newOrderIds = currentTracks.map { it.id }.shuffled()
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.SHUFFLE) }
    }

    fun stopShuffle() {
        _uiState.update { it.copy(isShuffling = false) }
        store.setManualOrder(route.id, _uiState.value.manualOrder)
        store.setSortOrder(route.id, SortOrder.SHUFFLE)
    }

    fun onTrackClick(track: Track) {
        val sorted = _uiState.value.getDisplayTracks()
        val idx = sorted.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) playerController.playQueue(sorted, idx)
        else playerController.playQueue(listOf(track), 0)
    }

    fun onFavoriteClick() {
        val state = _uiState.value
        viewModelScope.launch {
            favoritesStore.toggle(
                StoredPlaylist(
                    id = route.id,
                    title = state.title,
                    owner = state.owner,
                    thumbnailUrl = state.thumbnailUrl,
                    mosaic = state.mosaicThumbnails,
                    isAlbum = state.isAlbum,
                )
            )
        }
    }

    fun onPlayNextClick(track: Track) { playerController.addToQueueNext(track) }

    fun onPlayNextAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.asReversed().forEach { playerController.addToQueueNext(it) }
    }

    fun onAddToQueueAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.forEach { playerController.addToQueueEnd(it) }
    }

    fun onDownloadAll() {
        val tracks = _uiState.value.allTracks
        if (tracks.isNotEmpty()) {
            trackDownloader.downloadAll(tracks)
            // FIX 1: registra a playlist/álbum como baixado, senão a aba
            // "Playlists baixadas" nunca tinha de onde listar.
            val state = _uiState.value
            viewModelScope.launch {
                downloadedPlaylistsStore.addFirst(
                    DownloadedPlaylist(
                        id = route.id,
                        title = state.title,
                        owner = state.owner,
                        thumbnailUrl = state.thumbnailUrl,
                        mosaic = state.mosaicThumbnails,
                        isAlbum = state.isAlbum,
                        trackKeys = tracks.map { StoredTrack.from(it).key },
                    )
                )
            }
        }
    }

    // FIX8_OPEN_ARTIST - Se nao tem UC ainda, tenta resolver na hora em vez de desistir. Canal grande precisava disso.
    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }
        val artistName = state.owner?.takeIf { it.isNotBlank() }
        if (directId != null && artistName != null) {
            viewModelScope.launch {
                _navigationEvent.emit(
                    NavigationEvent.OpenArtist(
                        Artist(id = directId, name = artistName, thumbnailUrl = state.ownerAvatarUrl, source = if (route.sourceName == "YOUTUBE" || isYouTubePlaylist(state)) Source.YOUTUBE else Source.YOUTUBE_MUSIC)
                    )
                )
            }
            return
        }
        // Playlist do YouTube sem UC ainda: tenta resolver o dono agora via NewPipe.
        if (route.sourceName == "YOUTUBE" || isYouTubePlaylist(state)) {
            viewModelScope.launch {
                val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                if (channel != null) {
                    _uiState.update { it.copy(artistId = channel.channelId, ownerAvatarUrl = channel.avatarUrl ?: it.ownerAvatarUrl) }
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            Artist(id = channel.channelId, name = channel.channelName.ifBlank { artistName ?: "Canal" }, thumbnailUrl = channel.avatarUrl ?: state.ownerAvatarUrl, source = Source.YOUTUBE)
                        )
                    )
                }
            }
            return
        }
        if (artistName != null) {
            onOpenArtistByName(artistName)
        }
    }


    private fun isYouTubePlaylist(state: MediaListUiState): Boolean {
        if (state.isAlbum || state.isLocal) return false
        if (playlistSource == Source.YOUTUBE) return true
        return state.allTracks.isNotEmpty() && state.allTracks.all { it.source == Source.YOUTUBE }
    }

    fun onOpenArtistByName(artistName: String) {
        // Playlist do YouTube: o "artista" é o canal. Sem UC, não chuta YT Music.
        if (isYouTubePlaylist(_uiState.value) || playlistSource == Source.YOUTUBE) return
        viewModelScope.launch {
            // Álbum: passa o título do álbum como contexto, pra resolver só aceitar
            // o artista que tem ESSE álbum no catálogo. Playlist: sem contexto, cai pro mais parecido.
            val albumTitle = _uiState.value.title.takeIf { _uiState.value.isAlbum }
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName, albumTitle = albumTitle, playlistId = route.id, playlistTitle = _uiState.value.title)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found))
            }
        }
    }

    fun onDeleteLocalPlaylist() {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch {
            localStore.remove(route.id)
            _navigationEvent.emit(NavigationEvent.CloseScreen)
        }
    }

    fun onRenameLocalPlaylist(newTitle: String) {
        if (!_uiState.value.isLocal) return
        val clean = newTitle.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            localStore.rename(route.id, clean)
        }
    }

    fun onChangeLocalPlaylistCover(coverUri: Uri) {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val coversDir = File(context.filesDir, "playlist_covers").apply {
                    if (!exists()) mkdirs()
                }
                val safeId = route.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destFile = File(coversDir, "${safeId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(coverUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    _uiState.value.thumbnailUrl?.let { oldPath ->
                        val oldFile = File(oldPath)
                        if (oldFile.exists() && oldFile.parentFile == coversDir) {
                            runCatching { oldFile.delete() }
                        }
                    }
                    localStore.updateCover(route.id, destFile.absolutePath)
                }
            }
        }
    }

    fun onPlayAll() {
        val sorted = _uiState.value.getDisplayTracks()
        if (sorted.isNotEmpty()) playerController.playQueue(sorted, 0)
    }

    fun onShuffle() {
        val shuffled = _uiState.value.filteredTracks.shuffled()
        if (shuffled.isNotEmpty()) playerController.playQueue(shuffled, 0)
    }
}
