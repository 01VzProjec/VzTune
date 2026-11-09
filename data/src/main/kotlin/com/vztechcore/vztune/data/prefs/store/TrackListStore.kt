package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A [JsonListStore] of tracks, most recent first. Shared by every "played tracks" list. */
abstract class TrackListStore(
    context: Context,
    storeName: String,
    maxItems: Int,
) : JsonListStore<StoredTrack>(
    context = context,
    storeName = storeName,
    keyName = "tracks",
    serializer = StoredTrack.serializer(),
    idOf = { it.key },
    maxItems = maxItems,
) {
    val tracks: Flow<List<Track>> = items.map { list -> list.mapNotNull { it.toTrack() } }

    suspend fun add(track: Track) = addFirst(StoredTrack.from(track))

    /** Adds the track if missing, removes it otherwise. */
    suspend fun toggle(track: Track) = toggle(StoredTrack.from(track))

    /** Emits whether [track] is in the list. */
    fun containsTrack(track: Track): Flow<Boolean> = contains(StoredTrack.from(track).key)
}
