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
import kotlinx.coroutines.flow.first
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
 * Central download manager for VzTune with a FIFO queue and up to [MAX_CONCURRENT_DOWNLOADS]
 * simultaneous transfers. Supports pause/resume (via HTTP Range when available) and atomic saving.
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
    private val resumeKeys = ConcurrentHashMap.newKeySet<String>()
    private val schedulerLock = Any()

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
        resumeKeys.remove(key)
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
        scheduleNext()
    }

    /** Enqueues multiple tracks at once (e.g., an album or playlist), running up to 5 simultaneously. */
    fun downloadAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        scope.launch {
            val alreadyDownloaded = downloadedTracksStore.downloadedKeys.first()
            val baseTime = System.currentTimeMillis()
            var firstEnqueued: Track? = null

            _downloads.update { currentMap ->
                val mutable = currentMap.toMutableMap()
                tracks.forEachIndexed { index, track ->
                    val key = StoredTrack.from(track).key
                    if (key in alreadyDownloaded) return@forEachIndexed
                    val existing = mutable[key]
                    if (existing?.status == DownloadStatus.DOWNLOADING || existing?.status == DownloadStatus.QUEUED) {
                        return@forEachIndexed
                    }
                    if (existing?.status == DownloadStatus.PAUSED) {
                        resumeKeys.add(key)
                        mutable[key] = existing.copy(
                            status = DownloadStatus.QUEUED,
                            errorMessage = null,
                            updatedAtMs = baseTime + index,
                        )
                    } else {
                        resumeKeys.remove(key)
                        mutable[key] = TrackDownloadItem(
                            track = track,
                            status = DownloadStatus.QUEUED,
                            progress = 0f,
                            createdAtMs = baseTime + index,
                            updatedAtMs = baseTime + index,
                        )
                    }
                    if (firstEnqueued == null) firstEnqueued = track
                }
                mutable
            }

            firstEnqueued?.let { _startedEvent.tryEmit(it) }
            scheduleNext()
        }
    }

    fun pause(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key] ?: return
        if (current.status != DownloadStatus.DOWNLOADING && current.status != DownloadStatus.QUEUED) return

        updateItem(key) {
            it.copy(status = DownloadStatus.PAUSED, updatedAtMs = System.currentTimeMillis())
        }
        activeJobs.remove(key)?.cancel()
        // Frees a concurrency slot for the next queued track
        scheduleNext()
    }

    fun resume(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key] ?: return
        if (current.status == DownloadStatus.DOWNLOADING || current.status == DownloadStatus.QUEUED) return

        resumeKeys.add(key)
        updateItem(key) {
            it.copy(
                status = DownloadStatus.QUEUED,
                errorMessage = null,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        scheduleNext()
    }

    fun togglePauseResume(track: Track) {
        val key = StoredTrack.from(track).key
        val current = _downloads.value[key] ?: return
        when (current.status) {
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> resume(track)
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> pause(track)
            DownloadStatus.COMPLETED -> Unit
        }
    }

    fun pauseAll() {
        val keysToPause = _downloads.value.values
            .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
            .map { StoredTrack.from(it.track).key }

        if (keysToPause.isEmpty()) return
        val now = System.currentTimeMillis()
        _downloads.update { map ->
            val mutable = map.toMutableMap()
            keysToPause.forEach { key ->
                mutable[key]?.let {
                    mutable[key] = it.copy(status = DownloadStatus.PAUSED, updatedAtMs = now)
                }
            }
            mutable
        }
        keysToPause.forEach { key ->
            activeJobs.remove(key)?.cancel()
        }
    }

    fun resumeAll() {
        val keysToResume = _downloads.value.values
            .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
            .map { StoredTrack.from(it.track).key }

        if (keysToResume.isEmpty()) return
        val now = System.currentTimeMillis()
        keysToResume.forEach { resumeKeys.add(it) }
        _downloads.update { map ->
            val mutable = map.toMutableMap()
            keysToResume.forEach { key ->
                mutable[key]?.let {
                    mutable[key] = it.copy(
                        status = DownloadStatus.QUEUED,
                        errorMessage = null,
                        updatedAtMs = now,
                    )
                }
            }
            mutable
        }
        scheduleNext()
    }

    fun cancel(track: Track) {
        val key = StoredTrack.from(track).key
        resumeKeys.remove(key)
        _downloads.update { it - key }
        activeJobs.remove(key)?.cancel()
        cleanupTempFiles(sanitizeFileName(key))
        scheduleNext()
    }

    fun cancelAll() {
        val allKeys = _downloads.value.keys.toList()
        allKeys.forEach { key ->
            resumeKeys.remove(key)
            activeJobs.remove(key)?.cancel()
            cleanupTempFiles(sanitizeFileName(key))
        }
        _downloads.value = emptyMap()
    }

    fun dismissFailed(track: Track) {
        val key = StoredTrack.from(track).key
        resumeKeys.remove(key)
        _downloads.update { it - key }
    }

    fun deleteDownload(track: Track) {
        cancel(track)
        scope.launch {
            downloadedTracksStore.deleteDownload(track)
        }
    }

    /**
     * Promotes queued items to [DownloadStatus.DOWNLOADING] until [MAX_CONCURRENT_DOWNLOADS]
     * workers are active simultaneously.
     */
    private fun scheduleNext() {
        synchronized(schedulerLock) {
            val currentMap = _downloads.value
            val runningCount = currentMap.values.count { it.status == DownloadStatus.DOWNLOADING }
            val availableSlots = (MAX_CONCURRENT_DOWNLOADS - runningCount).coerceAtLeast(0)
            if (availableSlots == 0) return

            val nextCandidates = currentMap.entries
                .filter { it.value.status == DownloadStatus.QUEUED && !activeJobs.containsKey(it.key) }
                .sortedBy { it.value.createdAtMs }
                .take(availableSlots)

            if (nextCandidates.isEmpty()) return

            val now = System.currentTimeMillis()
            _downloads.update { map ->
                val mutable = map.toMutableMap()
                nextCandidates.forEach { (key, item) ->
                    mutable[key] = item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        errorMessage = null,
                        updatedAtMs = now,
                    )
                }
                mutable
            }

            nextCandidates.forEach { (key, item) ->
                val isResume = resumeKeys.remove(key)
                val job = scope.launch {
                    runDownload(key, item.track, isResume)
                }
                activeJobs[key] = job
            }
        }
    }

    private suspend fun runDownload(key: String, track: Track, isResume: Boolean) {
        val safeBaseName = sanitizeFileName(key)
        var tempFile: File? = null
        try {
            val streamUrl = track.streamUrl?.takeIf { it.isNotBlank() }
                ?: musicRepository.getStreamUrl(track)
                ?: throw IllegalStateException("Não foi possível obter o link de áudio.")

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
                    throw IllegalStateException("Erro HTTP ${response.code} ao baixar faixa.")
                }

                val body = response.body ?: throw IllegalStateException("Resposta de áudio vazia.")
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
                                    if (it.status == DownloadStatus.DOWNLOADING) {
                                        it.copy(
                                            progress = fraction,
                                            bytesDownloaded = bytesReadTotal,
                                            totalBytes = totalBytes,
                                            updatedAtMs = now,
                                        )
                                    } else {
                                        it
                                    }
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
                    errorMessage = e.message ?: "Erro ao baixar faixa.",
                    updatedAtMs = System.currentTimeMillis(),
                )
            }
        } finally {
            activeJobs.remove(key)
            scheduleNext()
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

    companion object {
        private const val TAG = "VzTuneDownloader"
        const val MAX_CONCURRENT_DOWNLOADS = 5
    }
}