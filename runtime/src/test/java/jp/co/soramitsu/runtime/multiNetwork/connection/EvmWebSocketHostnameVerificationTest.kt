@file:Suppress("PackageNaming") // Preserve the existing runtime multiNetwork package.

package jp.co.soramitsu.runtime.multiNetwork.connection

import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.protocol.websocket.WebSocketClient
import org.web3j.protocol.websocket.WebSocketListener
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ConnectException
import java.net.InetAddress
import java.net.URI
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.security.auth.x500.X500Principal

/**
 * CVE-2020-11050 regression for the EVM wss:// transport. Java-WebSocket before 1.5.0 never
 * checked that the server certificate names the dialed host, so an on-path peer holding any
 * trusted certificate could impersonate an RPC node and read the request path (API keys).
 *
 * Every case uses the same loopback peer, the same certificate (issued only to [CERTIFIED_HOST])
 * and the same client trust; only the dialed host name changes. Trust comes from a test-local
 * SSLContext handed to the client with setSocketFactory, so the JVM default SSLContext and trust
 * store are never changed. Java-WebSocket applies its HTTPS endpoint identification
 * (onSetSSLParameters) to every SSLSocket it opens, whichever factory created it. Host names
 * resolve to loopback through setDnsResolver, so no DNS query is made.
 */
class EvmWebSocketHostnameVerificationTest {
    private val fixture = TlsFixture()

