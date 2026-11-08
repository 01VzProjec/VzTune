package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.innertube.InnerTube
import org.json.JSONObject

/**
 * Parser dedicated to extracting complex Artist data,
 * inspired by the robustness of SimpMusic/InnerTune.
 */
internal object YTMusicArtistParser {

    data class ArtistPage(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val views: String?,
        val description: String?,
        val topSongs: List<Track>,
        val songsMore: BrowseRef?,
        val albums: List<Album>,
        val albumsMore: BrowseRef?,
        val singles: List<Album>,
        val singlesMore: BrowseRef?,
        val videos: List<Track>,
        val videosMore: BrowseRef?,
        val featured: List<Playlist>,
        val featuredMore: BrowseRef?,
        val relatedArtists: List<Artist>,
    )

    /** One page of an artist "see all" list. Only the list matching the requested kind is filled. */
    data class ListPage(
        val tracks: List<Track>,
        val albums: List<Album>,
        val playlists: List<Playlist>,
        val continuation: String?,
    )

    fun parseArtistPage(browseId: String, json: JSONObject): ArtistPage {
        val header = YTMusicTextUtils.findFirst(json, "musicImmersiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicVisualHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicHeaderRenderer")

        val name = extractHeaderTitle(header) ?: "Unknown Artist"
        val thumbnail = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
        val subscribers = formatSubscribers(extractSubscribers(header))

        // About section: description + total views
        val descShelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
        val description = descShelf?.let { YTMusicTextUtils.extractRunsText(it.optJSONObject("description")) }
            ?.takeIf { it.isNotBlank() }
            ?: header?.optJSONObject("description")
                ?.let { YTMusicTextUtils.extractRunsText(it) }
                ?.takeIf { it.isNotBlank() }
        val views = formatViews(
            descShelf?.let { YTMusicTextUtils.runsText(it.optJSONObject("subheader")) }
                ?.takeIf { it.isNotBlank() }
                ?: descShelf?.let { YTMusicTextUtils.runsText(it.optJSONObject("footer")) }
        )

        val topSongs = mutableListOf<Track>()
        var songsMore: BrowseRef? = null
        var songsSeen = false

        val albums = mutableListOf<Album>()
        var albumsMore: BrowseRef? = null
        val singles = mutableListOf<Album>()
        var singlesMore: BrowseRef? = null
        val videos = mutableListOf<Track>()
        var videosMore: BrowseRef? = null
        val featured = mutableListOf<Playlist>()
        var featuredMore: BrowseRef? = null
        val relatedArtists = mutableListOf<Artist>()

        YTMusicTextUtils.walk(json) { node ->
            // Vertical shelf: the first one is the "popular songs" list
            node.optJSONObject("musicShelfRenderer")?.let { shelf ->
                if (!songsSeen) {
                    songsSeen = true
                    extractTracksFromShelf(shelf, name, topSongs)
                    songsMore = shelf.optJSONObject("bottomEndpoint")?.optJSONObject("browseEndpoint")?.toRef()
                        ?: shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.toRef()
                }
            }

            // Horizontal carousels: albums, singles, videos, featured on, related artists
            node.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
                val headerR = carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                val title = YTMusicTextUtils.runsText(headerR?.optJSONObject("title")).lowercase()
                val more = headerR?.optJSONObject("moreContentButton")
                    ?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.toRef()

                when {
                    title.contains("fãs") || title.contains("fans") || title.contains("relacion") ||
                        title.contains("similar") || title.contains("also like") || title.contains("podem gostar") ->
                        extractArtistsFromShelf(carousel, relatedArtists)

                    title.contains("álbu") || title.contains("albu") -> {
                        extractAlbumsFromShelf(carousel, name, albums)
                        if (albumsMore == null) albumsMore = more
                    }

                    title.contains("single") -> {
                        extractAlbumsFromShelf(carousel, name, singles)
                        if (singlesMore == null) singlesMore = more
                    }

                    title.contains("vídeo") || title.contains("video") -> {
                        extractVideosFromCarousel(carousel, name, videos)
                        if (videosMore == null) videosMore = more
                    }

                    title.contains("aparece") || title.contains("featured") || title.contains("destaq") ||
                        title.contains("playlist") -> {
                        extractPlaylistsFromCarousel(carousel, featured)
                        if (featuredMore == null) featuredMore = more
                    }
                }
            }
        }

