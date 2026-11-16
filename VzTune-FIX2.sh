#!/bin/bash
# VzTune FIX5 - 1) Playlists baixadas aparecem  2) Ir para canal (YouTube) sem artista errado  3) Iniciar rádio infinito
# Base: FIX4 aplicado. Rode na raiz do projeto (onde tem settings.gradle.kts).
set -e

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/prefs/models/DownloadedPlaylist.kt" <<'FIX5EOF'
package com.vztechcore.vztune.data.prefs.models

import kotlinx.serialization.Serializable

/**
 * Playlist ou álbum que o usuário baixou via "Baixar todas as faixas" / "Baixar álbum".
 * Sem isso a aba "Playlists baixadas" ficava sempre vazia: o download era só por faixa,
 * ninguém registrava a qual playlist/álbum aquelas faixas pertenciam.
 */
@Serializable
data class DownloadedPlaylist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val mosaic: List<String> = emptyList(),
    val isAlbum: Boolean = false,
    // Keys "SOURCE_id" das faixas baixadas. A playlist some da aba quando nenhuma
    // dessas keys ainda está baixada (usuário apagou todos os downloads dela).
    val trackKeys: List<String> = emptyList(),
    val downloadedAtMs: Long = System.currentTimeMillis(),
)
FIX5EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/prefs/store/DownloadedPlaylistsStore.kt" <<'FIX5EOF'
package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.vztechcore.vztune.data.prefs.models.DownloadedPlaylist

