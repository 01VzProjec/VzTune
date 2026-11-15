package com.vztechcore.vztune.ui.components.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack

/**
 * Provides the set of downloaded track keys across the Compose hierarchy
 * so track cards on any screen automatically reflect offline availability.
 */
val LocalDownloadedKeys = compositionLocalOf<Set<String>> { emptySet() }

@Composable
fun isTrackDownloaded(track: Track): Boolean {
    val key = StoredTrack.from(track).key
    return key in LocalDownloadedKeys.current
}