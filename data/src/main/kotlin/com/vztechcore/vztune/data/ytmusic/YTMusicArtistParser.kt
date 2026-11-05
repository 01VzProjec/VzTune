package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import org.json.JSONObject

/**
 * Parser focado 100% em extrair dados complexos de Artistas,
 * inspirado na robustez do SimpMusic/InnerTune.
 */
internal object YTMusicArtistParser {

    data class ArtistPage(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val relatedArtists: List<Artist>
    )

    fun parseArtistPage(browseId: String, json: JSONObject): ArtistPage {
        val header = YTMusicTextUtils.findFirst(json, "musicImmersiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicVisualHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicHeaderRenderer")

        val name = extractHeaderTitle(header) ?: "Artista Desconhecido"
        val thumbnail = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
        val subscribers = extractSubscribers(header)
        val description = extractDescription(json)

        val topSongs = mutableListOf<Track>()
        val albums = mutableListOf<Album>()
        val singles = mutableListOf<Album>()
        val relatedArtists = mutableListOf<Artist>()

        // SimpMusic approach: Walk through shelves to categorize items
        YTMusicTextUtils.walk(json) { node ->
            node.optJSONObject("musicShelfRenderer")?.let { shelf ->
                val title = YTMusicTextUtils.runsText(shelf.optJSONObject("title")).lowercase()
                
                when {
                    title.contains("música") || title.contains("song") || title.contains("top") -> {
                        extractTracksFromShelf(shelf, name, topSongs)
                    }
                    title.contains("álbum") || title.contains("album") -> {
                        extractAlbumsFromShelf(shelf, name, albums)
                    }
                    title.contains("single") -> {
                        extractAlbumsFromShelf(shelf, name, singles)
                    }
                    title.contains("fã") || title.contains("fan") || title.contains("relacionado") -> {
                        extractArtistsFromShelf(shelf, relatedArtists)
                    }
                }
            }
            // Horizontal carousels (CarouselShelfRenderer)
            node.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
                 val title = YTMusicTextUtils.runsText(carousel.optJSONObject("header")
                     ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")).lowercase()
                 
                 when {
                     title.contains("álbum") || title.contains("album") -> extractAlbumsFromShelf(carousel, name, albums)
                     title.contains("single") -> extractAlbumsFromShelf(carousel, name, singles)
                     title.contains("relacionado") || title.contains("similar") -> extractArtistsFromShelf(carousel, relatedArtists)
                 }
            }
        }

        return ArtistPage(
            browseId = browseId,
            name = name,
            thumbnailUrl = thumbnail,
            subscribers = subscribers,
            description = description,
            topSongs = topSongs.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            singles = singles.distinctBy { it.id },
            relatedArtists = relatedArtists.distinctBy { it.id }
        )
    }

    fun extractHeaderTitle(header: JSONObject?): String? {
        if (header == null) return null
        return YTMusicTextUtils.runsText(header.optJSONObject("title"))
            .takeIf { it.isNotBlank() }
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

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer") ?: return null
        return YTMusicTextUtils.extractRunsText(shelf.optJSONObject("description"))
    }

    private fun extractTracksFromShelf(shelf: JSONObject, artistName: String, out: MutableList<Track>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val columns = renderer.optJSONArray("flexColumns") ?: continue
            
            val title = YTMusicTextUtils.flexText(columns, 0) ?: continue
            val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            
            if (!videoId.isNullOrBlank()) {
                out.add(
                    Track(
                        id = videoId,
                        title = title,
                        artist = artistName,
                        thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnail")),
                        durationSeconds = YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(renderer)),
                        source = Source.YOUTUBE_MUSIC
                    )
                )
            }
        }
    }

    private fun extractAlbumsFromShelf(shelf: JSONObject, artistName: String, out: MutableList<Album>) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
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