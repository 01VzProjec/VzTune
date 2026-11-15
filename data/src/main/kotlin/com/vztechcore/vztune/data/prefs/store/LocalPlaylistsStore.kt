package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Playlists created by the user inside the app (Library "Suas playlists" tab). */
@Singleton
class LocalPlaylistsStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<LocalPlaylist>(
    context = context,
    storeName = "vztune_local_playlists",
    keyName = "playlists",
    serializer = LocalPlaylist.serializer(),
    idOf = { it.id },
) {
    /** Emits the playlist with [id], or null when it does not exist (anymore). */
    fun playlist(id: String): Flow<LocalPlaylist?> = items.map { list -> list.firstOrNull { it.id == id } }

    /** Renames an existing local playlist. */
    suspend fun rename(playlistId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        update(playlistId) { playlist ->
            playlist.copy(name = clean)
        }
    }

    /** Updates the custom cover image path (or `vz_color:...` token) of an existing local playlist. */
    suspend fun updateCover(playlistId: String, coverPath: String?) {
        update(playlistId) { playlist ->
            playlist.copy(coverPath = coverPath)
        }
    }

    /** Assigns a randomly sorted background color token when the playlist has no custom cover. */
    suspend fun ensureRandomDefaultCover(playlistId: String) {
        val token = randomDefaultCoverToken()
        update(playlistId) { playlist ->
            if (playlist.coverPath.isNullOrBlank()) {
                playlist.copy(coverPath = token)
            } else {
                playlist
            }
        }
    }

    /** Appends [track] to the playlist, ignoring duplicates. */
    suspend fun addTrack(playlistId: String, track: Track) {
        val stored = StoredTrack.from(track)
        update(playlistId) { playlist ->
            if (playlist.tracks.any { it.key == stored.key }) playlist
            else playlist.copy(tracks = playlist.tracks + stored)
        }
    }

    suspend fun removeTrack(playlistId: String, track: Track) {
        val key = StoredTrack.from(track).key
        update(playlistId) { playlist -> playlist.copy(tracks = playlist.tracks.filterNot { it.key == key }) }
    }

    companion object {
        const val DEFAULT_COLOR_PREFIX = "vz_color:"
        private val DEFAULT_COLOR_IDS = listOf(
            "BLUE",
            "BLACK",
            "WHITE",
            "RED",
            "GREEN",
            "PURPLE",
            "ORANGE",
            "PINK",
            "CYAN",
            "INDIGO",
            "AMBER",
            "TEAL",
            "YELLOW",
            "LIME",
            "ROSE",
            "BROWN",
            "GRAY",
            "VIOLET",
        )

        fun randomDefaultCoverToken(): String {
            return "$DEFAULT_COLOR_PREFIX${DEFAULT_COLOR_IDS.random()}"
        }
    }
}