        return ArtistPage(
            browseId = browseId,
            name = name,
            thumbnailUrl = thumbnail,
            subscribers = subscribers,
            views = views,
            description = description,
            topSongs = topSongs.distinctBy { it.id },
            songsMore = songsMore,
            albums = albums.distinctBy { it.id },
            albumsMore = albumsMore,
            singles = singles.distinctBy { it.id },
            singlesMore = singlesMore,
            videos = videos.distinctBy { it.id },
            videosMore = videosMore,
            featured = featured.distinctBy { it.id },
            featuredMore = featuredMore,
            relatedArtists = relatedArtists.distinctBy { it.id },
        )
    }

    /** Parses one page (first page or continuation) of an artist "see all" list. */
    fun parseListPage(kind: ArtistListKind, artistName: String, json: JSONObject): ListPage {
        val token = InnerTube.findContinuationToken(json)
        return when (kind) {
            ArtistListKind.SONGS -> ListPage(
                tracks = YTMusicPlaylistParser.parseTracksAnywhere(json),
                albums = emptyList(), playlists = emptyList(), continuation = token,
            )

            // Videos always play through the regular YouTube source
            ArtistListKind.VIDEOS -> ListPage(
                tracks = YTMusicPlaylistParser.parseTracksAnywhere(json).map { it.copy(source = Source.YOUTUBE) },
                albums = emptyList(), playlists = emptyList(), continuation = token,
            )

            ArtistListKind.ALBUMS, ArtistListKind.SINGLES -> {
                val albums = mutableListOf<Album>()
                YTMusicTextUtils.walk(json) { node ->
                    node.optJSONObject("musicTwoRowItemRenderer")?.let { parseAlbumItem(it, artistName, albums) }
                }
                ListPage(emptyList(), albums, emptyList(), token)
            }

            ArtistListKind.FEATURED -> {
                val playlists = mutableListOf<Playlist>()
                YTMusicTextUtils.walk(json) { node ->
                    node.optJSONObject("musicTwoRowItemRenderer")?.let { parsePlaylistItem(it, playlists) }
                }
                ListPage(emptyList(), emptyList(), playlists, token)
            }
        }
    }

    fun extractHeaderTitle(header: JSONObject?): String? {
        if (header == null) return null
        return YTMusicTextUtils.runsText(header.optJSONObject("title"))
            .takeIf { it.isNotBlank() }
    }

    // ---------------- header helpers ----------------

    private fun JSONObject.toRef(): BrowseRef? {
        val id = optString("browseId").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return BrowseRef(id, optString("params").takeIf { it.isNotBlank() && it != "null" })
    }

    private fun extractSubscribers(header: JSONObject?): String? {
        if (header == null) return null
        val subText = YTMusicTextUtils.runsText(header.optJSONObject("subscriptionButton")
            ?.optJSONObject("subscribeButtonRenderer")
            ?.optJSONObject("subscriberCountText"))
        if (subText.isNotBlank()) return subText

        // Fallback for some visual headers
        val subtitle = YTMusicTextUtils.runsText(header.optJSONObject("subtitle"))
        if (subtitle.contains("inscritos", true) || subtitle.contains("subscribers", true)) {
            return subtitle.split("•").lastOrNull()?.trim()
        }
        return null
    }

    /** "1,13 mi" -> "1,13 mi de inscritos" */
    private fun formatSubscribers(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (text.contains("inscrit", true) || text.contains("subscri", true)) return text
        return "$text de inscritos"
    }

    /** "512.669.634" -> "512.669.634 visualizações" */
    private fun formatViews(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!text.any { it.isDigit() }) return null
        if (YTMusicTextUtils.isViewCount(text)) return text.replace("views", "visualizações", ignoreCase = true)
        return "$text visualizações"
    }

    // ---------------- shelf helpers ----------------

    private fun extractTracksFromShelf(shelf: JSONObject, artistName: String, out: MutableList<Track>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val columns = renderer.optJSONArray("flexColumns") ?: continue

            val title = YTMusicTextUtils.flexText(columns, 0) ?: continue
            val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")

            if (!videoId.isNullOrBlank()) {
                val subtitleCol = columns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                val subtitle = YTMusicTextUtils.runsText(subtitleCol)
                // Keeps every credited artist ("Mc J mito, DJ TOPO, DJ Yago")
                val artists = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                    ?.takeIf { it.isNotBlank() } ?: artistName

                out.add(
                    Track(
                        id = videoId,
                        title = title,
                        artist = artists,
                        thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnail")),
                        durationSeconds = YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(renderer)),
                        source = Source.YOUTUBE_MUSIC,
                        isExplicit = YTMusicSearchParser.hasExplicitBadge(renderer)
                    )
                )
            }
        }
    }

    private fun extractAlbumsFromShelf(shelf: JSONObject, artistName: String, out: MutableList<Album>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
            parseAlbumItem(renderer, artistName, out)
        }
    }

    private fun parseAlbumItem(renderer: JSONObject, artistName: String, out: MutableList<Album>) {
        val title = YTMusicTextUtils.runsText(renderer.optJSONObject("title"))
        val browseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")?.optString("browseId")

        if (!browseId.isNullOrBlank() && browseId.startsWith("MPREb_")) {
            out.add(
                Album(
                    id = browseId,
                    title = title,
                    artist = artistName,
                    thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),
                    source = Source.YOUTUBE_MUSIC
                )
            )
        }
    }

    /** Videos carousel: every item is a music video (watch endpoint), shown as a 16:9 card. */
    private fun extractVideosFromCarousel(shelf: JSONObject, artistName: String, out: MutableList<Track>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
            val videoId = renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")
                ?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val title = YTMusicTextUtils.runsText(renderer.optJSONObject("title"))

            // Subtitle: "Artists • 12 mi de visualizações" -> keep only the artists part
            val subtitle = YTMusicTextUtils.runsText(renderer.optJSONObject("subtitle"))
            val artists = subtitle.split("•").map { it.trim() }
                .firstOrNull { it.isNotBlank() && !YTMusicTextUtils.isViewCount(it) && !YTMusicTextUtils.isTypeWord(it) }
                ?: artistName

            out.add(
                Track(
                    id = videoId,
                    title = title,
                    artist = artists,
                    thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),
                    durationSeconds = 0L,
                    source = Source.YOUTUBE,
                    isExplicit = YTMusicSearchParser.hasExplicitBadge(renderer),
                )
            )
        }
    }

    private fun extractPlaylistsFromCarousel(shelf: JSONObject, out: MutableList<Playlist>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
            parsePlaylistItem(renderer, out)
        }
    }

    private fun parsePlaylistItem(renderer: JSONObject, out: MutableList<Playlist>) {
        val browseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf { it.isNotBlank() && it != "null" } ?: return
        val isPlaylist = browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD")
        if (!isPlaylist) return

        val title = YTMusicTextUtils.runsText(renderer.optJSONObject("title"))
        val subtitle = YTMusicTextUtils.runsText(renderer.optJSONObject("subtitle"))
        val owner = subtitle.split("•").map { it.trim() }
            .firstOrNull {
                it.isNotBlank() && !YTMusicTextUtils.isTypeWord(it) &&
                    !YTMusicTextUtils.isViewCount(it) && !YTMusicTextUtils.isCountOrDurationLabel(it)
            }

        out.add(
            Playlist(
                id = YTMusicTextUtils.normalizePlaylistBrowseId(browseId),
                title = title,
                owner = owner,
                thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),
                source = Source.YOUTUBE_MUSIC,
            )
        )
    }

    private fun extractArtistsFromShelf(shelf: JSONObject, out: MutableList<Artist>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
            val name = YTMusicTextUtils.runsText(renderer.optJSONObject("title"))
            val browseId = renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            if (!browseId.isNullOrBlank() && browseId.startsWith("UC")) {
                out.add(
                    Artist(
                        id = browseId,
                        name = name,
                        thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),
                        source = Source.YOUTUBE_MUSIC
                    )
                )
            }
        }
    }

    fun extractArtistsFromSearch(json: JSONObject, out: MutableList<Artist>) {
        YTMusicTextUtils.walk(json) { renderer ->
            renderer.optJSONObject("musicResponsiveListItemRenderer")?.let {
                val browseId = it.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (browseId?.startsWith("UC") == true) {
                    val name = YTMusicTextUtils.flexText(it.optJSONArray("flexColumns"), 0)
                    if (name != null) {
                        out.add(
                            Artist(
                                id = browseId,
                                name = name,
                                thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(it.optJSONObject("thumbnail")),
                                source = Source.YOUTUBE_MUSIC
                            )
                        )
                    }
                }
            }
        }
    }
}
