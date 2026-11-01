package com.vztechcore.vztune.data.youtube

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between NewPipeExtractor's Downloader and OkHttp.
 * Ported verbatim from VzMusic's DownloaderImpl so behavior matches.
 */
@Singleton
class NewPipeDownloader @Inject constructor() : Downloader() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val requestBody = request.dataToSend()?.toRequestBody()

        val requestBuilder = okhttp3.Request.Builder()
            .method(request.httpMethod(), requestBody)
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}