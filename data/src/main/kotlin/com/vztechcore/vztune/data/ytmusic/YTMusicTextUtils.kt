package com.vztechcore.vztune.data.ytmusic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure text / JSON helpers. No state, no caches, no network.
 */
internal object YTMusicTextUtils {

    val TYPE_WORDS = setOf(
        "song", "songs",
        "música", "músicas", "musica", "musicas",
        "video", "vídeo", "videos", "vídeos",
        "episódio", "episodio", "episode", "episodes",
        "single", "singles",
        "album", "álbum", "albums", "álbuns",
        "playlist", "playlists",
    )

    private val DURATION_REGEX = Regex("^\\d+:\\d+.*$")
    private val YEAR_REGEX = Regex("^\\d{4}$")
    private val PT_DATE_REGEX = Regex(
        "^\\d{1,2} de \\w+\\.? de \\d{4}$",
        RegexOption.IGNORE_CASE,
    )
    private val EN_DATE_REGEX = Regex("^[A-Z][a-z]{2} \\d{1,2}, \\d{4}$")
    private val EPISODE_REGEX = Regex(
        "^(episódio|episodio|episode|temporada|season|t\\d+e\\d+)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Normalizes playlist browse IDs so InnerTube browse endpoint can load their tracks.
     * YouTube playlists (PL...) or regular IDs (YT:PL...) need to be prefixed with VL (VLPL...).
     */
    fun normalizePlaylistBrowseId(rawId: String): String {
        var id = rawId.removePrefix("YT:").trim()
        if (!id.startsWith("VL") && (id.startsWith("PL") || id.startsWith("RD") || id.startsWith("LR") || id.startsWith("SE"))) {
            id = "VL$id"
        }
        return id
    }

    fun walk(node: Any?, block: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                block(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), block)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), block)
        }
    }

    fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findFirst(node.opt(keys.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findFirst(node.opt(i), key)?.let { return it }
        }
        return null
    }

    fun runsText(container: JSONObject?): String {
        val runs = container?.optJSONObject("text")?.optJSONArray("runs")
            ?: container?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
        }
        return sb.toString()
    }

    fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val runs = obj.optJSONArray("runs")
            ?: return obj.optString("simpleText").orEmpty().trim()
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    fun flexText(columns: JSONArray, index: Int): String? {
        val col = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: return null
        return runsText(col).takeIf { it.isNotBlank() }
    }

    fun fixedText(r: JSONObject): String? {
        val fixed = r.optJSONArray("fixedColumns") ?: return null
        for (i in 0 until fixed.length()) {
            val col = fixed.optJSONObject(i) ?: continue
            val text = runsText(
                col.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?: col.optJSONObject("musicResponsiveListItemFlexColumnRenderer"),
            )
            if (DURATION_REGEX.matches(text)) return text
        }
        return null
    }

    fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return try {
            text.split(":").map { it.trim().toLong() }
                .reversed()
                .foldIndexed(0L) { idx, acc, v ->
                    acc + v * when (idx) {
                        0 -> 1L
                        1 -> 60L
                        else -> 3600L
                    }
                }
        } catch (e: Exception) {
            0L
        }
    }

    fun isDuration(text: String): Boolean = DURATION_REGEX.matches(text)
    fun isYear(text: String): Boolean = YEAR_REGEX.matches(text)
    fun isDate(text: String): Boolean =
        PT_DATE_REGEX.matches(text) || EN_DATE_REGEX.matches(text)
    fun isEpisode(text: String): Boolean = EPISODE_REGEX.containsMatchIn(text)
    fun isViewCount(text: String): Boolean =
        text.contains("views", true) ||
            text.contains("visualizaç", true) ||
            text.contains("reproduç", true)
    fun isTypeWord(text: String): Boolean = text.lowercase() in TYPE_WORDS

    fun cleanArtistName(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.trim()
        s = s.removeSuffix(" - Topic").removeSuffix(" - topic").trim()
        if (s.startsWith("Provided to YouTube", true)) return ""
        if (s.startsWith("Auto-generated by YouTube", true)) return ""
        if (s.equals("Various Artists", true)) return "Vários Artistas"
        if (s.startsWith("UC") && s.length > 20) return ""
        if (s.equals("Álbum", true) || s.equals("Album", true)) return ""
        return s
    }
}