/** Playlists/álbuns baixados; mostrados na aba "Playlists baixadas" da Biblioteca. */
@Singleton
class DownloadedPlaylistsStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<DownloadedPlaylist>(
    context = context,
    storeName = "vztune_downloaded_playlists",
    keyName = "playlists",
    serializer = DownloadedPlaylist.serializer(),
    idOf = { it.id },
)
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt" <<'FIX5EOF'
package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.DownloadedPlaylist
import com.vztechcore.vztune.data.prefs.store.DownloadedPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.data.prefs.store.PlaylistCoverStorage
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.newLocalPlaylistId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    historyStore: PlayHistoryStore,
    private val localStore: LocalPlaylistsStore,
    favoritesStore: FavoritePlaylistsStore,
    downloadedPlaylistsStore: DownloadedPlaylistsStore,
    downloadedTracksStore: DownloadedTracksStore,
    private val coverStorage: PlaylistCoverStorage,
    private val playerController: PlayerController,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTab.OVERVIEW)

    private var recentTracks: List<Track> = emptyList()

    val uiState: StateFlow<LibraryUiState> = combine(
        selectedTab,
        historyStore.tracks,
        localStore.items,
        favoritesStore.items,
        downloadedPlaylistsStore.items,
        downloadedTracksStore.downloadedKeys,
    ) { tab, recent, mine, favorites, downloaded, downloadedKeys ->
        recentTracks = recent
        // Só mostra a playlist baixada enquanto pelo menos 1 faixa dela ainda está baixada.
        // trackKeys vazio = registro antigo sem keys -> mostra mesmo assim.
        val visibleDownloaded = downloaded.filter { item ->
            item.trackKeys.isEmpty() || item.trackKeys.any { it in downloadedKeys }
        }
        LibraryUiState(
            selectedTab = tab,
            recentTracks = recent,
            myPlaylists = mine.map { it.toItem() },
            favoritePlaylists = favorites.map { it.toItem() },
            downloadedPlaylists = visibleDownloaded.map { it.toItem() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onTabSelected(tab: LibraryTab) {
        selectedTab.value = tab
    }

    fun onTrackClick(track: Track) {
        val index = recentTracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (index >= 0) playerController.playQueue(recentTracks, index)
        else playerController.playQueue(listOf(track), 0)
    }

    /** Creates a local playlist; without a cover it shows the thumbnails of its tracks. */
    fun createPlaylist(name: String, coverUri: Uri?) {
        viewModelScope.launch {
            val id = newLocalPlaylistId()
            val coverPath = coverUri?.let { coverStorage.save(id, it) }
            localStore.addFirst(LocalPlaylist(id = id, name = name.trim(), coverPath = coverPath))
        }
    }

    private fun LocalPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = name,
        subtitle = "Sua playlist",
        imageUrl = coverPath ?: mosaic.firstOrNull(),
        mosaic = if (coverPath == null) mosaic else emptyList(),
    )

    private fun StoredPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = title,
        subtitle = owner,
        imageUrl = thumbnailUrl,
        mosaic = mosaic,
        isAlbum = isAlbum,
    )

    private fun DownloadedPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = title,
        subtitle = owner ?: if (isAlbum) "Álbum baixado" else "Playlist baixada",
        imageUrl = thumbnailUrl ?: mosaic.firstOrNull(),
        mosaic = mosaic,
        isAlbum = isAlbum,
    )
}
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt" <<'FIX5EOF'
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
                } else {
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

    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }
        val artistName = state.owner?.takeIf { it.isNotBlank() }
        if (directId != null && artistName != null) {
            viewModelScope.launch {
                _navigationEvent.emit(
                    NavigationEvent.OpenArtist(
                        Artist(
                            id = directId,
                            name = artistName,
                            thumbnailUrl = state.ownerAvatarUrl,
                            source = Source.YOUTUBE_MUSIC,
                        )
                    )
                )
            }
            return
        }
        // Playlist do YouTube sem UC...: NÃO cai pra busca por nome no YT Music
        // (era isso que abria artista qualquer com nome parecido). Sem canal, não abre nada.
        if (isYouTubePlaylist(state)) return
        if (artistName != null) {
            onOpenArtistByName(artistName)
        }
    }

    private fun isYouTubePlaylist(state: MediaListUiState): Boolean {
        if (state.isAlbum || state.isLocal) return false
        return state.allTracks.isNotEmpty() && state.allTracks.all { it.source == Source.YOUTUBE }
    }

    fun onOpenArtistByName(artistName: String) {
        // Playlist do YouTube: o "artista" é o canal. Sem UC, não chuta YT Music.
        if (isYouTubePlaylist(_uiState.value)) return
        viewModelScope.launch {
            // Álbum: passa o título do álbum como contexto, pra resolver só aceitar
            // o artista que tem ESSE álbum no catálogo. Playlist: sem contexto, cai pro mais parecido.
            val albumTitle = _uiState.value.title.takeIf { _uiState.value.isAlbum }
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName, albumTitle = albumTitle)
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
FIX5EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt" <<'FIX5EOF'
package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YTMusicRepository @Inject constructor(
    private val enricher: YTMusicEnricher,
) {

    data class SearchPage(val items: List<SearchHit>, val continuation: String?)

    data class AlbumResult(
        val coverUrl: String?,
        val artist: String?,
        val title: String?,
        val description: String?,
        val views: String?,
        val tracks: List<Track>,
        val artistId: String? = null,
        val year: String? = null,
    )

    data class PlaylistResult(
        val coverUrl: String?,
        val owner: String?,
        val ownerAvatarUrl: String?,
        val title: String?,
        val description: String?,
        val trackCount: Int,
        val year: String?,
        val views: String?,
        val tracks: List<Track>,
        // UC... do canal dono da playlist (vem do header do InnerTube).
        // Sem isso a playlist caía no findChannelOrArtist() por nome e abria artista errado.
        val ownerChannelId: String? = null,
    )

    data class ArtistResult(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val relatedArtists: List<Artist>,
        val views: String? = null,
        val songsMore: BrowseRef? = null,
        val albumsMore: BrowseRef? = null,
        val singlesMore: BrowseRef? = null,
        val videos: List<Track> = emptyList(),
        val videosMore: BrowseRef? = null,
        val featured: List<Playlist> = emptyList(),
        val featuredMore: BrowseRef? = null,
    )

    /** One page of an artist "see all" list. Only the list matching the requested kind is filled. */
    data class ArtistListPage(
        val tracks: List<Track> = emptyList(),
        val albums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val continuation: String? = null,
    )

    data class VideoDetails(
        val viewCount: String?,
        val likeCount: String?,
        val description: String?,
    )

    private data class ResolvedAlbumRef(
        val browseId: String,
        val title: String,
    )

    private data class PlaylistHeaderExtras(
        val ownerAvatarUrl: String?,
        val year: String?,
        val channelId: String?,
    )

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        continuation: String?,
    ): SearchPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.search(query, filter.params)
            else InnerTube.searchContinuation(continuation)
        }.getOrNull() ?: return@withContext SearchPage(emptyList(), null)

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val next = InnerTube.findContinuationToken(json)
        val hits = parsed.mapNotNull { YTMusicSearchParser.toHit(it, filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    /**
     * Resolves the official YouTube Music [Album] (or Single/EP) that contains [track]
     * using the song's exact `MPREb_` browseId from InnerTube instead of guessing.
     */
    suspend fun findAlbumForTrack(track: Track): Album? = withContext(Dispatchers.IO) {
        val primaryArtist = track.artist
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat.")
            .substringBefore("ft.")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }

        val albumHint = track.album?.takeIf {
            it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
        }

        // 1. Search the exact song in YT Music to read its official MPREb_ album reference
        val songQuery = listOfNotNull(track.title, primaryArtist).joinToString(" ").trim()
        val exactRef = if (songQuery.isNotBlank()) {
            runCatching {
                val songJson = InnerTube.search(songQuery, SearchFilter.SONGS.params)
                extractAlbumRefFromSongSearch(songJson, track)
            }.getOrNull()
        } else {
            null
        }

        if (exactRef != null) {
            val header = runCatching { YTMusicAlbumParser.parseAlbumHeader(exactRef.browseId) }.getOrNull()
            return@withContext Album(
                id = exactRef.browseId,
                title = header?.title?.takeIf { it.isNotBlank() } ?: exactRef.title,
                artist = header?.artist?.takeIf { it.isNotBlank() } ?: primaryArtist ?: track.artist,
                thumbnailUrl = header?.coverUrl ?: track.thumbnailUrl,
                views = header?.views,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        // 2. Fallback only when track already carried an explicit album name
        val targetTitle = albumHint ?: return@withContext null
        val albumSearchQuery = listOfNotNull(targetTitle, primaryArtist).joinToString(" ").trim()
        if (albumSearchQuery.isBlank()) return@withContext null

        val json = runCatching { InnerTube.search(albumSearchQuery, SearchFilter.ALBUMS.params) }.getOrNull()
            ?: return@withContext null

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val albums = parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.ALBUMS) }
            .filterIsInstance<SearchHit.AlbumHit>()
            .map { it.album }

        albums.firstOrNull { album ->
            val titleMatches = album.title.equals(targetTitle, ignoreCase = true) ||
                album.title.contains(targetTitle, ignoreCase = true)
            val artistMatches = primaryArtist == null ||
                album.artist.contains(primaryArtist, ignoreCase = true) ||
                primaryArtist.contains(album.artist, ignoreCase = true)
            titleMatches && artistMatches
        }
    }

    /**
     * Fills missing views / owner on playlist hits that did not come from YouTube Music
     * (e.g. NewPipe results). Cover and mosaic are left untouched.
     */
    suspend fun fillPlaylistStats(hits: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> =
        enricher.fillPlaylistStats(hits)

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()

            coroutineScope {
                val queriesDeferred = async {
                    runCatching {
                        val json = InnerTube.searchSuggestions(query)
                        YTMusicSearchParser.parseSuggestions(json)
                    }.getOrDefault(emptyList())
                }

                val songsDeferred = async {
                    runCatching {
                        val limit = when {
                            query.length <= 2 -> 3
                            query.length == 3 -> 4
                            else -> 5
                        }

                        val json = InnerTube.search(query, SearchFilter.SONGS.params)
                        val parsed = YTMusicSearchParser.parseSearchResults(json)
                        parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.SONGS) }
                            .filterIsInstance<SearchHit.SongHit>()
                            .take(limit)
                            .map { it.track }
                    }.getOrDefault(emptyList())
                }

                SearchSuggestions(
                    songs = songsDeferred.await(),
                    queries = queriesDeferred.await()
                )
            }
        }

    // ================= BROWSE =================

    suspend fun browseAlbum(albumId: String): AlbumResult = withContext(Dispatchers.IO) {
        // Normalize OLAK5uy_ playlist-backed album IDs so InnerTube browse always resolves them
        val browseId = if (albumId.startsWith("OLAK5uy_")) "VL$albumId" else albumId

        val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
            ?: return@withContext AlbumResult(null, null, null, null, null, emptyList())

        val headerInfo = YTMusicAlbumParser.parseFromJson(json)
        val cover = headerInfo?.coverUrl
        val title = headerInfo?.title
        val artist = headerInfo?.artist
        val views = headerInfo?.views
        val desc = extractDescription(json)
        val (artistId, year) = extractAlbumHeaderExtras(json)

        // Check both musicShelfRenderer and musicPlaylistShelfRenderer used by two-column album layouts
        val primaryShelf = YTMusicTextUtils.findFirst(json, "musicShelfRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer")

        var rawTracks = (primaryShelf?.let { collectMusicItems(it) } ?: emptyList())
            .ifEmpty { collectMusicItems(json) }
            .distinctBy { it.id }

        // Fallback: some MPREb_ pages only expose the album's OLAK5uy_ playlistId in the Play button
        if (rawTracks.isEmpty()) {
            val fallbackPlaylistId = findAlbumAudioPlaylistId(json)
            if (!fallbackPlaylistId.isNullOrBlank() && fallbackPlaylistId != browseId) {
                val playlistFallback = runCatching { browsePlaylist(fallbackPlaylistId) }.getOrNull()
                if (playlistFallback != null && playlistFallback.tracks.isNotEmpty()) {
                    rawTracks = playlistFallback.tracks
                }
            }
        }

        val normalizedTracks = rawTracks.map { item ->
            val resolvedArtist = if (item.artist.isBlank() || item.artist.equals("Desconhecido", ignoreCase = true)) {
                artist ?: item.artist
            } else {
                item.artist
            }
            val resolvedAlbum = item.album?.takeIf { it.isNotBlank() } ?: title
            item.copy(
                artist = resolvedArtist,
                album = resolvedAlbum,
                thumbnailUrl = item.thumbnailUrl ?: cover,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        val finalTracks = enricher.enrichTracks(normalizedTracks, artist)

        AlbumResult(
            coverUrl = cover,
            artist = artist,
            title = title,
            description = desc,
            views = views,
            tracks = finalTracks,
            artistId = artistId,
            year = year,
        )
    }

    /**
     * Fetches the artist profile and other albums/singles by the same artist,
     * excluding the currently opened album.
     */
    suspend fun getMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumId: String,
        excludeAlbumTitle: String?,
    ): Pair<Artist?, List<Album>> = withContext(Dispatchers.IO) {
        val resolvedId = artistId?.takeIf { it.startsWith("UC") }
            ?: artistName?.takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
                ?.let { name ->
                    val primary = name.substringBefore(",").substringBefore("&").trim()
                    // Verifica que o artista tem esse álbum no catálogo, senão pega o homônimo errado
                    runCatching {
                        resolveArtist(primary, albumTitle = excludeAlbumTitle)?.id
                    }.getOrNull()
                }
            ?: return@withContext (null to emptyList())

        val artistDetail = runCatching { browseArtist(resolvedId) }.getOrNull()
            ?: return@withContext (null to emptyList())

        val artist = Artist(
            id = artistDetail.browseId,
            name = artistDetail.name,
            thumbnailUrl = artistDetail.thumbnailUrl,
            source = Source.YOUTUBE_MUSIC,
        )

        val cleanExcludeId = excludeAlbumId.removePrefix("VL")
        val otherReleases = (artistDetail.albums + artistDetail.singles)
            .distinctBy { it.id }
            .filter { candidate ->
                val sameId = candidate.id == excludeAlbumId || candidate.id.removePrefix("VL") == cleanExcludeId
                val sameTitle = !excludeAlbumTitle.isNullOrBlank() &&
                    candidate.title.equals(excludeAlbumTitle, ignoreCase = true)
                !sameId && !sameTitle
            }
            .take(15)

        artist to otherReleases
    }

    suspend fun browsePlaylist(playlistId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val cleanId = YTMusicTextUtils.normalizePlaylistBrowseId(playlistId)
        val json = runCatching { InnerTube.browse(cleanId) }.getOrNull()
            ?: return@withContext PlaylistResult(null, null, null, null, null, 0, null, null, emptyList(), null)

        val parsed = YTMusicPlaylistParser.parsePlaylist(json)
        val headerExtras = extractPlaylistHeaderExtras(json)
        val finalTracks = enricher.enrichTracks(parsed.tracks, null)

        val resolvedAvatar = parsed.ownerAvatarUrl?.takeIf { it.isNotBlank() }
            ?: headerExtras.ownerAvatarUrl
            ?: headerExtras.channelId?.let { cid ->
                runCatching { browseArtist(cid).thumbnailUrl }.getOrNull()
            }

        val resolvedYear = parsed.year?.takeIf { it.isNotBlank() } ?: headerExtras.year

        PlaylistResult(
            coverUrl = parsed.coverUrl,
            owner = parsed.owner,
            ownerAvatarUrl = resolvedAvatar,
            title = parsed.title,
            description = parsed.description,
            trackCount = parsed.trackCount.takeIf { it > 0 } ?: finalTracks.size,
            year = resolvedYear,
            views = parsed.views,
            tracks = finalTracks,
            ownerChannelId = headerExtras.channelId,
        )
    }

    // ================= ARTISTS =================

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Artist>()
        runCatching {
            val json = InnerTube.search(query, SearchFilter.ARTISTS.params)
            YTMusicArtistParser.extractArtistsFromSearch(json, out)
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.take(30)
    }

    /**
     * Resolves an artist or YouTube channel profile (including avatar thumbnail) by name.
     * Sem contexto de faixa/álbum: usa só o nome mais parecido (Levenshtein, SimpMusic).
     * Com contexto, usa resolveArtist() com verificação de catálogo.
     */
    suspend fun findChannelOrArtist(name: String): Artist? =
        resolveArtist(name, trackTitle = null, albumTitle = null)

    /**
     * Resolve o artista CERTO pra um nome clicado dentro de álbum/faixa.
     *
     * Antigo: searchArtists(nome).firstOrNull() -> pegava o primeiro, que com nome
     * repetido era quase sempre o artista errado ou o "- Topic".
     *
     * Novo (SimpMusic + verificação que você pediu):
     * 1. Busca candidatos (até 30), rankeia por Levenshtein no nome normalizado,
     *    desempata Topic (oficial sem " - Topic" ganha).
     * 2. Se tem nome exato não-Topic, devolve ele direto sem gastar browse.
     * 3. Se tem faixa/álbum de contexto, abre a página dos 3 mais parecidos e só
     *    aceita quem tem aquela faixa / aquele álbum no catálogo. Quem não tem, não é ele.
     * 4. Sem verificação ou ninguém verificado, cai pro mais parecido.
     */
    suspend fun resolveArtist(
        artistName: String,
        trackTitle: String? = null,
        albumTitle: String? = null,
    ): Artist? = withContext(Dispatchers.IO) {
        val cleanQuery = artistName.trim()
        if (cleanQuery.isBlank() || cleanQuery.equals("YouTube Music", ignoreCase = true)) {
            return@withContext null
        }

        // 1. Candidatos: filtro ARTISTS primeiro, igual antes
        var candidates = searchArtists(cleanQuery)

        // 2. Fallback pra busca geral (canal/curador UC...) quando ARTISTS não achou nada
        if (candidates.isEmpty()) {
            val fallbackOut = mutableListOf<Artist>()
            runCatching {
                val json = InnerTube.search(cleanQuery, null)
                YTMusicArtistParser.extractArtistsFromSearch(json, fallbackOut)
            }
            candidates = fallbackOut.distinctBy { it.id }
                .filter { !it.thumbnailUrl.isNullOrBlank() }
        }
        if (candidates.isEmpty()) return@withContext null

        // Rankeia: menor Levenshtein primeiro, não-Topic antes de Topic, ordem original desempata
        val ranked = candidates.mapIndexed { idx, artist ->
            Triple(artist, ArtistResolver.distance(cleanQuery, artist.name), idx)
        }.sortedWith(
            compareBy<Triple<Artist, Int, Int>> { it.second }
                .thenBy { if (ArtistResolver.isTopicName(it.first.name)) 1 else 0 }
                .thenBy { it.third }
        )

        // Exato não-Topic: é ele, sem gastar browse
        val exactNonTopic = ranked.firstOrNull {
            it.second == 0 && !ArtistResolver.isTopicName(it.first.name)
        }
        if (exactNonTopic != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank()) {
            return@withContext exactNonTopic.first
        }
        // Exato (mesmo que seja Topic) sem contexto: também é ele, sem gastar browse
        val exactAny = ranked.firstOrNull { it.second == 0 }
        if (exactAny != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank()) {
            return@withContext exactAny.first
        }

        // 3. Verificação de catálogo: só pros 3 mais parecidos, pra não estourar rede
        val hasContext = !trackTitle.isNullOrBlank() || !albumTitle.isNullOrBlank()
        if (hasContext) {
            for ((candidate, _, _) in ranked.take(3)) {
                val detail = runCatching { browseArtist(candidate.id) }.getOrNull() ?: continue
                val contains = ArtistResolver.catalogContains(
                    topSongTitles = detail.topSongs.map { it.title },
                    videoTitles = detail.videos.map { it.title },
                    albumTitles = detail.albums.map { it.title },
                    singleTitles = detail.singles.map { it.title },
                    trackTitle = trackTitle,
                    albumTitle = albumTitle,
                )
                if (contains) {
                    // Devolve com o nome/foto da página verificada (mais confiável que o da busca)
                    return@withContext Artist(
                        id = candidate.id,
                        name = detail.name.takeUnless { it == "Artista" || it == "Unknown Artist" }
                            ?: candidate.name,
                        thumbnailUrl = detail.thumbnailUrl ?: candidate.thumbnailUrl,
                        source = candidate.source,
                    )
                }
            }
            // Ninguém verificado: cai pro mais parecido abaixo (não retorna null, senão o clique morre)
        }

        // 4. Mais parecido (SimpMusic: bestMatchingIndex por Levenshtein)
        return@withContext ranked.first().first
    }

    suspend fun featuredArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val seeds = listOf(
            "top hits", "pop", "funk brasileiro", "sertanejo",
            "rock", "trap", "mpb", "lofi",
        ).shuffled().take(3)

        val out = mutableListOf<Artist>()
        for (seed in seeds) {
            runCatching {
                val json = InnerTube.search(seed, SearchFilter.ARTISTS.params)
                YTMusicArtistParser.extractArtistsFromSearch(json, out)
            }
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.shuffled().take(30)
    }

    suspend fun browseArtist(browseId: String): ArtistResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
                ?: return@withContext ArtistResult(
                    browseId = browseId, name = "Artista",
                    thumbnailUrl = null, subscribers = null, description = null,
                    topSongs = emptyList(), albums = emptyList(), singles = emptyList(),
                    relatedArtists = emptyList()
                )

            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)

            ArtistResult(
                browseId = parsed.browseId,
                name = parsed.name,
                thumbnailUrl = parsed.thumbnailUrl,
                subscribers = parsed.subscribers,
                description = parsed.description,
                topSongs = parsed.topSongs,
                albums = parsed.albums,
                singles = parsed.singles,
                relatedArtists = parsed.relatedArtists,
                views = parsed.views,
                songsMore = parsed.songsMore,
                albumsMore = parsed.albumsMore,
                singlesMore = parsed.singlesMore,
                videos = parsed.videos,
                videosMore = parsed.videosMore,
                featured = parsed.featured,
                featuredMore = parsed.featuredMore,
            )
        }

    /**
     * Loads one page of an artist "see all" list (songs, videos, albums, singles or featured playlists).
     * Pass [continuation] = null for the first page, then the token returned in the previous page.
     */
    suspend fun browseArtistList(
        kind: ArtistListKind,
        artistName: String,
        ref: BrowseRef,
        continuation: String?,
    ): ArtistListPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.browse(ref.browseId, ref.params)
            else InnerTube.browseContinuation(continuation)
        }.getOrNull() ?: return@withContext ArtistListPage()

        val page = YTMusicArtistParser.parseListPage(kind, artistName, json)
        ArtistListPage(page.tracks, page.albums, page.playlists, page.continuation)
    }

    suspend fun getArtistFromVideo(videoId: String): Artist? =
        withContext(Dispatchers.IO) {
            val playerJson = runCatching { InnerTube.player(videoId) }.getOrNull()
                ?: return@withContext null
            val videoDetails = playerJson.optJSONObject("videoDetails")
                ?: return@withContext null
            val channelId = videoDetails.optString("channelId")
                .takeIf { it.isNotBlank() && it != "null" }
            val author = videoDetails.optString("author")
                .takeIf { it.isNotBlank() && it != "null" }
            if (channelId == null || author == null) return@withContext null
            val cleanName = YTMusicTextUtils.cleanArtistName(author)
            if (cleanName.isBlank()) return@withContext null
            val detail = runCatching { browseArtist(channelId) }.getOrNull()
            Artist(
                id = channelId, name = cleanName,
                thumbnailUrl = detail?.thumbnailUrl,
                source = Source.YOUTUBE_MUSIC,
            )
        }

    // ================= QUEUE =================

    suspend fun buildSongQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.artist.isNotBlank()) append(seed.artist)
            if (seed.title.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.title)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val json = runCatching {
            InnerTube.search(query, SearchFilter.SONGS.params)
        }.getOrNull() ?: return@withContext listOf(seed)

        val found = collectMusicItems(json)
            .distinctBy { it.id }
            .filter { it.source == Source.YOUTUBE_MUSIC }

        val normalized = found.map { item ->
            val uploader = item.artist.takeIf {
                it.isNotBlank() && !it.equals("Unknown", true)
            } ?: seed.artist
            item.copy(artist = uploader)
        }

        val list = if (normalized.any { it.id == seed.id }) {
            listOf(seed) + normalized.filter { it.id != seed.id }
        } else {
            listOf(seed) + normalized
        }
        list.take(50)
    }

    // ================= VIDEO DETAILS =================

    suspend fun getVideoDetails(videoId: String): VideoDetails? =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = InnerTube.player(videoId)
                val videoDetails = json.optJSONObject("videoDetails")
                    ?: return@runCatching null

                val viewCountRaw = videoDetails.optString("viewCount")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.toLongOrNull()

                var description = videoDetails.optString("shortDescription")
                    .takeIf { it.isNotBlank() && it != "null" }

                if (description == null) {
                    description = json.optJSONObject("microformat")
                        ?.optJSONObject("playerMicroformatRenderer")
                        ?.optJSONObject("description")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                }

                VideoDetails(
                    viewCount = viewCountRaw?.let { formatNumber(it) },
                    likeCount = null,
                    description = description,
                )
            }.getOrNull()
        }

    // ================= HELPERS =================

    private fun extractPlaylistHeaderExtras(json: JSONObject): PlaylistHeaderExtras {
        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return PlaylistHeaderExtras(null, null, null)

        val avatarUrl = YTMusicThumbnailUtils.thumbnailOf(header.optJSONObject("straplineThumbnail"))
            ?: YTMusicThumbnailUtils.thumbnailOf(
                header.optJSONObject("facepile")
                    ?.optJSONObject("avatarStackViewModel")
                    ?.optJSONArray("avatars")
                    ?.optJSONObject(0)
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
            )

        var year: String? = null
        var channelId: String? = null

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (channelId == null && browseId != null && browseId.startsWith("UC")) {
                    channelId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }

        return PlaylistHeaderExtras(
            ownerAvatarUrl = avatarUrl,
            year = year,
            channelId = channelId,
        )
    }

    private fun extractAlbumHeaderExtras(json: JSONObject): Pair<String?, String?> {
        var artistId: String? = null
        var year: String? = null

        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return (null to null)

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (artistId == null && browseId != null && browseId.startsWith("UC")) {
                    artistId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }
        return artistId to year
    }

    private fun findAlbumAudioPlaylistId(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val pid = node.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                if (!pid.isNullOrBlank() && pid != "null" && pid.startsWith("OLAK5uy_")) {
                    return pid
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findAlbumAudioPlaylistId(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findAlbumAudioPlaylistId(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractAlbumRefFromSongSearch(
        node: Any?,
        targetTrack: Track,
        candidates: MutableList<Pair<Boolean, ResolvedAlbumRef>> = mutableListOf(),
    ): ResolvedAlbumRef? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val rowTitle = columns?.let { YTMusicTextUtils.flexText(it, 0) }.orEmpty()
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    val exactIdMatch = !videoId.isNullOrBlank() && videoId == targetTrack.id
                    val titleMatch = rowTitle.isNotBlank() &&
                        rowTitle.equals(targetTrack.title, ignoreCase = true)

                    if ((exactIdMatch || titleMatch) && columns != null) {
                        val albumRef = findAlbumRunInColumns(columns)
                        if (albumRef != null) {
                            candidates += (exactIdMatch to albumRef)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractAlbumRefFromSongSearch(node.opt(keys.next()), targetTrack, candidates)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractAlbumRefFromSongSearch(node.opt(i), targetTrack, candidates)
                }
            }
        }
        return candidates.firstOrNull { it.first }?.second ?: candidates.firstOrNull()?.second
    }

    private fun findAlbumRunInColumns(columns: JSONArray): ResolvedAlbumRef? {
        for (c in 0 until columns.length()) {
            val runs = columns.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue

            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" } ?: continue

                if (browseId.startsWith("MPREb_")) {
                    val albumTitle = run.optString("text").trim()
                    if (albumTitle.isNotBlank()) {
                        return ResolvedAlbumRef(browseId = browseId, title = albumTitle)
                    }
                }
            }
        }
        return null
    }

    private fun extractVideoIdFromRenderer(r: JSONObject, columns: JSONArray?): String? {
        // 1. Standard playlist/search item data
        r.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 2. Top-level navigationEndpoint
        r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 3. First column title run navigationEndpoint (common on album tracklists)
        columns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 4. Overlay play button endpoint (used on newer YTMusic album rows without playlistItemData)
        r.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        return null
    }

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
            ?: return null
        val descNode = shelf.optJSONObject("description") ?: return null
        val text = YTMusicTextUtils.extractRunsText(descNode)
        return text.takeIf { it.isNotBlank() }
    }

    private fun collectMusicItems(
        node: Any?,
        out: MutableList<Track> = mutableListOf(),
    ): List<Track> {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    if (title != null && !videoId.isNullOrBlank()) {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                            ?: subtitle.substringBefore("•").trim().ifBlank { "Desconhecido" }
                        val albumTitle = columns.let { findAlbumRunInColumns(it)?.title }
                        out += Track(
                            id = videoId,
                            title = title,
                            artist = uploader,
                            album = albumTitle,
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils
                                .parseDuration(YTMusicTextUtils.fixedText(r)),
                            source = source,
                            isExplicit = YTMusicSearchParser.hasExplicitBadge(r),
                        )
                    }
                }
                node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    val videoId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val title = r.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text")
                    if (!videoId.isNullOrBlank() && !title.isNullOrBlank()) {
                        val subtitle = YTMusicTextUtils.runsText(r.optJSONObject("subtitle"))
                        out += Track(
                            id = videoId, title = title,
                            artist = subtitle.substringBefore("•").trim()
                                .ifBlank { "Desconhecido" },
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                            durationSeconds = 0L,
                            source = Source.YOUTUBE,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) {
                collectMusicItems(node.opt(i), out)
            }
        }
        return out
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000_000L -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
FIX5EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt" <<'FIX5EOF'
package com.vztechcore.vztune.data.youtube

