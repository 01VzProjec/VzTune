package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vztechcore.vztune.ui.screens.library.LocalPlaylist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localPlaylistsDataStore by preferencesDataStore(name = "local_playlists")

/**
 * Storage for user local playlists.
 *
 * Cover values stored in [LocalPlaylist.coverPath]:
 * - A file path when the user picked a photo.
 * - A "vz_color:" token when the user picked a Vz color or when a random
 *   default color was assigned. Example: "vz_color:RED".
 * - Null when the playlist has no cover yet. In that case
 *   [ensureRandomDefaultCover] assigns a random color token once, so the
 *   Library card and the detail screen always show the same cover.
 *
 * All code and comments are in English. UI text lives in the screens, not here.
 */
@Singleton
class LocalPlaylistsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        /** Prefix for Vz color cover tokens, for example "vz_color:BLUE". */
        const val DEFAULT_COLOR_PREFIX = "vz_color:"

        /** Returns a random Vz color token. */
        fun randomDefaultCoverToken(): String {
            val colors = listOf(
                "BLUE", "BLACK", "WHITE", "RED", "GREEN", "PURPLE",
                "ORANGE", "PINK", "CYAN", "INDIGO", "AMBER", "TEAL",
                "YELLOW", "LIME", "ROSE", "BROWN", "GRAY", "VIOLET",
            )
            return "$DEFAULT_COLOR_PREFIX${colors.random()}"
        }
    }

    private val playlistsKey = stringPreferencesKey("local_playlists_json")
    private val json = Json { ignoreUnknownKeys = true }

    /** Flow of all local playlists, newest first. */
    val playlists: Flow<List<LocalPlaylist>> = context.localPlaylistsDataStore.data.map { prefs ->
        val raw = prefs[playlistsKey].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<LocalPlaylist>>(raw) }.getOrDefault(emptyList())
    }

    /** Adds a playlist to the top of the list. */
    suspend fun addFirst(playlist: LocalPlaylist) {
        context.localPlaylistsDataStore.edit { prefs ->
            val current = readCurrent(prefs[playlistsKey].orEmpty())
            val updated = listOf(playlist) + current.filter { it.id != playlist.id }
            prefs[playlistsKey] = json.encodeToString(updated)
        }
    }

    /** Deletes a playlist by id. */
    suspend fun delete(id: String) {
        updateAll { list -> list.filter { it.id != id } }
    }

    /** Renames a playlist. */
    suspend fun rename(id: String, newName: String) {
        updateAll { list ->
            list.map { if (it.id == id) it.copy(name = newName) else it }
        }
    }

    /**
     * Replaces the cover of a playlist.
     * Accepts a file path or a "vz_color:" token.
     */
    suspend fun updateCover(id: String, coverPath: String) {
        updateAll { list ->
            list.map { if (it.id == id) it.copy(coverPath = coverPath) else it }
        }
    }

    /**
     * Assigns a random Vz color cover when the playlist has no cover.
     * If the playlist already has a cover, nothing changes.
     */
    suspend fun ensureRandomDefaultCover(id: String) {
        updateAll { list ->
            list.map {
                if (it.id == id && it.coverPath.isNullOrBlank()) {
                    it.copy(coverPath = randomDefaultCoverToken())
                } else it
            }
        }
    }

    /** Moves a track inside the stored order for a playlist. */
    suspend fun reorderTrack(playlistId: String, from: Int, to: Int) {
        // Track order is stored inside the playlist tracks list by the repository.
        // This store only keeps playlist metadata, so reordering is a no-op here
        // and is handled by the MediaList repository in the real app.
        // Kept as a hook so the ViewModel call stays valid.
    }

    private suspend fun updateAll(transform: (List<LocalPlaylist>) -> List<LocalPlaylist>) {
        context.localPlaylistsDataStore.edit { prefs ->
            val current = readCurrent(prefs[playlistsKey].orEmpty())
            prefs[playlistsKey] = json.encodeToString(transform(current))
        }
    }

    private fun readCurrent(raw: String): List<LocalPlaylist> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LocalPlaylist>>(raw) }.getOrDefault(emptyList())
    }
}
