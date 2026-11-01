package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Source
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared in-memory caches across the whole YT Music pipeline.
 * Same lifetime as the app process — keyed by videoId.
 */
internal object YTMusicCaches {

    /** Which source each video belongs to (YT_MUSIC vs YOUTUBE). */
    val sourceCache = ConcurrentHashMap<String, Source>()

    /** Resolved artist name per video (avoids repeated player calls). */
    val authorCache = ConcurrentHashMap<String, String>()

    /** Resolved thumbnail URL per video. */
    val thumbnailCache = ConcurrentHashMap<String, String?>()

    /** Video IDs whose source is still uncertain. */
    val uncertainIds = ConcurrentHashMap.newKeySet<String>()
}