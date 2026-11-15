package com.vztechcore.vztune.data.download

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central download manager for VzTune.
 * Resolves audio streams via [MusicRepository] and downloads them via [OkHttpClient]
 * with support for pausing, resuming (via HTTP Range when available), and atomic saving.
 */
@Singleton
class TrackDownloader @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadedTracksStore: DownloadedTracksStore,
) {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, TrackDownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, TrackDownloadItem>> = _downloads.asStateFlow()

    /** Emits a track whenever a new download starts so the UI can show a notification banner. */
    private val _startedEvent = MutableSharedFlow<Track>(extraBufferCapacity = 8)
    val startedEvent: SharedFlow<Track> = _startedEvent.asSharedFlow()

    fun download(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key]
        if (current?.status == DownloadStatus.DOWNLOADING || current?.status == DownloadStatus.QUEUED) {
            return
        }
        if (current?.status == DownloadStatus.PAUSED) {
            resume(track)
            return
        }

        val now = System.currentTimeMillis()
        _downloads.update { map ->
            map + (
                key to TrackDownloadItem(
                    track = track,
                    status = DownloadStatus.QUEUED,
                    progress = 0f,
                    createdAtMs = now,
                    updatedAtMs = now,
                )
            )
        }
        _startedEvent.tryEmit(track)

        startJob(key, track, isResume = false)
    }

    fun pause(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key] ?: return
        if (current.status != DownloadStatus.DOWNLOADING && current.status != DownloadStatus.QUEUED) return

        // Mark as paused before cancelling job so the CancellationException handler preserves .tmp files
        updateItem(key) {
            it.copy(status = DownloadStatus.PAUSED, updatedAtMs = System.currentTimeMillis())
        }
        activeJobs.remove(key)?.cancel()
    }

    fun resume(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key] ?: return
        if (current.status == DownloadStatus.DOWNLOADING || current.status == DownloadStatus.QUEUED) return

        updateItem(key) {
            it.copy(status = DownloadStatus.QUEUED, errorMessage = null, updatedAtMs = System.currentTimeMillis())
        }
        startJob(key, track, isResume = true)
    }

    fun togglePauseResume(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key] ?: return
        if (current.status == DownloadStatus.PAUSED) {
            resume(track)
        } else if (current.status == DownloadStatus.DOWNLOADING || current.status == DownloadStatus.QUEUED) {
            pause(track)
        }
    }

    fun cancel(track: Track) {
        val key = StoredTrack.from(track).key
        _downloads.update { it - key }
        activeJobs.remove(key)?.cancel()
        cleanupTempFiles(sanitizeFileName(key))
    }

    fun dismissFailed(track: Track) {
        val key = StoredTrack.from(track).key
        _downloads.update { it - key }
    }

    fun deleteDownload(track: Track) {
        cancel(track)
        scope.launch {
            downloadedTracksStore.deleteDownload(track)
        }
    }

    private fun startJob(key: String, track: Track, isResume: Boolean) {
        activeJobs.remove(key)?.cancel()
        val job = scope.launch {
            runDownload(key, track, isResume)
        }
        activeJobs[key] = job
    }

    private suspend fun runDownload(key: String, track: Track, isResume: Boolean) {
        val safeBaseName = sanitizeFileName(key)
        var tempFile: File? = null
        try {
            updateItem(key) {
                it.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null)
            }

            val streamUrl = track.streamUrl?.takeIf { it.isNotBlank() }
                ?: musicRepository.getStreamUrl(track)
                ?: throw IllegalStateException("Unable to resolve audio stream URL.")

            val techInfo = runCatching { musicRepository.getTechnicalInfo(track) }.getOrNull()
            val mimeType = techInfo?.mimeType ?: "audio/mp4"
            val extension = resolveExtension(mimeType)

            val dir = downloadedTracksStore.downloadsDir
            if (!dir.exists()) dir.mkdirs()

            val finalFile = File(dir, "$safeBaseName.$extension")
            tempFile = File(dir, "$safeBaseName.$extension.tmp")

            val existingBytes = if (isResume && tempFile.exists()) tempFile.length() else {
                if (tempFile.exists()) tempFile.delete()
                0L
            }

            val requestBuilder = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0")

            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IllegalStateException("HTTP ${response.code} while downloading track.")
                }

                val body = response.body ?: throw IllegalStateException("Empty audio response body.")
                val isPartial = response.code == 206 && existingBytes > 0L
                val startOffset = if (isPartial) existingBytes else {
                    if (tempFile.exists()) tempFile.delete()
                    0L
                }

                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0L) startOffset + contentLength else -1L
                var bytesReadTotal = startOffset
                var lastEmitTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile, isPartial).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesReadTotal += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 250L) {
                                lastEmitTime = now
                                val fraction = if (totalBytes > 0L) {
                                    (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                } else {
                                    0f
                                }
                                updateItem(key) {
                                    it.copy(
                                        status = DownloadStatus.DOWNLOADING,
                                        progress = fraction,
                                        bytesDownloaded = bytesReadTotal,
                                        totalBytes = totalBytes,
                                        updatedAtMs = now,
                                    )
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                val savedRecord = DownloadedTrack.from(
                    track = track,
                    filePath = finalFile.absolutePath,
                    mimeType = mimeType,
                    fileSizeBytes = finalFile.length(),
                )
                downloadedTracksStore.saveDownload(savedRecord)

                // Remove from active downloads map once persisted in DownloadedTracksStore
                _downloads.update { it - key }
            }
        } catch (ce: CancellationException) {
            val status = _downloads.value[key]?.status
            if (status != DownloadStatus.PAUSED) {
                tempFile?.delete()
            }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download '${track.title}': ${e.message}", e)
            tempFile?.delete()
            updateItem(key) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Error downloading track.",
                    updatedAtMs = System.currentTimeMillis(),
                )
            }
        } finally {
            activeJobs.remove(key)
        }
    }

    private fun updateItem(key: String, transform: (TrackDownloadItem) -> TrackDownloadItem) {
        _downloads.update { map ->
            val existing = map[key] ?: return@update map
            map + (key to transform(existing))
        }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun resolveExtension(mimeType: String): String {
        val lower = mimeType.lowercase()
        return when {
            lower.contains("webm") || lower.contains("opus") -> "webm"
            lower.contains("ogg") -> "ogg"
            else -> "m4a"
        }
    }

    private fun cleanupTempFiles(safeBaseName: String) {
        runCatching {
            downloadedTracksStore.downloadsDir
                .listFiles { file -> file.name.startsWith(safeBaseName) && file.name.endsWith(".tmp") }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        const val TAG = "VzTuneDownloader"
    }
}