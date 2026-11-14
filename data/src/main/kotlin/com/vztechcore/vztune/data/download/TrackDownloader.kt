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
 * Extracts the audio stream via [MusicRepository] (NewPipe) and streams bytes
 * with [OkHttpClient], atomically renaming (.tmp -> final file) in internal storage.
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

    /** Emits the track whenever a new download starts so the UI can show a floating banner. */
    private val _startedEvent = MutableSharedFlow<Track>(extraBufferCapacity = 8)
    val startedEvent: SharedFlow<Track> = _startedEvent.asSharedFlow()

    fun download(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key]
        if (current?.status == DownloadStatus.DOWNLOADING || current?.status == DownloadStatus.QUEUED) {
            return
        }

        _downloads.update { map ->
            map + (key to TrackDownloadItem(track = track, status = DownloadStatus.QUEUED, progress = 0f))
        }
        _startedEvent.tryEmit(track)

        val job = scope.launch {
            runDownload(key, track)
        }
        activeJobs[key] = job
    }

    fun cancel(track: Track) {
        val key = StoredTrack.from(track).key
        activeJobs.remove(key)?.cancel()
        _downloads.update { it - key }
        cleanupTempFiles(key)
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

    private suspend fun runDownload(key: String, track: Track) {
        var tempFile: File? = null
        try {
            updateItem(key) {
                it.copy(status = DownloadStatus.DOWNLOADING, progress = 0f, errorMessage = null)
            }

            // 1. Resolve direct stream URL and MIME type via NewPipe
            val streamUrl = track.streamUrl?.takeIf { it.isNotBlank() }
                ?: musicRepository.getStreamUrl(track)
                ?: throw IllegalStateException("Não foi possível obter o link de áudio.")

            val techInfo = runCatching { musicRepository.getTechnicalInfo(track) }.getOrNull()
            val mimeType = techInfo?.mimeType ?: "audio/mp4"
            val extension = resolveExtension(mimeType)

            val dir = downloadedTracksStore.downloadsDir
            val finalFile = File(dir, "$key.$extension")
            tempFile = File(dir, "$key.$extension.tmp").apply { if (exists()) delete() }

            // 2. Download stream via OkHttp in 8KB chunks
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Erro HTTP ${response.code} ao baixar faixa.")
                }

                val body = response.body ?: throw IllegalStateException("Resposta de áudio vazia.")
                val totalBytes = body.contentLength()
                var bytesReadTotal = 0L
                var lastEmitTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesReadTotal += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 200L) {
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

                // Remove from active downloads once saved to the 7-day completed store
                _downloads.update { it - key }
            }
        } catch (ce: CancellationException) {
            tempFile?.delete()
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download '${track.title}': ${e.message}", e)
            tempFile?.delete()
            updateItem(key) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Erro ao baixar faixa.",
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

    private fun resolveExtension(mimeType: String): String {
        val lower = mimeType.lowercase()
        return when {
            lower.contains("webm") || lower.contains("opus") -> "webm"
            lower.contains("ogg") -> "ogg"
            else -> "m4a"
        }
    }

    private fun cleanupTempFiles(key: String) {
        runCatching {
            downloadedTracksStore.downloadsDir
                .listFiles { file -> file.name.startsWith(key) && file.name.endsWith(".tmp") }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        const val TAG = "VzTuneDownloader"
    }
}