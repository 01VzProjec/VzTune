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
    val qualityMode: String = "auto",
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
@ApplicationContext private val context: Context,
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

    private val prefs = context.getSharedPreferences("vztune_shorts", Context.MODE_PRIVATE)

    private fun loadMode(): String = prefs.getString("quality_mode", "auto") ?: "auto"

    private fun isWifi(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(true)

    /** Escolhe o stream pelo modo: auto(wifi=melhor, dados=menor), high=melhor, save=480/360. */
    private fun pickStream(streams: List<YouTubeRepository.ShortVideoStream>, mode: String): YouTubeRepository.ShortVideoStream? {
        if (streams.isEmpty()) return null
        val sorted = streams.sortedByDescending { it.height }
        val effective = when (mode) {
            "high" -> "high"
            "save" -> "save"
            else -> if (isWifi()) "high" else "save"
        }
        return if (effective == "high") {
            sorted.firstOrNull { it.height > 0 } ?: sorted.first()
        } else {
            sorted.firstOrNull { it.height in 360..480 }
                ?: sorted.firstOrNull { it.height > 240 }
                ?: sorted.first()
        }
    }
    private val _state = MutableStateFlow(
        ShortsUiState(
            channelId = route.channelId,
            channelName = route.channelName,
            channelAvatarUrl = route.channelAvatarUrl,
            channelHandle = route.channelHandle,
            items = items,
            index = route.startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            isLoading = items.isNotEmpty(),
            qualityMode = loadMode(),
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
for (i in listOf(index + 1, index - 1, index + 2, index + 3)) {
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
selectedQuality = pickStream(cached.streams, _state.value.qualityMode)?.qualityLabel,
            )
        }
loadJob?.cancel()
prefetchAround(index)
if (cached != null) {
playStream(pickStream(cached.streams, _state.value.qualityMode), 0L)
            return
        }
        loadJob = viewModelScope.launch {
            // Fast-path: InnerTube primeiro (1 request) -> toca na hora, igual YouTube.
            val fast = musicRepository.getShortStreamsFast(track.id)
            if (_state.value.index != index) return@launch
            if (fast.isNotEmpty()) {
                val pick = pickStream(fast, _state.value.qualityMode)
                _state.update {
                    it.copy(
                        info = YouTubeRepository.ShortPlaybackInfo(streams = fast),
                        isLoading = false,
                        selectedQuality = pick?.qualityLabel,
                    )
                }
                playStream(pick, 0L)
            }
            // Detalhes (likes, comentarios, descricao, data) chegam depois, sem reiniciar o video.
            val full = musicRepository.getShortPlaybackInfo(track.id)
            if (full != null) {
                infoCache[track.id] = full
                if (_state.value.index == index) {
                    _state.update { it.copy(info = full, isLoading = false) }
                }
            } else if (fast.isEmpty()) {
                _state.update { it.copy(isLoading = false) }
            }
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

    /** Modo de qualidade persistido: fica salvo ate o usuario trocar de novo. */
    fun selectQualityMode(mode: String) {
        prefs.edit().putString("quality_mode", mode).apply()
        _state.update { it.copy(qualityMode = mode) }
        val info = _state.value.info ?: return
        val pick = pickStream(info.streams, mode) ?: return
        playStream(pick, player.currentPosition)
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
