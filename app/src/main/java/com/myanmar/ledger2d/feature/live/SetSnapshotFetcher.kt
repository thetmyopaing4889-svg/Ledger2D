package com.myanmar.ledger2d.feature.live

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal data class SetFetchObservation(
    val snapshot: SetMarketSnapshot,
    /** Local receive timestamp for diagnostics and staleness checks, never cutoff selection. */
    val fetchedAt: Instant,
    val roundTripMs: Long,
)

/**
 * One bounded public-page request. No polling loop or background worker is enabled
 * here. The integration layer must use Dispatchers.IO, check access restrictions,
 * and leave Luke as the sole authority for system final results.
 */
internal object SetSnapshotFetcher {
    private const val PAGE = "https://www.set.or.th/en/market/index/set/overview"
    private const val MAX_PAGE_BYTES = 3_000_000
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    fun fetchOnce(): SetFetchObservation? {
        val started = System.nanoTime()
        return runCatching {
            val connection = URL(PAGE).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("User-Agent", "Cherry2D-PersonalResearchProbe/1.0")
                if (connection.responseCode !in 200..299) return null
                val contentType = connection.contentType.orEmpty().lowercase()
                if (!contentType.contains("text/html")) return null
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_PAGE_BYTES) return null
                        bytes.write(buffer, 0, read)
                    }
                }
                val html = bytes.toString(Charsets.UTF_8.name())
                val snapshot = SetSnapshotHtmlParser.parse(html) ?: return null
                SetFetchObservation(
                    snapshot = snapshot,
                    fetchedAt = Instant.now(),
                    roundTripMs = (System.nanoTime() - started) / 1_000_000,
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
