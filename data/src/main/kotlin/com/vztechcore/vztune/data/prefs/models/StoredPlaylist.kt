package com.vztechcore.vztune.data.prefs.models

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import kotlinx.serialization.Serializable

@Serializable
data class StoredPlaylist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val mosaic: List<String> = emptyList(),
) {
    fun toPlaylist(source: Source = Source.YOUTUBE_MUSIC) = Playlist(
        id = id, title = title, owner = owner, thumbnailUrl = thumbnailUrl,
        source = source, mosaicThumbnails = mosaic,
    )
}