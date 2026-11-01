package com.buggygamesyt.shortsfinder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An HTTP response with a non-2xx status code. */
class HttpException(val code: Int, message: String) : IOException(message)

/** YouTube answered, but not with something we could use. */
class YouTubeException(message: String) : Exception(message)

/**
 * Small OkHttp wrapper with retries for flaky mobile connections.
 * Retries connection errors, timeouts, HTTP 429 and HTTP 5xx with exponential backoff.
 */
class Http {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(url, headers, null)

    suspend fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val text = execute(url, headers, body.toString())
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw YouTubeException("YouTube sent a response the app couldn't read.")
        }
    }

    private suspend fun execute(url: String, headers: Map<String, String>, jsonBody: String?): String {
        var lastError: IOException? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(BASE_DELAY_MS * (1L shl (attempt - 1)))
            try {
                return withContext(Dispatchers.IO) { callOnce(url, headers, jsonBody) }
            } catch (e: HttpException) {
                if (e.code != 429 && e.code < 500) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Request failed")
    }

    private fun callOnce(url: String, headers: Map<String, String>, jsonBody: String?): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            // Skips the cookie consent interstitial shown in some regions.
            .header("Cookie", "CONSENT=YES+cb; SOCS=CAI")
        for ((k, v) in headers) builder.header(k, v)
        if (jsonBody != null) builder.post(jsonBody.toRequestBody(JSON_TYPE))
        return client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
            text
        }
    }

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Safari/537.36"
        private const val MAX_ATTEMPTS = 3
        private const val BASE_DELAY_MS = 800L
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
