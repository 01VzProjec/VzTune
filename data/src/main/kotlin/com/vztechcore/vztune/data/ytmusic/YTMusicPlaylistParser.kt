package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import org.json.JSONObject

/**
 * Parser dedicated to extracting Playlists in detail,
 * supporting continuations (pagination) to load giant playlists.
 */
internal object YTMusicPlaylistParser {

    data class PlaylistDetails(
        val title: String,
        val owner: String,
        val description: String?,
        val coverUrl: String?,
        val trackCount: Int,
        val year: String?,
        val tracks: List<Track>,
        val continuationToken: String?
    )

    fun parsePlaylist(json: JSONObject): PlaylistDetails {
        val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")

        val title = YTMusicArtistParser.extractHeaderTitle(header) ?: "Playlist"
        val coverUrl = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
        
        // Robust system for extracting playlist owner/creator
        val owner = extractOwner(header) ?: "Unknown"
        
        val subtitle = YTMusicTextUtils.runsText(header?.optJSONObject("subtitle"))
        val trackCount = extractTrackCount(subtitle)
        val year = extractYear(subtitle)
        
        val shelf = YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer") 
            ?: YTMusicTextUtils.findFirst(json, "musicShelfRenderer")

        val tracks = mutableListOf<Track>()
        shelf?.optJSONArray("contents")?.let { contents ->
            for (i in 0 until contents.length()) {
                val renderer = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null) {
                    parsePlaylistItem(renderer, tracks)
                }
            }
        }

        val continuation = extractContinuation(shelf)

        return PlaylistDetails(
            title = title,
            owner = owner,
            description = extractDescription(json, header),
            coverUrl = coverUrl,
            trackCount = trackCount,
            year = year,
            tracks = tracks,
            continuationToken = continuation
        )
    }

    fun parsePlaylistContinuation(json: JSONObject): Pair<List<Track>, String?> {
        val tracks = mutableListOf<Track>()
        val contents = YTMusicTextUtils.findFirst(json, "musicPlaylistShelfContinuation")
            ?.optJSONArray("contents") ?: json.optJSONArray("continuationContents")
        
        if (contents != null) {
             for (i in 0 until contents.length()) {
                val renderer = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null) {
                    parsePlaylistItem(renderer, tracks)
                }
            }
        }
        
        val continuation = extractContinuation(YTMusicTextUtils.findFirst(json, "musicPlaylistShelfContinuation"))
        return Pair(tracks, continuation)
    }

    private fun parsePlaylistItem(r: JSONObject, out: MutableList<Track>) {
        val columns = r.optJSONArray("flexColumns") ?: return
        val title = YTMusicTextUtils.flexText(columns, 0) ?: return
        
        var videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
        if (videoId.isNullOrBlank() || videoId == "null") {
            // Mixes RD... sometimes hide the videoId deeper
            videoId = r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
        }

        if (!videoId.isNullOrBlank() && videoId != "null") {
            val subtitleCol = columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val subtitle = YTMusicTextUtils.runsText(subtitleCol)
            
            // SimpMusic robust artist extraction
            val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                ?: subtitle.substringBefore("•").trim().ifBlank { "Unknown" }
                
            out.add(
                Track(
                    id = videoId,
                    title = title,
                    artist = uploader,
                    thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail")),
                    durationSeconds = YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(r)),
                    source = Source.YOUTUBE_MUSIC
                )
            )
        }
    }

    private fun extractOwner(header: JSONObject?): String? {
        if (header == null) return null
        
        // 1. Try finding in straplineTextOne (Common in new layout: musicResponsiveHeaderRenderer)
        val strapline = YTMusicTextUtils.runsText(header.optJSONObject("straplineTextOne"))
        if (strapline.isNotBlank()) {
            val owner = strapline.substringBefore("•").trim()
            if (owner.isNotBlank()) return owner
        }
        
        // 2. Try finding in secondSubtitle
        val secondSubtitle = YTMusicTextUtils.runsText(header.optJSONObject("secondSubtitle"))
        if (secondSubtitle.isNotBlank()) {
            val owner = secondSubtitle.substringBefore("•").trim()
            if (owner.isNotBlank()) return owner
        }
        
        // 3. Try finding in classic subtitle hunting for the channel link (UC...)
        val runs = header.optJSONObject("subtitle")?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                
                if (browseId?.startsWith("UC") == true || browseId?.startsWith("FE") == true) {
                    return run.optString("text").trim()
                }
            }
        }
        
        // 4. Fallback: Grab the first segment of the subtitle before a bullet point '•'
        val subtitle = YTMusicTextUtils.runsText(header.optJSONObject("subtitle"))
        val firstPart = subtitle.split("•").firstOrNull()?.trim()
        if (firstPart != null && firstPart.isNotBlank() && !firstPart.equals("Playlist", true)) {
            return firstPart
        }
        
        return null
    }

    private fun extractTrackCount(subtitle: String): Int {
        val match = Regex("(\\d+)\\s*(músicas|songs|faixas|tracks)", RegexOption.IGNORE_CASE).find(subtitle)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractYear(subtitle: String): String? {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(subtitle)
        return match?.value
    }

    private fun extractDescription(json: JSONObject, header: JSONObject?): String? {
        // 1. Look for the description in the global page shelves
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
        val shelfDesc = YTMusicTextUtils.extractRunsText(shelf?.optJSONObject("description"))
        if (shelfDesc.isNotBlank()) return shelfDesc

        // 2. Fallback: Look inside the header
        val headerDesc = YTMusicTextUtils.extractRunsText(header?.optJSONObject("description"))
        if (headerDesc.isNotBlank()) return headerDesc
        
        // 3. Try grabbing as normal text if it's not a 'run'
        val rawDesc = YTMusicTextUtils.runsText(header?.optJSONObject("description"))
        if (rawDesc.isNotBlank()) return rawDesc

        return null
    }

    private fun extractContinuation(node: JSONObject?): String? {
        val continuations = node?.optJSONArray("continuations") ?: return null
        return continuations.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
    }
}