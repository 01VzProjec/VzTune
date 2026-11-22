package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.innertube.InnerTubeYouTube
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses YouTube InnerTube browse JSON for a channel (youtube.com).
 * Header + tabs (Videos / Shorts / Playlists). Falls back to whole JSON if no tabs.
 */
object YouTubeChannelParser {

    data class ChannelData(
        val channelName: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val description: String?,
        val subscriberCount: String?,
        val videos: List<Track>,
        val shorts: List<Track>,
        val playlists: List<Playlist>,
        val videosContinuation: String?,
        val playlistsContinuation: String?,
        val shortsContinuation: String?,
    )

    fun parseChannelPage(json: JSONObject): ChannelData {
        val header = parseHeader(json)
        val tabs = findTabs(json)
        var videos = emptyList<Track>()
        var shorts = emptyList<Track>()
        var playlists = emptyList<Playlist>()
        var videosCont: String? = null
        var playlistsCont: String? = null
        var shortsCont: String? = null
        if (tabs.isEmpty()) {
            videos = collectVideos(json)
            shorts = collectShorts(json)
            playlists = collectPlaylists(json)
            videosCont = InnerTubeYouTube.findContinuationToken(json)
        } else {
            for ((title, tabJson) in tabs) {
                val low = title.lowercase()
                when {
                    low.contains("video") || low.contains("vídeo") -> {
                        videos = collectVideos(tabJson)
                        videosCont = InnerTubeYouTube.findContinuationToken(tabJson)
                    }
                    low.contains("short") -> {
                        shorts = collectShorts(tabJson)
                        shortsCont = InnerTubeYouTube.findContinuationToken(tabJson)
                    }
                    low.contains("playlist") -> {
                        playlists = collectPlaylists(tabJson)
                        playlistsCont = InnerTubeYouTube.findContinuationToken(tabJson)
                    }
                    low.contains("início") || low.contains("inicio") || low.contains("home") -> {
                        if (videos.isEmpty()) {
                            val homeVideos = collectVideos(tabJson)
                            if (homeVideos.isNotEmpty()) videos = homeVideos
                        }
                    }
                }
            }
            if (videos.isEmpty()) {
                val allVideos = collectVideos(json)
                val shortIds = shorts.map { it.id }.toSet()
                videos = allVideos.filter { it.id !in shortIds }
            }
        }
        return ChannelData(
            channelName = header.first,
            avatarUrl = header.second,
            bannerUrl = header.third,
            description = header.fourth,
            subscriberCount = header.fifth,
            videos = videos.distinctBy { it.id },
            shorts = shorts.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
            videosContinuation = videosCont,
            playlistsContinuation = playlistsCont,
            shortsContinuation = shortsCont
        )
    }

