package com.vztechcore.vztune.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ported verbatim from VzMusic (YouTubeMusicNewPipeRepository).
 * Resolves YouTube Music song IDs to direct audio URLs via NewPipe.
 *
 * Strategy (same as VzMusic, do NOT change):
 *   1. Try www.youtube.com first (most reliable with NewPipe).
 *   2. Fallback to music.youtube.com.
 *   3. Best audio stream -> HLS -> lowest progressive.
 */
@Singleton
class YouTubeMusicNewPipeRepository @Inject constructor() {

    suspend fun getAudioStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        // Attempt 1: www.youtube.com
        val fromYt = runCatching {
            extractFrom("https://www.youtube.com/watch?v=$videoId")
        }.getOrNull()
        if (!fromYt.isNullOrBlank()) return@withContext fromYt

        // Attempt 2: music.youtube.com
        val fromMusic = runCatching {
            extractFrom("https://music.youtube.com/watch?v=$videoId")
        }.getOrNull()
        if (!fromMusic.isNullOrBlank()) return@withContext fromMusic

        throw Exception("Sem streams disponíveis para $videoId")
    }

    private fun extractFrom(url: String): String {
        val extractor: StreamExtractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        // 1) Best pure-audio stream.
        val audioStreams = extractor.audioStreams
        val bestAudio: AudioStream? = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.firstOrNull()
        if (bestAudio?.content?.isNotBlank() == true) return bestAudio.content

        // 2) HLS
        extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }

        // 3) Progressive (audio + video), lowest resolution.
        val progressive = extractor.videoStreams
            .filter { !it.isVideoOnly }
            .minByOrNull {
                it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }
        if (progressive?.content?.isNotBlank() == true) return progressive.content

        throw Exception("Nenhum stream disponível para $url")
    }
}