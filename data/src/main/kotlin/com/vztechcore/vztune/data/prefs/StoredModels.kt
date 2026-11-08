package com.vztechcore.vztune.data.prefs

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import kotlinx.serialization.Serializable
import java.util.UUID

/** Storage shape for a track (keeps the domain [Track] free of serialization). */
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
    /** Unique per source: the same id can exist on YouTube and YouTube Music. */
    val key: String get() = "$source:$id"

    fun toTrack(): Track? {
        val parsedSource = runCatching { Source.valueOf(source) }.getOrNull() ?: return null
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            source = parsedSource,
            isExplicit = isExplicit,
        )
    }

    companion object {
        fun from(track: Track) = StoredTrack(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            thumbnailUrl = track.thumbnailUrl,
            source = track.source.name,
            isExplicit = track.isExplicit,
        )
    }
}

/** Prefix of every local playlist id, so screens can tell them apart from YouTube ids. */
private const val LOCAL_PLAYLIST_PREFIX = "local:"

fun isLocalPlaylistId(id: String): Boolean = id.startsWith(LOCAL_PLAYLIST_PREFIX)

fun newLocalPlaylistId(): String = LOCAL_PLAYLIST_PREFIX + UUID.randomUUID()

/** How many times a track was played (feeds "Mais ouvidas"). */
@Serializable
data class StoredPlayCount(
    val track: StoredTrack,
    val count: Int = 1,
)

/** A playlist created by the user, stored only on the device. */
@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    // Path/URI of the cover chosen by the user; null = use the track thumbnails.
    val coverPath: String? = null,
    val tracks: List<StoredTrack> = emptyList(),
) {
    /** Up to 4 distinct thumbnails of the playlist tracks (2x2 mosaic). */
    val mosaic: List<String>
        get() = tracks.mapNotNull { it.thumbnailUrl }.distinct().take(4)
}

/** A YouTube / YouTube Music playlist the user marked as favorite. */
@Serializable
data class StoredPlaylist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val mosaic: List<String> = emptyList(),
) {
    fun toPlaylist(source: Source = Source.YOUTUBE_MUSIC) = Playlist(
        id = id,
        title = title,
        owner = owner,
        thumbnailUrl = thumbnailUrl,
        source = source,
        mosaicThumbnails = mosaic,
    )
}

/** An artist the user follows inside the app (local only, not a YouTube subscription). */
@Serializable
data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)
