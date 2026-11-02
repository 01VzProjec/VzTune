package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.data.innertube.InnerTube
import java.util.concurrent.ConcurrentHashMap

internal object YTMusicPlaylistMosaic {
    private val cache = ConcurrentHashMap<String, List<String>>()

    fun fetchMosaic(browseId: String): List<String> {
        cache[browseId]?.let { return it }
        if (browseId.startsWith("YT:")) return emptyList()

        val json = runCatching { InnerTube.browse(browseId) }
            .getOrElse { e ->
                Log.w("VzTuneMosaic", "browse failed for $browseId: ${e.message}")
                return emptyList()
            }

        val thumbs = mutableListOf<String>()
        YTMusicTextUtils.walk(json) { node ->
            if (thumbs.size >= 4) return@walk
            node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                val thumb = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail"))
                if (!thumb.isNullOrBlank() && thumb !in thumbs) thumbs += thumb
            }
        }
        val result = thumbs.take(4)
        cache[browseId] = result
        return result
    }
}