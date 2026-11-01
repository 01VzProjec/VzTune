package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.data.innertube.InnerTube
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fixes fake/missing artist names by calling the player endpoint and
 * reading videoDetails.author (same strategy as VzMusic).
 *
 * Two entry points:
 *   - enrichHits(List<SearchHit>)    -> used after every search
 *   - enrichTracks(List<Track>, ..)  -> used when opening albums / playlists
 *
 * Semaphore(10) keeps it fast without hammering the endpoint.
 */
@Singleton
class YTMusicEnricher @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
) {

    // ================= HITS =================

    suspend fun enrichHits(hits: List<SearchHit>): List<SearchHit> {
        if (hits.isEmpty()) return hits
        return coroutineScope {
            val semaphore = Semaphore(10)
            hits.map { hit ->
                async { semaphore.withPermit { enrichSingleHit(hit) } }
            }.awaitAll()
        }
    }

    private suspend fun enrichSingleHit(hit: SearchHit): SearchHit {
        if (hit !is SearchHit.SongHit) return hit

        val track = hit.track
        val cachedAuthor = YTMusicCaches.authorCache[track.id]
        if (!cachedAuthor.isNullOrBlank()) {
            return if (cachedAuthor != track.artist) {
                SearchHit.SongHit(track.copy(artist = cachedAuthor))
            } else hit
        }

        if (!isLikelyMetadata(track.artist)) {
            YTMusicCaches.authorCache[track.id] = track.artist
            return hit
        }

        val real = runCatching { fetchAuthor(track.id) }.getOrNull()
        if (real.isNullOrBlank()) return hit

        YTMusicCaches.authorCache[track.id] = real
        return SearchHit.SongHit(track.copy(artist = real))
    }

    // ================= TRACKS (album / playlist / queue) =================

    suspend fun enrichTracks(
        items: List<Track>,
        albumArtistFallback: String?,
    ): List<Track> {
        if (items.isEmpty()) return items
        return coroutineScope {
            val semaphore = Semaphore(10)
            items.map { item ->
                async {
                    semaphore.withPermit {
                        enrichSingleTrack(item, albumArtistFallback)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun enrichSingleTrack(
        item: Track,
        albumArtistFallback: String?,
    ): Track {
        val cachedSource = YTMusicCaches.sourceCache[item.id]
        val startSource = cachedSource ?: item.source
        val needsSourceConfirm = cachedSource == null || item.id in YTMusicCaches.uncertainIds
        val needsThumb = item.thumbnailUrl.isNullOrBlank()
        val needsArtist = item.artist.isBlank() ||
            item.artist.equals("Unknown", true) ||
            isLikelyMetadata(item.artist)

        if (!needsThumb && !needsArtist && !needsSourceConfirm) {
            return item.copy(source = startSource)
        }

        var working = item.copy(source = startSource)
        if (needsArtist && !albumArtistFallback.isNullOrBlank()) {
            working = working.copy(artist = albumArtistFallback)
        }

        val shouldCall = needsSourceConfirm || needsArtist ||
            (needsThumb && startSource == Source.YOUTUBE_MUSIC)

        val playerJson = if (shouldCall) {
            runCatching { InnerTube.player(item.id) }.getOrNull()
        } else null

        var resolvedSource = startSource
        if (playerJson != null) {
            detectSourceFromPlayerResponse(playerJson)?.let { s ->
                YTMusicCaches.sourceCache[item.id] = s
                YTMusicCaches.uncertainIds.remove(item.id)
                resolvedSource = s
            }
            val extracted = extractArtistFromPlayer(playerJson)
            if (extracted.isNotBlank() && isLikelyMetadata(working.artist)) {
                working = working.copy(artist = extracted)
                YTMusicCaches.authorCache[item.id] = extracted
            }
        }
        working = working.copy(source = resolvedSource)

        when (resolvedSource) {
            Source.YOUTUBE -> if (needsThumb) {
                val artistHint = working.artist.takeIf {
                    it.isNotBlank() && !it.equals("Unknown", true)
                }
                val ytThumb = runCatching {
                    youTubeRepository.findVideoThumbnail(
                        videoId = working.id,
                        title = working.title,
                        artist = artistHint,
                    )
                }.getOrNull()
                if (!ytThumb.isNullOrBlank()) {
                    working = working.copy(thumbnailUrl = ytThumb)
                    YTMusicCaches.thumbnailCache[item.id] = ytThumb
                }
            }
            Source.YOUTUBE_MUSIC -> if (playerJson != null) {
                val thumb = YTMusicThumbnailUtils.extractThumbnailFromPlayer(playerJson)
                if (!thumb.isNullOrBlank()) {
                    YTMusicCaches.thumbnailCache[item.id] = thumb
                    if (thumb != working.thumbnailUrl) {
                        working = working.copy(thumbnailUrl = thumb)
                    }
                }
            }
        }

        return working
    }

    // ================= PUBLIC UTILITIES =================

    /**
     * Heuristic: does this string look like metadata (episode / date /
     * "Song", "Música", "Video", "Unknown") rather than an artist?
     */
    fun isLikelyMetadata(s: String): Boolean {
        if (s.isBlank()) return true
        if (YTMusicTextUtils.isTypeWord(s)) return true
        val lower = s.lowercase()
        if (lower == "unknown" || lower == "desconhecido" ||
            lower == "artista desconhecido") return true
        if (YTMusicTextUtils.isEpisode(s)) return true
        if (YTMusicTextUtils.isDuration(s)) return true
        if (YTMusicTextUtils.isYear(s)) return true
        if (YTMusicTextUtils.isDate(s)) return true
        return false
    }

    // ================= INTERNAL =================

    private suspend fun fetchAuthor(videoId: String): String? {
        return try {
            val playerJson = InnerTube.player(videoId)
            extractArtistFromPlayer(playerJson).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "enrich failed for $videoId: ${e.message}")
            null
        }
    }

    private fun extractArtistFromPlayer(playerJson: JSONObject): String {
        val videoDetails = playerJson.optJSONObject("videoDetails")
        val microformat = playerJson.optJSONObject("microformat")
            ?.optJSONObject("playerMicroformatRenderer")

        val candidates = listOfNotNull(
            videoDetails?.optString("author")?.takeIf { it.isNotBlank() },
            microformat?.optString("ownerChannelName")?.takeIf { it.isNotBlank() },
            microformat?.optString("author")?.takeIf { it.isNotBlank() },
        )

        for (raw in candidates) {
            val cleaned = YTMusicTextUtils.cleanArtistName(raw)
            if (cleaned.isNotBlank()) return cleaned
        }
        return ""
    }

    private fun detectSourceFromPlayerResponse(playerJson: JSONObject): Source? {
        YTMusicSearchParser.findFirstMusicVideoType(playerJson)?.let { type ->
            return when {
                type == "MUSIC_VIDEO_TYPE_ATV" ||
                    type == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" -> Source.YOUTUBE_MUSIC
                type.startsWith("MUSIC_VIDEO_TYPE_") -> Source.YOUTUBE
                else -> null
            }
        }

        val viewCount = playerJson.optJSONObject("videoDetails")
            ?.optString("viewCount")?.toLongOrNull()
        if (viewCount != null) {
            return if (viewCount > 0) Source.YOUTUBE else Source.YOUTUBE_MUSIC
        }

        YTMusicSearchParser.detectSourceFromThumbnailUrl(
            YTMusicThumbnailUtils.extractThumbnailFromPlayer(playerJson)
        )?.let { return it }
        return null
    }

    private companion object {
        const val TAG = "VzTuneYTM"
    }
}