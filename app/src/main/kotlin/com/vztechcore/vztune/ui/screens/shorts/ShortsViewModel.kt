package com.vztechcore.vztune.ui.screens.shorts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortsUiState(
    val channelId: String = "",
    val channelName: String = "",
    val channelAvatarUrl: String? = null,
    val channelHandle: String? = null,
    val items: List<Track> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val isMuted: Boolean = false,
    val speed: Float = 1f,
    val info: YouTubeRepository.ShortPlaybackInfo? = null,
    val selectedQuality: String? = null,
    val isFollowing: Boolean = false,
    val isFavorite: Boolean = false,
    // Cue de pause/play: token muda a cada toque; a tela anima o icone.
    val cueToken: Long = 0L,
    val cueIsPause: Boolean = false,
)

/**
 * Tela Shorts: ExoPlayer PROPRIO (nao toca no PlayerController).
 * O foco de audio (handleAudioFocus = true) pausa a musica principal ao
 * entrar e o release() ao sair devolve o foco -> o MiniPlayer continua
 * exatamente na mesma faixa em que estava.
 */
@HiltViewModel
class ShortsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val musicRepository: MusicRepository,
    private val followedStore: FollowedArtistsStore,
    private val favoriteTracksStore: FavoriteTracksStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.Shorts>()
    private val items: List<Track> = ShortsFeed.get(route.feedKey)
    private val infoCache = mutableMapOf<String, YouTubeRepository.ShortPlaybackInfo>()
    private var favKeys: Set<String> = emptySet()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(
        ShortsUiState(
            channelId = route.channelId,
            channelName = route.channelName,
            channelAvatarUrl = route.channelAvatarUrl,
            channelHandle = route.channelHandle,
            items = items,
            index = route.startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            isLoading = items.isNotEmpty(),
        )
    )
    val uiState: StateFlow<ShortsUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE // Short roda em loop.
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            })
        }

    init {
        viewModelScope.launch {
            followedStore.isFollowing(route.channelId).collect { f ->
                _state.update { it.copy(isFollowing = f) }
            }
        }
        viewModelScope.launch {
            favoriteTracksStore.tracks
                .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
                .collect { keys ->
                    favKeys = keys
                    _state.update { it.copy(isFavorite = keyOf(it.index) in keys) }
                }
        }
    }

    private val prefetchJobs = mutableMapOf<String, Job>()

    /** Assim que assenta num Short, ja resolve o link do proximo, do anterior e do +2.
     *  Quando o usuario desliza, o cache ja tem tudo -> toca sem delay de busca. */
    private fun prefetchAround(index: Int) {
        for (i in listOf(index + 1, index - 1, index + 2)) {
            val t = items.getOrNull(i) ?: continue
            if (infoCache.containsKey(t.id)) continue
            if (prefetchJobs[t.id]?.isActive == true) continue
            prefetchJobs[t.id] = viewModelScope.launch {
                val info = musicRepository.getShortPlaybackInfo(t.id)
                if (info != null) infoCache[t.id] = info
            }
        }
    }
    private fun keyOf(index: Int): String =
        items.getOrNull(index)?.let { StoredTrack.from(it).key } ?: ""

    /** Chamado quando o pager assenta numa pagina: carrega info + troca o video. */
    fun onPageSettled(index: Int) {
        if (index !in items.indices) return
        val track = items[index]
        val cached = infoCache[track.id]
        _state.update {
            it.copy(
                index = index,
                info = cached,
                isLoading = cached == null,
                isFavorite = keyOf(index) in favKeys,
                selectedQuality = cached?.streams?.firstOrNull()?.qualityLabel,
            )
        }
loadJob?.cancel()
prefetchAround(index)
if (cached != null) {
            playStream(cached.streams.firstOrNull(), 0L)
            return
        }
        loadJob = viewModelScope.launch {
            val info = musicRepository.getShortPlaybackInfo(track.id)
            if (info != null) infoCache[track.id] = info
            if (_state.value.index != index) return@launch
            if (info == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            _state.update {
                it.copy(info = info, isLoading = false, selectedQuality = info.streams.firstOrNull()?.qualityLabel)
            }
            playStream(info.streams.firstOrNull(), 0L)
        }
    }

    private fun playStream(stream: YouTubeRepository.ShortVideoStream?, startPosMs: Long) {
        if (stream == null) return
        val audioUrl = stream.audioUrl
        if (audioUrl != null) {
            val factory = ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())
            val video = factory.createMediaSource(MediaItem.fromUri(stream.url))
            val audio = factory.createMediaSource(MediaItem.fromUri(audioUrl))
            player.setMediaSource(MergingMediaSource(true, true, video, audio))
            if (startPosMs > 0) player.seekTo(startPosMs)
        } else {
            player.setMediaItem(MediaItem.fromUri(stream.url), startPosMs)
        }
        player.prepare()
        player.playWhenReady = true
        _state.update { it.copy(selectedQuality = stream.qualityLabel, isLoading = true) }
    }
    fun selectQuality(label: String) {
        val info = _state.value.info ?: return
        val stream = info.streams.firstOrNull { it.qualityLabel == label } ?: return
        playStream(stream, player.currentPosition)
    }

    fun togglePlayPause() {
        val nowPause = player.isPlaying
        if (nowPause) player.pause() else player.play()
        _state.update { it.copy(cueToken = System.nanoTime(), cueIsPause = nowPause) }
    }

    fun toggleMute() {
        val muted = !_state.value.isMuted
        player.volume = if (muted) 0f else 1f
        _state.update { it.copy(isMuted = muted) }
    }

    fun setSpeed(value: Float) {
        val v = value.coerceIn(0.35f, 4f)
        player.setPlaybackSpeed(v)
        _state.update { it.copy(speed = v) }
    }

    fun toggleFavorite() {
        val track = items.getOrNull(_state.value.index) ?: return
        viewModelScope.launch { favoriteTracksStore.toggle(track) }
    }

    fun toggleFollow() {
        viewModelScope.launch {
            followedStore.toggle(
                FollowedArtist(
                    id = route.channelId,
                    name = route.channelName,
                    thumbnailUrl = route.channelAvatarUrl,
                    isYouTubeChannel = true,
                )
            )
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        player.release() // devolve o foco de audio -> musica principal retoma.
        super.onCleared()
    }
}
