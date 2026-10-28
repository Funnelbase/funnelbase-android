package com.funnelbase.sdk.internal

import android.util.Log
import com.funnelbase.sdk.IngestResponse
import com.funnelbase.sdk.SdkJson
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

internal sealed interface UploadResult {
    /** Accepted; the caller should drop the uploaded events from the queue. */
    data object Success : UploadResult

    /** Transient (network, 5xx, 429) — keep the events and try again later. */
    data class Retryable(val reason: String) : UploadResult

    /**
     * Permanent (4xx other than 429). Retrying will never work, so the events are dropped:
     * a bad API key or a malformed payload would otherwise block the queue forever.
     */
    data class Permanent(val reason: String) : UploadResult
}

/**
 * Uploads batches over `HttpURLConnection`.
 *
 * No OkHttp: this library is linked into other people's apps, and an HTTP client is the single
 * most likely dependency to collide with the host's own version.
 */
internal class EventUploader(
    private val url: String,
    private val apiKey: String,
    private val debugLogging: Boolean,
) {
    fun upload(body: String): UploadResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Api-Key", apiKey)
                // Event batches are highly repetitive JSON; gzip typically cuts them by 80%+,
                // which is bandwidth on someone else's mobile data plan.
                setRequestProperty("Content-Encoding", "gzip")
            }

            GZIPOutputStream(connection.outputStream).use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            when {
                status in 200..299 -> {
                    // The server accepts the batch (202) even when it rejected individual
                    // events — a stale Event Catalog is the common cause. Nothing retries a
                    // 2xx, so if we didn't read the body a developer would have no signal at
                    // all that their events are being dropped.
                    reportRejections(connection.inputStream.bufferedReader().use(BufferedReader::readText))
                    UploadResult.Success
                }
                // 429 is the server asking us to slow down, not to give up.
                status == 429 || status >= 500 -> UploadResult.Retryable("HTTP $status")
                else -> {
                    val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                    UploadResult.Permanent("HTTP $status: ${error.take(200)}")
                }
            }
        } catch (throwable: Throwable) {
            // Offline is the normal case for a mobile SDK, not an exception worth surfacing.
            UploadResult.Retryable(throwable.message ?: throwable.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Surfaces per-event rejections from an otherwise-successful upload.
     *
     * Warn level regardless of [debugLogging]: these events are gone, and that is worth seeing
     * in a release build's logcat too.
     */
    private fun reportRejections(body: String) {
        val response = runCatching {
            SdkJson.decodeFromString(IngestResponse.serializer(), body)
        }.getOrNull() ?: return

        if (response.rejected.isEmpty()) {
            log("Uploaded batch, ${response.accepted} accepted")
            return
        }

        val first = response.rejected.first()
        Log.w(
            TAG,
            "Server rejected ${response.rejected.size} event(s); ${response.accepted} accepted. " +
                "First: [${first.code}] ${first.reason}",
        )
    }

    private fun log(message: String) {
        if (debugLogging) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "Analytics"
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
    }
}
