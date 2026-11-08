package com.vztechcore.vztune.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.followedDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "vztune_followed_artists")

/** An artist the user follows inside the app (local only, not a YouTube subscription). */
@Serializable
data class FollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

@Singleton
class FollowedArtistsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("artists")
    private val json = Json { ignoreUnknownKeys = true }

    val artists: Flow<List<FollowedArtist>> = context.followedDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.readList() }

    fun isFollowing(artistId: String): Flow<Boolean> =
        artists.map { list -> list.any { it.id == artistId } }

    /** Follows the artist if not followed yet, otherwise unfollows. */
    suspend fun toggle(artist: FollowedArtist) {
        context.followedDataStore.edit { prefs ->
            val current = prefs.readList()
            val updated = if (current.any { it.id == artist.id }) {
                current.filterNot { it.id == artist.id }
            } else {
                listOf(artist) + current
            }
            prefs[key] = json.encodeToString(updated)
        }
    }

    private fun Preferences.readList(): List<FollowedArtist> =
        this[key]?.let {
            runCatching { json.decodeFromString<List<FollowedArtist>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
}
