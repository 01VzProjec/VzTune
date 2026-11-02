package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.data.innertube.InnerTube
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Parses the `musicDetailHeaderRenderer` (or `musicResponsiveHeaderRenderer`)
 * from an album browse response to extract the real cover, title and artist.
 *
 * Why this exists: the search result item for an album shows
 * `subtitle = "Single • Artist Name"`. The naive parser extracts
 * `subtitle.substringBefore("•")` which gives "Single" instead of the artist.
 * This class fixes it by reading the album's own browse response, where the
 * header holds the real artist in its `straplineTextOne.runs` or
 * `subtitle.runs` (with `browseId` starting with "UC" / "MPAD").
 */
internal object YTMusicAlbumParser {

    data class AlbumHeader(
        val coverUrl: String?,
        val title: String?,
        val artist: String?,
    )

    private val headerCache = ConcurrentHashMap<String, AlbumHeader>()
    private val emptyHeader = AlbumHeader(null, null, null)

    /**
     * Fetches and parses the album header for the given browseId.
     * Result is cached forever (same process lifetime).
     * Returns null if the fetch failed.
     */
    fun parseAlbumHeader(browseId: String): AlbumHeader? {
        headerCache[browseId]?.let { return it.takeIf { h -> h != emptyHeader } }

        val json = runCatching { InnerTube.browse(browseId) }
            .getOrElse { e ->
                Log.w(TAG, "browse failed for $browseId: ${e.message}")
                return null
            }

        val parsed = parseFromJson(json) ?: run {
            headerCache[browseId] = emptyHeader
            return null
        }

        headerCache[browseId] = parsed
        return parsed
    }

    fun parseFromJson(json: JSONObject): AlbumHeader? {
        val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")
            ?: return null

        val cover = YTMusicThumbnailUtils.extractHeaderCover(header)
        val title = YTMusicArtistParser.extractHeaderTitle(header)
        val artist = extractArtist(header)

        return AlbumHeader(coverUrl = cover, title = title, artist = artist)
    }

    /**
     * Tries `straplineTextOne` first (album + year format), then `subtitle`.
     * Only runs whose browseId starts with UC or MPAD are treated as artists.
     */
    private fun extractArtist(header: JSONObject): String? {
        header.optJSONObject("straplineTextOne")?.optJSONArray("runs")?.let { runs ->
            val artists = collectArtistsFromRuns(runs)
            if (artists.isNotEmpty()) return artists
        }

        header.optJSONObject("subtitle")?.optJSONArray("runs")?.let { runs ->
            val artists = collectArtistsFromRuns(runs)
            if (artists.isNotEmpty()) return artists
        }

        // Fallback: some album headers only have `musicResponsiveHeaderRenderer`
        // with a plain string title and a subtitle like "Album • Artist".
        val subtitleText = YTMusicTextUtils.extractRunsText(header.optJSONObject("subtitle"))
        if (subtitleText.isNotBlank()) {
            return subtitleText
                .split("•")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { !YTMusicTextUtils.isTypeWord(it) }
                .filter { !YTMusicTextUtils.isYear(it) }
                .firstOrNull { it.length < 80 }
        }

        return null
    }

    private fun collectArtistsFromRuns(runs: org.json.JSONArray): String {
        val artists = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text").trim()
                .trim(' ', ',', '&', '·', '•', '-', '/', ';')
            if (text.isBlank() || text == "•" || text == "·") continue

            val browseId = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            when {
                browseId?.startsWith("MPREb_") == true -> break
                browseId?.startsWith("VL") == true -> break
                browseId?.startsWith("UC") == true ||
                    browseId?.startsWith("MPAD") == true -> {
                    if (text !in artists) artists += text
                }
            }
        }
        return artists.joinToString(", ")
    }

    private const val TAG = "VzTuneAlbum"
}