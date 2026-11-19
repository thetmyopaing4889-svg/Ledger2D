package com.myanmar.ledger2d.feature.live

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal data class SetFetchObservation(
    val snapshot: SetMarketSnapshot,
    /** Receipt time is diagnostic ONLY; cutoff selection uses SET source time. */
    val fetchedAt: Instant,
    val roundTripMs: Long,
)

internal data class SetHttpEvidence(
    val startedAt: Instant,
    val serverDate: String? = null,
    val age: String? = null,
    val cacheControl: String? = null,
    val contentType: String? = null,
    val retryAfter: String? = null,
    val responseBytes: Int = 0,
    val parserStage: String? = null,
)

internal sealed interface SetProbeOutcome {
    data class Success(
        val observation: SetFetchObservation,
        val httpStatus: Int,
        val htmlBytes: Int,
        val evidence: SetHttpEvidence,
    ) : SetProbeOutcome
    data class Failure(
        val reason: String,
        val httpStatus: Int?,
        val roundTripMs: Long,
        val evidence: SetHttpEvidence,
    ) : SetProbeOutcome
}

/**
 * The Colab probe's public SET HTML GET, ported to a bounded Android
 * HttpURLConnection. No cookie spoofing, access-control bypass or polling
 * is performed by this class. Must run on Dispatchers.IO.
 */
internal object SetSnapshotFetcher {
    private const val PAGE = "https://www.set.or.th/en/market/index/set/overview"
    private const val MAX_PAGE_BYTES = 3_000_000
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000

    fun fetchOnce(): SetFetchObservation? =
        (probeOnce() as? SetProbeOutcome.Success)?.observation

    fun probeOnce(endpoint: URL = URL(PAGE)): SetProbeOutcome {
        val startedNanos = System.nanoTime()
        var evidence = SetHttpEvidence(startedAt = Instant.now())
        fun elapsed() = ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(0L)
        fun failure(message: String, code: Int? = null) =
            SetProbeOutcome.Failure(message.take(160), code, elapsed(), evidence)

        return try {
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                // The website may provide HTML from a cache. Request revalidation,
                // do not allow Android's local URL cache to supply the old page.
                connection.useCaches = false
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("Accept-Language", "en")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Pragma", "no-cache")
                connection.setRequestProperty("User-Agent", "Cherry2D-PersonalResearchProbe/1.0")

                val code = connection.responseCode
                evidence = evidence.copy(
                    serverDate = connection.getHeaderField("Date"),
                    age = connection.getHeaderField("Age"),
                    cacheControl = connection.getHeaderField("Cache-Control"),
                    contentType = connection.contentType,
                    retryAfter = connection.getHeaderField("Retry-After"),
                )
                if (code !in 200..299) return failure("HTTP_$code", code)

                val buffer = ByteArray(8192)
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    var size = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > MAX_PAGE_BYTES) {
                            evidence = evidence.copy(responseBytes = size)
                            return failure("HTML_TOO_LARGE", code)
                        }
                        output.write(buffer, 0, read)
                    }
                }
                evidence = evidence.copy(responseBytes = output.size())
                // Python's Colab probe parsed the response body regardless of
                // Content-Type. We do too; incorrect MIME is diagnostic, not a
                // reason to silently drop valid HTML.
                val parsed = SetSnapshotHtmlParser.parseDetailed(
                    output.toString(Charsets.UTF_8.name())
                )
                if (parsed.snapshot == null) {
                    val stage = parsed.failureStage ?: "UNKNOWN"
                    evidence = evidence.copy(parserStage = stage)
                    return failure("PARSER_$stage", code)
                }
                SetProbeOutcome.Success(
                    observation = SetFetchObservation(
                        snapshot = parsed.snapshot,
                        fetchedAt = Instant.now(),
                        roundTripMs = elapsed(),
                    ),
                    httpStatus = code,
                    htmlBytes = output.size(),
                    evidence = evidence,
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            failure(e.javaClass.simpleName + ":" + e.message.orEmpty().take(110))
        }
    }
}