import android.net.Uri
import android.util.Log
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The metadata a YouTube search match returns.
 * Used by YTMusicEnricher as a last-resort artist / duration / thumb resolver.
 */
data class VideoMetadata(
    val videoId: String,
    val channelName: String,
    val title: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
)

/** Canal real do YouTube que postou um vídeo (via NewPipe, não via busca por nome). */
data class YouTubeChannelInfo(
    val channelId: String,
    val channelName: String,
    val avatarUrl: String?,
    val channelUrl: String?,
)

/** Info técnica do stream de áudio resolvido pelo NewPipe (usada na tela TrackInfo). */
data class AudioStreamInfo(
    val itag: String?,
    val mimeType: String?,
    val quality: String?,
)

@Singleton
class YouTubeRepository @Inject constructor(
    private val downloader: NewPipeDownloader,
) {
    init {
        NewPipeInitializer.ensureInitialized(downloader)
    }

    private var activeVideoSearch: SearchExtractor? = null
    private var lastVideoPage: Page? = null
    private var activePlaylistSearch: SearchExtractor? = null
    private var lastPlaylistPage: Page? = null

    fun resetSession() {
        activeVideoSearch = null
        lastVideoPage = null
        activePlaylistSearch = null
        lastPlaylistPage = null
    }

    data class VideoPage(
        val items: List<SearchHit.VideoHit>,
        val hasMore: Boolean,
    )

    // ================= SEARCH =================

    suspend fun searchVideos(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 20,
    ): VideoPage = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchVideos: NewPipe not initialized")
            return@withContext VideoPage(emptyList(), false)
        }
        runCatching {
            if (!loadMore) {
                activeVideoSearch = ServiceList.YouTube.getSearchExtractor(query)
                activeVideoSearch!!.fetchPage()
                lastVideoPage = activeVideoSearch!!.initialPage.nextPage
                val items = parseVideoItems(activeVideoSearch!!.initialPage.items).take(pageSize)
                VideoPage(items, lastVideoPage != null)
            } else {
                val extractor = activeVideoSearch
                    ?: return@runCatching VideoPage(emptyList(), false)
                val page = lastVideoPage
                    ?: return@runCatching VideoPage(emptyList(), false)
                val next = extractor.getPage(page)
                lastVideoPage = next.nextPage
                VideoPage(parseVideoItems(next.items).take(pageSize), lastVideoPage != null)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchVideos failed for query=$query", e)
            VideoPage(emptyList(), false)
        }
    }

    suspend fun searchPlaylists(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
    ): List<SearchHit.PlaylistHit> = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchPlaylists: NewPipe not initialized")
            return@withContext emptyList()
        }
        runCatching {
            if (!loadMore) {
                activePlaylistSearch = ServiceList.YouTube.getSearchExtractor(
                    query, listOf("playlists"), null,
                )
                activePlaylistSearch!!.fetchPage()
                lastPlaylistPage = activePlaylistSearch!!.initialPage.nextPage
                parsePlaylistItems(activePlaylistSearch!!.initialPage.items).take(pageSize)
            } else {
                val extractor = activePlaylistSearch
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val page = lastPlaylistPage
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val next = extractor.getPage(page)
                lastPlaylistPage = next.nextPage
                parsePlaylistItems(next.items).take(pageSize)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchPlaylists failed for query=$query", e)
            emptyList()
        }
    }

    // ================= SMART VIDEO MATCH (for artist fallback) =================

    /**
     * Finds the best YouTube video match for the given title/artist hints
     * and returns the full metadata (channel, duration, thumbnail).
     *
     * Scoring:
     *   +1000 if the videoId matches exactly (strongest signal)
     *   +200  if the thumbnail URL matches exactly
     *   +100  if the title matches case-insensitively
     *   +50   if the uploader contains the artist hint
     *   +30   if the duration is within 5s of durationHint
     *   +10   if the duration is within 15s of durationHint
     *
     * Returns null if no candidate reaches MIN_SCORE (60).
     */
    suspend fun findVideoMetadata(
        videoId: String?,
        title: String,
        artistHint: String?,
        durationHintSeconds: Long? = null,
        thumbnailHint: String? = null,
    ): VideoMetadata? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (title.isBlank()) return@withContext null

        // 1) Try exact videoId lookup first — most accurate.
        if (!videoId.isNullOrBlank()) {
            runCatching {
                extractMetadataForId(videoId)
            }.getOrNull()?.let { return@withContext it }
        }

        // 2) Search + score.
        val query = buildString {
            append(title)
            if (!artistHint.isNullOrBlank()) {
                append(" ")
                append(artistHint)
            }
        }

        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            val titleLower = title.lowercase().trim()
            val artistLower = artistHint?.lowercase()?.trim().orEmpty()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val candidateId = extractVideoId(stream.url) ?: return@mapNotNull null
                    val candidateTitle = stream.name ?: return@mapNotNull null
                    val candidateTitleLower = candidateTitle.lowercase().trim()
                    val candidateUploader = stream.uploaderName?.trim().orEmpty()
                    val candidateUploaderLower = candidateUploader.lowercase()
                    val candidateDuration = stream.duration
                    val candidateThumb = stream.thumbnails.lastOrNull()?.url

                    var score = 0

                    if (candidateId == videoId) score += 1000
                    if (!thumbnailHint.isNullOrBlank() && candidateThumb == thumbnailHint) score += 200
                    if (candidateTitleLower == titleLower) score += 100
                    if (artistLower.isNotBlank() && candidateUploaderLower.contains(artistLower)) score += 50

                    if (durationHintSeconds != null && durationHintSeconds > 0 && candidateDuration > 0) {
                        val diff = kotlin.math.abs(candidateDuration - durationHintSeconds)
                        when {
                            diff <= 5 -> score += 30
                            diff <= 15 -> score += 10
                        }
                    }

                    if (score < MIN_SCORE) return@mapNotNull null

                    score to VideoMetadata(
                        videoId = candidateId,
                        channelName = cleanChannelName(candidateUploader),
                        title = candidateTitle,
                        durationSeconds = candidateDuration,
                        thumbnailUrl = candidateThumb,
                    )
                }
                .maxByOrNull { it.first }
                ?.second
        }.getOrElse { e ->
            Log.w(TAG, "findVideoMetadata failed for '$title': ${e.message}")
            null
        }
    }

    /**
     * Direct lookup by videoId via getStreamExtractor — no scoring needed.
     * Falls back to null on any error (removed video, private, etc).
     */
    private fun extractMetadataForId(videoId: String): VideoMetadata? {
        return runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val uploader = extractor.uploaderName?.trim().orEmpty()
            if (uploader.isBlank()) return@runCatching null

            VideoMetadata(
                videoId = videoId,
                channelName = cleanChannelName(uploader),
                title = extractor.name ?: "",
                durationSeconds = extractor.length,
                thumbnailUrl = extractor.thumbnails?.lastOrNull()?.url,
            )
        }.getOrNull()
    }

    private fun cleanChannelName(raw: String): String {
        var s = raw.trim()
        s = s.removeSuffix(" - Topic").removeSuffix(" - topic").trim()
        if (s.startsWith("Provided to YouTube", true)) return ""
        if (s.startsWith("Auto-generated by YouTube", true)) return ""
        return s
    }

    // ================= STREAM RESOLUTION =================

    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "getStreamUrl: NewPipe not initialized")
            return@withContext null
        }

        Log.d(TAG, "getStreamUrl: videoId=$videoId")

        val fromWww = runCatching {
            extractFromUrl("https://www.youtube.com/watch?v=$videoId")
        }.getOrElse { e ->
            Log.w(TAG, "www attempt failed for $videoId: ${e.message}")
            null
        }
        if (!fromWww.isNullOrBlank()) {
            Log.d(TAG, "resolved via www: ${fromWww.take(80)}...")
            return@withContext fromWww
        }

        val fromMusic = runCatching {
            extractFromUrl("https://music.youtube.com/watch?v=$videoId")
        }.getOrElse { e ->
            Log.w(TAG, "music attempt failed for $videoId: ${e.message}")
            null
        }
        if (!fromMusic.isNullOrBlank()) {
            Log.d(TAG, "resolved via music: ${fromMusic.take(80)}...")
            return@withContext fromMusic
        }

        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                val fromAlt = runCatching {
                    extractFromUrl("https://www.youtube.com/watch?v=$altId")
                }.getOrNull()
                if (!fromAlt.isNullOrBlank()) {
                    Log.d(TAG, "resolved via fallback $altId: ${fromAlt.take(80)}...")
                    return@withContext fromAlt
                }
            }
        }

        Log.e(TAG, "NO STREAMS for $videoId")
        null
    }

    /**
     * Retorna info técnica (itag / mime / bitrate) do melhor stream de áudio.
     * Usado pela tela TrackInfo pra exibir detalhes técnicos.
     */
    suspend fun getAudioStreamInfo(videoId: String): AudioStreamInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "getAudioStreamInfo: NewPipe not initialized")
            return@withContext null
        }
        runCatching {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            val audio = extractor.audioStreams
                .filter { it.averageBitrate > 0 }
                .maxByOrNull { it.averageBitrate }
                ?: extractor.audioStreams.firstOrNull()
                ?: return@runCatching null

            AudioStreamInfo(
                itag = runCatching { audio.itag.toString() }.getOrNull(),
                mimeType = runCatching { audio.format?.mimeType }.getOrNull(),
                quality = audio.averageBitrate
                    .takeIf { it > 0 }
                    ?.let { "${it / 1000} kbps" },
            )
        }.getOrElse { e ->
            Log.w(TAG, "getAudioStreamInfo failed for $videoId: ${e.message}")
            null
        }
    }

    private fun extractFromUrl(url: String): String {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val audioStreams = extractor.audioStreams
        val bestAudio: AudioStream? = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.firstOrNull()
        if (bestAudio?.content?.isNotBlank() == true) return bestAudio.content

        extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val progressive = extractor.videoStreams
            .filter { !it.isVideoOnly }
            .minByOrNull {
                it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }
        if (progressive?.content?.isNotBlank() == true) return progressive.content

        throw Exception("No streams available for $url")
    }

    private fun findBestMatchVideoId(title: String, artist: String?): String? {
        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()

        val titleWords = title.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 2 }
            .toSet()
        val artistLower = artist?.lowercase()?.trim().orEmpty()

        return extractor.initialPage.items
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { stream ->
                val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
                val name = stream.name?.lowercase() ?: return@mapNotNull null
                val uploader = stream.uploaderName?.lowercase().orEmpty()
                var score = titleWords.count { name.contains(it) } * 10
                if (artistLower.isNotBlank() && uploader.contains(artistLower)) score += 40
                videoId to score
            }
            .filter { it.second >= 20 }
            .maxByOrNull { it.second }
            ?.first
    }

    // ================= FALLBACK THUMBNAIL =================

    suspend fun findVideoThumbnail(
        videoId: String,
        title: String,
        artist: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null

        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }

        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            val titleWords = title.lowercase()
                .split(Regex("\\s+"))
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .toSet()
            val artistLower = artist?.lowercase()?.trim().orEmpty()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val name = stream.name?.lowercase() ?: return@mapNotNull null
                    val uploader = stream.uploaderName?.lowercase().orEmpty()
                    val thumb = stream.thumbnails.lastOrNull()?.url ?: return@mapNotNull null
                    var score = titleWords.count { name.contains(it) } * 10
                    if (artistLower.isNotBlank() && uploader.contains(artistLower)) score += 40
                    thumb to score
                }
                .filter { it.second >= 20 }
                .maxByOrNull { it.second }
                ?.first
        }.getOrElse { e ->
            Log.w(TAG, "findVideoThumbnail failed for $videoId: ${e.message}")
            null
        }
    }

    // ================= PARSERS =================

    private fun parseVideoItems(items: List<InfoItem>): List<SearchHit.VideoHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
            SearchHit.VideoHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parsePlaylistItems(items: List<InfoItem>): List<SearchHit.PlaylistHit> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { playlist ->
            val listId = extractListId(playlist.url) ?: return@mapNotNull null
            SearchHit.PlaylistHit(
                Playlist(
                    id = "YT:$listId",
                    title = playlist.name ?: "Playlist",
                    owner = playlist.uploaderName ?: "",
                    thumbnailUrl = playlist.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun extractVideoId(url: String): String? = try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v")
            ?: uri.lastPathSegment?.takeIf { uri.host?.contains("youtu.be") == true }
    } catch (e: Exception) {
        null
    }

    private fun extractListId(url: String): String? = try {
        Uri.parse(url).getQueryParameter("list")
    } catch (e: Exception) {
        null
    }

    /**
     * Resolve o CANAL que postou o vídeo pelo videoId, via StreamExtractor do NewPipe.
     * Nunca busca por nome: uploaderUrl é a fonte da verdade (UC... ou @handle).
     * Retorna null quando o NewPipe não expõe o canal -> o chamador deve ESCONDER
     * "Ir para o canal", nunca cair pra busca por nome no YT Music.
     */
    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (videoId.isBlank()) return@withContext null
        runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            val avatarUrl = extractor.uploaderAvatarUrl?.takeIf { it.isNotBlank() }
                ?: extractor.thumbnails?.lastOrNull()?.url
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            YouTubeChannelInfo(
                channelId = channelId,
                channelName = cleanChannelName(uploaderName),
                avatarUrl = avatarUrl,
                channelUrl = uploaderUrl,
            )
        }.getOrNull()
    }

    /**
     * Extrai o UC... de uma uploaderUrl do NewPipe.
     * Formas: .../channel/UC..., .../UC..., .../@handle, .../c/nome, .../user/nome.
     * @handle / /c / /user não têm UC na URL -> resolve via ChannelExtractor do NewPipe.
     */
    private fun extractChannelId(uploaderUrl: String): String? {
        val ucRegex = Regex("UC[\\w-]{22}")
        ucRegex.find(uploaderUrl)?.let { return it.value }
        // @handle, /c/, /user/: ChannelExtractor resolve o id real.
        return runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(uploaderUrl)
            extractor.fetchPage()
            extractor.id?.takeIf { it.startsWith("UC") }
        }.getOrNull()
    }

    suspend fun buildVideoQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.title.isNotBlank()) append(seed.title)
            if (seed.artist.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.artist)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val page = searchVideos(query, loadMore = false, pageSize = 30)
        val tracks = page.items.map { it.track }

        val list = if (tracks.any { it.id == seed.id }) {
            listOf(seed) + tracks.filter { it.id != seed.id }
        } else {
            listOf(seed) + tracks
        }
        list.distinctBy { it.id }.take(50)
    }

    private companion object {
        const val TAG = "VzTuneYT"
        const val MIN_SCORE = 60
    }
}
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt" <<'FIX5EOF'
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
        // Vídeo do YouTube: o "artista" é o CANAL. Nunca resolve por nome no YT Music.
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
        }
    }

    /**
     * FIX 2: abre o CANAL REAL que postou o vídeo (NewPipe StreamExtractor -> UC...).
     * Se o NewPipe não expõe o canal, não navega. Nunca cai pra busca por nome
     * no YT Music (era isso que abria artista qualquer com nome parecido).
     */
    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
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
            }
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
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt" <<'FIX5EOF'
package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Info
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Album
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled

