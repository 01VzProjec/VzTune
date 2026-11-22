KOTLIN_CODE = 'package com.vztechcore.vztune.data.innertube\n\nimport kotlinx.coroutines.coroutineScope\nimport okhttp3.MediaType.Companion.toMediaType\nimport okhttp3.OkHttpClient\nimport okhttp3.Request\nimport okhttp3.RequestBody.Companion.toRequestBody\nimport org.json.JSONArray\nimport org.json.JSONObject\nimport java.util.concurrent.TimeUnit\n\n/**\n * InnerTube client for regular YouTube (youtube.com).\n *\n * YT Music already uses [InnerTube] with WEB_REMIX.\n * This one uses WEB / WEB_EMBEDDED / VISIONOS for YouTube:\n * search, browse (channel, playlist, tabs), next and player.\n *\n * Part 1 only: creates the client. No repository uses it yet,\n * so existing behavior does not change.\n */\nobject InnerTubeYouTube {\n\n    private const val API = "https://www.youtube.com/youtubei/v1"\n    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()\n\n    private val http = OkHttpClient.Builder()\n        .connectTimeout(15, TimeUnit.SECONDS)\n        .readTimeout(20, TimeUnit.SECONDS)\n        .build()\n\n    private data class Client(\n        val name: String,\n        val version: String,\n        val apiKey: String,\n        val userAgent: String,\n        val referer: String? = null,\n    )\n\n    // Main client for search / browse / next\n    private val WEB = Client(\n        name = "WEB",\n        version = "2.20261120.00.00",\n        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",\n        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",\n        referer = "https://www.youtube.com/",\n    )\n\n    // Embedded client, more permissive for player in some cases\n    private val WEB_EMBEDDED = Client(\n        name = "WEB_EMBEDDED",\n        version = "1.20251120.01.00",\n        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",\n        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",\n        referer = "https://www.youtube.com/",\n    )\n\n    // Fallback client for player when WEB returns 403 / no URLs\n    private val VISIONOS = Client(\n        name = "VISIONOS",\n        version = "1.9.27",\n        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",\n        userAgent = "Mozilla/5.0 (VisionOS; U; CPU OS 1_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)",\n        referer = "https://www.youtube.com/",\n    )\n\n    @Volatile\n    var visitorData: String? = null\n        private set\n\n    private fun buildBody(client: Client): JSONObject = JSONObject().apply {\n        put("context", JSONObject().apply {\n            put("client", JSONObject().apply {\n                put("clientName", client.name)\n                put("clientVersion", client.version)\n                put("hl", "pt")\n                put("gl", "BR")\n                visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) }\n            })\n        })\n    }\n\n    fun search(query: String, params: String? = null): JSONObject =\n        post("search", WEB, buildBody(WEB).apply {\n            put("query", query)\n            if (params != null) put("params", params)\n        })\n\n    fun searchContinuation(token: String): JSONObject =\n        post("search", WEB, buildBody(WEB).apply {\n            put("continuation", token)\n        })\n\n    fun browse(browseId: String, params: String? = null): JSONObject =\n        post("browse", WEB, buildBody(WEB).apply {\n            put("browseId", browseId)\n            if (!params.isNullOrBlank()) put("params", params)\n        })\n\n    fun browseContinuation(token: String): JSONObject =\n        post("browse", WEB, buildBody(WEB).apply {\n            put("continuation", token)\n        })\n\n    fun next(videoId: String): JSONObject =\n        post("next", WEB, buildBody(WEB).apply {\n            put("videoId", videoId)\n        })\n\n    /**\n     * Player endpoint. Tries WEB first, then VISIONOS as fallback.\n     * Returns raw player JSON, caller parses streamingData.\n     */\n    suspend fun player(videoId: String): JSONObject = coroutineScope {\n        val webResult = runCatching {\n            playerWithClient(videoId, WEB)\n        }.getOrNull()\n        if (webResult != null && isPlayable(webResult) && hasPlayableUrls(webResult)) {\n            return@coroutineScope webResult\n        }\n        val embeddedResult = runCatching {\n            playerWithClient(videoId, WEB_EMBEDDED)\n        }.getOrNull()\n        if (embeddedResult != null && isPlayable(embeddedResult) && hasPlayableUrls(embeddedResult)) {\n            return@coroutineScope embeddedResult\n        }\n        val visionResult = runCatching {\n            playerWithClient(videoId, VISIONOS)\n        }.getOrNull()\n        if (visionResult != null && isPlayable(visionResult)) {\n            return@coroutineScope visionResult\n        }\n        // Return best we have, or throw if nothing playable\n        webResult ?: embeddedResult ?: visionResult ?: throw Exception("Not playable")\n    }\n\n    private fun playerWithClient(videoId: String, client: Client): JSONObject =\n        post("player", client, buildBody(client).apply {\n            put("videoId", videoId)\n            put("contentCheckOk", true)\n            put("racyCheckOk", true)\n        })\n\n    private fun post(endpoint: String, client: Client, body: JSONObject): JSONObject {\n        val request = Request.Builder()\n            .url("$API/$endpoint?prettyPrint=false&key=${client.apiKey}")\n            .addHeader("Content-Type", "application/json")\n            .addHeader("User-Agent", client.userAgent)\n            .addHeader("X-Goog-Api-Format-Version", "1")\n            .addHeader("X-YouTube-Client-Name", client.name)\n            .addHeader("X-YouTube-Client-Version", client.version)\n            .addHeader("x-origin", "https://www.youtube.com")\n            .apply {\n                if (client.referer != null) addHeader("Referer", client.referer)\n                visitorData?.takeIf { it.isNotBlank() }?.let {\n                    addHeader("X-Goog-Visitor-Id", it)\n                }\n            }\n            .post(body.toString().toRequestBody(JSON_MEDIA))\n            .build()\n\n        http.newCall(request).execute().use { response ->\n            if (!response.isSuccessful) throw Exception("InnerTubeYouTube HTTP ${response.code}")\n            val text = response.body?.string() ?: throw Exception("InnerTubeYouTube empty")\n            val json = JSONObject(text)\n            updateVisitorData(json)\n            return json\n        }\n    }\n\n    private fun updateVisitorData(json: JSONObject) {\n        val data = json.optJSONObject("responseContext")?.optString("visitorData")\n        if (!data.isNullOrBlank() && data != "null") {\n            visitorData = data\n        }\n    }\n\n    fun isPlayable(playerJson: JSONObject): Boolean =\n        playerJson.optJSONObject("playabilityStatus")?.optString("status") == "OK"\n\n    private fun hasPlayableUrls(playerJson: JSONObject): Boolean {\n        val streaming = playerJson.optJSONObject("streamingData") ?: return false\n        val formats = streaming.optJSONArray("formats")\n        val adaptive = streaming.optJSONArray("adaptiveFormats")\n        return (formats != null && formats.length() > 0) ||\n            (adaptive != null && adaptive.length() > 0)\n    }\n\n    fun findContinuationToken(node: Any?): String? {\n        // Reuse the same logic already tested in InnerTube for YT Music\n        return InnerTube.findContinuationToken(node)\n    }\n\n    // Keep JSONArray import used, helper for future parsers\n    @Suppress("unused")\n    private fun isEmptyArray(arr: JSONArray?): Boolean = arr == null || arr.length() == 0\n}\n'

