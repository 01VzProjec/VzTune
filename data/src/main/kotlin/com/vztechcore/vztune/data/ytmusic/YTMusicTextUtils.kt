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

    // "20 songs", "12 músicas", "3 vídeos"...
    private val COUNT_LABEL_REGEX = Regex(
        "^\\d[\\d.,]*\\s*(músicas?|musicas?|songs?|faixas?|tracks?|itens?|items?|vídeos?|videos?|episódios?|episodios?|episodes?)\\b.*$",
        RegexOption.IGNORE_CASE,
    )

    // "1 hora e 20 minutos", "1 hour, 20 minutes", "45 min"...
    private val TOTAL_DURATION_REGEX = Regex(
        "^(\\d+\\s*(h|hr|hrs|hora|horas|hour|hours|min|mins|minuto|minutos|minute|minutes|s|seg|sec)\\b[\\s,.]*(e|and)?[\\s,.]*)+$",
        RegexOption.IGNORE_CASE,
    )

    // ================= OWNER VALIDATION =================
    // Rules for deciding whether a string coming from a YTMusic header is a real
    // account name (channel / playlist creator) and not metadata.
    //
    // These used to live inside MediaListViewModel (app layer); moved here so the
    // parsing/validation logic stays in one place (:data) and the UI never has to
    // reimplement it.

    private val PLACEHOLDER_OWNERS = setOf(
        "criador", "creator", "unknown", "desconhecido", "álbum", "album", "playlist",
    )

    private val NON_OWNER_PATTERNS = listOf(
        // "20 songs", "12 músicas", "1.2M views", "3 mil visualizações"
        Regex(
            "^\\d[\\d.,]*\\s*(k|m|b|mil|mi|bi)?\\s*(músicas?|musicas?|songs?|faixas?|tracks?|itens?|items?|vídeos?|videos?|episódios?|episodes?|views?|visualizações|visualizacoes|reproduções|reproducoes|plays?)\\b.*$",
            RegexOption.IGNORE_CASE,
        ),
        // "1h 30min", "1 hora e 20 minutos total"
        Regex(
            "^(\\d+\\s*(h|hr|hrs|hora|horas|hour|hours|min|mins|minuto|minutos|minute|minutes|s|seg|sec)\\b[\\s,.]*(e|and)?[\\s,.]*)+(total)?$",
            RegexOption.IGNORE_CASE,
        ),
    )

    /**
     * True when [text] looks like a real account name (channel / playlist owner).
     *
     * Rejects:
     *  - counts ("12 songs", "3 vídeos")
     *  - view counts ("1.2M views", "3 mil visualizações")
     *  - total durations ("1h 30min")
     *  - placeholders ("Creator", "Unknown", "Álbum", "Playlist")
     *  - pure numbers
     */
    fun isValidOwnerName(text: String?): Boolean {
        if (text.isNullOrBlank()) return false

        val trimmed = text.trim()
        if (trimmed.lowercase() in PLACEHOLDER_OWNERS) return false
        if (NON_OWNER_PATTERNS.any { it.matches(trimmed) }) return false
        if (trimmed.toIntOrNull() != null) return false

        return true
    }

    // ================= PLAYLIST IDS =================

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

    // ================= JSON WALKERS =================

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

    // ================= TEXT EXTRACTION =================

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

    // ================= PARSING =================

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

    // ================= CLASSIFIERS =================

    fun isDuration(text: String): Boolean = DURATION_REGEX.matches(text)
    fun isYear(text: String): Boolean = YEAR_REGEX.matches(text)
    fun isDate(text: String): Boolean =
        PT_DATE_REGEX.matches(text) || EN_DATE_REGEX.matches(text)
    fun isEpisode(text: String): Boolean = EPISODE_REGEX.containsMatchIn(text)
    fun isViewCount(text: String): Boolean =
        text.contains("views", true) ||
            text.contains("visualizaç", true) ||
            text.contains("exibiç", true) ||
            text.contains("reproduç", true)

    /** True for track-count labels ("20 songs") and total-duration labels ("1 hora e 20 minutos"). */
    fun isCountOrDurationLabel(text: String): Boolean {
        val t = text.trim()
        return COUNT_LABEL_REGEX.matches(t) || TOTAL_DURATION_REGEX.matches(t)
    }
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