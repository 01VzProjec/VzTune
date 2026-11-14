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
 * Gerencia o registro persistente das faixas baixadas no dispositivo
 * e sincroniza com os arquivos físicos salvos em [filesDir/downloads].
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
    /** Pasta privada do app onde os áudios offline são armazenados. */
    val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply {
            if (!exists()) mkdirs()
        }

    /** Chaves de todas as faixas já baixadas (e com arquivo físico válido). */
    val downloadedKeys: Flow<Set<String>> = items.map { list ->
        list.filter { File(it.filePath).exists() }
            .map { it.key }
            .toSet()
    }

    /** Faixas baixadas nos últimos 7 dias (exibidas com destaque de conclusão). */
    val recentCompleted7Days: Flow<List<DownloadedTrack>> = items.map { list ->
        val now = System.currentTimeMillis()
        list.filter { File(it.filePath).exists() && it.isWithinSevenDays(now) }
            .sortedByDescending { it.downloadedAtMs }
    }

    /** Retorna o arquivo local da faixa se ela estiver baixada e existir no disco. */
    suspend fun getLocalFile(track: Track): File? {
        val key = StoredTrack.from(track).key
        val entry = items.first().firstOrNull { it.key == key } ?: return null
        val file = File(entry.filePath)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /** Salva ou atualiza o registro de uma faixa baixada. */
    suspend fun saveDownload(downloadedTrack: DownloadedTrack) {
        remove(downloadedTrack.key)
        prepend(downloadedTrack)
    }

    /** Remove o registro do DataStore e apaga o arquivo físico do aparelho. */
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