private val SheetContainerColor = Color(0xFF121829)
private val DownloadGreen = Color(0xFF4ADE80)
private val DangerRed = Color(0xFFF87171)

enum class TrackMenuContext {
    DEFAULT,
    RECENT_HISTORY,
    EXTERNAL_PLAYLIST,
    LOCAL_PLAYLIST,
}

/**
 * Context menu opened from the three-dots button on any track card across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    menuContext: TrackMenuContext = TrackMenuContext.DEFAULT,
    resolvedAlbum: Album? = null,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    downloadItem: TrackDownloadItem? = null,
    localPlaylists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onRemoveFromRecentHistory: (Track) -> Unit = {},
    onRemoveFromLocalPlaylist: (Track) -> Unit = {},
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (playlistId: String, track: Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (Track) -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenAlbumForTrack: (Track) -> Unit = {},
    onOpenArtistByName: (String) -> Unit,
    onOpenArtistForTrack: (Track, String) -> Unit = { _, name -> onOpenArtistByName(name) },
    onStartRadio: (Track) -> Unit = {},
    onTrackInfo: (Track) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showRemoveDownloadSheet by remember { mutableStateOf(false) }

    val artists = remember(track.artist) { parseTrackArtists(track.artist) }
    val isDownloading = downloadItem?.status == DownloadStatus.DOWNLOADING ||
        downloadItem?.status == DownloadStatus.QUEUED ||
        downloadItem?.status == DownloadStatus.PAUSED

    val addToPlaylistLabel = when (menuContext) {
        TrackMenuContext.EXTERNAL_PLAYLIST, TrackMenuContext.LOCAL_PLAYLIST -> "Adicionar à outra playlist"
        else -> "Adicionar à playlist"
    }

    if (showRemoveDownloadSheet) {
        RemoveDownloadSheet(
            track = track,
            onConfirmRemove = {
                showRemoveDownloadSheet = false
                onRemoveDownload(track)
                onDismiss()
            },
            onDismiss = { showRemoveDownloadSheet = false },
        )
        return
    }

    if (showPlaylistPicker) {
        PlaylistPickerSheet(
            title = addToPlaylistLabel,
            playlists = localPlaylists,
            onPick = { playlist ->
                onAddToPlaylist(playlist.id, track)
                showPlaylistPicker = false
                onDismiss()
            },
            onDismiss = { showPlaylistPicker = false },
        )
        return
    }

    if (showArtistPicker) {
        ArtistPickerSheet(
            artists = artists,
            onPick = { artistName ->
                showArtistPicker = false
                onDismiss()
                // Passa a faixa: o resolver só aceita quem tem ESSA faixa no catálogo
                onOpenArtistForTrack(track, artistName)
            },
            onDismiss = { showArtistPicker = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // Context-specific top removal actions
            if (menuContext == TrackMenuContext.RECENT_HISTORY) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Apagar",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromRecentHistory(track)
                        onDismiss()
                    },
                )
            }

            if (menuContext == TrackMenuContext.LOCAL_PLAYLIST) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir faixa da playlist",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromLocalPlaylist(track)
                        onDismiss()
                    },
                )
            }

            // 1. Favorite / Unfavorite
            SheetOptionRow(
                icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                iconTint = if (isFavorite) Color(0xFFEC4899) else Color.White,
                label = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = {
                    onToggleFavorite(track)
                    onDismiss()
                },
            )

            // 2. Add to playlist / Add to another playlist
            SheetOptionRow(
                icon = VzIcons.PlaylistAdd,
                label = addToPlaylistLabel,
                onClick = { showPlaylistPicker = true },
            )

            // 3. Play next
            SheetOptionRow(
                icon = VzIcons.PlayCircleFilled,
                label = "Reproduzir como próxima faixa",
                onClick = {
                    onPlayNext(track)
                    onDismiss()
                },
            )

            // 4. Add to queue
            SheetOptionRow(
                icon = VzIcons.QueueMusic,
                label = "Adicionar à fila",
                onClick = {
                    onAddToQueue(track)
                    onDismiss()
                },
            )

            // 4b. FIX 3: Iniciar rádio (Mix infinito) - a partir de qualquer faixa,
            // em qualquer lugar (busca, playlist, álbum, artista). Nome "Iniciar rádio":
            // é o termo do YT Music / Spotify, mais profissional que "Mix".
            SheetOptionRow(
                icon = VzIcons.PlayCircleFilled,
                label = "Iniciar rádio",
                subtitle = "Mix infinito a partir desta faixa",
                onClick = {
                    onStartRadio(track)
                    onDismiss()
                },
            )

            // 5. Download / Downloading / Downloaded
            when {
                isDownloading -> {
                    val progress = downloadItem?.progress ?: 0f
                    val pct = downloadItem?.percentage ?: 0
                    val statusLabel = if (downloadItem?.status == DownloadStatus.PAUSED) {
                        "Download pausado ($pct%)"
                    } else if (pct > 0) {
                        "Baixando faixa ($pct%)"
                    } else {
                        "Baixando faixa"
                    }
                    SheetDownloadProgressRow(
                        progress = progress,
                        label = statusLabel,
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                }
                isDownloaded -> {
                    SheetOptionRow(
                        icon = VzIcons.DownloadDone,
                        iconTint = DownloadGreen,
                        label = "Faixa baixada",
                        labelColor = Color.White,
                        onClick = { showRemoveDownloadSheet = true },
                    )
                }
                else -> {
                    SheetOptionRow(
                        icon = VzIcons.Download,
                        iconTint = Color.White,
                        label = "Baixar faixa",
                        onClick = {
                            onDownload(track)
                            onDismiss()
                        },
                    )
                }
            }

            // 6. Share
            SheetOptionRow(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    shareTrack(context, track)
                    onDismiss()
                },
            )

            // 7. Go to album (shows immediately when track.album is known or once resolved)
            val albumTitle = resolvedAlbum?.title ?: track.album?.takeIf {
                it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
            }
            if (!albumTitle.isNullOrBlank()) {
                SheetOptionRow(
                    icon = VzIcons.Album,
                    label = "Álbum",
                    subtitle = albumTitle,
                    onClick = {
                        onDismiss()
                        if (resolvedAlbum != null) {
                            onOpenAlbum(resolvedAlbum)
                        } else {
                            onOpenAlbumForTrack(track)
                        }
                    },
                )
            }

            // 8. Go to artist(s) / canal
            // FIX 2: vídeo do YouTube -> "Ir para o canal" (resolve o canal real via NewPipe).
            // YT Music -> "Ir para o(s) artista(s)" como antes.
            // Sem canal real, o ViewModel não navega (nunca chuta artista por nome).
            if (artists.isNotEmpty()) {
                val isYouTubeVideo = track.source == Source.YOUTUBE
                val hasMultiple = artists.size > 1 && !isYouTubeVideo
                SheetOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = when {
                        isYouTubeVideo -> "Ir para o canal"
                        hasMultiple -> "Ir para os artistas"
                        else -> "Ir para o artista"
                    },
                    subtitle = if (hasMultiple) artists.joinToString(", ") else artists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistForTrack(track, artists.first())
                        }
                    },
                )
            }

            // 9. Track details
            SheetOptionRow(
                icon = VzIcons.Info,
                label = "Ver informações da faixa",
                onClick = {
                    onDismiss()
                    onTrackInfo(track)
                },
            )
        }
    }
}

/**
 * Confirmation bottom sheet displayed when tapping "Faixa baixada" or the delete icon in completed downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveDownloadSheet(
    track: Track,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VzIcons.DownloadDone,
                        contentDescription = null,
                        tint = DownloadGreen,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Faixa salva para reprodução offline",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Esta música já está armazenada no seu dispositivo para você ouvir mesmo sem conexão com a internet. Deseja removê-la dos seus downloads?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Text(
                            text = "Manter faixa",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onConfirmRemove,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed.copy(alpha = 0.18f),
                            contentColor = DangerRed,
                        ),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(
                            imageVector = VzIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Apagar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSheetHeader(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (track.thumbnailUrl != null) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SheetDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(
                imageVector = VzIcons.Download,
                contentDescription = null,
                tint = DownloadGreen,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    title: String = "Adicionar à playlist",
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "Você ainda não criou nenhuma playlist. Crie uma na Biblioteca, em \"Suas playlists\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPickerSheet(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Escolher artista",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            artists.forEach { artistName ->
                SheetOptionRow(
                    icon = VzIcons.Artist,
                    label = artistName,
                    onClick = { onPick(artistName) },
                )
            }
        }
    }
}

private fun parseTrackArtists(raw: String): List<String> {
    if (raw.isBlank() || raw.equals("Desconhecido", ignoreCase = true)) return emptyList()
    return raw
        .split(Regex("\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private val SharePhrases = listOf(
    "Ouvindo no VzTune 🎧",
    "Escuta essa faixa no VzTune ✨",
    "Descobri no VzTune 🔥",
    "Tocando agora no VzTune 🎵",
    "Recomendo ouvir no VzTune 🚀",
    "Vibe boa no VzTune 🎶",
)

private fun shareTrack(context: Context, track: Track) {
    val url = if (track.source == Source.YOUTUBE) {
        "https://www.youtube.com/watch?v=${track.id}"
    } else {
        "https://music.youtube.com/watch?v=${track.id}"
    }
    val phrase = SharePhrases.random()
    val shareText = "$phrase\n${track.title} — ${track.artist}\n$url"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(sendIntent, "Compartilhar faixa")
    context.startActivity(chooser)
}
FIX5EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt" <<'FIX5EOF'
package com.vztechcore.vztune.data.music

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicTextUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
) {

    // =====================================================================
    //  Fachada — API que os ViewModels esperam do MusicRepository
    // =====================================================================

    data class TechnicalInfo(
        val itag: String?,
        val mimeType: String?,
        val quality: String?,
    )

    /** Extração de áudio via NewPipe (delegado pro YouTubeRepository). */
    suspend fun getStreamUrl(track: Track): String? {
        val artistHint = track.artist
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
        val url = runCatching {
            youTubeRepository.getStreamUrl(
                videoId = track.id,
                titleHint = track.title.takeIf { it.isNotBlank() },
                artistHint = artistHint,
            )
        }.getOrElse { e ->
            Log.e(TAG, "getStreamUrl failed for ${track.id}: ${e.message}", e)
            null
        }
        if (url.isNullOrBlank()) {
            Log.w(TAG, "getStreamUrl returned null for ${track.id} '${track.title}'")
        }
        return url
    }

    /** Info técnica (itag / mime / bitrate) — usada pela tela TrackInfo. */
    suspend fun getTechnicalInfo(track: Track): TechnicalInfo? = runCatching {
        val info = youTubeRepository.getAudioStreamInfo(track.id) ?: return@runCatching null
        TechnicalInfo(itag = info.itag, mimeType = info.mimeType, quality = info.quality)
    }.getOrNull()

    /** Sugestões de busca — delegate direto pro YTMusic. */
    suspend fun suggestions(query: String): SearchSuggestions =
        ytMusicRepository.suggestions(query)

    /**
     * Fila "rádio" — escolhe o repositório certo pelo `seed.source`.
     *   YOUTUBE_MUSIC → ytMusic.buildSongQueue
     *   YOUTUBE       → youtube.buildVideoQueue
     */
    suspend fun buildRadioQueue(seed: Track): List<Track> = runCatching {
        when (seed.source) {
            Source.YOUTUBE_MUSIC -> ytMusicRepository.buildSongQueue(seed)
            Source.YOUTUBE -> youTubeRepository.buildVideoQueue(seed)
            else -> listOf(seed)
        }
    }.getOrElse { e ->
        Log.w(TAG, "buildRadioQueue failed for ${seed.id}: ${e.message}")
        listOf(seed)
    }

    /**
     * FIX 3: próximo lote do rádio infinito.
     * Estratégia pra não repetir os mesmos 50 e manter o "clima" da seed
     * (mesmo artista / artistas parecidos / mesma língua-gênero implícita na busca):
     *  1. Busca pela última faixa tocada (artista + título) -> varia o centro.
     *  2. Busca só pelo artista da seed -> volta pro núcleo.
     *  3. YT Music: topSongs dos artistas relacionados ao artista da seed.
     *  4. YouTube: buildVideoQueue da última faixa.
     * Sempre filtra os ids já tocados e embaralha.
     */
    suspend fun buildRadioContinuation(
        seed: Track,
        excludeIds: Set<String>,
        lastTrack: Track?,
    ): List<Track> = runCatching {
        val candidates = mutableListOf<Track>()
        when (seed.source) {
            Source.YOUTUBE -> {
                val anchor = lastTrack ?: seed
                candidates += youTubeRepository.buildVideoQueue(anchor)
                if (candidates.size < 15 && lastTrack != null && lastTrack.id != seed.id) {
                    candidates += youTubeRepository.buildVideoQueue(seed)
                }
            }
            else -> {
                // 1. Última faixa como âncora
                lastTrack?.let { candidates += ytMusicRepository.buildSongQueue(it) }
                // 2. Artista da seed sozinho (mais amplo que artista+título)
                if (candidates.size < 20 && seed.artist.isNotBlank()) {
                    val artistOnly = Track(
                        id = seed.id, title = "", artist = seed.artist,
                        source = Source.YOUTUBE_MUSIC,
                    )
                    candidates += ytMusicRepository.buildSongQueue(artistOnly)
                }
                // 3. Artistas relacionados: abre a página do artista e pega topSongs dos relacionados
                if (candidates.size < 20) {
                    runCatching {
                        val artistId = ytMusicRepository.resolveArtist(seed.artist)?.id
                        if (!artistId.isNullOrBlank()) {
                            val page = ytMusicRepository.browseArtist(artistId)
                            page.relatedArtists.shuffled().take(3).forEach { related ->
                                runCatching {
                                    candidates += ytMusicRepository.browseArtist(related.id).topSongs
                                }
                            }
                        }
                    }
                }
            }
        }
        candidates
            .distinctBy { "${it.source}_${it.id}" }
            .filter { it.id != seed.id && "${it.source}_${it.id}" !in excludeIds && it.id !in excludeIds }
            .shuffled()
            .take(30)
    }.getOrElse { e ->
        Log.w(TAG, "buildRadioContinuation failed for ${seed.id}: ${e.message}")
        emptyList()
    }

    // =====================================================================
    //  SEARCH
    // =====================================================================

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
        var topArtistsLoaded: Boolean = false,
    )

    private val searchStates = mutableMapOf<String, SearchState>()

    suspend fun search(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
    ): SearchResult {
        if (query.isBlank()) return SearchResult.Success(emptyList(), false)
        val stateKey = "$query|${filter.name}"
        val state = searchStates.getOrPut(stateKey) { SearchState() }
        if (!loadMore) {
            state.seenKeys.clear()
            state.ytHasMore = true
            state.ytmContinuation = null
            state.ytmHasMore = true
            state.topArtistsLoaded = false
            youTubeRepository.resetSession()
        }
        return when (filter) {
            SearchFilter.ALL -> searchAll(query, loadMore, state)
            SearchFilter.VIDEOS -> searchVideos(query, loadMore, state)
            SearchFilter.SONGS -> searchSongs(query, loadMore, state)
            SearchFilter.PLAYLISTS -> searchPlaylists(query, loadMore, state)
            else -> searchOther(query, filter, loadMore, state)
        }
    }

    private suspend fun searchAll(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult = coroutineScope {
        val pageSize = if (loadMore) 10 else 20
        val topArtistsDeferred = if (!state.topArtistsLoaded && !loadMore) {
            async { runCatching { ytMusicRepository.searchArtists(query) }.getOrDefault(emptyList()).take(3) }
        } else null
        val ytDeferred = async { youTubeRepository.searchVideos(query, loadMore, pageSize) }
        val ytmDeferred = async { ytMusicRepository.search(query, SearchFilter.ALL, state.ytmContinuation) }
        val ytPage = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        val topArtists = topArtistsDeferred?.await().orEmpty()
        state.ytHasMore = ytPage.hasMore
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val merged = mutableListOf<SearchHit>()
        if (topArtists.isNotEmpty()) {
            topArtists.forEach { merged += SearchHit.ArtistHit(it) }
            state.topArtistsLoaded = true
        }
        
        val ytmTracksOnly = ytmPage.items.filterIsInstance<SearchHit.SongHit>()
        val ytmAlbums = ytmPage.items.filterIsInstance<SearchHit.AlbumHit>()
        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()

        val validAlbumsDeferred = async { validateAlbums(ytmAlbums.take(3)) }
        val validPlaylistsDeferred = async { validatePlaylists(ytmPlaylists.take(3)) }

        merged += interleave(ytPage.items, ytmTracksOnly.take(pageSize))
        
        if (!loadMore) {
            merged += validAlbumsDeferred.await()
            merged += validPlaylistsDeferred.await()
        }

        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytHasMore || state.ytmHasMore)
    }

    private suspend fun searchVideos(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val ytPage = youTubeRepository.searchVideos(query, loadMore, 30)
        state.ytHasMore = ytPage.hasMore
        val newHits = ytPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytHasMore)
    }

    private suspend fun searchSongs(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val ytmPage = ytMusicRepository.search(query, SearchFilter.SONGS, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.take(30).filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun searchPlaylists(query: String, loadMore: Boolean, state: SearchState): SearchResult = coroutineScope {
        val ytDeferred = async { if (!loadMore) youTubeRepository.searchPlaylists(query, loadMore = false) else emptyList() }
        val ytmDeferred = async { ytMusicRepository.search(query, SearchFilter.PLAYLISTS, state.ytmContinuation) }
        val ytHits = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val ytmPlaylists = ytmPage.items.filterIsInstance<SearchHit.PlaylistHit>()
        val ytFiltered = ytHits.filterNot { hit ->
            hit.playlist.title.startsWith("Mix -", ignoreCase = true) &&
                hit.playlist.id.contains("RD", ignoreCase = true)
        }
        // YouTube playlists come without view counts, so fill them in like the YT Music ones.
        val ytWithStats = ytMusicRepository.fillPlaylistStats(ytFiltered)
        val allPlaylists = (ytWithStats + ytmPlaylists).distinctBy { hit ->
            YTMusicTextUtils.normalizePlaylistBrowseId(hit.playlist.id)
        }
        val validated = validatePlaylists(allPlaylists)
        val newHits = validated.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    private suspend fun validateAlbums(albums: List<SearchHit.AlbumHit>): List<SearchHit.AlbumHit> = coroutineScope {
        if (albums.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(6)
        val deferred = albums.map { hit ->
            async<SearchHit.AlbumHit?> {
                semaphore.withPermit {
                    val hasTracks = runCatching {
                        withTimeoutOrNull(10000L) { ytMusicRepository.browseAlbum(hit.album.id).tracks.isNotEmpty() }
                    }.getOrNull()
                    if (hasTracks == false) null else hit
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    private suspend fun validatePlaylists(playlists: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> = coroutineScope {
        if (playlists.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(8)
        val deferred = playlists.map { hit ->
            async<SearchHit.PlaylistHit?> {
                semaphore.withPermit {
                    val count = hit.playlist.trackCount
                    if (count != null && count > 0) return@withPermit hit
                    val hasTracks = runCatching {
                        withTimeoutOrNull(10000L) { ytMusicRepository.browsePlaylist(hit.playlist.id).tracks.isNotEmpty() }
                    }.getOrNull()
                    if (hasTracks == false) null else hit
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    private suspend fun searchOther(query: String, filter: SearchFilter, loadMore: Boolean, state: SearchState): SearchResult = coroutineScope {
        val ytmPage = ytMusicRepository.search(query, filter, state.ytmContinuation)
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        if (filter == SearchFilter.ALBUMS) {
            val albums = ytmPage.items.filterIsInstance<SearchHit.AlbumHit>()
            val validated = validateAlbums(albums)
            val newHits = validated.filter { state.seenKeys.add(it.stableKey) }
            return@coroutineScope SearchResult.Success(newHits, state.ytmHasMore)
        }
        val newHits = ytmPage.items.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = mutableListOf<T>()
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private companion object { const val TAG = "VzTuneMusic" }
}
FIX5EOF

cat > "data/src/main/kotlin/com/vztechcore/vztune/data/player/PlayerController.kt" <<'FIX5EOF'
package com.vztechcore.vztune.data.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.player.PlaybackState
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val downloadedTracksStore: DownloadedTracksStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "vztune_media"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    val player: ExoPlayer = run {
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val d = duration.coerceAtLeast(0L)
                            _state.update { it.copy(isLoading = false, durationMs = d) }
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            playNext(auto = true)
                        }
                        Log.d(TAG, "state=$playbackState")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "error=${error.errorCodeName}: ${error.message}", error)
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                isLoading = false,
                                error = "${error.errorCodeName}: ${error.message}",
                            )
                        }
                    }
                })
            }
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var preloadJob: Job? = null

    private var queue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    // FIX 3 - Rádio infinito: seed original + ids já tocados. Quando a fila está
    // acabando, busca o próximo lote e anexa (nunca termina).
    private var radioSeed: Track? = null
    private var radioPlayedIds: MutableSet<String> = mutableSetOf()
    private var isExtendingRadio = false
    private var preloadedUrl: String? = null
    private var preloadedIndex: Int = -1

    private var consecutiveFailures = 0

    init {
        scope.launch {
            while (true) {
                delay(1000)
                if (player.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                    maybePreloadNext()
                }
            }
        }
    }

    /**
     * FIX 3: inicia o rádio a partir de [seed] com o 1º lote já buscado.
     * A fila cresce sozinha via maybeExtendRadio() / playNext(auto=true).
     */
    fun startRadio(seed: Track, firstBatch: List<Track>) {
        val batch = if (firstBatch.isEmpty()) listOf(seed) else firstBatch
        playQueue(batch, batch.indexOfFirst { it.id == seed.id && it.source == seed.source }.takeIf { it >= 0 } ?: 0)
        radioSeed = seed
        radioPlayedIds = batch.map { "${it.source}_${it.id}" }.toMutableSet()
        isExtendingRadio = false
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        // Fila normal (playlist/álbum/busca) cancela o rádio.
        radioSeed = null
        radioPlayedIds.clear()
        isExtendingRadio = false
        consecutiveFailures = 0
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        queue = tracks
        currentIndex = safeIndex
        preloadedUrl = null
        preloadedIndex = -1
        playAtIndex(safeIndex)
    }

    fun play(track: Track) {
        playQueue(listOf(track), 0)
    }

    fun addToQueueNext(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val mutableQueue = queue.toMutableList()
        val existingIndex = mutableQueue.indexOfFirst { it.id == track.id && it.source == track.source }
        if (existingIndex != -1) {
            mutableQueue.removeAt(existingIndex)
            if (existingIndex <= currentIndex && currentIndex > 0) {
                currentIndex--
            }
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, mutableQueue.size)
        mutableQueue.add(insertIndex, track)
        queue = mutableQueue
        if (preloadedIndex == insertIndex) {
            preloadedIndex = -1
            preloadedUrl = null
        }
        if (currentIndex == -1 && queue.isNotEmpty()) {
            currentIndex = 0
            playAtIndex(0)
        }
    }

    fun addToQueueEnd(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val mutableQueue = queue.toMutableList()
        if (mutableQueue.none { it.id == track.id && it.source == track.source }) {
            mutableQueue.add(track)
            queue = mutableQueue
        }
    }

    fun playNext(auto: Boolean = false) {
        val nextIdx = currentIndex + 1
        if (nextIdx >= queue.size) {
            // Rádio infinito: fila acabou -> busca mais um lote e continua, nunca pausa.
            if (auto && radioSeed != null) {
                maybeExtendRadio { extended ->
                    if (extended) playAtIndex(currentIndex + 1)
                    else {
                        player.pause()
                        _state.update { it.copy(isPlaying = false) }
                    }
                }
                return
            }
            if (auto) {
                player.pause()
                _state.update { it.copy(isPlaying = false) }
            }
            return
        }
        // Rádio: quando faltam 5 pra acabar, já busca o próximo lote em background.
        if (radioSeed != null && queue.size - nextIdx <= 5) {
            maybeExtendRadio {}
        }
        playAtIndex(nextIdx)
    }

    /** Busca o próximo lote do rádio e anexa na fila. Chama [onDone] com true se anexou algo. */
    private fun maybeExtendRadio(onDone: (Boolean) -> Unit) {
        val seed = radioSeed ?: run { onDone(false); return }
        if (isExtendingRadio) { onDone(false); return }
        isExtendingRadio = true
        scope.launch {
            val last = queue.lastOrNull()
            val exclude = radioPlayedIds.toSet()
            val next = runCatching {
                musicRepository.buildRadioContinuation(seed, exclude, last)
            }.getOrDefault(emptyList())
            if (next.isNotEmpty()) {
                val fresh = next.filter { "${it.source}_${it.id}" !in radioPlayedIds }
                if (fresh.isNotEmpty()) {
                    queue = queue + fresh
                    radioPlayedIds += fresh.map { "${it.source}_${it.id}" }
                    isExtendingRadio = false
                    onDone(true)
                    return@launch
                }
            }
            val retry = runCatching {
                musicRepository.buildRadioContinuation(seed, exclude, seed)
            }.getOrDefault(emptyList())
            val freshRetry = retry.filter { "${it.source}_${it.id}" !in radioPlayedIds }
            if (freshRetry.isNotEmpty()) {
                queue = queue + freshRetry
                radioPlayedIds += freshRetry.map { "${it.source}_${it.id}" }
            }
            isExtendingRadio = false
            onDone(freshRetry.isNotEmpty())
        }
    }

    fun playPrevious() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0)
            return
        }
        val prevIdx = currentIndex - 1
        if (prevIdx < 0) {
            player.seekTo(0)
            return
        }
        playAtIndex(prevIdx)
    }

    fun pause() = player.pause()
    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun release() {
        player.release()
        mediaCache.release()
    }

    private fun playAtIndex(idx: Int) {
        if (idx !in queue.indices) {
            _state.update {
                it.copy(isLoading = false, isPlaying = false, error = "Fim da fila.")
            }
            return
        }

        val track = queue[idx]
        val cachedUrl = if (preloadedIndex == idx) preloadedUrl else null
        preloadedUrl = null
        preloadedIndex = -1
        currentIndex = idx

        _state.update {
            it.copy(
                currentTrack = track,
                isPlaying = false,
                isLoading = true,
                error = null,
                positionMs = 0L,
                durationMs = 0L,
            )
        }

        loadJob?.cancel()
        loadJob = scope.launch {
            // Prioritize local downloaded file for instant offline playback
            val localFile = downloadedTracksStore.getLocalFile(track)
            val mediaUri: Uri? = if (localFile != null) {
                Uri.fromFile(localFile)
            } else {
                val url = cachedUrl ?: musicRepository.getStreamUrl(track)
                url?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            }

            if (mediaUri == null) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.e(TAG, "Too many consecutive failures (${consecutiveFailures}), stopping")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isPlaying = false,
                            error = "Falha ao carregar áudio (${consecutiveFailures} tentativas).",
                        )
                    }
                    return@launch
                }
                Log.w(TAG, "Stream URL null for ${track.id} '${track.title}', auto-skipping")
                scope.launch { playAtIndex(idx + 1) }
                return@launch
            }

            consecutiveFailures = 0
            ensureForegroundService()
            try {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(MediaItem.fromUri(mediaUri))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun ensureForegroundService() {
        runCatching {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to start PlaybackService: ${e.message}")
        }
    }

    private fun maybePreloadNext() {
        val duration = player.duration
        if (duration <= 0) return
        val remaining = duration - player.currentPosition
        if (remaining <= 0 || remaining > PRELOAD_WINDOW_MS) return
        val nextIdx = currentIndex + 1
        if (nextIdx >= queue.size) return
        if (preloadedIndex == nextIdx) return

        preloadedIndex = nextIdx
        preloadedUrl = null
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val nextTrack = queue[nextIdx]
            if (downloadedTracksStore.getLocalFile(nextTrack) == null) {
                preloadedUrl = musicRepository.getStreamUrl(nextTrack)
            }
        }
    }

    private companion object {
        const val TAG = "VzTunePlayer"
        const val CACHE_SIZE_BYTES = 200L * 1024 * 1024
        const val PRELOAD_WINDOW_MS = 10_000L
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt" <<'FIX5EOF'
package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.RecentPlayedStore
import com.vztechcore.vztune.data.prefs.store.SearchHistoryStore
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val historyStore: SearchHistoryStore,
    private val recentPlayedStore: RecentPlayedStore,
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val trackDownloader: TrackDownloader,
    private val playerController: PlayerController,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenPlaylist(
            val id: String, val title: String, val owner: String?,
            val thumbnailUrl: String?, val mosaic: List<String>,
        ) : NavigationEvent
        data class OpenAlbum(
            val id: String, val title: String, val artist: String,
            val thumbnailUrl: String?,
        ) : NavigationEvent
        data class OpenArtist(
            val id: String, val name: String, val thumbnailUrl: String?,
        ) : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private data class CachedTab(val query: String, val hits: List<SearchHit>, val hasMore: Boolean)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.items
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filterCache = mutableMapOf<SearchFilter, CachedTab>()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            historyStore.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch {
            recentPlayedStore.tracks.collect { list ->
                _uiState.update { it.copy(recentTracks = list) }
            }
        }
    }

    fun onQueryChange(value: String) {
        suggestJob?.cancel()
        searchJob?.cancel()
        filterCache.clear()
        _uiState.update {
            it.copy(
                query = value,
                errorMessage = null,
                hasSearched = false,
                hits = emptyList(),
                hasMore = false,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
            )
        }
        if (value.isBlank() || value.trim().length < 2) {
            _uiState.update { it.copy(suggestions = SearchSuggestions()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(400)
            val s = musicRepository.suggestions(value)
            _uiState.update { it.copy(suggestions = s) }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
    }

    fun onFilterSelect(filter: SearchFilter) {
        if (filter == _uiState.value.selectedFilter) return
        val currentQuery = _uiState.value.query
        val cached = filterCache[filter]
        if (cached != null && cached.query == currentQuery) {
            _uiState.update { it.copy(selectedFilter = filter, hits = cached.hits, hasMore = cached.hasMore, hasSearched = true) }
            return
        }
        _uiState.update { it.copy(selectedFilter = filter) }
        if (currentQuery.isNotBlank()) {
            runSearch(currentQuery, filter, isRefresh = false, loadMore = false)
        }
    }

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(query) }
        filterCache.clear()
        runSearch(query, _uiState.value.selectedFilter, isRefresh = false, loadMore = false)
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        filterCache.clear()
        runSearch(_uiState.value.query, _uiState.value.selectedFilter, isRefresh = true, loadMore = false)
    }

    fun onRemoveHistory(query: String) { viewModelScope.launch { historyStore.remove(query) } }
    fun onClearHistory() { viewModelScope.launch { historyStore.clear() } }

    fun onLoadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.query.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val filter = state.selectedFilter
            when (val result = musicRepository.search(state.query, filter, loadMore = true)) {
                is SearchResult.Success -> {
                    val merged = _uiState.value.hits + result.hits
                    filterCache[filter] = CachedTab(state.query, merged, result.hasMore)
                    _uiState.update { it.copy(isLoadingMore = false, hits = merged, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onHitClick(hit: SearchHit) {
        when (hit) {
            is SearchHit.SongHit -> playRadio(hit.track)
            is SearchHit.VideoHit -> playRadio(hit.track)
            is SearchHit.PlaylistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenPlaylist(
                            hit.playlist.id,
                            hit.playlist.title,
                            hit.playlist.owner,
                            hit.playlist.thumbnailUrl,
                            hit.playlist.mosaicThumbnails,
                        )
                    )
                }
            }
            is SearchHit.AlbumHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenAlbum(
                            hit.album.id,
                            hit.album.title,
                            hit.album.artist,
                            hit.album.thumbnailUrl,
                        )
                    )
                }
            }
            is SearchHit.ArtistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            hit.artist.id,
                            hit.artist.name,
                            hit.artist.thumbnailUrl,
                        )
                    )
                }
            }
        }
    }

    fun onPlayNextClick(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueueClick(track: Track) {
        playerController.addToQueueEnd(track)
    }

    /** Strictly toggles favorite status without triggering any automatic download. */
    fun onToggleFavorite(track: Track) {
        viewModelScope.launch {
            favoriteTracksStore.toggle(track)
        }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            localPlaylistsStore.addTrack(playlistId, track)
        }
    }

    fun onDownloadTrack(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Mais parecido por Levenshtein, nunca mais o primeiro da busca
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }
    }

    private fun playRadio(seed: Track) {
        if (_uiState.value.isBuildingQueue) return
        _uiState.update { it.copy(isBuildingQueue = true) }
        viewModelScope.launch { recentPlayedStore.add(seed) }
        viewModelScope.launch {
            val queue = runCatching { musicRepository.buildRadioQueue(seed) }.getOrDefault(listOf(seed))
            _uiState.update { it.copy(isBuildingQueue = false) }
            // Rádio infinito: startRadio() faz a fila crescer sozinha quando está acabando.
            playerController.startRadio(seed, queue)
        }
    }

    private fun runSearch(query: String, filter: SearchFilter, isRefresh: Boolean, loadMore: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !loadMore,
                    isRefreshing = isRefresh,
                    hasSearched = true,
                    errorMessage = null,
                )
            }
            when (val result = musicRepository.search(query, filter, loadMore)) {
                is SearchResult.Success -> {
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, hits = result.hits, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message) }
                }
            }
        }
    }
}
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" <<'FIX5EOF'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.EclipseBackground
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.sheets.TrackMenuContext
import com.vztechcore.vztune.ui.components.sheets.TrackMenuViewModel
import com.vztechcore.vztune.ui.components.sheets.TrackOptionsSheet
import com.vztechcore.vztune.ui.screens.artist.ArtistScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistViewModel
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListScreen
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadBannerViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadsScreen
import com.vztechcore.vztune.ui.screens.downloads.DownloadsViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryScreen
import com.vztechcore.vztune.ui.screens.library.LibraryViewModel
import com.vztechcore.vztune.ui.screens.library.list.LibraryListScreen
import com.vztechcore.vztune.ui.screens.library.list.LibraryListViewModel
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel
import com.vztechcore.vztune.ui.screens.medialist.MediaListScreen
import com.vztechcore.vztune.ui.screens.medialist.MediaListViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoScreen
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface

