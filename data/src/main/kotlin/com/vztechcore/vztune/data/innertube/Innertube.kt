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

    private const val API = "https://music.youtube.com/youtubei/v1"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ================= CLIENTS (valores do InnerTune) =================

    private data class Client(
        val name: String,
        val version: String,
        val apiKey: String,
        val userAgent: String,
        val referer: String? = null,
    )

    // Usado para search, browse, álbum, playlist, artist
    private val WEB_REMIX = Client(
        name = "WEB_REMIX",
        version = "1.20220606.03.00",
        apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36",
        referer = "https://music.youtube.com/",
    )

    // Usado para o player (streaming / metadados técnicos)
    private val ANDROID_MUSIC = Client(
        name = "ANDROID_MUSIC",
        version = "5.01",
        apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
        userAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Mobile Safari/537.36",
    )

    // ================= BODY BUILDERS =================

    private fun buildBody(client: Client): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", client.name)
                put("clientVersion", client.version)
                put("hl", "pt")
                put("gl", "BR")
            })
        })
    }

    // ================= ENDPOINTS =================

    fun search(query: String, params: String? = null): JSONObject =
        post("search", WEB_REMIX, buildBody(WEB_REMIX).apply {
            put("query", query)
            if (params != null) put("params", params)
        })

    fun searchContinuation(token: String): JSONObject =
        post("search", WEB_REMIX, buildBody(WEB_REMIX).apply {
            put("continuation", token)
        })

    fun browse(browseId: String, params: String? = null): JSONObject =
        post("browse", WEB_REMIX, buildBody(WEB_REMIX).apply {
            put("browseId", browseId)
            if (!params.isNullOrBlank()) put("params", params)
        })

    fun browseContinuation(token: String): JSONObject =
        post("browse", WEB_REMIX, buildBody(WEB_REMIX).apply {
            put("continuation", token)
        })

    fun searchSuggestions(input: String): JSONObject =
        post("music/get_search_suggestions", WEB_REMIX, buildBody(WEB_REMIX).apply {
            put("input", input)
        })

    /**
     * Player endpoint.
     * Usa ANDROID_MUSIC (client oficial de streaming).
     * Retorna streamingData com bitrate real.
     */
    suspend fun player(videoId: String): JSONObject = coroutineScope {
        val json = post("player", ANDROID_MUSIC, buildBody(ANDROID_MUSIC).apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("params", "CgIQBg")
        })
        if (isPlayable(json)) json else throw Exception("Not playable")
    }

    private fun post(endpoint: String, client: Client, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$API/$endpoint?prettyPrint=false&key=${client.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-Goog-Api-Format-Version", "1")
            .addHeader("X-YouTube-Client-Name", client.name)
            .addHeader("X-YouTube-Client-Version", client.version)
            .addHeader("x-origin", "https://music.youtube.com")
            .apply {
                if (client.referer != null) addHeader("Referer", client.referer)
            }
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("InnerTube HTTP ${response.code}")
            val text = response.body?.string() ?: throw Exception("InnerTube empty")
            return JSONObject(text)
        }
    }

    // ================= HELPERS =================

    fun isPlayable(playerJson: JSONObject): Boolean =
        playerJson.optJSONObject("playabilityStatus")?.optString("status") == "OK"

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
            is JSONArray -> for (i in 0 until node.length()) {
                findContinuationToken(node.opt(i))?.let { return it }
            }
        }
        return null
    }
}
