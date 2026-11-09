package com.vztechcore.vztune.data.ytmusic

/**
 * Public facade over [YTMusicTextUtils] for callers outside the :data module.
 *
 * [YTMusicTextUtils] is `internal` on purpose: its low-level helpers
 * (`walk`, `findFirst`, `runsText`, `flexText`, `fixedText`, `parseDuration`)
 * are only meaningful inside the parsing pipeline. Exposing them to :app
 * would let UI code reach into the raw InnerTube JSON — exactly what the
 * module split is meant to prevent.
 *
 * The UI layer only needs ONE thing from that file: deciding whether a
 * string coming from a YTMusic header is a real account name ("Mc J mito")
 * or metadata ("12 songs", "1.2M views", "1h 30min"). That rule is
 * what this object exposes.
 */
object YTMusicOwnerValidator {

    /**
     * True when [text] looks like a real account name (channel / playlist owner).
     *
     * Rejects:
     *  - counts ("12 songs", "3 vídeos")
     *  - view counts ("1.2M views", "3 mil visualizações")
     *  - total durations ("1h 30min")
     *  - placeholders ("Creator", "Unknown", "Álbum", "Playlist")
     *  - pure numbers
     *
     * Delegates to [YTMusicTextUtils.isValidOwnerName].
     */
    fun isValidOwnerName(text: String?): Boolean = YTMusicTextUtils.isValidOwnerName(text)
}