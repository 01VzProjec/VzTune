package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import org.json.JSONArray
import org.json.JSONObject

internal object YTMusicSearchParser {

    sealed interface ParsedItem {
        val stableKey: String

        data class Song(
            val videoId: String,
            val title: String,
            val artist: String,
            val thumbnailUrl: String?,
            val durationSeconds: Long,
            val source: Source,
        ) : ParsedItem {
            override val stableKey: String get() = "song_${source.name}_$videoId"
        }

        data class Album(
            val browseId: String,
            val title: String,
            val artist: String,
            val thumbnailUrl: String?,
        ) : ParsedItem {
            override val stableKey: String get() = "album_$browseId"
        }

        data class Artist(
            val browseId: String,
            val name: String,
            val thumbnailUrl: String?,
        ) : ParsedItem {
            override val stableKey: String get() = "artist_$browseId"
        }

        data class Playlist(
            val browseId: String,
            val title: String,
            val owner: String,
            val thumbnailUrl: String?,
        ) : ParsedItem {
            override val stableKey: String get() = "playlist_$browseId"
        }
    }

    fun parseSearchResults(json: JSONObject): List<ParsedItem> {
        val out = mutableListOf<ParsedItem>()
        YTMusicTextUtils.walk(json) { renderer ->
            renderer.optJSONObject("musicResponsiveListItemRenderer")?.let {
                parseResponsiveListItem(it, out)
            }
            renderer.optJSONObject("musicTwoRowItemRenderer")?.let {
                parseTwoRowItem(it, out)
            }
        }
        return out.distinctBy { it.stableKey }
    }

    fun parseSuggestions(json: JSONObject): List<String> {
        val out = mutableListOf<String>()
        YTMusicTextUtils.walk(json) { node ->
            val s = node.optJSONObject("searchSuggestionRenderer") ?: return@walk
            val runs = s.optJSONObject("suggestion")?.optJSONArray("runs") ?: return@walk
            val text = buildString {
                for (i in 0 until runs.length()) {
                    append(runs.optJSONObject(i)?.optString("text") ?: "")
                }
            }.takeIf { it.isNotBlank() }
            if (text != null && text !in out) out += text
        }
        return out
    }

