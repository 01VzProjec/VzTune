package com.vztechcore.vztune.data.prefs.models

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import kotlinx.serialization.Serializable

@Serializable
data class StoredTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val source: String,
    val isExplicit: Boolean = false,
) {
    val key: String get() = "$source:$id"

    fun toTrack(): Track? {
        val parsedSource = runCatching { Source.valueOf(source) }.getOrNull() ?: return null
        return Track(
            id = id, title = title, artist = artist, album = album,
            durationSeconds = durationSeconds, thumbnailUrl = thumbnailUrl,
            source = parsedSource, isExplicit = isExplicit,
        )
    }

    companion object {
        fun from(track: Track) = StoredTrack(
            id = track.id, title = track.title, artist = track.artist, album = track.album,
            durationSeconds = track.durationSeconds, thumbnailUrl = track.thumbnailUrl,
            source = track.source.name, isExplicit = track.isExplicit,
        )
    }
}