package com.vztechcore.vztune.data.prefs

import android.content.Context
import com.vztechcore.vztune.core.domain.music.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_TRACKED = 300

/** Play counter per track; feeds Library > Mais ouvidas. */
@Singleton
class MostPlayedStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<StoredPlayCount>(
    context = context,
    storeName = "vztune_most_played",
    keyName = "counts",
    serializer = StoredPlayCount.serializer(),
    idOf = { it.track.key },
) {
    /** Tracks ordered from the most to the least played. */
    val tracks: Flow<List<Track>> = items.map { list ->
        list.sortedByDescending { it.count }.mapNotNull { it.track.toTrack() }
    }

    /** Counts one more play of [track]. */
    suspend fun record(track: Track) {
        val stored = StoredTrack.from(track)
        edit { current ->
            val previous = current.firstOrNull { it.track.key == stored.key }
            val updated = StoredPlayCount(stored, (previous?.count ?: 0) + 1)
            (current.filterNot { it.track.key == stored.key } + updated)
                .sortedByDescending { it.count }
                .take(MAX_TRACKED)
        }
    }
}
