package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.data.innertube.InnerTube
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches up to 4 thumbnails from the first tracks of a playlist, so the UI
 * can render a 2x2 mosaic when the playlist has no dedicated cover.
 *
 * Special cases:
 *   - Auto-generated playlists ("YT:" prefix): skipped (they're YouTube
 *     playlists, mosaic comes from YouTube itself).
 *   - VL... playlists: browse returns `musicResponsiveListItemRenderer`
 *     entries whose `thumbnail` we grab.
 */
internal object YTMusicPlaylistMosaic {

    private val cache = ConcurrentHashMap<String, List<String>>()

    fun fetchMosaic(browseId: String): List<String> {
        cache[browseId]?.let { return it }

        // YouTube playlists have their own mosaic handled by NewPipe.
        if (browseId.startsWith("YT:")) return emptyList()

        val json = runCatching { InnerTube.browse(browseId) }
            .getOrElse { e ->
                Log.w(TAG, "browse failed for $browseId: ${e.message}")
                return emptyList()
            }

        val thumbs = mutableListOf<String>()

        YTMusicTextUtils.walk(json) { node ->
            if (thumbs.size >= 4) return@walk

            node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                val thumb = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail"))
                if (!thumb.isNullOrBlank() && thumb !in thumbs) {
                    thumbs += thumb
                }
            }
        }

        val result = thumbs.take(4)
        cache[browseId] = result
        return result
    }

    private const val TAG = "VzTuneMosaic"
}