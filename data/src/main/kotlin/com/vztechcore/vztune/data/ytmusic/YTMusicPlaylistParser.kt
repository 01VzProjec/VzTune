package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import org.json.JSONArray
import org.json.JSONObject

internal object YTMusicPlaylistParser {

    data class PlaylistDetails(
        val title: String,
        val owner: String?,             // null = unknown (UI decides fallback)
        val ownerAvatarUrl: String?,
        val description: String?,
        val coverUrl: String?,
        val trackCount: Int,
        val year: String?,
        val views: String?,
        val tracks: List<Track>,
        val continuationToken: String?
    )

    private data class Meta(
        val owner: String?,
        val ownerAvatarUrl: String?,
        val year: String?,
        val views: String?,
    )

    fun parsePlaylist(json: JSONObject): PlaylistDetails {
        val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "playlistHeaderRenderer")

        val title = YTMusicArtistParser.extractHeaderTitle(header) ?: "Playlist"
        val coverUrl = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }

        val meta = extractPlaylistMetadata(header)

        val trackCount = extractTrackCount(
            YTMusicTextUtils.runsText(header?.optJSONObject("secondSubtitle")) + " " +
                YTMusicTextUtils.runsText(header?.optJSONObject("subtitle"))
        )

        val shelf = YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicShelfRenderer")

        val tracks = mutableListOf<Track>()
        shelf?.optJSONArray("contents")?.let { contents ->
            for (i in 0 until contents.length()) {
                val renderer = contents.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null) {
                    parsePlaylistItem(renderer, tracks)
                }
            }
        }

        return PlaylistDetails(
            title = title,
            owner = meta.owner,
            ownerAvatarUrl = meta.ownerAvatarUrl,
            description = extractDescription(json, header),
            coverUrl = coverUrl,
            trackCount = trackCount,
            year = meta.year,
            views = meta.views,
            tracks = tracks,
            continuationToken = extractContinuation(shelf)
        )
    }

    private fun extractPlaylistMetadata(header: JSONObject?): Meta {
        if (header == null) return Meta(null, null, null, null)

        var owner: String? = null
        var ownerAvatarUrl: String? = null
        var year: String? = null
        var views: String? = null

        // ---- 1. AVATAR — multiple known layouts ----
        ownerAvatarUrl = extractAvatar(header)

        // ---- 2. OWNER ----
        // straplineTextOne: usually "Channel: <Name>" or just "<Name>"
        header.optJSONObject("straplineTextOne")?.optJSONArray("runs")?.let { runs ->
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (browseId?.startsWith("UC") == true || browseId?.startsWith("MPAD") == true) {
                    val cleaned = cleanOwner(run.optString("text"))
                    if (cleaned != null) { owner = cleaned; break }
                }
            }
        }

        // ownerText / author
        if (owner == null) {
            owner = cleanOwner(YTMusicTextUtils.runsText(header.optJSONObject("ownerText")))
        }
        if (owner == null) {
            owner = cleanOwner(YTMusicTextUtils.runsText(header.optJSONObject("author")))
        }

        // Deep walk for UC browseIds inside runs
        if (owner == null) {
            YTMusicTextUtils.walk(header) { node ->
                if (owner != null) return@walk
                val runs = node.optJSONArray("runs") ?: return@walk
                for (i in 0 until runs.length()) {
                    val run = runs.optJSONObject(i) ?: continue
                    val browseId = run.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (browseId?.startsWith("UC") == true || browseId?.startsWith("MPAD") == true) {
                        val cleaned = cleanOwner(run.optString("text"))
                        if (cleaned != null) { owner = cleaned; break }
                    }
                }
            }
        }

        // ---- 3. SUBTITLES (year / views / maybe owner) ----
        val subtitleArrays = listOf(
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in subtitleArrays) {
            if (runs == null) continue
            for (i in 0 until runs.length()) {
                val text = runs.optJSONObject(i)?.optString("text")?.trim() ?: continue
                if (text.isBlank() || text == "•" || text == "·") continue

                when {
                    YTMusicTextUtils.isYear(text) -> year = text
                    YTMusicTextUtils.isViewCount(text) -> views = text
                    owner == null &&
                        !YTMusicTextUtils.isTypeWord(text) &&
                        !YTMusicTextUtils.isDuration(text) &&
                        !YTMusicTextUtils.isDate(text) -> {
                        owner = cleanOwner(text)
                    }
                }
            }
        }

        // ---- 4. VIEWS FALLBACK ----
        // YouTube (non-Music) playlists may expose the view count in other runs of the header.
        if (views == null) {
            YTMusicTextUtils.walk(header) { node ->
                if (views != null) return@walk
                val runs = node.optJSONArray("runs") ?: return@walk
                for (i in 0 until runs.length()) {
                    val text = runs.optJSONObject(i)?.optString("text")?.trim().orEmpty()
                    if (text.isNotBlank() && YTMusicTextUtils.isViewCount(text)) {
                        views = text
                        break
                    }
                }
            }
        }

        return Meta(owner, ownerAvatarUrl, year, views)
    }

    /**
     * Cleans an owner candidate:
     *  - strips "Channel: " / "Canal: " prefix
     *  - strips " - Topic" suffix
     *  - drops type words ("Playlist", "Álbum", ...), empty strings and dates
     * Returns null when the candidate is not a usable owner name.
     */
    private fun cleanOwner(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        s = s.removePrefix("Channel: ").removePrefix("channel: ")
        s = s.removePrefix("Canal: ").removePrefix("canal: ")
        s = YTMusicTextUtils.cleanArtistName(s).trim()
        if (s.isBlank()) return null
        if (YTMusicTextUtils.isTypeWord(s)) return null
        if (YTMusicTextUtils.isDuration(s)) return null
        if (YTMusicTextUtils.isDate(s)) return null
        if (YTMusicTextUtils.isYear(s)) return null
        // Track counts ("20 songs") and total durations must never be treated as the creator name.
        if (YTMusicTextUtils.isCountOrDurationLabel(s)) return null
        if (YTMusicTextUtils.isViewCount(s)) return null
        if (s.length > 100) return null
        return s
    }

    private fun extractAvatar(header: JSONObject): String? {
        // 1. straplineThumbnail — classic layout
        header.optJSONObject("straplineThumbnail")?.let { node ->
            YTMusicThumbnailUtils.thumbnailOf(node)?.let { return it }
        }
        // 2. authorThumbnail
        header.optJSONObject("authorThumbnail")?.let { node ->
            YTMusicThumbnailUtils.thumbnailOf(node)?.let { return it }
        }
        // 3. facepile.avatarStackModel.avatars[0]
        header.optJSONObject("facepile")
            ?.optJSONObject("avatarStackModel")
            ?.optJSONArray("avatars")
            ?.let { arr ->
                if (arr.length() > 0) {
                    arr.optJSONObject(0)
                        ?.optJSONObject("avatar")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.let { YTMusicThumbnailUtils.thumbnailOf(it) }
                        ?.let { return it }
                }
            }
        // 4. owner.musicArtistCardModel
        header.optJSONObject("owner")
            ?.optJSONObject("musicArtistCardModel")
            ?.let { node ->
                YTMusicThumbnailUtils.thumbnailOf(node)?.let { return it }
            }
        return null
    }

    fun parsePlaylistContinuation(json: JSONObject): Pair<List<Track>, String?> {
        val tracks = mutableListOf<Track>()

        val contents = YTMusicTextUtils.findFirst(json, "musicPlaylistShelfContinuation")
            ?.optJSONArray("contents")
            ?: json.optJSONArray("continuationContents")

        if (contents != null) {
            for (i in 0 until contents.length()) {
                val renderer = contents.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null) {
                    parsePlaylistItem(renderer, tracks)
                }
            }
        }

        val continuation = extractContinuation(
            YTMusicTextUtils.findFirst(json, "musicPlaylistShelfContinuation")
        )

        return Pair(tracks, continuation)
    }

    /**
     * Collects every track-like list item found anywhere in a browse / continuation response.
     * Used by the artist "see all" pages (songs and videos), which come as playlist-like shelves.
     */
    fun parseTracksAnywhere(json: JSONObject): List<Track> {
        val tracks = mutableListOf<Track>()
        YTMusicTextUtils.walk(json) { node ->
            node.optJSONObject("musicResponsiveListItemRenderer")?.let { parsePlaylistItem(it, tracks) }
        }
        return tracks
    }

    private fun parsePlaylistItem(r: JSONObject, out: MutableList<Track>) {
        val columns = r.optJSONArray("flexColumns") ?: return
        val title = YTMusicTextUtils.flexText(columns, 0) ?: return

        var videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
        if (videoId.isNullOrBlank() || videoId == "null") {
            videoId = r.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
        }

        if (!videoId.isNullOrBlank() && videoId != "null") {
            val subtitleCol = columns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val subtitle = YTMusicTextUtils.runsText(subtitleCol)

            val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                ?: subtitle.substringBefore("•").trim().ifBlank { "YouTube" }

            val source = YTMusicSearchParser.detectSource(videoId, r)

            out.add(
                Track(
                    id = videoId,
                    title = title,
                    artist = uploader,
                    thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(
                        r.optJSONObject("thumbnail")
                    ),
                    durationSeconds = YTMusicTextUtils.parseDuration(
                        YTMusicTextUtils.fixedText(r)
                    ),
                    source = source,
                    isExplicit = YTMusicSearchParser.hasExplicitBadge(r)
                )
            )
        }
    }

    private fun extractTrackCount(text: String): Int {
        val match = Regex("""(\d+)\s*(músicas|songs|faixas|tracks)""", RegexOption.IGNORE_CASE)
            .find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractDescription(json: JSONObject, header: JSONObject?): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
        val shelfDesc = YTMusicTextUtils.extractRunsText(shelf?.optJSONObject("description"))
        if (shelfDesc.isNotBlank()) return shelfDesc

        val headerDesc = YTMusicTextUtils.extractRunsText(header?.optJSONObject("description"))
        if (headerDesc.isNotBlank()) return headerDesc

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
