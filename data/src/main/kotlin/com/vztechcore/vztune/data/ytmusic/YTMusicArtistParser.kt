package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import org.json.JSONObject

/**
 * Parses the JSON returned by InnerTube's `browse` endpoint for an artist
 * page into a structured ArtistResult.
 */
internal object YTMusicArtistParser {

    data class ArtistResult(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val allSongs: List<Track>,
        val allVideos: List<Track>,
        val allAlbums: List<Playlist>,
        val allSingles: List<Playlist>,
        val featured: List<Track>,
        val relatedArtists: List<Artist>,
    )

    fun parseArtistPage(browseId: String, json: JSONObject): ArtistResult {
        val immersive = YTMusicTextUtils.findFirst(json, "musicImmersiveHeaderRenderer")
        val visual = YTMusicTextUtils.findFirst(json, "musicVisualHeaderRenderer")

        val name = immersive?.let { extractHeaderTitle(it) }
            ?: visual?.let { extractHeaderTitle(it) }
            ?: "Artista"

        val headerThumb = immersive?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
            ?: visual?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }

        val subscribers = immersive?.let { extractSubscribers(it) }
            ?: visual?.let { extractSubscribers(it) }

        val allSongs = mutableListOf<Track>()
        val allVideos = mutableListOf<Track>()
        val allAlbums = mutableListOf<Playlist>()
        val allSingles = mutableListOf<Playlist>()
        val featured = mutableListOf<Track>()
        val related = mutableListOf<Artist>()
        val seen = mutableSetOf<String>()

