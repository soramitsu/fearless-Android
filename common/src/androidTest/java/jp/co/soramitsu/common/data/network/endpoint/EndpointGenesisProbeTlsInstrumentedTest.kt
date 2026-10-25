package jp.co.soramitsu.common.data.network.endpoint

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.x509.X509V3CertificateGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Default-probe trust rejection and a separate Android TLS-provider hostname control.
 * The latter uses a test-local trust context, never SSLContext.setDefault or production trust.
 * No successful production-default trusted TLS or Android WSS hostname-isolation claim.
 */
class EndpointGenesisProbeTlsInstrumentedTest {
    private val substrate = "0x" + "12".repeat(32)
    private val solana = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Before fun requireIsolatedCommonTestUidAndSupportedApi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("jp.co.soramitsu.common.test", instrumentation.context.packageName)
        assertEquals(instrumentation.context.applicationInfo.uid, instrumentation.targetContext.applicationInfo.uid)
        assertTrue(Build.VERSION.SDK_INT in setOf(26, 34, 36))
    }

    @Test fun defaultHttpsProbeRejectsPublicUntrustedCertificateBeforeApplicationRequest() = defaultProbeRejects(false)

    @Test fun defaultWssProbeRejectsPublicUntrustedCertificateBeforeApplicationRequest() = defaultProbeRejects(true)

    @Test fun testLocalAndroidTlsProviderChecksMatchingAndWrongHostnamesWithoutChangingDefaultTrust() {
        val defaultContext = SSLContext.getDefault()
        val defaultFactory = SSLSocketFactory.getDefault()
        val providers = Security.getProviders()
        val fixture = TlsFixture()
        val client = fixture.trustedClient()
        for (hostname in listOf("localhost", "wrong.invalid")) {
            val peer = TlsPeer(fixture.serverContext())
            try {
                Socket().use { raw ->
                    // Only this loopback address is connected. The logical peer name does not trigger DNS.
                    raw.connect(InetSocketAddress(loopback, peer.port), 3_000)
                    (client.socketFactory.createSocket(raw, hostname, peer.port, true) as SSLSocket).use { tls ->
                        tls.soTimeout = 3_000
                        tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                        if (hostname == "localhost") {
                            tls.startHandshake()
                            tls.getOutputStream().write(42); tls.getOutputStream().flush()
                            assertTrue(peer.completed.await(5, TimeUnit.SECONDS))
                            assertEquals(1, peer.handshakesSucceeded.get()); assertEquals(1, peer.applicationBytes.get())
                        } else {
                            assertThrows(SSLException::class.java) { tls.startHandshake() }
                            assertTrue(peer.completed.await(5, TimeUnit.SECONDS))
                            assertEquals(0, peer.applicationBytes.get())
                        }
                    }
                }
            } finally { peer.close() }
            assertEquals(1, peer.connections.get())
        }
        assertArrayEquals(providers, Security.getProviders())
        assertSame(defaultContext, SSLContext.getDefault())
        assertEquals(defaultFactory.javaClass, SSLSocketFactory.getDefault().javaClass)
    }

    private fun defaultProbeRejects(ws: Boolean) {
        val defaultContext = SSLContext.getDefault()
        val defaultFactory = SSLSocketFactory.getDefault()
        val providers = Security.getProviders()
        val fixture = TlsFixture()
        val peer = TlsPeer(fixture.serverContext())
        try {
            val outcome = runBlocking {
                EndpointGenesisProbe().probe(EndpointProbeRequest(
                    if (ws) EndpointProbeFamily.SUBSTRATE else EndpointProbeFamily.SOLANA,
                    "${if (ws) "wss" else "https"}://127.0.0.1:${peer.port}/", if (ws) substrate else solana
                ))
            }
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, outcome)
            assertTrue(peer.completed.await(5, TimeUnit.SECONDS))
        } finally { peer.close() }
        assertEquals(1, peer.connections.get())
        assertEquals(0, peer.applicationBytes.get())
        assertArrayEquals(providers, Security.getProviders())
        assertSame(defaultContext, SSLContext.getDefault())
        assertEquals(defaultFactory.javaClass, SSLSocketFactory.getDefault().javaClass)
    }

    /** Per-invocation ephemeral fixture. No key bytes, keystore or certificate are written anywhere. */
    @Suppress("DEPRECATION") // Existing bcprov dependency; no bcpkix or provider registration is required.
    private class TlsFixture {
        private val random = SecureRandom()
        private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()
        val certificate: X509Certificate = X509V3CertificateGenerator().apply {
            val subject = X500Principal("CN=localhost,OU=PUBLIC TEST ONLY,O=Fearless Loopback Fixture")
            val now = System.currentTimeMillis()
            setSerialNumber(BigInteger(160, random).add(BigInteger.ONE))
            setIssuerDN(subject); setSubjectDN(subject)
            setNotBefore(Date(now - TimeUnit.DAYS.toMillis(1)))
            setNotAfter(Date(now + TimeUnit.DAYS.toMillis(2)))
            setPublicKey(pair.public); setSignatureAlgorithm("SHA256withRSA")
            addExtension(Extension.subjectAlternativeName, false, GeneralNames(arrayOf(
                GeneralName(GeneralName.dNSName, "localhost"), GeneralName(GeneralName.iPAddress, "127.0.0.1")
            )))
        }.generate(pair.private, random).also { it.checkValidity(); it.verify(pair.public) }

        fun serverContext(): SSLContext {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null); setKeyEntry("ephemeral-test-only", pair.private, CharArray(0), arrayOf(certificate))
            }
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
            return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        }

        fun trustedClient(): SSLContext {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null); setCertificateEntry("ephemeral-test-only", certificate)
            }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            return SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        }
    }

    private inner class TlsPeer(context: SSLContext) : AutoCloseable {
        private val server = context.serverSocketFactory.createServerSocket(0, 4, loopback) as SSLServerSocket
        val port = server.localPort
        val connections = AtomicInteger()
        val handshakesSucceeded = AtomicInteger()
        val applicationBytes = AtomicInteger()
        val completed = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var closing = false
        @Volatile private var socket: SSLSocket? = null
        private val thread = Thread {
            try {
                while (!closing) {
                    (server.accept() as SSLSocket).use { accepted ->
                        socket = accepted; connections.incrementAndGet(); accepted.soTimeout = 5_000
                        try {
                            accepted.startHandshake(); handshakesSucceeded.incrementAndGet()
                            if (accepted.getInputStream().read() >= 0) applicationBytes.incrementAndGet()
                        } catch (_: IOException) { /* Expected when the client rejects the public fixture certificate/name. */ }
                        finally { completed.countDown(); socket = null }
                    }
                }
            } catch (error: IOException) { if (!closing) failure.set(error) }
            catch (error: Throwable) { failure.set(error) }
        }.apply { isDaemon = true; start() }
        override fun close() {
            closing = true; server.close(); socket?.close(); thread.join(5_000)
            assertFalse("TLS loopback peer did not terminate", thread.isAlive)
            failure.get()?.let { throw AssertionError("TLS loopback fixture failed", it) }
        }
    }
}
