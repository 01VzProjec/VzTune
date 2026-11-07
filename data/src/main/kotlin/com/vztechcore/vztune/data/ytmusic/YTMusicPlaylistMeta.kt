package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * One-shot metadata fetch for a playlist (cover, mosaic, owner, avatar, views).
 * Used by the search-result enricher to populate cards without a full track parse.
 */
internal object YTMusicPlaylistMeta {

    data class Meta(
        val coverUrl: String?,
        val mosaic: List<String>,
        val owner: String?,
        val ownerAvatarUrl: String?,
        val views: String?,
    )

    private val empty = Meta(null, emptyList(), null, null, null)
    private val cache = ConcurrentHashMap<String, Meta>()

    suspend fun fetch(browseId: String): Meta = withContext(Dispatchers.IO) {
        val cleanId = YTMusicTextUtils.normalizePlaylistBrowseId(browseId)
        cache[cleanId]?.let { return@withContext it }

        val json = runCatching { InnerTube.browse(cleanId) }
            .getOrElse { e ->
                Log.w(TAG, "browse failed for $cleanId: ${e.message}")
                return@withContext empty
            }

        val parsed = YTMusicPlaylistParser.parsePlaylist(json)
        val mosaic = parsed.tracks
            .asSequence()
            .mapNotNull { it.thumbnailUrl }
            .distinct()
            .take(4)
            .toList()

        val meta = Meta(
            coverUrl = parsed.coverUrl,
            mosaic = mosaic,
            owner = parsed.owner?.takeIf { it.isNotBlank() },
            ownerAvatarUrl = parsed.ownerAvatarUrl,
            views = parsed.views,
        )
        cache[cleanId] = meta
        meta
    }

    private const val TAG = "VzTunePlaylistMeta"
}