private data class ActiveTrackMenu(
    val track: Track,
    val context: TrackMenuContext = TrackMenuContext.DEFAULT,
    val localPlaylistId: String? = null,
)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentTab = when {
        backStackEntry?.destination?.hasRoute<Routes.Search>() == true -> AppTab.SEARCH
        backStackEntry?.destination?.hasRoute<Routes.Analytics>() == true -> AppTab.ANALYTICS
        backStackEntry?.destination?.hasRoute<Routes.Library>() == true -> AppTab.LIBRARY
        else -> AppTab.HOME
    }

    val isMediaListScreen = backStackEntry?.destination?.hasRoute<Routes.MediaList>() == true
    val isArtistScreen = backStackEntry?.destination?.hasRoute<Routes.Artist>() == true
    val isArtistListScreen = backStackEntry?.destination?.hasRoute<Routes.ArtistList>() == true
    val isLibraryListScreen = backStackEntry?.destination?.hasRoute<Routes.LibraryList>() == true
    val isDownloadsScreen = backStackEntry?.destination?.hasRoute<Routes.Downloads>() == true
    val isDetailScreen = isMediaListScreen || isArtistScreen || isArtistListScreen || isLibraryListScreen || isDownloadsScreen
    val isTrackInfoScreen = backStackEntry?.destination?.hasRoute<Routes.TrackInfo>() == true

    val playback = LocalNowPlaying.current
    val track = playback.currentTrack

    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val bannerVm: DownloadBannerViewModel = hiltViewModel()
    val menuVm: TrackMenuViewModel = hiltViewModel()

    val dominantColor = rememberDominantColor(track?.thumbnailUrl)
    val isMinimized by miniPlayerVm.isMinimized.collectAsStateWithLifecycle()
    val bannerTrack by bannerVm.bannerTrack.collectAsStateWithLifecycle()

    val favoriteKeys by menuVm.favoriteKeys.collectAsStateWithLifecycle()
    val downloadedKeys by menuVm.downloadedKeys.collectAsStateWithLifecycle()
    val activeDownloads by menuVm.activeDownloads.collectAsStateWithLifecycle()
    val localPlaylists by menuVm.localPlaylists.collectAsStateWithLifecycle()
    val resolvedAlbum by menuVm.resolvedAlbum.collectAsStateWithLifecycle()

    var activeMenu by remember { mutableStateOf<ActiveTrackMenu?>(null) }

    fun openTrackMenu(
        targetTrack: Track,
        context: TrackMenuContext = TrackMenuContext.DEFAULT,
        localPlaylistId: String? = null,
    ) {
        menuVm.resolveAlbumFor(targetTrack)
        activeMenu = ActiveTrackMenu(targetTrack, context, localPlaylistId)
    }

    LaunchedEffect(Unit) {
        menuVm.navigationEvent.collect { event ->
            when (event) {
                is TrackMenuViewModel.MenuNavigationEvent.OpenAlbum -> navController.openAlbum(event.album)
                is TrackMenuViewModel.MenuNavigationEvent.OpenArtist -> navController.navigate(
                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                )
            }
        }
    }

    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            miniPlayerVm.setMinimized(true)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30f) miniPlayerVm.setMinimized(true)
                else if (available.y > 30f) miniPlayerVm.setMinimized(false)
                return Offset.Zero
            }
        }
    }

    activeMenu?.let { menu ->
        val key = StoredTrack.from(menu.track).key
        TrackOptionsSheet(
            track = menu.track,
            menuContext = menu.context,
            resolvedAlbum = resolvedAlbum,
            isFavorite = key in favoriteKeys,
            isDownloaded = key in downloadedKeys,
            downloadItem = activeDownloads[key],
            localPlaylists = localPlaylists,
            onDismiss = { activeMenu = null },
            onRemoveFromRecentHistory = menuVm::onRemoveFromRecentHistory,
            onRemoveFromLocalPlaylist = { t ->
                menu.localPlaylistId?.let { pid -> menuVm.onRemoveFromLocalPlaylist(pid, t) }
            },
            onToggleFavorite = menuVm::onToggleFavorite,
            onAddToPlaylist = menuVm::onAddToPlaylist,
            onPlayNext = menuVm::onPlayNext,
            onAddToQueue = menuVm::onAddToQueue,
            onDownload = menuVm::onDownload,
            onRemoveDownload = menuVm::onRemoveDownload,
            onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
            onOpenAlbum = { album -> navController.openAlbum(album) },
            onOpenAlbumForTrack = menuVm::onOpenAlbumForTrack,
            onOpenArtistByName = menuVm::onOpenArtistByName,
            onOpenArtistForTrack = menuVm::onOpenArtistForTrack,
            onStartRadio = menuVm::onStartRadio,
            onTrackInfo = { t -> navController.navigateToTrackInfo(t) },
        )
    }

    EclipseBackground(baseColor = dominantColor) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
            ) {
                composable<Routes.Home> { Placeholder("Home — coming soon") }
                composable<Routes.Analytics> { Placeholder("Analytics — coming soon") }

                composable<Routes.Library> {
                    val vm: LibraryViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        state = state,
                        onTabSelected = vm::onTabSelected,
                        onShortcutClick = { navController.navigate(Routes.LibraryList(it.name)) },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(t, context = TrackMenuContext.RECENT_HISTORY)
                        },
                        onPlaylistClick = { item ->
                            navController.navigate(
                                Routes.MediaList(
                                    id = item.id,
                                    // Favorito de álbum abre como álbum, senão crashava com key duplicada
                                    isAlbum = item.isAlbum,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.imageUrl,
                                )
                            )
                        },
                        onCreatePlaylist = vm::createPlaylist,
                    )
                }

                composable<Routes.LibraryList> {
                    val vm: LibraryListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onShuffleClick = vm::onShuffleClick,
                        onPlayAllClick = vm::onPlayAllClick,
                        onDownloadsHistoryClick = { navController.navigate(Routes.Downloads) },
                    )
                }

                composable<Routes.Downloads> {
                    val vm: DownloadsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DownloadsScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTogglePauseResume = vm::onTogglePauseResume,
                        onCancelDownload = vm::onCancelDownload,
                        onDeleteDownload = vm::onDeleteDownload,
                        onPlayTrack = vm::onPlayTrack,
                        onPauseAll = vm::onPauseAll,
                        onResumeAll = vm::onResumeAll,
                        onCancelAll = vm::onCancelAll,
                    )
                }

                composable<Routes.Search> {
                    val vm: SearchViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is SearchViewModel.NavigationEvent.OpenPlaylist -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = false,
                                        title = event.title,
                                        subtitle = event.owner,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenAlbum -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = true,
                                        title = event.title,
                                        subtitle = event.artist,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                            }
                        }
                    }

                    SearchScreen(
                        state = state,
                        favoriteKeys = favoriteKeys,
                        downloadedKeys = downloadedKeys,
                        activeDownloads = activeDownloads,
                        localPlaylists = localPlaylists,
                        resolvedAlbum = resolvedAlbum,
                        onQueryChange = vm::onQueryChange,
                        onFocusChanged = vm::onFocusChanged,
                        onFilterSelect = vm::onFilterSelect,
                        onSubmit = vm::onSubmit,
                        onRefresh = vm::onRefresh,
                        onRemoveHistory = vm::onRemoveHistory,
                        onClearHistory = vm::onClearHistory,
                        onLoadMore = vm::onLoadMore,
                        onHitClick = vm::onHitClick,
                        onPlayNextClick = vm::onPlayNextClick,
                        onAddToQueueClick = vm::onAddToQueueClick,
                        onToggleFavorite = vm::onToggleFavorite,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadTrack = vm::onDownloadTrack,
                        onRemoveDownload = vm::onRemoveDownload,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onPrepareTrackMenu = menuVm::resolveAlbumFor,
                        onOpenAlbum = { album -> navController.openAlbum(album) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                    )
                }

                composable<Routes.MediaList> {
                    val vm: MediaListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val playlistId = state.listId
                    val menuCtx = when {
                        state.isAlbum -> TrackMenuContext.DEFAULT
                        state.isLocal || isLocalPlaylistId(playlistId) -> TrackMenuContext.LOCAL_PLAYLIST
                        else -> TrackMenuContext.EXTERNAL_PLAYLIST
                    }

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is MediaListViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                                )
                                MediaListViewModel.NavigationEvent.CloseScreen -> navController.popBackStack()
                            }
                        }
                    }

                    MediaListScreen(
                        state = state,
                        activeDownloads = activeDownloads,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTrackClick = vm::onTrackClick,
                        onPlayAllClick = vm::onPlayAll,
                        onShuffleClick = vm::onShuffle,
                        onSortSelected = vm::onSortOrderSelected,
                        onReorder = vm::onReorder,
                        onStartShuffle = vm::startShuffle,
                        onShuffleOnce = vm::shuffleOnce,
                        onStopShuffle = vm::stopShuffle,
                        onPlayNextClick = vm::onPlayNextClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(
                                targetTrack = t,
                                context = menuCtx,
                                localPlaylistId = playlistId.takeIf { menuCtx == TrackMenuContext.LOCAL_PLAYLIST },
                            )
                        },
                        onFavoriteClick = vm::onFavoriteClick,
                        onPlayNextAll = vm::onPlayNextAll,
                        onAddToQueueAll = vm::onAddToQueueAll,
                        onDownloadAll = vm::onDownloadAll,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onOpenArtist = vm::onOpenArtist,
                        onAlbumClick = { album ->
                            if (album.id != state.listId) navController.openAlbum(album)
                        },
                        onDeleteLocalPlaylist = vm::onDeleteLocalPlaylist,
                        onRenameLocalPlaylist = vm::onRenameLocalPlaylist,
                        onChangeLocalPlaylistCover = vm::onChangeLocalPlaylistCover,
                    )
                }

                composable<Routes.Artist> {
                    val vm: ArtistViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onRandomClick = vm::onRandomClick,
                        onFollowClick = vm::onFollowClick,
                        onSongClick = vm::onSongClick,
                        onVideoClick = vm::onVideoClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onSeeAllClick = { kind, ref ->
                            navController.navigate(
                                Routes.ArtistList(
                                    artistId = state.artistId,
                                    artistName = state.name,
                                    kind = kind.name,
                                    browseId = ref.browseId,
                                    params = ref.params,
                                )
                            )
                        },
                    )
                }

                composable<Routes.ArtistList> {
                    val vm: ArtistListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onLoadMore = vm::onLoadMore,
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                    )
                }

                composable<Routes.TrackInfo> {
                    val vm: TrackInfoViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    TrackInfoScreen(
                        state = state,
                        isDownloaded = state.isDownloaded,
                        onBackClick = { navController.popBackStack() },
                        onFavoriteClick = vm::onFavoriteClick,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadClick = {
                            if (state.isDownloaded) vm.onRemoveDownloadClick() else vm.onDownloadClick()
                        },
                    )
                }
            }

            PlaybackChrome(
                navController = navController,
                currentTab = currentTab,
                playback = playback,
                track = track,
                dominantColor = dominantColor,
                miniPlayerVm = miniPlayerVm,
                isMinimized = isMinimized,
                isDetailScreen = isDetailScreen,
                isTrackInfoScreen = isTrackInfoScreen,
                bannerTrack = bannerTrack,
                onDismissBanner = bannerVm::dismissBanner,
                onViewDownloadsClick = { navController.navigate(Routes.Downloads) },
            )
        }
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}
FIX5EOF

cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt" <<'FIX5EOF'
package com.vztechcore.vztune.ui.screens.medialist

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.ui.components.download.LocalDownloadedKeys
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.sheets.MediaListArtistPickerSheet
import com.vztechcore.vztune.ui.components.sheets.MediaListOptionsSheet
import com.vztechcore.vztune.ui.components.sheets.parseMediaListArtists
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val HEADER_OFFSET = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(
    state: MediaListUiState,
    activeDownloads: Map<String, TrackDownloadItem> = emptyMap(),
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onStartShuffle: () -> Unit,
    onShuffleOnce: () -> Unit,
    onStopShuffle: () -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayNextAll: () -> Unit = {},
    onAddToQueueAll: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onOpenArtistByName: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onDeleteLocalPlaylist: () -> Unit = {},
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nowPlaying = LocalNowPlaying.current
    val downloadedKeys = LocalDownloadedKeys.current
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showListOptionsSheet by remember { mutableStateOf(false) }
    var showHeaderArtistPicker by remember { mutableStateOf(false) }
    var shuffleJob by remember { mutableStateOf<Job?>(null) }

    val isCollapsed by rememberHeaderCollapsed(listState)

    // Fundo preto AMOLED - sem camaleao, sem gradiente de cor dominante
    val dominantColor = Color.Black
    // Thumb do header colapsado: se a capa for token de cor, usa o mosaico
    val collapsedThumbnailUrl = remember(state.thumbnailUrl, state.mosaicThumbnails) {
        val raw = state.thumbnailUrl
        if (!raw.isNullOrBlank() && !PlaylistCoverPalette.isDefaultColorToken(raw)) raw
        else state.mosaicThumbnails.firstOrNull()
    }

    val ownerArtists = remember(state.owner, state.isLocal) {
        if (!state.isLocal) parseMediaListArtists(state.owner.orEmpty()) else emptyList()
    }

    // FIX 2: playlist do YouTube sem UC... (canal real) -> sem clique no artista.
    // Antes clicava e caía na busca por nome no YT Music, abrindo artista errado.
    // Com UC..., o clique abre o canal. Álbum / playlist YT Music mantém o comportamento.
    val isYouTubePlaylist = !state.isAlbum && !state.isLocal &&
        state.allTracks.isNotEmpty() && state.allTracks.all { it.source == Source.YOUTUBE }
    val hasRealChannel = state.artistId?.startsWith("UC") == true
    val handleHeaderArtistClick: (() -> Unit)? = remember(state.isLocal, state.owner, ownerArtists, isYouTubePlaylist, hasRealChannel) {
        if (state.isLocal || state.owner.isNullOrBlank()) {
            null
        } else if (isYouTubePlaylist && !hasRealChannel) {
            null
        } else if (ownerArtists.size > 1 && !isYouTubePlaylist) {
            { showHeaderArtistPicker = true }
        } else {
            onOpenArtist
        }
    }

    val displayedTracks = remember(state.filteredTracks, state.sortOrder, state.manualOrder, state.selectedTab, state.isAlbum) {
        state.getDisplayTracks()
    }

    val allTracksDownloaded = remember(state.allTracks, downloadedKeys) {
        state.allTracks.isNotEmpty() && state.allTracks.all {
            StoredTrack.from(it).key in downloadedKeys
        }
    }

    val batchDownloadState = remember(state.allTracks, downloadedKeys, activeDownloads) {
        if (state.allTracks.isEmpty()) {
            false to 0f
        } else {
            var activeCount = 0
            var totalProgress = 0f
            state.allTracks.forEach { t ->
                val k = StoredTrack.from(t).key
                val item = activeDownloads[k]
                when {
                    k in downloadedKeys -> totalProgress += 1f
                    item != null && (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) -> {
                        activeCount++
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                    item != null && item.status == DownloadStatus.PAUSED -> {
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                }
            }
            val isRunning = activeCount > 0
            val avgProgress = (totalProgress / state.allTracks.size.toFloat()).coerceIn(0f, 1f)
            isRunning to avgProgress
        }
    }

    val canReorder = !state.isAlbum && state.sortOrder == SortOrder.MANUAL && state.selectedTab == MediaListTab.ALL

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!canReorder) return@rememberReorderableLazyListState
        val fromTrack = from.index - HEADER_OFFSET
        val toTrack = to.index - HEADER_OFFSET
        if (fromTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        if (toTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        onReorder(fromTrack, toTrack)
    }

    LaunchedEffect(state.isShuffling) {
        if (state.isShuffling) {
            shuffleJob?.cancel()
            shuffleJob = launch {
                while (isActive) {
                    onShuffleOnce()
                    delay(150)
                }
            }
            delay(1500)
            onStopShuffle()
        } else {
            shuffleJob?.cancel()
        }
    }

    if (showHeaderArtistPicker && ownerArtists.size > 1) {
        MediaListArtistPickerSheet(
            artists = ownerArtists,
            onPick = { selectedArtist ->
                showHeaderArtistPicker = false
                onOpenArtistByName(selectedArtist)
            },
            onDismiss = { showHeaderArtistPicker = false },
        )
    }

    if (showSortSheet) {
        MediaListSortSheet(
            currentSort = state.sortOrder,
            onSelect = { onSortSelected(it); showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showListOptionsSheet) {
        MediaListOptionsSheet(
            state = state,
            allTracksDownloaded = allTracksDownloaded,
            isDownloadingBatch = batchDownloadState.first,
            batchDownloadProgress = batchDownloadState.second,
            onDismiss = { showListOptionsSheet = false },
            onToggleFavorite = onFavoriteClick,
            onPlayNextAll = onPlayNextAll,
            onAddToQueueAll = onAddToQueueAll,
            onDownloadAll = onDownloadAll,
            onOpenDownloadsScreen = onOpenDownloadsScreen,
            onOpenArtistByName = onOpenArtistByName,
            onDeleteLocalPlaylist = onDeleteLocalPlaylist,
            onRenameLocalPlaylist = onRenameLocalPlaylist,
            onChangeLocalPlaylistCover = onChangeLocalPlaylistCover,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            item(key = "header") {
                MediaListExpandedHeader(
                    state = state,
                    dominantColor = dominantColor,
                    allTracksDownloaded = allTracksDownloaded,
                    onBackClick = onBackClick,
                    onTabSelected = onTabSelected,
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onSearchClick = { showSearchOverlay = true },
                    onMoreClick = { showListOptionsSheet = true },
                    onSortClick = { showSortSheet = true },
                    onShuffleToggle = {
                        if (state.isShuffling) onStopShuffle() else onStartShuffle()
                    },
                    onFavoriteClick = onFavoriteClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }
                state.errorMessage != null -> item(key = "error") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.errorMessage, color = Color.Red)
                    }
                }
                displayedTracks.isEmpty() -> item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Nenhuma faixa por aqui.", color = Color.White.copy(alpha = 0.6f))
                    }
                }
                else -> itemsIndexed(
                    items = displayedTracks,
                    // Key com índice: mesmo se o InnerTube devolver a mesma faixa 2x, nunca duplica e nunca crasha
                    key = { index, t -> "${t.source.name}_${t.id}_$index" },
                ) { index, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val isPlaying = isCurrent && nowPlaying.isPlaying
                    val isPaused = isCurrent && !nowPlaying.isPlaying

                    val useVideoLayout = state.selectedTab == MediaListTab.VIDEOS

                    if (useVideoLayout) {
                        MediaListVideoCard(
                            track = track,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                        )
                    } else if (canReorder) {
                        val itemKey = "${track.source.name}_${track.id}_$index"
                        ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                            MediaListTrackRow(
                                track = track,
                                index = if (state.isAlbum) index + 1 else null,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                onClick = { onTrackClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                                onPlayNext = { onPlayNextClick(track) },
                                showDragHandle = true,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                modifier = Modifier
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        )
                                    )
                                    .background(Color.Black.copy(alpha = if (isDragging) 0.5f else 0f)),
                            )
                        }
                    } else {
                        MediaListTrackRow(
                            track = track,
                            index = if (state.isAlbum) index + 1 else null,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                            onPlayNext = { onPlayNextClick(track) },
                            showDragHandle = false,
                            dragHandleModifier = Modifier,
                            modifier = Modifier,
                        )
                    }
                }
            }

            item(key = "footer") {
                MediaListFooter(
                    state = state,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.title,
                thumbnailUrl = collapsedThumbnailUrl,
                dominantColor = dominantColor,
                onBackClick = onBackClick,
                onSearchClick = { showSearchOverlay = true },
                onMoreClick = { showListOptionsSheet = true },
                showFavorite = state.showFavorite,
                isFavorite = state.isFavorite,
                onFavoriteClick = onFavoriteClick,
            )
        }

        if (showSearchOverlay) {
            MediaListSearchOverlay(
                allTracks = displayedTracks,
                isAlbum = state.isAlbum,
                onClose = { showSearchOverlay = false },
                onTrackClick = onTrackClick,
                onPlayNextClick = onPlayNextClick,
                onTrackInfoClick = onTrackInfoClick,
            )
        }
    }
}
FIX5EOF

echo "FIX5 aplicado: 13 arquivos. Sincronize o Gradle e rode o app."
