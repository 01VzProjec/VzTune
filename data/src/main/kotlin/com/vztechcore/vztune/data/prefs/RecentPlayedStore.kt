package com.vztechcore.vztune.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "vztune_recent_played")

private const val MAX_RECENT = 20

/** Storage shape for a played track (kept separate so the domain model stays serialization-free). */
@Serializable
private data class PlayedTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val source: String,
    val isExplicit: Boolean = false,
)

/**
 * Tracks the user played from the Search screen, most recent first.
 * Shown as the horizontal "recently played" row under the search input.
 */
@Singleton
class RecentPlayedStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("tracks")
    private val json = Json { ignoreUnknownKeys = true }

    val tracks: Flow<List<Track>> = context.recentDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.readList() }

    suspend fun add(track: Track) {
        context.recentDataStore.edit { prefs ->
            val current = prefs.readDtos()
            val updated = (listOf(track.toDto()) + current.filterNot { it.id == track.id && it.source == track.source.name })
                .take(MAX_RECENT)
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        context.recentDataStore.edit { it.remove(key) }
    }

    private fun Preferences.readDtos(): List<PlayedTrackDto> =
        this[key]?.let {
            runCatching { json.decodeFromString<List<PlayedTrackDto>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    private fun Preferences.readList(): List<Track> =
        readDtos().mapNotNull { dto ->
            val source = runCatching { Source.valueOf(dto.source) }.getOrNull() ?: return@mapNotNull null
            Track(
                id = dto.id,
                title = dto.title,
                artist = dto.artist,
                album = dto.album,
                durationSeconds = dto.durationSeconds,
                thumbnailUrl = dto.thumbnailUrl,
                source = source,
                isExplicit = dto.isExplicit,
            )
        }

    private fun Track.toDto() = PlayedTrackDto(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
        source = source.name,
        isExplicit = isExplicit,
    )
}
