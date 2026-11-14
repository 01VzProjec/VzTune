package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the persistent registry of downloaded tracks on the device
 * and synchronizes with physical audio files stored under [filesDir/downloads].
 */
@Singleton
class DownloadedTracksStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : JsonListStore<DownloadedTrack>(
    context = context,
    storeName = "vztune_downloaded_tracks",
    keyName = "downloads",
    serializer = DownloadedTrack.serializer(),
    idOf = { it.key },
) {
    /** Private app directory where offline audio files are saved. */
    val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply {
            if (!exists()) mkdirs()
        }

    /** Keys of all tracks currently downloaded and valid on disk. */
    val downloadedKeys: Flow<Set<String>> = items.map { list ->
        list.filter { File(it.filePath).exists() }
            .map { it.key }
            .toSet()
    }

    /** Tracks completed within the last 7 days. */
    val recentCompleted7Days: Flow<List<DownloadedTrack>> = items.map { list ->
        val now = System.currentTimeMillis()
        list.filter { File(it.filePath).exists() && it.isWithinSevenDays(now) }
            .sortedByDescending { it.downloadedAtMs }
    }

    /** Returns the local audio file if the track is downloaded and non-empty on disk. */
    suspend fun getLocalFile(track: Track): File? {
        val key = StoredTrack.from(track).key
        val entry = items.first().firstOrNull { it.key == key } ?: return null
        val file = File(entry.filePath)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /** Saves or updates a downloaded track record at the top of the list. */
    suspend fun saveDownload(downloadedTrack: DownloadedTrack) {
        addFirst(downloadedTrack)
    }

    /** Removes the DataStore record and deletes the physical audio file from storage. */
    suspend fun deleteDownload(track: Track) {
        val key = StoredTrack.from(track).key
        deleteByKey(key)
    }

    suspend fun deleteByKey(key: String) {
        val existing = items.first().firstOrNull { it.key == key }
        if (existing != null) {
            runCatching { File(existing.filePath).delete() }
        }
        remove(key)
    }
}