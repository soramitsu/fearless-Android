package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verify API v2 with synthetic ES256 attestations (raw r||s, made with Node's crypto and a throwaway
 * P-256 key) for the type 0 message of [WcCryptoTest]; the clock is fixed before their expiry.
 */
class WcVerifyTest {
    private var nowMillis = 1_700_000_000_000
    private val clock = object : WcClock {
        override fun nowMillis(): Long = nowMillis
    }
    private val key = WcVerifyPublicKey(WcEncoding.unbase64Url(KEY_X), WcEncoding.unbase64Url(KEY_Y), expiresAtSeconds = 1_900_000_000)
    private var fetches = 0
    private val verifier = WcVerifier({ fetches += 1; key }, clock)

    @Test
    fun `an attested origin equal to the claimed url is valid`() = runBlocking {
        val context = verifier.resolve(VALID, WcCryptoTest.TYPE_0, "https://app.example.org/")

        assertEquals(WalletConnectValidation.VALID, context.validation)
        assertEquals("https://app.example.org", context.verifiedOrigin)
        assertFalse(context.isScam)
    }

    @Test
    fun `an attested origin other than the claimed url is invalid and shows the real origin`() = runBlocking {
        val context = verifier.resolve(VALID, WcCryptoTest.TYPE_0, "https://app.examp1e.org")

        assertEquals(WalletConnectValidation.INVALID, context.validation)
        assertEquals("https://app.example.org", context.verifiedOrigin)
        assertEquals("https://app.examp1e.org", context.claimedUrl)
    }

    @Test
    fun `a scam verdict counts even when the origin is not verified`() = runBlocking {
        val context = verifier.resolve(SCAM, WcCryptoTest.TYPE_0, "https://app.example.org")

        assertTrue(context.isScam)
        assertEquals(WalletConnectValidation.UNKNOWN, context.validation)
        assertEquals("https://drainer.example", context.verifiedOrigin)
    }

    @Test
    fun `unverified, expired, foreign or misbound attestations are unknown`() = runBlocking {
        listOf(UNVERIFIED, EXPIRED, OTHER_ID, OTHER_KEY, "", "a.b", "not-a-jwt").forEach { attestation ->
            val context = verifier.resolve(attestation, WcCryptoTest.TYPE_0, "https://app.example.org")
            assertEquals(attestation, WalletConnectValidation.UNKNOWN, context.validation)
            assertNull(attestation, context.verifiedOrigin)
            assertFalse(attestation, context.isScam)
        }
        assertEquals(WalletConnectValidation.UNKNOWN, verifier.resolve(null, WcCryptoTest.TYPE_0, "https://app.example.org").validation)
    }

    @Test
    fun `an attestation for another message is unknown`() = runBlocking {
        assertEquals(WalletConnectValidation.UNKNOWN, verifier.resolve(VALID, WcCryptoTest.TYPE_1, "https://app.example.org").validation)
    }

    @Test
    fun `the key is fetched once and refreshed at most every ten minutes when a signature fails`() = runBlocking {
        verifier.resolve(VALID, WcCryptoTest.TYPE_0, "https://app.example.org")
        verifier.resolve(VALID, WcCryptoTest.TYPE_0, "https://app.example.org")
        assertEquals(1, fetches)

        verifier.resolve(OTHER_KEY, WcCryptoTest.TYPE_0, "https://app.example.org")
        assertEquals(1, fetches)

        nowMillis += 601_000
        verifier.resolve(OTHER_KEY, WcCryptoTest.TYPE_0, "https://app.example.org")
        assertEquals(2, fetches)
    }

    @Test
    fun `without a key every attestation is unknown`() = runBlocking {
        val offline = WcVerifier({ throw java.io.IOException("offline") }, clock)

        assertEquals(WalletConnectValidation.UNKNOWN, offline.resolve(VALID, WcCryptoTest.TYPE_0, "https://app.example.org").validation)
    }

    @Test
    fun `the published key format is read`() {
        val parsed = WcVerifyPublicKey.parse(
            """{"publicKey":{"crv":"P-256","ext":true,"key_ops":["verify"],"kty":"EC","x":"$KEY_X","y":"$KEY_Y"},"expiresAt":1796639162}"""
        )

        assertEquals(1_796_639_162L, parsed?.expiresAtSeconds)
        assertNull(WcVerifyPublicKey.parse("""{"publicKey":{"kty":"OKP","crv":"Ed25519","x":"$KEY_X"},"expiresAt":1}"""))
        assertNull(WcVerifyPublicKey.parse("""{"publicKey":{"kty":"EC","crv":"P-256","x":"AA","y":"$KEY_Y"},"expiresAt":1}"""))
        assertNull(WcVerifyPublicKey.parse("not json"))
    }

    @Test
    fun `origins are compared in canonical form`() {
        assertEquals("https://app.example.org", WcVerifier.normalizeOrigin("HTTPS://App.Example.org:443/path?q=1"))
        assertEquals("http://localhost:3000", WcVerifier.normalizeOrigin("http://localhost:3000/"))
        assertEquals("http://example.org", WcVerifier.normalizeOrigin("http://example.org:80"))
        assertNull(WcVerifier.normalizeOrigin("app.example.org"))
        assertNull(WcVerifier.normalizeOrigin("https://"))
        assertNull(WcVerifier.normalizeOrigin("ht tp://bad"))
    }