    @Test(timeout = TEST_TIMEOUT_MS)
    fun web3jClientRejectsTrustedCertificateIssuedForAnotherHost() {
        TlsWebSocketPeer(fixture.serverContext()).use { peer ->
            val listener = RecordingListener()
            val client = fixture.client(peer.urlFor(OTHER_HOST)).apply { setListener(listener) }
            try {
                assertFalse(client.connectBlocking(CONNECT_TIMEOUT_S, TimeUnit.SECONDS))
            } finally {
                client.close()
            }

            listener.assertHostnameRejected()
            peer.awaitConnectionEnd()
            assertEquals(1, peer.connections.get())
            assertEquals(0, peer.handshakesSucceeded.get())
            // The upgrade request, including its secret-bearing path, never reached the peer.
            assertEquals(0, peer.applicationBytes.get())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun web3jClientConnectsWhenTrustedCertificateNamesDialedHost() {
        TlsWebSocketPeer(fixture.serverContext()).use { peer ->
            val listener = RecordingListener()
            val client = fixture.client(peer.urlFor(CERTIFIED_HOST)).apply { setListener(listener) }
            val errorsBeforeClose: List<Exception>
            try {
                assertTrue(client.connectBlocking(CONNECT_TIMEOUT_S, TimeUnit.SECONDS))
                errorsBeforeClose = listener.errors.toList()
            } finally {
                client.close()
            }

            assertEquals(emptyList<Exception>(), errorsBeforeClose)
            peer.awaitConnectionEnd()
            assertEquals(1, peer.handshakesSucceeded.get())
            assertEquals(listOf("GET $RPC_PATH HTTP/1.1"), peer.requestLines.toList())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun evmConnectionReportsErrorInsteadOfQueryingImpostorNode() {
        TlsWebSocketPeer(fixture.serverContext()).use { peer ->
            val url = peer.urlFor(OTHER_HOST)
            val listener = RecordingListener()
            val statuses = CopyOnWriteArrayList<EvmConnectionStatus>()
            val connection = EVMConnection.WSS(url, fixture.client(url).apply { setListener(listener) }) {
                statuses += it
            }
            val reported: List<EvmConnectionStatus>
            try {
                connection.connect()
                reported = statuses.toList()
            } finally {
                connection.web3j.shutdown()
            }

            assertEquals(2, reported.size)
            assertEquals(EvmConnectionStatus.Connecting(url), reported[0])
            val error = reported[1] as EvmConnectionStatus.Error
            assertEquals(url, error.node)
            assertTrue(error.error.toString(), error.error is ConnectException)
            listener.assertHostnameRejected()
            peer.awaitConnectionEnd()
            assertEquals(0, peer.handshakesSucceeded.get())
            assertEquals(0, peer.applicationBytes.get())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun evmConnectionAnswersJsonRpcWhenCertificateNamesDialedHost() {
        TlsWebSocketPeer(fixture.serverContext()).use { peer ->
            val url = peer.urlFor(CERTIFIED_HOST)
            val statuses = CopyOnWriteArrayList<EvmConnectionStatus>()
            val connection = EVMConnection.WSS(url, fixture.client(url)) { statuses += it }
            try {
                connection.connect()
                assertEquals(
                    listOf(EvmConnectionStatus.Connecting(url), EvmConnectionStatus.Connected(url)),
                    statuses.toList()
                )
                // A second request proves web3j's WebSocketService round trip on the upgraded client.
                assertEquals(BigInteger.valueOf(BLOCK_NUMBER), connection.web3j.ethBlockNumber().send().blockNumber)
            } finally {
                connection.web3j.shutdown()
            }

            peer.awaitConnectionEnd()
            assertEquals(1, peer.handshakesSucceeded.get())
            assertEquals(listOf("GET $RPC_PATH HTTP/1.1"), peer.requestLines.toList())
            assertEquals(2, peer.jsonRpcRequests.get())
        }
    }

    private class RecordingListener : WebSocketListener {
        val errors = CopyOnWriteArrayList<Exception>()
        private val hostnameRejected = CountDownLatch(1)

        override fun onMessage(message: String) = Unit

        override fun onError(e: Exception) {
            errors += e
            if (e is SSLException && e.causedByHostnameMismatch()) hostnameRejected.countDown()
        }

        override fun onClose() = Unit

        fun assertHostnameRejected() {
            assertTrue(
                "client did not reject the certificate for $OTHER_HOST: $errors",
                hostnameRejected.await(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            )
        }

        private fun Throwable.causedByHostnameMismatch(): Boolean = generateSequence(this) { it.cause }.any {
            it is CertificateException &&
                it.message.orEmpty().contains("subject alternative", ignoreCase = true) &&
                it.message.orEmpty().contains(OTHER_HOST)
        }
    }

    /** Per-test ephemeral key and certificate. Nothing is written to disk and no provider is registered. */
    @Suppress("DEPRECATION") // Existing bcprov dependency; no bcpkix or provider registration is required.
    private class TlsFixture {
        private val random = SecureRandom()
        private val keyPassword = "loopback-fixture".toCharArray()
        private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(EC_KEY_BITS, random) }
            .generateKeyPair()
        private val certificate: X509Certificate = run {
            // Fully qualified, not imported, so the class-level DEPRECATION suppression covers it.
            val generated = org.bouncycastle.x509.X509V3CertificateGenerator().apply {
                val subject = X500Principal("CN=$CERTIFIED_HOST,OU=PUBLIC TEST ONLY,O=Fearless Loopback Fixture")
                val now = System.currentTimeMillis()
                setSerialNumber(BigInteger(SERIAL_BITS, random).add(BigInteger.ONE))
                setIssuerDN(subject)
                setSubjectDN(subject)
                setNotBefore(Date(now - TimeUnit.DAYS.toMillis(1)))
                setNotAfter(Date(now + TimeUnit.DAYS.toMillis(2)))
                setPublicKey(pair.public)
                setSignatureAlgorithm("SHA256withECDSA")
                addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    GeneralNames(GeneralName(GeneralName.dNSName, CERTIFIED_HOST))
                )
            }.generate(pair.private, random)
            // Re-parse with the platform factory so the TLS stack only sees JDK certificate and key objects.
            (CertificateFactory.getInstance("X.509").generateCertificate(generated.encoded.inputStream()) as X509Certificate)
                .also {
                    it.checkValidity()
                    it.verify(pair.public)
                }
        }
        private val trustedClientContext: SSLContext = run {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("ephemeral-test-only", certificate)
            }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(store) }
            SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, random) }
        }

        fun serverContext(): SSLContext {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("ephemeral-test-only", pair.private, keyPassword, arrayOf(certificate))
            }
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(store, keyPassword) }
            return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, random) }
        }

        /** The class EthereumConnectionPool uses, with test trust and loopback resolution only. */
        fun client(url: String): WebSocketClient = WebSocketClient(URI(url)).apply {
            setSocketFactory(trustedClientContext.socketFactory)
            setDnsResolver { uri -> InetAddress.getByAddress(uri.host, LOOPBACK.address) }
        }
    }

    /** Loopback TLS WebSocket peer, one connection at a time, answering every JSON-RPC text frame. */
    private class TlsWebSocketPeer(context: SSLContext) : AutoCloseable {
        private val server = context.serverSocketFactory.createServerSocket(0, BACKLOG, LOOPBACK) as SSLServerSocket
        val connections = AtomicInteger()
        val handshakesSucceeded = AtomicInteger()
        val applicationBytes = AtomicInteger()
        val jsonRpcRequests = AtomicInteger()
        val requestLines = CopyOnWriteArrayList<String>()
        private val firstConnectionEnded = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()

        @Volatile private var closing = false

        @Volatile private var current: SSLSocket? = null
        private val thread = Thread(::serve, "evm-wss-test-peer").apply {
            isDaemon = true
            start()
        }

        fun urlFor(host: String) = "wss://$host:${server.localPort}$RPC_PATH"

        fun awaitConnectionEnd() {
            assertTrue("peer connection did not end", firstConnectionEnded.await(PEER_TIMEOUT_S, TimeUnit.SECONDS))
        }

        override fun close() {
            closing = true
            server.close()
            current?.close()
            thread.join(TimeUnit.SECONDS.toMillis(PEER_TIMEOUT_S))
            assertFalse("TLS WebSocket peer did not stop", thread.isAlive)
            failure.get()?.let { throw AssertionError("TLS WebSocket peer failed", it) }
        }

        @Suppress("TooGenericExceptionCaught") // Any fixture failure must fail the test in close().
        private fun serve() {
            try {
                while (!closing) {
                    (server.accept() as SSLSocket).use { socket ->
                        current = socket
                        connections.incrementAndGet()
                        socket.soTimeout = SOCKET_TIMEOUT_MS
                        try {
                            socket.startHandshake()
                            handshakesSucceeded.incrementAndGet()
                            exchange(socket.inputStream, socket.outputStream)
                        } catch (_: IOException) {
                            // Expected when the client rejects the certificate or closes the connection.
                        } finally {
                            current = null
                            firstConnectionEnded.countDown()
                        }
                    }
                }
            } catch (error: IOException) {
                if (!closing) failure.set(error)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        private fun exchange(input: InputStream, output: OutputStream) {
            val head = readUpgradeRequest(input).split("\r\n")
            requestLines += head.first()
            val key = head.firstNotNullOfOrNull { line ->
                line.takeIf { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }?.substringAfter(':')?.trim()
            } ?: throw IOException("upgrade request has no Sec-WebSocket-Key")
            output.write(
                (
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
            )
            output.flush()
            while (true) {
                val (opcode, payload) = readFrame(input) ?: return
                when (opcode) {
                    OPCODE_TEXT -> answer(output, payload.toString(Charsets.UTF_8))
                    OPCODE_PING -> writeFrame(output, OPCODE_PONG, payload)
                    OPCODE_CLOSE -> {
                        writeFrame(output, OPCODE_CLOSE, payload)
                        return
                    }
                }
            }
        }

        private fun readUpgradeRequest(input: InputStream): String {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val next = input.read()
                if (next < 0) throw EOFException("connection closed inside the upgrade request")
                applicationBytes.incrementAndGet()
                head.append(next.toChar())
                check(head.length <= MAX_UPGRADE_REQUEST_BYTES) { "upgrade request is too large" }
            }
            return head.toString()
        }

        private fun answer(output: OutputStream, request: String) {
            val id = JSON_RPC_ID.find(request)?.groupValues?.get(1) ?: throw IOException("no JSON-RPC id: $request")
            jsonRpcRequests.incrementAndGet()
            val reply = """{"jsonrpc":"2.0","id":$id,"result":"0x${BLOCK_NUMBER.toString(HEX_RADIX)}"}"""
            writeFrame(output, OPCODE_TEXT, reply.toByteArray(Charsets.UTF_8))
        }

        private fun readFrame(input: InputStream): Pair<Int, ByteArray>? {
            val first = input.read()
            if (first < 0) return null
            val second = input.readByteOrThrow()
            var length = (second and PAYLOAD_LENGTH_MASK).toLong()
            if (length == EXTENDED_16_BIT) {
                length = ((input.readByteOrThrow() shl Byte.SIZE_BITS) or input.readByteOrThrow()).toLong()
            } else if (length == EXTENDED_64_BIT) {
                length = 0
                repeat(Long.SIZE_BYTES) { length = (length shl Byte.SIZE_BITS) or input.readByteOrThrow().toLong() }
            }
            check(length <= MAX_FRAME_BYTES) { "frame is too large: $length" }
            val mask = if ((second and MASK_BIT) != 0) ByteArray(MASK_BYTES) { input.readByteOrThrow().toByte() } else null
            val payload = ByteArray(length.toInt()) { input.readByteOrThrow().toByte() }
            mask?.let { key -> payload.indices.forEach { payload[it] = (payload[it].toInt() xor key[it % MASK_BYTES].toInt()).toByte() } }
            return (first and OPCODE_MASK) to payload
        }

        private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
            check(payload.size < EXTENDED_16_BIT) { "test frames stay below the extended-length threshold" }
            output.write(byteArrayOf((FIN_BIT or opcode).toByte(), payload.size.toByte()) + payload)
            output.flush()
        }

        private fun InputStream.readByteOrThrow(): Int = read().also { if (it < 0) throw EOFException() }

        private fun acceptKey(key: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII))
        )
    }

    private companion object {
        const val CERTIFIED_HOST = "evm-a.fearless.test"
        const val OTHER_HOST = "evm-b.fearless.test"
        const val RPC_PATH = "/v1/test-api-key-placeholder"
        const val BLOCK_NUMBER = 16L
        const val TEST_TIMEOUT_MS = 120_000L
        const val CONNECT_TIMEOUT_S = 30L
        const val PEER_TIMEOUT_S = 30L
        const val SOCKET_TIMEOUT_MS = 30_000
        const val BACKLOG = 4
        const val EC_KEY_BITS = 256
        const val SERIAL_BITS = 160
        const val HEX_RADIX = 16
        const val MAX_UPGRADE_REQUEST_BYTES = 16_384
        const val MAX_FRAME_BYTES = 65_536L
        const val FIN_BIT = 0x80
        const val MASK_BIT = 0x80
        const val OPCODE_MASK = 0x0f
        const val PAYLOAD_LENGTH_MASK = 0x7f
        const val EXTENDED_16_BIT = 126L
        const val EXTENDED_64_BIT = 127L
        const val MASK_BYTES = 4
        const val OPCODE_TEXT = 0x1
        const val OPCODE_CLOSE = 0x8
        const val OPCODE_PING = 0x9
        const val OPCODE_PONG = 0xA
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val JSON_RPC_ID = Regex(""""id"\s*:\s*(\d+)""")
        val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    }
}