    fun toHit(item: ParsedItem, filter: SearchFilter): SearchHit? {
        return when (item) {
            is ParsedItem.Song -> {
                if (item.source == Source.YOUTUBE) return null
                if (filter != SearchFilter.ALL && filter != SearchFilter.SONGS) return null
                SearchHit.SongHit(
                    Track(
                        id = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        durationSeconds = item.durationSeconds,
                        thumbnailUrl = item.thumbnailUrl,
                        source = item.source,
                    )
                )
            }
            is ParsedItem.Album -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.ALBUMS) return null
                SearchHit.AlbumHit(
                    Album(
                        id = item.browseId,
                        title = item.title,
                        artist = item.artist,
                        thumbnailUrl = item.thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
            is ParsedItem.Artist -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.ARTISTS) return null
                SearchHit.ArtistHit(
                    Artist(
                        id = item.browseId,
                        name = item.name,
                        thumbnailUrl = item.thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
            is ParsedItem.Playlist -> {
                if (filter != SearchFilter.ALL && filter != SearchFilter.PLAYLISTS) return null
                SearchHit.PlaylistHit(
                    Playlist(
                        id = item.browseId,
                        title = item.title,
                        owner = item.owner,
                        thumbnailUrl = item.thumbnailUrl,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }
        }
    }

    fun detectSource(videoId: String, renderer: JSONObject): Source {
        YTMusicCaches.sourceCache[videoId]?.let { return it }
        val type = findFirstMusicVideoType(renderer)
        if (type != null) {
            val resolved: Source? = when {
                type == "MUSIC_VIDEO_TYPE_ATV" || type == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" -> Source.YOUTUBE_MUSIC
                type.startsWith("MUSIC_VIDEO_TYPE_") -> Source.YOUTUBE
                else -> null
            }
            if (resolved != null) {
                YTMusicCaches.sourceCache[videoId] = resolved
                return resolved
            }
        }
        detectSourceFromThumbnailUrl(YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnail")))?.let {
            YTMusicCaches.sourceCache[videoId] = it
            return it
        }
        val fallback = Source.YOUTUBE_MUSIC
        YTMusicCaches.sourceCache[videoId] = fallback
        return fallback
    }

    fun detectSourceFromThumbnailUrl(url: String?): Source? {
        if (url.isNullOrBlank()) return null
        if (url.contains("ytimg.com")) return Source.YOUTUBE
        if (url.contains("googleusercontent.com")) {
            val regex = Regex("=w(\\d+)-h(\\d+)")
            val match = regex.find(url) ?: return Source.YOUTUBE_MUSIC
            val w = match.groupValues[1].toIntOrNull() ?: return null
            val h = match.groupValues[2].toIntOrNull() ?: return null
            return when {
                w == h -> Source.YOUTUBE_MUSIC
                w > h -> Source.YOUTUBE
                else -> null
            }
        }
        return null
    }

    fun findFirstMusicVideoType(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.optString("musicVideoType")
                if (direct.startsWith("MUSIC_VIDEO_TYPE_")) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    findFirstMusicVideoType(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                findFirstMusicVideoType(node.opt(i))?.let { return it }
            }
        }
        return null
    }

    private fun parseResponsiveListItem(r: JSONObject, out: MutableList<ParsedItem>) {
        val columns = r.optJSONArray("flexColumns") ?: return
        val title = YTMusicTextUtils.flexText(columns, 0) ?: return
        val subtitleCol = columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
        val thumb = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail"))

        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
        if (!videoId.isNullOrBlank() && videoId != "null") {
            val source = detectSource(videoId, r)
            val uploader = extractArtistFromSubtitle(subtitleCol, subtitle) ?: extractChannelName(subtitleCol) ?: "Unknown"
            out += ParsedItem.Song(videoId, title, uploader, thumb, YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(r)), source)
            return
        }

        val browseId = extractBrowseId(r) ?: return
        when {
            browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                val cleanBrowseId = YTMusicTextUtils.normalizePlaylistBrowseId(browseId)
                val owner = extractPlaylistOwner(subtitleCol) ?: subtitle.substringBefore("•").trim().takeIf { it.isNotBlank() && !YTMusicTextUtils.isTypeWord(it) } ?: "Playlist"
                out += ParsedItem.Playlist(cleanBrowseId, title, owner, thumb)
            }
            browseId.startsWith("MPREb_") -> {
                val artist = extractAlbumArtist(subtitleCol) ?: subtitle.substringBefore("•").trim().takeIf { it.isNotBlank() && !YTMusicTextUtils.isTypeWord(it) } ?: ""
                out += ParsedItem.Album(browseId, title, artist, thumb)
            }
            browseId.startsWith("UC") -> out += ParsedItem.Artist(browseId, title, thumb)
        }
    }

    private fun parseTwoRowItem(r: JSONObject, out: MutableList<ParsedItem>) {
        val titleRuns = r.optJSONObject("title")?.optJSONArray("runs")
        val first = titleRuns?.optJSONObject(0) ?: return
        val title = first.optString("text").takeIf { it.isNotBlank() } ?: return
        val browseId = first.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() && it != "null" } ?: extractBrowseId(r) ?: return
        val subtitleCol = r.optJSONObject("subtitle")
        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
        val thumb = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnailRenderer"))

        when {
            browseId.startsWith("MPREb_") -> {
                val artist = extractAlbumArtist(subtitleCol) ?: subtitle.substringBefore("•").trim().takeIf { it.isNotBlank() && !YTMusicTextUtils.isTypeWord(it) } ?: ""
                out += ParsedItem.Album(browseId, title, artist, thumb)
            }
            browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                val cleanBrowseId = YTMusicTextUtils.normalizePlaylistBrowseId(browseId)
                val owner = extractPlaylistOwner(subtitleCol) ?: subtitle.substringBefore("•").trim().takeIf { it.isNotBlank() && !YTMusicTextUtils.isTypeWord(it) } ?: "Playlist"
                out += ParsedItem.Playlist(cleanBrowseId, title, owner, thumb)
            }
            browseId.startsWith("UC") -> out += ParsedItem.Artist(browseId, title, thumb)
        }
    }

    private fun extractAlbumArtist(subtitleCol: JSONObject?): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs") ?: subtitleCol?.optJSONArray("runs") ?: return null
        val artists = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val raw = run.optString("text")
            val text = raw.trim()
            if (text.isBlank() || text == "•" || text == "·") continue
            val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
            if (browseId?.startsWith("MPREb_") == true || browseId?.startsWith("VL") == true) break
            if (browseId?.startsWith("UC") == true || browseId?.startsWith("MPAD") == true) {
                val clean = YTMusicTextUtils.cleanArtistName(text).trim()
                if (clean.isNotBlank() && clean !in artists) artists += clean
            }
        }
        if (artists.isEmpty()) {
            val full = YTMusicTextUtils.runsText(subtitleCol)
            val beforeBullet = full.substringBefore("•").trim()
            if (beforeBullet.isNotBlank()) {
                return splitArtists(beforeBullet).joinToString(", ").takeIf { it.isNotBlank() }
            }
        }
        return if (artists.isNotEmpty()) artists.joinToString(", ") else null
    }

    private fun extractPlaylistOwner(subtitleCol: JSONObject?): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs") ?: subtitleCol?.optJSONArray("runs") ?: return null
        val owners = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text").trim()
            if (text.isBlank() || text == "•" || text == "·") continue
            val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
            if (browseId?.startsWith("UC") == true) {
                if (text !in owners) owners += text
            }
        }
        return if (owners.isNotEmpty()) owners.joinToString(", ") else null
    }

    internal fun extractArtistFromSubtitle(subtitleCol: JSONObject?, subtitleText: String): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs") ?: subtitleCol?.optJSONArray("runs")
        if (runs != null) {
            val artists = mutableListOf<String>()
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val rawText = run.optString("text")
                val textTrimmed = rawText.trim()
                if (textTrimmed.isBlank()) continue

                if (textTrimmed == "•" || textTrimmed == "·") {
                    var hasAlbumAhead = false
                    for (j in i + 1 until runs.length()) {
                        val r2 = runs.optJSONObject(j) ?: continue
                        val b2 = r2.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                        if (b2?.startsWith("MPREb_") == true || b2?.startsWith("VL") == true) {
                            hasAlbumAhead = true
                            break
                        }
                    }
                    if (hasAlbumAhead && artists.isNotEmpty()) break
                    continue
                }

                val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (browseId?.startsWith("MPREb_") == true || browseId?.startsWith("VL") == true) break

                if (YTMusicTextUtils.isDuration(textTrimmed)) break
                if (YTMusicTextUtils.isYear(textTrimmed)) continue
                if (YTMusicTextUtils.isDate(textTrimmed)) continue
                if (YTMusicTextUtils.isEpisode(textTrimmed)) continue
                if (YTMusicTextUtils.isViewCount(textTrimmed)) continue
                if (YTMusicTextUtils.isTypeWord(textTrimmed)) continue
                if (textTrimmed.length > 80) continue

                if (browseId?.startsWith("UC") == true || browseId?.startsWith("MPAD") == true) {
                    val clean = YTMusicTextUtils.cleanArtistName(textTrimmed)
                    if (clean.isNotBlank() && clean !in artists) {
                        splitArtists(clean).forEach { part ->
                            if (part !in artists) artists.add(part)
                        }
                    }
                } else {
                    val lower = textTrimmed.lowercase()
                    if (lower in setOf(",", "&", "e", "and", "x", "feat.", "feat", "ft.", "ft", "com", "with", "/", ";")) continue
                    
                    if (textTrimmed.contains(",") || textTrimmed.contains("&")) {
                        splitArtists(textTrimmed).forEach { part ->
                            val c = YTMusicTextUtils.cleanArtistName(part)
                            if (c.isNotBlank() && c !in artists && !YTMusicTextUtils.isTypeWord(c)) {
                                artists.add(c)
                            }
                        }
                    } else {
                        if (artists.size < 5) {
                            val clean = YTMusicTextUtils.cleanArtistName(textTrimmed)
                            if (clean.isNotBlank() && clean.length in 2..60 && clean !in artists) {
                                if (!YTMusicTextUtils.isTypeWord(clean)) {
                                    if (clean.contains(",") || clean.contains("&")) {
                                        splitArtists(clean).forEach { p ->
                                            if (p !in artists) artists.add(p)
                                        }
                                    } else {
                                        artists.add(clean)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (artists.isNotEmpty()) {
                return artists.distinct().joinToString(", ")
            }
        }

        val firstBlock = subtitleText.split("•").firstOrNull()?.trim().orEmpty()
        if (firstBlock.isNotBlank()) {
            val splitted = splitArtists(firstBlock)
            val valid = splitted.filter { seg ->
                seg.isNotBlank() &&
                !YTMusicTextUtils.isTypeWord(seg) &&
                !YTMusicTextUtils.isDuration(seg) &&
                !YTMusicTextUtils.isYear(seg) &&
                !YTMusicTextUtils.isDate(seg) &&
                seg.length <= 80
            }
            if (valid.isNotEmpty()) return valid.joinToString(", ")
        }
        return null
    }

    internal fun splitArtists(input: String): List<String> {
        val regex = Regex("\\s*,\\s*|\\s*&\\s*|\\s+ e \\s+|\\s+ and \\s+|\\s+ x \\s+|\\s+ feat\\.? \\s+|\\s+ ft\\.? \\s+|\\s+ com \\s+|\\s+ with \\s+|\\s*\\|\\s*|\\s*/\\s*|\\s*;\\s*", RegexOption.IGNORE_CASE)
        return input.split(regex).map { it.trim().trim(',', '&', '·', '•', '-', '/', ';', ' ') }.filter { it.isNotBlank() }
    }

    private fun extractChannelName(subtitleCol: JSONObject?): String? {
        val runs = subtitleCol?.optJSONObject("text")?.optJSONArray("runs") ?: subtitleCol?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                if (text.isBlank() || text == "•" || text == "·") continue
                val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (browseId?.startsWith("UC") == true) return text
            }
        }
        val full = YTMusicTextUtils.runsText(subtitleCol)
        val segments = full.split("•").map { it.trim() }.filter { it.isNotBlank() }
        return segments.firstOrNull { it.length < 80 && !YTMusicTextUtils.isTypeWord(it) }
    }

    private fun extractBrowseId(r: JSONObject): String? {
        val nav = r.optJSONObject("navigationEndpoint")
        nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return null
    }
}