package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages exclusively the list of user-favorited tracks.
 * Never triggers automatic downloads.
 */
@Singleton
class FavoriteTracksStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<StoredTrack>(
    context = context,
    storeName = "vztune_favorite_tracks",
    keyName = "favorites",
    serializer = StoredTrack.serializer(),
    idOf = { it.key },
) {
    val tracks: Flow<List<Track>> = items.map { list ->
        list.mapNotNull { it.toTrack() }
    }

    fun containsTrack(track: Track): Flow<Boolean> {
        val key = StoredTrack.from(track).key
        return items.map { list -> list.any { it.key == key } }
    }

    suspend fun toggle(track: Track) {
        val stored = StoredTrack.from(track)
        val current = items.first()
        if (current.any { it.key == stored.key }) {
            remove(stored.key)
        } else {
            addFirst(stored)
        }
    }
}