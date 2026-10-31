package com.vztechcore.vztune.data.innertube

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InnerTube {

    private const val API = "https://www.youtube.com/youtubei/v1"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun webRemixBody(): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20250801.01.00")
                put("hl", "pt")
                put("gl", "BR")
            })
        })
    }

    private fun androidMusicBody(): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_MUSIC")
                put("clientVersion", "7.27.52")
                put("androidSdkVersion", 30)
                put("osName", "Android")
                put("osVersion", "11")
                put("hl", "pt")
                put("gl", "BR")
            })
        })
    }

    private fun androidVrBody(): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_VR")
                put("clientVersion", "1.60.19")
                put("deviceMake", "Oculus")
                put("deviceModel", "Quest 3")
                put("androidSdkVersion", 32)
                put("osName", "Android")
                put("osVersion", "12")
                put("hl", "pt")
                put("gl", "BR")
            })
        })
    }

    fun search(query: String, params: String? = null): JSONObject =
        post("search", webRemixBody().apply {
            put("query", query)
            if (params != null) put("params", params)
        })

    fun searchContinuation(token: String): JSONObject =
        post("search", webRemixBody().apply {
            put("continuation", token)
        })

    fun searchSuggestions(input: String): JSONObject =
        post("music/get_search_suggestions", webRemixBody().apply {
            put("input", input)
        })

    suspend fun player(videoId: String): JSONObject = coroutineScope {
        val clients = listOf(::androidMusicBody, ::androidVrBody)
        val deferreds = clients.map { build ->
            async {
                val json = post("player", build().apply {
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                    put("params", "CgIQBg")
                })
                if (isPlayable(json)) json else throw Exception("Not playable")
            }
        }
        select { deferreds.forEach { d -> d.onAwait { it } } }
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$API/$endpoint?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
            .addHeader("X-Goog-Api-Format-Version", "2")
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", "1.20250801.01.00")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("InnerTube HTTP ${response.code}")
            val text = response.body?.string() ?: throw Exception("InnerTube vazio")
            return JSONObject(text)
        }
    }

    fun isPlayable(playerJson: JSONObject): Boolean =
        playerJson.optJSONObject("playabilityStatus")?.optString("status") == "OK"

    /**
     * Pick the best audio URL from a player
     * Prefers mp4 (AAC) with the highest bitrate — most compatible with ExoPlayer.
     */
    fun extractBestAudioUrl(playerJson: JSONObject): String? {
        val streaming = playerJson.optJSONObject("streamingData") ?: return null

        val candidates = mutableListOf<Triple<String, String, Int>>() // mime, url, bitrate

        streaming.optJSONArray("adaptiveFormats")?.let { formats ->
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType")
                if (!mime.startsWith("audio/")) continue
                val url = fmt.optString("url").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val br = fmt.optInt("bitrate", 0)
                candidates += Triple(mime, url, br)
            }
        }

        if (candidates.isEmpty()) {
            streaming.optJSONArray("formats")?.let { formats ->
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val url = fmt.optString("url").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val br = fmt.optInt("bitrate", 0)
                    candidates += Triple("", url, br)
                }
            }
        }

        if (candidates.isEmpty()) return null

        val mp4 = candidates.filter { it.first.contains("mp4", ignoreCase = true) }
        val best = mp4.maxByOrNull { it.third } ?: candidates.maxByOrNull { it.third }
        return best?.second
    }

    fun findContinuationToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("continuationCommand")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.let { return it }
                node.optJSONObject("nextContinuationData")
                    ?.optString("continuation")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findContinuationToken(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findContinuationToken(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }
}