    private companion object {
        const val KEY_X = "PfTxUsfd2MxSWXzRolmZ0ItqKm5qpqNSi9_7EIlZUvU"
        const val KEY_Y = "kBSJYN_YfX9zAT85XXaft_z-27KcDDXYhcmZSdkPKGY"
        const val VALID = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJleHAiOjE4MDAwMDAwMDAsImlkIjoiOTc3M2Y4NTgzZGMwODA4Y2M3NGU1N2VhYTg2NDEwNWJhOTI4OWY2YzlmNDRiN2E1ODcwMzEyNTk1MDY3MmYzMiIs" +
                "Im9yaWdpbiI6Imh0dHBzOi8vYXBwLmV4YW1wbGUub3JnIiwiaXNTY2FtIjpmYWxzZSwiaXNWZXJpZmllZCI6dHJ1ZX0." +
            "LDmzQ-bH_Ud67fhIQcFTsnFdnuDRt6rRlh6F_YNtAd1zz0oyMsmAc0KkYZ7yi4DY2iJFYgbWSwgxJPfSLLLj4A"
        const val SCAM = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJleHAiOjE4MDAwMDAwMDAsImlkIjoiOTc3M2Y4NTgzZGMwODA4Y2M3NGU1N2VhYTg2NDEwNWJhOTI4OWY2YzlmNDRiN2E1ODcwMzEyNTk1MDY3MmYzMiIs" +
                "Im9yaWdpbiI6Imh0dHBzOi8vZHJhaW5lci5leGFtcGxlIiwiaXNTY2FtIjp0cnVlLCJpc1ZlcmlmaWVkIjpmYWxzZX0." +
            "dwB_JoJ5491Vhg2NSd0Jjl_7JjwJXzm8cDzkDHGjW4WIGVQWea_HHqJtO24MqsEro5XlOTMwRjjHkyJmMWu-qA"
        const val UNVERIFIED = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJleHAiOjE4MDAwMDAwMDAsImlkIjoiOTc3M2Y4NTgzZGMwODA4Y2M3NGU1N2VhYTg2NDEwNWJhOTI4OWY2YzlmNDRiN2E1ODcwMzEyNTk1MDY3MmYzMiIs" +
                "Im9yaWdpbiI6Imh0dHBzOi8vYXBwLmV4YW1wbGUub3JnIiwiaXNTY2FtIjpmYWxzZSwiaXNWZXJpZmllZCI6ZmFsc2V9." +
            "oWvMbNDYqTPqcQB0UvFB914SM4g2uDO7CPBGnz_ms8W1Pc4Tfg8mVpxx9_2pWQsj97_mI_GqVFJ6Uga4RTL9bg"
        const val EXPIRED = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJleHAiOjE2MDAwMDAwMDAsImlkIjoiOTc3M2Y4NTgzZGMwODA4Y2M3NGU1N2VhYTg2NDEwNWJhOTI4OWY2YzlmNDRiN2E1ODcwMzEyNTk1MDY3MmYzMiIs" +
                "Im9yaWdpbiI6Imh0dHBzOi8vYXBwLmV4YW1wbGUub3JnIiwiaXNTY2FtIjpmYWxzZSwiaXNWZXJpZmllZCI6dHJ1ZX0." +
            "GxeB8JFniDNgMjldquROTZG3v73ZuPU2we4Ns99L-jmvOKaMdv-VjaKYuq7VR23O8CFaokWG96GhIKKOvmatzA"
        const val OTHER_ID = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJleHAiOjE4MDAwMDAwMDAsImlkIjoiZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZmZiIs" +
                "Im9yaWdpbiI6Imh0dHBzOi8vYXBwLmV4YW1wbGUub3JnIiwiaXNTY2FtIjpmYWxzZSwiaXNWZXJpZmllZCI6dHJ1ZX0." +
            "cXBv7QfO87mT8l4SQOfJVu95oCWVc_HSaRzuqOZX64-Iomo7379FEN66xo2blGybNhfxIMyZTrcEGR-5udzjdw"
        const val OTHER_KEY = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJleHAiOjE4MDAwMDAwMDAsImlkIjoiOTc3M2Y4NTgzZGMwODA4Y2M3NGU1N2VhYTg2NDEwNWJhOTI4OWY2YzlmNDRiN2E1ODcwMzEyNTk1MDY3MmYzMiIs" +
                "Im9yaWdpbiI6Imh0dHBzOi8vYXBwLmV4YW1wbGUub3JnIiwiaXNTY2FtIjpmYWxzZSwiaXNWZXJpZmllZCI6dHJ1ZX0." +
            "Hypez7OKoY43eJ5Y_E4Pu5ymzv5FKdUMANbOwV-fcCVNh4ZYPXa1pb5APVz8hbUpnGHbK8UPl6qBXLkyDgg_Dg"
    }
}
