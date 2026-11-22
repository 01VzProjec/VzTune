package com.vztechcore.vztune.data.innertube

import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * InnerTube client for regular YouTube (youtube.com).
 *
 * YT Music already uses [InnerTube] with WEB_REMIX.
 * This one uses WEB / WEB_EMBEDDED / VISIONOS for YouTube:
 * search, browse (channel, playlist, tabs), next and player.
 *
 * Part 1 only: creates the client. No repository uses it yet,
 * so existing behavior does not change.
 */
object InnerTubeYouTube {

    private const val API = "https://www.youtube.com/youtubei/v1"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class Client(
        val name: String,
        val version: String,
        val apiKey: String,
        val userAgent: String,
        val referer: String? = null,
    )

    // Main client for search / browse / next
    private val WEB = Client(
        name = "WEB",
        version = "2.20261120.00.00",
        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        referer = "https://www.youtube.com/",
    )

    // Embedded client, more permissive for player in some cases
    private val WEB_EMBEDDED = Client(
        name = "WEB_EMBEDDED",
        version = "1.20251120.01.00",
        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        referer = "https://www.youtube.com/",
    )

    // Fallback client for player when WEB returns 403 / no URLs
    private val VISIONOS = Client(
        name = "VISIONOS",
        version = "1.9.27",
        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
        userAgent = "Mozilla/5.0 (VisionOS; U; CPU OS 1_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)",
        referer = "https://www.youtube.com/",
    )

    @Volatile
    var visitorData: String? = null
        private set

    private fun buildBody(client: Client): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", client.name)
                put("clientVersion", client.version)
                put("hl", "pt")
                put("gl", "BR")
                visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) }
            })
        })
    }

    fun search(query: String, params: String? = null): JSONObject =
        post("search", WEB, buildBody(WEB).apply {
            put("query", query)
            if (params != null) put("params", params)
        })

    fun searchContinuation(token: String): JSONObject =
        post("search", WEB, buildBody(WEB).apply {
            put("continuation", token)
        })

    fun browse(browseId: String, params: String? = null): JSONObject =
        post("browse", WEB, buildBody(WEB).apply {
            put("browseId", browseId)
            if (!params.isNullOrBlank()) put("params", params)
        })

    fun browseContinuation(token: String): JSONObject =
        post("browse", WEB, buildBody(WEB).apply {
            put("continuation", token)
        })

    fun next(videoId: String): JSONObject =
        post("next", WEB, buildBody(WEB).apply {
            put("videoId", videoId)
        })

    /**
     * Player endpoint. Tries WEB first, then VISIONOS as fallback.
     * Returns raw player JSON, caller parses streamingData.
     */
    suspend fun player(videoId: String): JSONObject = coroutineScope {
        val webResult = runCatching {
            playerWithClient(videoId, WEB)
        }.getOrNull()
        if (webResult != null && isPlayable(webResult) && hasPlayableUrls(webResult)) {
            return@coroutineScope webResult
        }
        val embeddedResult = runCatching {
            playerWithClient(videoId, WEB_EMBEDDED)
        }.getOrNull()
        if (embeddedResult != null && isPlayable(embeddedResult) && hasPlayableUrls(embeddedResult)) {
            return@coroutineScope embeddedResult
        }
        val visionResult = runCatching {
            playerWithClient(videoId, VISIONOS)
        }.getOrNull()
        if (visionResult != null && isPlayable(visionResult)) {
            return@coroutineScope visionResult
        }
        // Return best we have, or throw if nothing playable
        webResult ?: embeddedResult ?: visionResult ?: throw Exception("Not playable")
    }

    private fun playerWithClient(videoId: String, client: Client): JSONObject =
        post("player", client, buildBody(client).apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        })

    private fun post(endpoint: String, client: Client, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$API/$endpoint?prettyPrint=false&key=${client.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-Goog-Api-Format-Version", "1")
            .addHeader("X-YouTube-Client-Name", client.name)
            .addHeader("X-YouTube-Client-Version", client.version)
            .addHeader("x-origin", "https://www.youtube.com")
            .apply {
                if (client.referer != null) addHeader("Referer", client.referer)
                visitorData?.takeIf { it.isNotBlank() }?.let {
                    addHeader("X-Goog-Visitor-Id", it)
                }
            }
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("InnerTubeYouTube HTTP ${response.code}")
            val text = response.body?.string() ?: throw Exception("InnerTubeYouTube empty")
            val json = JSONObject(text)
            updateVisitorData(json)
            return json
        }
    }

    private fun updateVisitorData(json: JSONObject) {
        val data = json.optJSONObject("responseContext")?.optString("visitorData")
        if (!data.isNullOrBlank() && data != "null") {
            visitorData = data
        }
    }

    fun isPlayable(playerJson: JSONObject): Boolean =
        playerJson.optJSONObject("playabilityStatus")?.optString("status") == "OK"

    private fun hasPlayableUrls(playerJson: JSONObject): Boolean {
        val streaming = playerJson.optJSONObject("streamingData") ?: return false
        val formats = streaming.optJSONArray("formats")
        val adaptive = streaming.optJSONArray("adaptiveFormats")
        return (formats != null && formats.length() > 0) ||
            (adaptive != null && adaptive.length() > 0)
    }

    fun findContinuationToken(node: Any?): String? {
        // Reuse the same logic already tested in InnerTube for YT Music
        return InnerTube.findContinuationToken(node)
    }

    // Keep JSONArray import used, helper for future parsers
    @Suppress("unused")
    private fun isEmptyArray(arr: JSONArray?): Boolean = arr == null || arr.length() == 0
}
