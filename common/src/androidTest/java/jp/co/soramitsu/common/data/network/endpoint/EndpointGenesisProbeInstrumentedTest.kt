package jp.co.soramitsu.common.data.network.endpoint

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual Android sockets, loopback peers only. No wallet state, radios, credentials or trust changes. */
class EndpointGenesisProbeInstrumentedTest {
    @Before fun requireIsolatedCommonTestUidAndSupportedApi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("jp.co.soramitsu.common.test", instrumentation.context.packageName)
        assertEquals(instrumentation.context.applicationInfo.uid, instrumentation.targetContext.applicationInfo.uid)
        assertTrue(Build.VERSION.SDK_INT in setOf(26, 34, 36))
    }
    private val ordinaryRuntime = EndpointProbeRuntime()

    private val substrate = "0x" + "12".repeat(32)
    private val solana = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
    private fun json(genesis: String) = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"$genesis\"}"
    private fun probe(peer: Peer, ws: Boolean = false, runtime: EndpointProbeRuntime = ordinaryRuntime) = runBlocking {
        if (runtime === ordinaryRuntime) {
            // Completion precedes asynchronous close. Protocol cases share this runtime but do not
            // test budget exhaustion; observe full retirement before the next single exchange.
            // Read only our debug-test class field: no lease acquisition, retry or state mutation.
            val permits = EndpointProbeRuntime::class.java.getDeclaredField("permits").let { field ->
                field.isAccessible = true
                field.get(runtime) as java.util.concurrent.Semaphore
            }
            withTimeout(5_000) { while (permits.availablePermits() != 2) delay(5) }
        }
        EndpointGenesisProbe(runtime).probe(EndpointProbeRequest(
            if (ws) EndpointProbeFamily.SUBSTRATE else EndpointProbeFamily.SOLANA,
            "${if (ws) "ws" else "http"}://127.0.0.1:${peer.port}/", if (ws) substrate else solana
        ))
    }

    @Test fun httpUsesOneExactGenesisRequestWithoutCompressionOrAmbientHeaders() {
        Peer { socket ->
            val request = readHttp(socket)
            assertTrue(request.first.startsWith("POST / HTTP/1.1\r\n"))
            assertTrue(request.first.contains("Accept-Encoding: identity\r\n", true))
            assertFalse(request.first.contains("Authorization:", true)); assertFalse(request.first.contains("Cookie:", true))
            assertEquals(GenesisProbeProtocol.request(EndpointProbeFamily.SOLANA), request.second)
            http(socket, "200 OK", json(solana).toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.MATCH, probe(it)); assertEquals(1, it.connections.get()) }
    }

    @Test fun websocketUsesActualUpgradeAndOneMaskedFixedRequest() {
        Peer { socket ->
            handshake(socket)
            assertEquals(GenesisProbeProtocol.request(EndpointProbeFamily.SUBSTRATE), readWsRequest(socket))
            frame(socket, 0x81, json(substrate).toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.MATCH, probe(it, true)) }
    }

    @Test fun httpRedirectIsRejectedWithoutContactingLocation() {
        Peer { socket -> readHttp(socket); http(socket, "200 OK", json(solana).toByteArray()) }.use { redirect ->
            Peer { socket -> readHttp(socket); http(socket, "302 Found", byteArrayOf(), "Location: http://127.0.0.1:${redirect.port}/\r\n") }.use {
                assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it)); assertEquals(0, redirect.connections.get())
            }
        }
    }

    @Test fun retryAfterDoesNotIssueSecondRequest() {
        Peer { socket -> readHttp(socket); http(socket, "503 Unavailable", byteArrayOf(), "Retry-After: 0\r\n") }.use {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it)); assertEquals(1, it.connections.get())
        }
    }

    @Test fun httpRejectsCompressedAndOversizedAdvertisedBodiesBeforeReadingThem() {
        Peer { socket -> readHttp(socket); http(socket, "200 OK", json(solana).toByteArray(), "Content-Encoding: gzip\r\n") }.use {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it))
        }
        Peer { socket -> readHttp(socket); socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2147483647\r\nConnection: close\r\n\r\n".toByteArray()); socket.getOutputStream().flush() }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it))
        }
    }

    @Test fun httpUnknownLengthBodyIsBoundedAndWrongGenesisIsMismatch() {
        Peer { socket -> readHttp(socket); socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n" + " ".repeat(16385)).toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it))
        }
        Peer { socket -> readHttp(socket); http(socket, "200 OK", json("1".repeat(32)).toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.MISMATCH, probe(it))
        }
    }

    @Test fun httpFramingCannotBypassRawStreamCapWithHeadersOrChunkExtensions() {
        Peer { socket ->
            readHttp(socket)
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nX-Large: " + "x".repeat(65536) + "\r\nContent-Length: 0\r\n\r\n").toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it)) }
        Peer { socket ->
            readHttp(socket)
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1;" + "x".repeat(65536) + "\r\nx\r\n0\r\n\r\n").toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it)) }
        Peer { socket ->
            readHttp(socket); val body = json(solana)
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + body.length.toString(16) + ";synthetic=yes\r\n" + body + "\r\n0\r\n\r\n").toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.MATCH, probe(it)) }
    }

    @Test fun chunkExtensionSyntaxIsBoundedAndRejectsMalformedNamesValuesAndQuotes() {
        for (extension in listOf(";", ";=value", ";name=", ";name=\"unterminated", ";name=bad value", ";name=\"closed\"junk")) {
            Peer { socket ->
                readHttp(socket)
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0" + extension + "\r\n\r\n").toByteArray())
            }.use { assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it)) }
        }
        Peer { socket ->
            readHttp(socket); val body = json(solana)
            val extension = ";bare;quoted=\"space and \\\"quote\";token=abc"
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + body.length.toString(16) + extension + "\r\n" + body + "\r\n0\r\n\r\n").toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.MATCH, probe(it)) }
    }

    @Test fun httpRejectsAmbiguousLengthsEncodingsAndMalformedLineEndings() {
        val malformed = listOf(
            "Content-Length: 0\r\nTransfer-Encoding: chunked\r\n",
            "Content-Length: 0\r\nContent-Length: 1\r\n",
            "Content-Length: 0\r\nContent-Length: 0\r\n",
            "Transfer-Encoding: gzip\r\n",
            "Content-Length: -1\r\n",
            "Folded: first\r\n second\r\n"
        )
        for (headers in malformed) Peer { socket ->
            readHttp(socket); socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n" + headers + "\r\n").toByteArray())
        }.use { assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it)) }
        Peer { socket -> readHttp(socket); socket.getOutputStream().write("HTTP/1.1 200 OK\nContent-Length: 0\n\n".toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it))
        }
    }

    @Test fun httpRejectsInterimResponsesPrematureEofAndForbiddenTrailers() {
        Peer { socket -> readHttp(socket); socket.getOutputStream().write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray()); http(socket, "200 OK", json(solana).toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it))
        }
        Peer { socket -> readHttp(socket); socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nshort".toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it))
        }
        Peer { socket -> readHttp(socket); socket.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 0\r\n\r\n".toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it))
        }
    }

    @Test fun httpCumulativeHeadersChunkMetadataAndTrailersHaveFiniteBudgets() {
        Peer { socket -> readHttp(socket); socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n" + (1..60).joinToString("") { "X-$it: " + "x".repeat(300) + "\r\n" } + "\r\n").toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it))
        }
        Peer { socket -> readHttp(socket); socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + ("1;" + "x".repeat(100) + "\r\nx\r\n").repeat(700)).toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it))
        }
        Peer { socket -> readHttp(socket); socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Large: " + "x".repeat(2048) + "\r\n\r\n").toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it))
        }
    }

    @Test fun explicitCancellationDuringDnsNeverConnectsAfterLateResolution() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); var published = false
        val runtime = EndpointProbeRuntime(3000, object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return listOf(InetAddress.getByName("127.0.0.1"))
            }
        })
        val heldSlot = checkNotNull(runtime.acquire())
        try { Peer { socket -> readHttp(socket); http(socket, "200 OK", json(solana).toByteArray()) }.use { peer ->
            val job = launch { EndpointGenesisProbe(runtime).probe(EndpointProbeRequest(EndpointProbeFamily.SOLANA, "http://localhost:${peer.port}/", solana)); published = true }
            try {
                withTimeout(5_000) { while (entered.count > 0) delay(5) }
                job.cancelAndJoin(); assertFalse(published); assertEquals(0, peer.connections.get())
            } finally { release.countDown() }
            // Poll actual worker admission after late DNS returns, not a fixed sleep for success.
            awaitWorkerRetirement(runtime)
            assertEquals(0, peer.connections.get()); assertFalse(published)
        }
        } finally { release.countDown(); heldSlot.finished() }
    }

    @Test fun websocketRejectsBinaryMalformedUtf8AndOversizedDeclaredFrames() {
        Peer { socket -> handshake(socket); readWsRequest(socket); frame(socket, 0x82, json(substrate).toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it, true))
        }
        Peer { socket -> handshake(socket); readWsRequest(socket); frame(socket, 0x81, byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }.use {
            assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it, true))
        }
        Peer { socket -> handshake(socket); readWsRequest(socket); socket.getOutputStream().write(byteArrayOf(0x81.toByte(), 126, 0x40, 0x01)); socket.getOutputStream().flush() }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it, true))
        }
    }

    @Test fun websocketFragmentAggregateIsBoundedAndCompressionNotOffered() {
        Peer { socket -> handshake(socket); readWsRequest(socket); frame(socket, 0x01, ByteArray(8192) { 32 }); frame(socket, 0x80, ByteArray(8193) { 32 }) }.use {
            assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it, true))
        }
        Peer { socket -> handshake(socket, "Sec-WebSocket-Extensions: permessage-deflate\r\n") }.use {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it, true))
        }
    }

    @Test fun websocketWholeHandshakeStreamHasFiniteAdmissionAndNoRedirect() {
        Peer { socket -> readHttp(socket); socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nX-Large: " + "x".repeat(65536) + "\r\n\r\n").toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it, true))
        }
        Peer { socket -> handshake(socket); readWsRequest(socket); frame(socket, 0x81, json(substrate).toByteArray()) }.use { destination ->
            Peer { socket -> readHttp(socket); http(socket, "302 Found", byteArrayOf(), "Location: ws://127.0.0.1:${destination.port}/\r\n") }.use {
                assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it, true)); assertEquals(1, it.connections.get())
                assertEquals(0, destination.connections.get())
            }
        }
    }

    @Test fun totalDeadlineReturnsWithoutWaitingForAStalledPeer() {
        val received = CountDownLatch(1)
        Peer { socket -> readHttp(socket); received.countDown(); assertEquals(-1, socket.getInputStream().read()) }.use {
            val start = System.nanoTime()
            assertEquals(EndpointProbeOutcome.TIMED_OUT, probe(it, runtime = EndpointProbeRuntime(500)))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000)
            assertTrue(received.await(1, TimeUnit.SECONDS))
        }
    }

    @Test fun cancellationClosesRegisteredSocketAndReturnsWithoutLatePublication() = runBlocking {
        val received = CountDownLatch(1); val closed = CountDownLatch(1); var published = false
        Peer { socket -> readHttp(socket); received.countDown(); assertEquals(-1, socket.getInputStream().read()); closed.countDown() }.use { peer ->
            val job = launch {
                EndpointGenesisProbe().probe(EndpointProbeRequest(EndpointProbeFamily.SOLANA, "http://127.0.0.1:${peer.port}/", solana))
                published = true
            }
            withTimeout(5_000) { while (received.count > 0) delay(5) }
            val start = System.nanoTime(); job.cancelAndJoin()
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
            assertTrue(closed.await(2, TimeUnit.SECONDS)); assertFalse(published)
        }
    }

    @Test fun blockedDnsKeepsBoundedSlotsAndCannotOpenLateSocketsAfterDeadline() = runBlocking {
        val entered = CountDownLatch(2); val release = CountDownLatch(1)
        val runtime = EndpointProbeRuntime(500, object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return listOf(InetAddress.getByName("127.0.0.1"))
            }
        })
        Peer { socket -> readHttp(socket); http(socket, "200 OK", json(solana).toByteArray()) }.use { peer ->
            val request = EndpointProbeRequest(EndpointProbeFamily.SOLANA, "http://localhost:${peer.port}/", solana)
            try {
                val first = async { EndpointGenesisProbe(runtime).probe(request) }
                val second = async { EndpointGenesisProbe(runtime).probe(request) }
                withTimeout(5_000) { while (entered.count > 0) delay(5) }
                assertEquals(EndpointProbeOutcome.TIMED_OUT, first.await()); assertEquals(EndpointProbeOutcome.TIMED_OUT, second.await())
                assertEquals(EndpointProbeOutcome.BUSY, EndpointGenesisProbe(runtime).probe(request))
            } finally { release.countDown() }
            var firstRetired: ProbeSocketLease? = null; var secondRetired: ProbeSocketLease? = null
            try {
                withTimeout(5_000) {
                    while (firstRetired == null) { firstRetired = runtime.acquire(); if (firstRetired == null) delay(5) }
                    while (secondRetired == null) { secondRetired = runtime.acquire(); if (secondRetired == null) delay(5) }
                }
                assertEquals(0, peer.connections.get())
            } finally { firstRetired?.finished(); secondRetired?.finished() }
        }
    }

    @Test fun bothProtocolsRejectMalformedJsonRpcEnvelopeAndExactIdentityLength() {
        for (ws in listOf(false, true)) {
            val value = if (ws) substrate else solana
            val malformed = listOf(
                "{\"jsonrpc\":\"1.0\",\"id\":1,\"result\":\"$value\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"$value\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":1.0,\"result\":\"$value\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":1,\"result\":\"$value\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"$value\",\"error\":{}}",
                "[${json(value)}]", "${json(value)} {}", json(if (ws) "0x12" else "1")
            )
            for (body in malformed) Peer { socket ->
                if (ws) { handshake(socket); readWsRequest(socket); frame(socket, 0x81, body.toByteArray()) }
                else { readHttp(socket); http(socket, "200 OK", body.toByteArray()) }
            }.use { assertEquals(EndpointProbeOutcome.MALFORMED_RESPONSE, probe(it, ws)) }
        }
    }

    @Test fun websocketWrongGenesisIsMismatchAnd64BitDeclaredPayloadIsRejected() {
        Peer { socket -> handshake(socket); readWsRequest(socket); frame(socket, 0x81, json("0x" + "34".repeat(32)).toByteArray()) }.use {
            assertEquals(EndpointProbeOutcome.MISMATCH, probe(it, true))
        }
        Peer { socket ->
            handshake(socket); readWsRequest(socket)
            // 4 GiB + 1: no payload allocation or integer truncation is admissible.
            socket.getOutputStream().write(byteArrayOf(0x81.toByte(), 127, 0, 0, 0, 1, 0, 0, 0, 1))
            socket.getOutputStream().flush()
        }.use { assertEquals(EndpointProbeOutcome.RESPONSE_LIMIT, probe(it, true)) }
    }

    @Test fun independentHttpInvocationsDoNotReuseResponseOrConnectionAndWebsocketDoesNotRetry503() {
        val responses = AtomicInteger()
        Peer { socket ->
            readHttp(socket)
            http(socket, "200 OK", json(if (responses.incrementAndGet() == 1) solana else "1".repeat(32)).toByteArray())
        }.use {
            assertEquals(EndpointProbeOutcome.MATCH, probe(it))
            assertEquals(EndpointProbeOutcome.MISMATCH, probe(it))
            assertEquals(2, it.connections.get()); assertEquals(2, responses.get())
        }
        Peer { socket -> readHttp(socket); http(socket, "503 Unavailable", byteArrayOf(), "Retry-After: 0\r\n") }.use {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe(it, true)); assertEquals(1, it.connections.get())
        }
    }

    @Test fun websocketCancellationClosesSocketAndSuppressesLateResult() = runBlocking {
        val received = CountDownLatch(1); val closed = CountDownLatch(1); var published = false
        Peer { socket ->
            handshake(socket); readWsRequest(socket); received.countDown()
            assertEquals(-1, socket.getInputStream().read()); closed.countDown()
        }.use { peer ->
            val job = launch {
                EndpointGenesisProbe().probe(EndpointProbeRequest(EndpointProbeFamily.SUBSTRATE, "ws://127.0.0.1:${peer.port}/", substrate))
                published = true
            }
            try {
                withTimeout(5_000) { while (received.count > 0) delay(5) }
                job.cancelAndJoin()
                assertTrue(closed.await(3, TimeUnit.SECONDS)); assertFalse(published)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun websocketDeadlineRetiresAStalledApplicationResponse() {
        val received = CountDownLatch(1); val closed = CountDownLatch(1)
        Peer { socket ->
            handshake(socket); readWsRequest(socket); received.countDown()
            assertEquals(-1, socket.getInputStream().read()); closed.countDown()
        }.use {
            val start = System.nanoTime()
            assertEquals(EndpointProbeOutcome.TIMED_OUT, probe(it, true, EndpointProbeRuntime(500)))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000)
            assertTrue(received.await(1, TimeUnit.SECONDS)); assertTrue(closed.await(3, TimeUnit.SECONDS))
        }
    }

    private class Peer(private val action: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val connections = AtomicInteger()
        val actionsCompleted = AtomicInteger()
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var closing = false
        @Volatile private var socket: Socket? = null
        private val thread = Thread {
            try {
                while (!closing) {
                    server.accept().use { accepted ->
                        socket = accepted; connections.incrementAndGet(); accepted.soTimeout = 5_000
                        try { action(accepted) }
                        catch (_: IOException) { /* Expected peer close at a rejection, cap or cancellation. */ }
                        finally { actionsCompleted.incrementAndGet(); socket = null }
                    }
                }
            } catch (error: IOException) { if (!closing) failure.set(error) }
            catch (error: Throwable) { failure.set(error) }
        }.apply { isDaemon = true; start() }
        override fun close() {
            closing = true; server.close(); socket?.close(); thread.join(5_000)
            assertFalse("Loopback peer did not terminate", thread.isAlive)
            failure.get()?.let { throw AssertionError("Loopback peer assertion failed", it) }
        }
    }

    private suspend fun awaitWorkerRetirement(runtime: EndpointProbeRuntime) = withTimeout(5_000) {
        var retired: ProbeSocketLease? = null
        while (retired == null) { retired = runtime.acquire(); if (retired == null) delay(5) }
        retired.finished()
    }

    private fun readHttp(socket: Socket): Pair<String, String> {
        val bytes = ByteArrayOutputStream(); val input = socket.getInputStream()
        while (!bytes.toString("US-ASCII").endsWith("\r\n\r\n")) {
            val byte = input.read(); check(byte >= 0 && bytes.size() < 16384); bytes.write(byte)
        }
        val header = bytes.toString("US-ASCII")
        val length = header.split("\r\n").firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
        check(length in 0..1024)
        val body = ByteArray(length)
        for (i in body.indices) { val byte = input.read(); check(byte >= 0); body[i] = byte.toByte() }
        return header to body.toString(Charsets.UTF_8)
    }

    private fun http(socket: Socket, status: String, body: ByteArray, extra: String = "") {
        socket.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n${extra}Connection: close\r\n\r\n").toByteArray())
        socket.getOutputStream().write(body); socket.getOutputStream().flush()
    }

    private fun handshake(socket: Socket, extra: String = "") {
        val headers = readHttp(socket).first
        assertFalse(headers.contains("Sec-WebSocket-Extensions:", true))
        val key = headers.split("\r\n").single { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
        val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
        socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n$extra\r\n").toByteArray())
        socket.getOutputStream().flush()
    }

    private fun readWsRequest(socket: Socket): String {
        val input = socket.getInputStream(); assertEquals(0x81, input.read()); val length = input.read()
        assertTrue(length and 128 != 0); check(length and 127 < 126)
        val mask = ByteArray(4) { input.read().also { check(it >= 0) }.toByte() }
        return ByteArray(length and 127) { (input.read().also { check(it >= 0) } xor mask[it % 4].toInt()).toByte() }.toString(Charsets.UTF_8)
    }

    private fun frame(socket: Socket, opcode: Int, payload: ByteArray) {
        val output = socket.getOutputStream(); output.write(opcode)
        if (payload.size < 126) output.write(payload.size) else { output.write(126); output.write(payload.size shr 8); output.write(payload.size and 255) }
        output.write(payload); output.flush()
    }
}
