package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a cover picked from the gallery into app storage, because gallery URIs
 * lose their permission after a restart. Returns the stored file as a URI string.
 */
@Singleton
class PlaylistCoverStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun save(playlistId: String, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
            val target = File(dir, "$playlistId.jpg")
            val input = context.contentResolver.openInputStream(source) ?: return@runCatching null
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }
}
