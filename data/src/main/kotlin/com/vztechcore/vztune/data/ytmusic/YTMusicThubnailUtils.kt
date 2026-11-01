package com.vztechcore.vztune.data.ytmusic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Thumbnail extraction helpers. All state is read from JSON nodes.
 */
internal object YTMusicThumbnailUtils {

    fun thumbnailOf(root: JSONObject?): String? {
        if (root == null) return null
        root.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { pickLargest(it) }?.let { return it }
        root.optJSONObject("croppedSquareThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { pickLargest(it) }?.let { return it }
        return findBestCoverUrl(root)
    }

    fun findBestCoverUrl(node: Any?): String? {
        var best: String? = null
        var bestW = 0
        YTMusicTextUtils.walk(node) { obj ->
            val thumbs = obj.optJSONArray("thumbnails") ?: return@walk
            for (i in 0 until thumbs.length()) {
                val t = thumbs.optJSONObject(i) ?: continue
                val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
                val w = t.optInt("width", 0)
                if (w > bestW) {
                    bestW = w
                    best = url
                }
            }
        }
        return best
    }

    fun pickLargest(thumbs: JSONArray): String? {
        if (thumbs.length() == 0) return null
        var best: String? = null
        var bestW = 0
        for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
            val w = t.optInt("width", 0)
            if (w >= bestW) {
                bestW = w
                best = url
            }
        }
        return best ?: thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
    }

    fun extractHeaderCover(header: JSONObject): String? {
        val thumbRoot = header.optJSONObject("thumbnail") ?: return null
        thumbRoot.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { pickLargest(it) }?.let { return it }
        thumbRoot.optJSONObject("croppedSquareThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { pickLargest(it) }?.let { return it }
        thumbRoot.optJSONArray("thumbnails")?.let { pickLargest(it) }?.let { return it }
        return findBestCoverUrl(thumbRoot)
    }

    fun extractThumbnailFromPlayer(playerJson: JSONObject): String? {
        playerJson.optJSONObject("videoDetails")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs ->
                if (thumbs.length() > 0) {
                    return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }
            }

        playerJson.optJSONObject("microformat")
            ?.optJSONObject("playerMicroformatRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs ->
                if (thumbs.length() > 0) {
                    return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }
            }

        return null
    }
}