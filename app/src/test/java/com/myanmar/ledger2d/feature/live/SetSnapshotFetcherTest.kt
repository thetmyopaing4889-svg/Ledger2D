package com.myanmar.ledger2d.feature.live

import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetSnapshotFetcherTest {
    private fun serve(code: Int, contentType: String, body: String): Pair<SetProbeOutcome, String> {
        val server = ServerSocket(0)
        server.soTimeout = 5000
        val captured = AtomicReference("")
        val responder = thread(isDaemon = true, name = "set-test-http") {
            server.use { listener ->
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = StringBuilder()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                        lines.append(line).append('\n')
                    }
                    captured.set(lines.toString())
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val status = if (code == 200) "OK" else "Too Many Requests"
                    val headers = "HTTP/1.1 " + code + " " + status + "\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "Content-Length: " + bytes.size + "\r\n" +
                        "Cache-Control: max-age=30\r\n" +
                        "Age: 4\r\n" +
                        "Connection: close\r\n\r\n"
                    socket.getOutputStream().use { out ->
                        out.write(headers.toByteArray(Charsets.US_ASCII))
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
        }
        val result = SetSnapshotFetcher.probeOnce(
            URL("http://127.0.0.1:" + server.localPort + "/")
        )
        responder.join(5000)
        return result to captured.get()
    }

    private val fixture = """
        <html><head><script>SET Index Series Index 999.99</script></head><body>
        <h1>SET Index Series</h1><h2>SET</h2>
        <p>Index&nbsp;<b>1,481.46</b></p>
        <p>Market Status : Intermission</p>
        <p>Last Update 18 Nov 2026 12:30:53</p>
        <p>Value (M.Baht) 29,810.24</p>
        <h2>SET50</h2><p>Index 9,999.99</p>
        </body></html>
    """.trimIndent()

    @Test fun parses_valid_html_even_if_server_sends_text_plain() {
        val (reply, request) = serve(200, "text/plain; charset=utf-8", fixture)
        assertTrue(reply is SetProbeOutcome.Success)
        val success = reply as SetProbeOutcome.Success
        assertEquals("60", success.observation.snapshot.computed2d)
        assertEquals("4", success.evidence.age)
        assertEquals("max-age=30", success.evidence.cacheControl)
        assertTrue(request.startsWith("GET / HTTP/1.1"))
        assertTrue(request.contains("Cache-Control: no-cache", ignoreCase = true))
    }

    @Test fun reports_parser_failure_stage_not_silent_null() {
        val (reply, _) = serve(200, "text/html", "<html><body>SET50 only</body></html>")
        assertTrue(reply is SetProbeOutcome.Failure)
        val failed = reply as SetProbeOutcome.Failure
        assertEquals(200, failed.httpStatus)
        assertEquals("SET_PANEL_MISSING", failed.evidence.parserStage)
        assertTrue(failed.reason.contains("PARSER_SET_PANEL_MISSING"))
    }

    @Test fun records_http_429_for_stop_not_repeated_retry() {
        val (reply, _) = serve(429, "text/plain", "Rate limited")
        assertTrue(reply is SetProbeOutcome.Failure)
        assertEquals(429, (reply as SetProbeOutcome.Failure).httpStatus)
    }
}
