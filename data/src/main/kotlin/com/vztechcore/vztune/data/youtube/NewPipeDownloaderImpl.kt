package com.vztechcore.vztune.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges NewPipeExtractor's Downloader interface to OkHttp.
 * One instance per app process; NewPipe.init(downloader) is called once
 * in YouTubeRepository before any extraction.
 */
@Singleton
class NewPipeDownloaderImpl @Inject constructor(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val data = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(method, data?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (key, values) ->
            values.forEach { requestBuilder.addHeader(key, it) }
        }

        val response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw e
        }

        if (response.code == 429) {
            throw ReCaptchaException("ReCaptcha challenge requested", url)
        }

        val body = response.body?.string()
        val responseHeaders = response.headers.toMultimap()
        return Response(response.code, response.message, responseHeaders, body, url)
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}