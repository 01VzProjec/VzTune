package com.vztechcore.vztune.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.TrackDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadBannerViewModel @Inject constructor(
    private val trackDownloader: TrackDownloader,
) : ViewModel() {

    private val _bannerTrack = MutableStateFlow<Track?>(null)
    val bannerTrack: StateFlow<Track?> = _bannerTrack.asStateFlow()

    init {
        viewModelScope.launch {
            trackDownloader.startedEvent.collect { track ->
                _bannerTrack.value = track
            }
        }
    }

    fun dismissBanner() {
        _bannerTrack.value = null
    }
}