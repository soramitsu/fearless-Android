@file:Suppress("PackageNaming") // Preserve the existing runtime multiNetwork package.

package jp.co.soramitsu.runtime.multiNetwork.connection

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Locale

class DwellirEndpointCredentialsTest {
    @Test
    fun canonicalProviderOriginsRoundTripWithoutChangingAnyOriginalByte() {
        listOf(
            BASE, "wss://hydration-rpc.n.dwellir.com", "WSS://API-POLKADOT.DWELLIR.COM",
            "wss://api-polkadot.dwellir.com:443"
        ).forEach { url ->
            val decorated = DwellirEndpointCredentials.fill(url, KEY)
            assertEquals("$url/$KEY", decorated)
            assertEquals(url, DwellirEndpointCredentials.clear(decorated, KEY))
        }
    }

    @Test
    fun everyDwellirEndpointInPinnedPublicCatalogRemainsCompatible() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/portfolio/chains-v13-20261009.json")).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
        assertEquals(CATALOG_SHA256, digest)
        val urls = mutableListOf<String>()
        collectProviderUrls(JsonParser.parseString(bytes.toString(Charsets.UTF_8)), urls)
        assertEquals(44, urls.size)
        assertEquals(44, urls.toSet().size)
        assertTrue("wss://hydration-rpc.n.dwellir.com" in urls)
        urls.forEach { url ->
            assertEquals("$url/$KEY", DwellirEndpointCredentials.fill(url, KEY))
            assertEquals(url, DwellirEndpointCredentials.clear("$url/$KEY", KEY))
        }
    }

    @Test
    fun lookalikeHostsAndProviderWordsInUnrelatedPathsNeverReceiveOrLoseCredentials() {
        listOf(
            "wss://dwellir.evil.invalid", "wss://api.dwellir.com.evil.invalid", "wss://notdwellir.com",
            "wss://dwellir.com", "wss://evil.invalid/dwellir", "wss://evil.invalid/$KEY",
            "wss://evil.invalid/?provider=dwellir", "wss://evil.invalid/#dwellir"
        ).forEach(::unchanged)
    }

    @Test
    fun anyUserinfoRejectsDecorationAndRemovalEvenWhenTheParsedHostIsTheProvider() {
        listOf(
            "wss://dwellir.com@evil.invalid", "wss://user@api.dwellir.com", "wss://user:pass@api.dwellir.com",
            "wss://@api.dwellir.com", "wss://user@api.dwellir.com/$KEY", "wss://api.dwellir.com@evil.invalid/$KEY"
        ).forEach(::unchanged)
    }

    @Test
    fun insecureOrNonWebsocketSchemesAndOpaqueUrlsAreUnchanged() {
        listOf(
            "ws://api.dwellir.com", "http://api.dwellir.com", "https://api.dwellir.com",
            "file://api.dwellir.com", "wss:api.dwellir.com", "//api.dwellir.com", "api.dwellir.com"
        ).forEach(::unchanged)
    }

    @Test
    fun noncanonicalPortsAndMalformedAuthoritiesAreUnchanged() {
        listOf(":80", ":444", ":0", ":-1", ":65536", ":0443", ":+443", ":", ":443:443").forEach { suffix ->
            unchanged("$BASE$suffix")
            unchanged("$BASE$suffix/$KEY")
        }
    }

    @Test
    fun queriesAndFragmentsIncludingEmptyOnesAreNeverRewritten() {
        listOf("?", "?api_key=dwellir", "#", "#dwellir", "/$KEY?", "/$KEY?x=1", "/$KEY#x").forEach { unchanged(BASE + it) }
    }

    @Test
    fun trailingSlashCustomPathsAndEncodedOrRepeatedSegmentsRemainByteExact() {
        listOf(
            "/", "//", "/rpc", "/rpc/dwellir", "/./", "/../", "/%2e", "/%2F$KEY", "/%50UBLIC_KEY-123",
            "//$KEY", "/$KEY/", "/prefix/$KEY", "/$KEY/extra"
        ).forEach { unchanged(BASE + it) }
    }

    @Test
    fun malformedUnicodeEscapedAndWhitespaceAuthoritiesRemainByteExact() {
        listOf(
            "wss://a..dwellir.com", "wss://-a.dwellir.com", "wss://a-.dwellir.com", "wss://a_b.dwellir.com",
            "wss://api.dwellir.com.", "wss://api.dwellir.com%2eevil.invalid", "wss://api%2edwellir.com",
            "wss://api.dwellir.com\\@evil.invalid", "wss://[::1]", "wss://雪.dwellir.com", "wss://api.dwellir.com\u0000",
            " $BASE", "$BASE ", "$BASE\r\n", "wss://", "", "wss://api.dwellir.com/%"
        ).forEach(::unchanged)
    }

    @Test
    fun emptyOrUnsafeKeysNeverAlterEitherAPlainOrCredentialBearingUrl() {
        listOf(
            "", ".", "..", "bad/key", "key?x", "key#x", "key%2F", "key@host", "key:value", "key=value",
            "key value", " key", "key\n", "雪", "A".repeat(513)
        ).forEach { key ->
            listOf(BASE, "$BASE/", "$BASE/$KEY", "wss://evil.invalid/$KEY").forEach { url ->
                assertEquals(url, DwellirEndpointCredentials.fill(url, key))
                assertEquals(url, DwellirEndpointCredentials.clear(url, key))
            }
        }
    }

    @Test
    fun unreservedKeyBytesArePreservedAndTheLengthBoundaryIsExplicit() {
        listOf("A", "public.fixture-key_123~", "A".repeat(512)).forEach { key ->
            assertEquals("$BASE/$key", DwellirEndpointCredentials.fill(BASE, key))
            assertEquals(BASE, DwellirEndpointCredentials.clear("$BASE/$key", key))
        }
    }

    @Test
    fun repeatedDecorationIsIdempotentAndRemovalIsCaseSensitiveAndExact() {
        val decorated = "$BASE/$KEY"
        assertEquals(decorated, DwellirEndpointCredentials.fill(decorated, KEY))
        assertEquals(BASE, DwellirEndpointCredentials.clear(decorated, KEY))
        assertEquals(BASE, DwellirEndpointCredentials.clear(BASE, KEY))
        assertEquals(decorated, DwellirEndpointCredentials.clear(decorated, KEY.lowercase(Locale.ROOT)))
        assertEquals(decorated, DwellirEndpointCredentials.clear(decorated, "PUBLIC_KEY"))
        assertEquals("$BASE/${KEY.lowercase(Locale.ROOT)}", DwellirEndpointCredentials.clear("$BASE/${KEY.lowercase(Locale.ROOT)}", KEY))
    }

    @Test
    fun providerMatchingDoesNotDependOnTurkishDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val url = "WSS://API-POLKADOT.DWELLIR.COM"
            assertEquals("$url/$KEY", DwellirEndpointCredentials.fill(url, KEY))
            assertEquals(url, DwellirEndpointCredentials.clear("$url/$KEY", KEY))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun dnsLabelAndTotalHostnameBoundsRejectAmbiguousOrOverlongOrigins() {
        val accepted = "wss://${"a".repeat(63)}.dwellir.com"
        assertEquals("$accepted/$KEY", DwellirEndpointCredentials.fill(accepted, KEY))
        unchanged("wss://${"a".repeat(64)}.dwellir.com")
        unchanged("wss://${List(4) { "a".repeat(63) }.joinToString(".")}.dwellir.com")
        unchanged(BASE + "/" + "a".repeat(8192))
    }

    private fun collectProviderUrls(value: JsonElement, urls: MutableList<String>) {
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (name, child) ->
                collectMember(name, child, urls)
            }
            value.isJsonArray -> value.asJsonArray.forEach { collectProviderUrls(it, urls) }
        }
    }

    private fun collectMember(
        name: String,
        value: JsonElement,
        urls: MutableList<String>
    ) {
        if (name == "url" && isProviderUrl(value)) {
            urls += value.asString
        }
        collectProviderUrls(value, urls)
    }

    private fun isProviderUrl(value: JsonElement): Boolean {
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return false
        return value.asString.lowercase(Locale.ROOT).contains("dwellir")
    }

    private fun unchanged(url: String) {
        assertEquals(url, DwellirEndpointCredentials.fill(url, KEY))
        assertEquals(url, DwellirEndpointCredentials.clear(url, KEY))
    }

    private companion object {
        const val BASE = "wss://api-polkadot.dwellir.com"
        const val KEY = "PUBLIC_KEY-123"
        const val CATALOG_SHA256 = "fa6f0e23cd87dfb3e5536980591dd22ae30e49bf59d04dc2a0b31fc53237ea66"
    }
}
