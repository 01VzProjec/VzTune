package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import org.json.JSONArray
import org.json.JSONObject

internal object YTMusicPlaylistParser {

    data class PlaylistDetails(
        val title: String,
        val owner: String,
        val description: String?,
        val coverUrl: String?,
        val trackCount: Int,
        val year: String?,
        val views: String?,
        val tracks: List<Track>,
        val continuationToken: String?
    )

    fun parsePlaylist(json: JSONObject): PlaylistDetails {
        val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")

        val title = YTMusicArtistParser.extractHeaderTitle(header) ?: "Playlist"
        val coverUrl = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
        
        val subtitleRuns = header?.optJSONObject("subtitle")?.optJSONArray("runs")
        val (owner, year, views) = extractPlaylistMetadata(subtitleRuns)
        
        val trackCount = extractTrackCount(YTMusicTextUtils.runsText(header?.optJSONObject("secondSubtitle")) + " " + YTMusicTextUtils.runsText(header?.optJSONObject("subtitle")))
        
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
            views = views,
            tracks = tracks,
            continuationToken = continuation
        )
    }

    private fun extractPlaylistMetadata(subtitleRuns: JSONArray?): Triple<String, String?, String?> {
        var owner = "Desconhecido"
        var year: String? = null
        var views: String? = null

        if (subtitleRuns == null) return Triple(owner, year, views)

        val parts = mutableListOf<String>()
        for (i in 0 until subtitleRuns.length()) {
            val text = subtitleRuns.optJSONObject(i)?.optString("text")?.trim() ?: continue
            if (text.isNotBlank() && text != "•" && text != "·") {
                parts.add(text)
            }
        }

        if (parts.isNotEmpty()) {
            // O primeiro item útil geralmente é o criador/dono
            val firstPart = parts[0]
            if (!firstPart.equals("Playlist", true) && !firstPart.equals("Álbum", true) && !firstPart.equals("Album", true)) {
                owner = firstPart
            }

            // O resto pode ser o Ano ou Visualizações
            for (part in parts.drop(1)) {
                if (YTMusicTextUtils.isYear(part)) {
                    year = part
                } else if (YTMusicTextUtils.isViewCount(part)) {
                    views = part
                }
            }
        }
        return Triple(owner, year, views)
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
            videoId = r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
        }

        if (!videoId.isNullOrBlank() && videoId != "null") {
            val subtitleCol = columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val subtitle = YTMusicTextUtils.runsText(subtitleCol)
            
            val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                ?: subtitle.substringBefore("•").trim().ifBlank { "Desconhecido" }
                
            // Correção crucial: Detectar se é música ou vídeo em vez de forçar Source.YOUTUBE_MUSIC
            val source = YTMusicSearchParser.detectSource(videoId, r)
                
            out.add(
                Track(
                    id = videoId,
                    title = title,
                    artist = uploader,
                    thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail")),
                    durationSeconds = YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(r)),
                    source = source
                )
            )
        }
    }

    private fun extractTrackCount(text: String): Int {
        val match = Regex("(\\d+)\\s*(músicas|songs|faixas|tracks)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractDescription(json: JSONObject, header: JSONObject?): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
        val shelfDesc = YTMusicTextUtils.extractRunsText(shelf?.optJSONObject("description"))
        if (shelfDesc.isNotBlank()) return shelfDesc

        val headerDesc = YTMusicTextUtils.extractRunsText(header?.optJSONObject("description"))
        if (headerDesc.isNotBlank()) return headerDesc
        
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