# parte1.py - Parte 1: InnerTube do YouTube (base, nao quebra nada)
# Rodar na raiz do projeto: python3 parte1.py
# Regras: se alguma ancora nao bater, aborta SEM escrever naquele arquivo e avisa qual.
# Nao cria .bak nem arquivo temporario.
import os
import sys

def fail(msg):
    print(f"ABORTOU: {msg}")
    sys.exit(1)

ROOT = os.getcwd()
INNER_OLD = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/innertube/Innertube.kt")
INNER_NEW = os.path.join(ROOT, "data/src/main/kotlin/com/vztechcore/vztune/data/innertube/InnerTubeYouTube.kt")
BAK = os.path.join(ROOT, ".acside/editor/openedFiles.json.bak")

# 0 - Confere raiz
if not os.path.isfile(INNER_OLD):
    fail(f"nao achei {INNER_OLD}. Roda na raiz do VzTune (/storage/emulated/0/AndroidIDE_Projects/VzTune)")

# 1 - Limpeza segura (Passo 0 dentro da Parte 1)
if os.path.isfile(BAK):
    try:
        os.remove(BAK)
        print(f"Apagado: {BAK}")
    except Exception as e:
        fail(f"nao consegui apagar {BAK}: {e}")
else:
    print("Limpeza: .bak ja nao existia, ok")

# 2 - Nao sobrescreve arquivo novo se ja existir (evita apagar teu edit)
if os.path.isfile(INNER_NEW):
    print(f"JA EXISTE: {INNER_NEW} - pulando criacao pra nao sobrescrever. Se quiser recriar, apaga ele manual e roda de novo.")
else:
    with open(INNER_NEW, "w", encoding="utf-8") as out:
        out.write(KOTLIN_CODE)
    print(f"Criado: {INNER_NEW}")

# 3 - Patch leve no Innertube.kt: so atualiza versao WEB_REMIX, nada de logica
with open(INNER_OLD, "r", encoding="utf-8") as f:
    old_text = f.read()

anchor_old = 'version = "1.20220606.03.00"'
anchor_new = 'version = "1.20250616.01.00"'
if anchor_old not in old_text:
    if anchor_new in old_text:
        print("Innertube.kt: versao ja atualizada, ok")
    else:
        fail(f"ancora nao bateu em Innertube.kt: {anchor_old}")
else:
    new_text = old_text.replace(anchor_old, anchor_new, 1)
    # So escreve se achou a ancora
    with open(INNER_OLD, "w", encoding="utf-8") as f:
        f.write(new_text)
    print("Innertube.kt: WEB_REMIX 1.20220606.03.00 -> 1.20250616.01.00")

print("Parte 1 OK - agora comita e da push, espera o build passar antes da Parte 2")