        YTMusicTextUtils.walk(json) { node ->
            node.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
                val rawTitle = carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")
                    ?.let { YTMusicTextUtils.extractRunsText(it) }.orEmpty()
                val titleLower = rawTitle.lowercase()
                if (!seen.add("c:$rawTitle")) return@let

                val contents = carousel.optJSONArray("contents") ?: return@let
                val songs = mutableListOf<Track>()
                val videos = mutableListOf<Track>()
                val albums = mutableListOf<Playlist>()
                val artists = mutableListOf<Artist>()

                for (i in 0 until contents.length()) {
                    val cell = contents.optJSONObject(i) ?: continue

                    cell.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                        parseArtistSongItem(r)?.let { item ->
                            if (titleLower.contains("video")) videos += item
                            else songs += item
                        }
                        return@let
                    }

                    cell.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                        val nav = r.optJSONObject("navigationEndpoint")
                        val bId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        val watchId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        val itemTitle = YTMusicTextUtils.extractRunsText(r.optJSONObject("title"))
                        val thumb = YTMusicThumbnailUtils
                            .thumbnailOf(r.optJSONObject("thumbnailRenderer"))
                        val sub = YTMusicTextUtils.extractRunsText(r.optJSONObject("subtitle"))

                        when {
                            watchId != null -> videos += Track(
                                id = watchId, title = itemTitle,
                                artist = sub.substringBefore("•").trim(),
                                thumbnailUrl = thumb, durationSeconds = 0L,
                                source = Source.YOUTUBE,
                            )
                            bId?.startsWith("UC") == true -> artists += Artist(
                                id = bId, name = itemTitle,
                                thumbnailUrl = thumb, source = Source.YOUTUBE_MUSIC,
                            )
                            bId?.startsWith("MPREb_") == true || bId?.startsWith("VL") == true ->
                                albums += Playlist(
                                    id = bId, title = itemTitle,
                                    owner = sub.substringBefore("•").trim(),
                                    thumbnailUrl = thumb, source = Source.YOUTUBE_MUSIC,
                                )
                        }
                    }
                }

                when {
                    titleLower.contains("video") -> allVideos += videos
                    titleLower.contains("single") || titleLower.contains("ep") -> allSingles += albums
                    titleLower.contains("álbum") ||
                        titleLower.contains("album") ||
                        titleLower.contains("discografia") -> allAlbums += albums
                    titleLower.contains("fãs") ||
                        titleLower.contains("relacionado") ||
                        titleLower.contains("similar") ||
                        titleLower.contains("parecido") -> related += artists
                    titleLower.contains("destaque") ||
                        titleLower.contains("feature") -> featured += songs + videos
                    else -> {
                        if (songs.isNotEmpty()) allSongs += songs
                        if (videos.isNotEmpty()) allVideos += videos
                        if (albums.isNotEmpty()) allAlbums += albums
                        if (artists.isNotEmpty()) related += artists
                    }
                }
            }
        }

        val uniqSongs = allSongs.distinctBy { it.id }
        val uniqVideos = allVideos.distinctBy { it.id }
        val uniqAlbums = allAlbums.distinctBy { it.id }
        val uniqSingles = allSingles.distinctBy { it.id }
        val uniqFeatured = featured.distinctBy { it.id }
        val uniqRelated = related.distinctBy { it.id }

        return ArtistResult(
            browseId = browseId,
            name = name,
            thumbnailUrl = headerThumb
                ?: uniqSongs.firstOrNull()?.thumbnailUrl
                ?: uniqVideos.firstOrNull()?.thumbnailUrl,
            subscribers = subscribers,
            description = null,
            topSongs = uniqSongs.take(5),
            allSongs = uniqSongs,
            allVideos = uniqVideos,
            allAlbums = uniqAlbums,
            allSingles = uniqSingles,
            featured = uniqFeatured,
            relatedArtists = uniqRelated,
        )
    }

    fun extractArtistsFromSearch(json: JSONObject, out: MutableList<Artist>) {
        YTMusicTextUtils.walk(json) { node ->
            node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                val columns = r.optJSONArray("flexColumns") ?: return@let
                val title = YTMusicTextUtils.flexText(columns, 0) ?: return@let
                val bId = YTMusicTextUtils.findFirst(r, "browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?: return@let
                if (!bId.startsWith("UC")) return@let
                out += Artist(
                    id = bId, name = title,
                    thumbnailUrl = YTMusicThumbnailUtils
                        .thumbnailOf(r.optJSONObject("thumbnail")),
                    source = Source.YOUTUBE_MUSIC,
                )
            }

            node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                val title = YTMusicTextUtils.extractRunsText(r.optJSONObject("title"))
                if (title.isBlank()) return@let
                val bId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" } ?: return@let
                if (!bId.startsWith("UC")) return@let
                out += Artist(
                    id = bId, name = title,
                    thumbnailUrl = YTMusicThumbnailUtils
                        .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                    source = Source.YOUTUBE_MUSIC,
                )
            }
        }
    }

    // ================= HEADER HELPERS =================

    fun extractHeaderTitle(header: JSONObject): String? {
        val runs = header.optJSONObject("title")?.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun extractSubscribers(header: JSONObject): String? {
        val candidates = listOfNotNull(
            header.optJSONObject("subtitle"),
            header.optJSONObject("straplineTextOne"),
        )
        for (c in candidates) {
            val text = YTMusicTextUtils.extractRunsText(c)
            if (text.isNotBlank() && (
                text.contains("ouvinte", true) ||
                    text.contains("listener", true) ||
                    text.contains("inscrito", true) ||
                    text.contains("subscriber", true)
                )
            ) return text
        }
        return null
    }

    private fun parseArtistSongItem(renderer: JSONObject): Track? {
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val title = YTMusicTextUtils.flexText(columns, 0) ?: return null
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val subtitleCol = columns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val uploader = YTMusicTextUtils.runsText(subtitleCol)
            .substringBefore("•")
            .trim()
            .ifBlank { "Unknown" }
        val source = YTMusicSearchParser.detectSource(videoId, renderer)
        return Track(
            id = videoId,
            title = title,
            artist = uploader,
            thumbnailUrl = YTMusicThumbnailUtils
                .thumbnailOf(renderer.optJSONObject("thumbnail")),
            durationSeconds = YTMusicTextUtils.parseDuration(
                YTMusicTextUtils.fixedText(renderer)
            ),
            source = source,
        )
    }
}