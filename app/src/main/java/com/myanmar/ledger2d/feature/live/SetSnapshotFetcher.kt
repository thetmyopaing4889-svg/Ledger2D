package com.myanmar.ledger2d.feature.live

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal data class SetFetchObservation(
    val snapshot: SetMarketSnapshot,
    /** Receive timestamp for diagnostics; NEVER a cutoff selection input. */
    val fetchedAt: Instant,
    val roundTripMs: Long,
)

internal sealed interface SetProbeOutcome {
    data class Success(
        val observation: SetFetchObservation,
        val httpStatus: Int,
        val htmlBytes: Int,
    ) : SetProbeOutcome
    data class Failure(
        val reason: String,
        val httpStatus: Int?,
        val roundTripMs: Long,
    ) : SetProbeOutcome
}

/**
 * One bounded public-page request. No polling loop/background worker is enabled.
 * Must be invoked off the UI thread and subject to SET's published access rules.
 * Luke remains the sole authority for system final results.
 */
internal object SetSnapshotFetcher {
    private const val PAGE = "https://www.set.or.th/en/market/index/set/overview"
    private const val MAX_PAGE_BYTES = 3_000_000
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    fun fetchOnce(): SetFetchObservation? =
        (probeOnce() as? SetProbeOutcome.Success)?.observation

    fun probeOnce(): SetProbeOutcome {
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000
        fun failure(reason: String, code: Int? = null) =
            SetProbeOutcome.Failure(reason, code, elapsed())

        return try {
            val connection = URL(PAGE).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("Accept-Language", "en")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("User-Agent", "Cherry2D-PersonalResearchProbe/1.0")

                val code = connection.responseCode
                if (code !in 200..299) return failure("Website HTTP response " + code, code)
                val contentType = connection.contentType.orEmpty().lowercase()
                if (!contentType.contains("text/html")) {
                    return failure("Unexpected content type: " + contentType, code)
                }
                val output = ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(8_192)
                    var total = 0
                    while (true) {
                        val bytesRead = stream.read(buffer)
                        if (bytesRead < 0) break
                        total += bytesRead
                        if (total > MAX_PAGE_BYTES) return failure("HTML response exceeds 3 MB", code)
                        output.write(buffer, 0, bytesRead)
                    }
                }
                val snapshot = SetSnapshotHtmlParser.parse(
                    output.toString(Charsets.UTF_8.name())
                ) ?: return failure("Parser: SET Index/Value/Last Update not found", code)
                SetProbeOutcome.Success(
                    observation = SetFetchObservation(
                        snapshot = snapshot,
                        fetchedAt = Instant.now(),
                        roundTripMs = elapsed(),
                    ),
                    httpStatus = code,
                    htmlBytes = output.size(),
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            failure(error.javaClass.simpleName + ": " + error.message.orEmpty().take(120))
        }
    }
}