    fun parseVideosContinuation(json: JSONObject): Pair<List<Track>, String?> {
        val videos = collectVideos(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return videos.distinctBy { it.id } to cont
    }

    fun parsePlaylistsContinuation(json: JSONObject): Pair<List<Playlist>, String?> {
        val playlists = collectPlaylists(json)
        val cont = InnerTubeYouTube.findContinuationToken(json)
        return playlists.distinctBy { it.id } to cont
    }

    private fun parseHeader(json: JSONObject): Quint<String, String?, String?, String?, String?> {
        val headers = mutableListOf<JSONObject>()
        collect(json, "c4TabbedHeaderRenderer", headers)
        collect(json, "pageHeaderRenderer", headers)
        collect(json, "channelHeaderRenderer", headers)
        val h = headers.firstOrNull()
        if (h != null) {
            val name = YouTubeSearchParser.textOf(h.optJSONObject("title"))
                ?: YouTubeSearchParser.textOf(h.optJSONObject("channelTitle")) ?: ""
            val avatar = YouTubeSearchParser.thumbnailOf(h.optJSONObject("avatar"))
            val banner = YouTubeSearchParser.thumbnailOf(h.optJSONObject("banner"))
            val desc = YouTubeSearchParser.textOf(h.optJSONObject("description"))
            val subs = YouTubeSearchParser.textOf(h.optJSONObject("subscriberCountText"))
                ?: YouTubeSearchParser.textOf(h.optJSONObject("subscriberCount"))
            return Quint(name, avatar, banner, desc, subs)
        }
        return Quint("", null, null, null, null)
    }

    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    private fun findTabs(json: JSONObject): List<Pair<String, JSONObject>> {
        val tabs = mutableListOf<Pair<String, JSONObject>>()
        val tabRenderers = mutableListOf<JSONObject>()
        collect(json, "tabRenderer", tabRenderers)
        for (t in tabRenderers) {
            val title = YouTubeSearchParser.textOf(t.optJSONObject("title")) ?: continue
            tabs += title to t
        }
        return tabs
    }

    private fun collectVideos(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val videoRenderers = mutableListOf<JSONObject>()
        collect(node, "videoRenderer", videoRenderers)
        collect(node, "gridVideoRenderer", videoRenderers)
        for (r in videoRenderers) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            val duration = YouTubeSearchParser.parseDuration(
                YouTubeSearchParser.textOf(r.optJSONObject("lengthText")) ?: r.optString("lengthText")
            )
            val channel = YouTubeSearchParser.textOf(r.optJSONObject("ownerText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("shortBylineText")) ?: ""
            val published = YouTubeSearchParser.textOf(r.optJSONObject("publishedTimeText"))
            out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published)
        }
        return out
    }

    private fun collectShorts(node: Any?): List<Track> {
        val out = mutableListOf<Track>()
        val reels = mutableListOf<JSONObject>()
        collect(node, "reelItemRenderer", reels)
        for (r in reels) {
            val videoId = r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("headline"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        val lockups = mutableListOf<JSONObject>()
        collect(node, "shortsLockupViewModel", lockups)
        for (r in lockups) {
            val videoId = r.optString("contentId").takeIf { it.isNotBlank() }
                ?: r.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val title = r.optString("title").takeIf { it.isNotBlank() } ?: continue
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            out += Track(id = videoId, title = title, artist = "", durationSeconds = 0L, thumbnailUrl = thumb, source = Source.YOUTUBE)
        }
        return out
    }

        private fun collectPlaylists(node: Any?): List<Playlist> {
        val out = mutableListOf<Playlist>()
        val renderers = mutableListOf<JSONObject>()
        collect(node, "playlistRenderer", renderers)
        collect(node, "gridPlaylistRenderer", renderers)
        for (r in renderers) {
            val pid = r.optString("playlistId").takeIf { it.isNotBlank() } ?: continue
            val title = YouTubeSearchParser.textOf(r.optJSONObject("title")) ?: continue
            val owner = YouTubeSearchParser.textOf(r.optJSONObject("shortBylineText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("longBylineText"))
                ?: YouTubeSearchParser.textOf(r.optJSONObject("ownerText")) ?: ""
            val thumb = YouTubeSearchParser.thumbnailOf(r.optJSONObject("thumbnail"))
            val countText = r.optString("videoCount").takeIf { it.isNotBlank() }
                ?: YouTubeSearchParser.textOf(r.optJSONObject("videoCountText"))
            val count = countText?.filter { it.isDigit() }?.toIntOrNull()
            out += Playlist(id = pid, title = title, owner = owner.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE)
        }
        // Layout 2026: lockupViewModel (mesma logica da busca)
        val lockups = mutableListOf<JSONObject>()
        collect(node, "lockupViewModel", lockups)
        for (lvm in lockups) {
            val type = lvm.optString("contentType")
            if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") continue
            val pid = YouTubeSearchParser.lockupPlaylistId(lvm) ?: continue
            if (!pid.startsWith("PL") && !pid.startsWith("UU") && !pid.startsWith("OL") && !pid.startsWith("RD") && !pid.startsWith("FL")) continue
            val title = YouTubeSearchParser.lockupTitle(lvm) ?: continue
            val owner = YouTubeSearchParser.lockupOwner(lvm)
            val thumb = YouTubeSearchParser.lockupThumbnail(lvm)
            val count = YouTubeSearchParser.lockupCount(lvm)
            out += Playlist(id = pid, title = title, owner = owner?.ifBlank { null }, thumbnailUrl = thumb, trackCount = count, source = Source.YOUTUBE)
        }
        return out
    }

    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) {
                    val v = node.opt(key)
                    if (v is JSONObject) out += v
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == key) continue
                    collect(node.opt(k), key, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collect(node.opt(i), key, out)
            }
        }
    }
}
