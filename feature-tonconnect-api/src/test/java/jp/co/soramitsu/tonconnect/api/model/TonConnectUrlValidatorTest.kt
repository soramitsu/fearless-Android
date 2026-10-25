package jp.co.soramitsu.tonconnect.api.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonConnectUrlValidatorTest {

    @Test
    fun `normalizeManifestUrl should normalize host and trailing slash`() {
        val normalized = TonConnectUrlValidator.normalizeManifestUrl("https://Example.com/tonconnect-manifest.json/")

        assertEquals("https://example.com/tonconnect-manifest.json", normalized)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeManifestUrl should reject localhost`() {
        TonConnectUrlValidator.normalizeManifestUrl("https://localhost/tonconnect-manifest.json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeManifestUrl should reject loopback ipv6`() {
        TonConnectUrlValidator.normalizeManifestUrl("https://[::1]/tonconnect-manifest.json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeManifestUrl should reject localhost subdomain`() {
        TonConnectUrlValidator.normalizeManifestUrl("https://wallet.localhost/tonconnect-manifest.json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeManifestUrl should reject decimal loopback host`() {
        TonConnectUrlValidator.normalizeManifestUrl("https://2130706433/tonconnect-manifest.json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeManifestUrl should reject short loopback host`() {
        TonConnectUrlValidator.normalizeManifestUrl("https://127.1/tonconnect-manifest.json")
    }

    @Test
    fun `isSameOrigin should compare host and port`() {
        assertTrue(
            TonConnectUrlValidator.isSameOrigin(
                "https://app.example.com/path",
                "https://app.example.com/another"
            )
        )

        assertFalse(
            TonConnectUrlValidator.isSameOrigin(
                "https://app.example.com/path",
                "https://cdn.example.com/path"
            )
        )
    }

    @Test
    fun `validateTonApiFetchUrl should allow tonapi host and normalize`() {
        val normalized = TonConnectUrlValidator.validateTonApiFetchUrl("https://API.tonapi.io/v2/rates/?tokens=TON")

        assertEquals("https://api.tonapi.io/v2/rates?tokens=TON", normalized)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateTonApiFetchUrl should reject non tonapi host`() {
        TonConnectUrlValidator.validateTonApiFetchUrl("https://example.com/v2/rates")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateTonApiFetchUrl should reject non default port`() {
        TonConnectUrlValidator.validateTonApiFetchUrl("https://tonapi.io:8443/v2/rates")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateTonApiFetchUrl should reject userinfo`() {
        TonConnectUrlValidator.validateTonApiFetchUrl("https://user@tonapi.io/v2/rates")
    }
}
