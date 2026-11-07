package com.vztechcore.vztune.data.ytmusic

import android.util.Log
import com.vztechcore.vztune.data.innertube.InnerTube
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal object YTMusicAlbumParser {

    data class AlbumHeader(
        val coverUrl: String?,
        val title: String?,
        val artist: String?,
        val views: String? = null,
    )

    private val headerCache = ConcurrentHashMap<String, AlbumHeader>()
    private val emptyHeader = AlbumHeader(null, null, null, null)

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
        val views = extractViews(header)

        return AlbumHeader(coverUrl = cover, title = title, artist = artist, views = views)
    }

    private fun extractViews(header: JSONObject): String? {
        val subtitleArrays = listOf(
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )
        for (runs in subtitleArrays) {
            if (runs == null) continue
            for (i in 0 until runs.length()) {
                val text = runs.optJSONObject(i)?.optString("text")?.trim() ?: continue
                if (text.isBlank()) continue
                if (YTMusicTextUtils.isViewCount(text)) return text
            }
        }
        return null
    }

    private fun extractArtist(header: JSONObject): String? {
        header.optJSONObject("straplineTextOne")?.optJSONArray("runs")?.let { runs ->
            val artists = collectArtistsFromRuns(runs)
            if (artists.isNotEmpty()) return artists
        }

        header.optJSONObject("subtitle")?.optJSONArray("runs")?.let { runs ->
            val artists = collectArtistsFromRuns(runs)
            if (artists.isNotEmpty()) return artists
        }

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