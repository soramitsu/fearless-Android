package jp.co.soramitsu.tonconnect.api.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
class TonConnectLinkTest {

    @Test
    fun universalLinkFromTheWalletsListIsParsed() {
        val link = TonConnectLink.parse(link(TonConnectLink.UNIVERSAL_LINK))

        assertEquals(CLIENT_ID, link.clientId)
        assertEquals("https://dapp.example/tonconnect-manifest.json", link.request.manifestUrl)
        assertEquals(listOf(ConnectRequest.Item.TonAddress, ConnectRequest.Item.TonProof(PROOF)), link.request.items)
        assertEquals(PROOF, link.request.proofPayload)
    }

    @Test
    fun tcLinkIsParsedTheSameWay() {
        assertEquals(TonConnectLink.parse(link(TonConnectLink.UNIVERSAL_LINK)), TonConnectLink.parse(link("tc://")))
    }

    @Test
    fun returnStrategyAndUnknownParametersAreIgnored() {
        val withExtras = link("tc://", extra = "&ret=none&trace_id=0193c2c4-5f4a-7000-8000-000000000000#fragment")
        val withBack = link(TonConnectLink.UNIVERSAL_LINK, extra = "&ret=back")
        val withUrl = link(TonConnectLink.UNIVERSAL_LINK, extra = "&ret=${form("https://dapp.example/return")}")

        listOf(withExtras, withBack, withUrl).forEach { value ->
            assertEquals(value, CLIENT_ID, TonConnectLink.parse(value).clientId)
        }
    }

    @Test
    fun theQueryIsFormDecodedLikeUrlSearchParamsWritesIt() {
        // URLSearchParams writes a space as `+` and a `+` as `%2B`.
        val request = """{"manifestUrl":"https://dapp.example/tonconnect-manifest.json","items":[{"name":"ton_addr"},""" +
            """{"name":"ton_proof","payload":"a+b c"}]}"""
        val value = "tc://?v=2&id=$CLIENT_ID&r=${form(request)}"
        assertTrue(value, value.contains("a%2Bb+c"))

        assertEquals("a+b c", TonConnectLink.parse(value).request.proofPayload)
    }

    @Test
    fun schemeAndHostCompareCaseInsensitively() {
        listOf("TC://", "Tc://", "HTTPS://FearlessWallet.io/ton-connect/").forEach { base ->
            assertEquals(base, CLIENT_ID, TonConnectLink.parse(link(base)).clientId)
        }
        assertEquals(CLIENT_ID.uppercase(), TonConnectLink.parse(link("tc://", clientId = CLIENT_ID.uppercase())).clientId)
    }

    @Test
    fun anythingButTheTwoLinkBasesIsRejected() {
        listOf(
            "https://fearlesswallet.io/ton-connect",
            "https://fearlesswallet.io/ton-connect/v2",
            "https://fearlesswallet.io/ton-connect//",
            "https://fearlesswallet.io/",
            "http://fearlesswallet.io/ton-connect/",
            "https://fearlesswallet.io:8443/ton-connect/",
            "https://user@fearlesswallet.io/ton-connect/",
            "https://fearlesswallet.io.attacker.example/ton-connect/",
            "https://attacker.example/ton-connect/",
            "https://app.tonkeeper.com/ton-connect",
            "tc://connect",
            "tc:",
            "tc:/",
            "tcp://",
            "wc://"
        ).forEach { base ->
            assertThrows(base, TonConnectException.RequestParsingError::class.java) { TonConnectLink.parse(link(base)) }
        }
        assertThrows(TonConnectException.RequestParsingError::class.java) { TonConnectLink.parse("tc://") }
        assertThrows(TonConnectException.RequestParsingError::class.java) { TonConnectLink.parse("") }
    }

    @Test
    fun onlyProtocolVersionTwoIsAccepted() {
        assertEquals(TonConnectException.UnsupportedVersion(-1), versionError(""))
        assertEquals(TonConnectException.UnsupportedVersion(1), versionError("&v=1"))
        assertEquals(TonConnectException.UnsupportedVersion(3), versionError("&v=3"))
        assertEquals(TonConnectException.UnsupportedVersion(-1), versionError("&v=2.0"))
        assertEquals(TonConnectException.UnsupportedVersion(-1), versionError("&v="))
        assertThrows(TonConnectException.RequestParsingError::class.java) {
            TonConnectLink.parse(link("tc://", extra = "&v=2"))
        }
    }

    @Test
    fun theClientIdMustBeOne32ByteHexKey() {
        listOf(null, "", "a".repeat(63), "a".repeat(65), "g".repeat(64), " ${"a".repeat(63)}").forEach { clientId ->
            val error = assertThrows(clientId.toString(), TonConnectException.WrongClientId::class.java) {
                TonConnectLink.parse(link("tc://", clientId = clientId))
            }
            assertEquals(clientId, error.clientId)
        }
        assertThrows(TonConnectException.RequestParsingError::class.java) {
            TonConnectLink.parse(link("tc://", extra = "&id=${"b".repeat(64)}"))
        }
    }

    @Test
    fun theRequestMustBeAParseableConnectRequestWithItems() {
        listOf(
            null,
            "",
            "not json",
            """{"items":[{"name":"ton_addr"}]}""",
            """{"manifestUrl":"http://dapp.example/manifest.json","items":[{"name":"ton_addr"}]}""",
            """{"manifestUrl":"https://localhost/manifest.json","items":[{"name":"ton_addr"}]}""",
            """{"manifestUrl":"https://dapp.example/manifest.json"}""",
            """{"manifestUrl":"https://dapp.example/manifest.json","items":[]}""",
            """{"manifestUrl":"https://dapp.example/manifest.json","items":[{"name":"unknown"}]}"""
        ).forEach { request ->
            assertThrows(request.toString(), TonConnectException.RequestParsingError::class.java) {
                TonConnectLink.parse(link("tc://", request = request))
            }
        }
        assertThrows(TonConnectException.RequestParsingError::class.java) {
            TonConnectLink.parse(link("tc://", extra = "&r=${form(REQUEST)}"))
        }
    }

    @Test
    fun malformedPercentEncodingIsRejected() {
        assertThrows(TonConnectException.RequestParsingError::class.java) {
            TonConnectLink.parse("tc://?v=2&id=$CLIENT_ID&r=%7B%zz")
        }
    }

    @Test
    fun candidatesAreTheTcSchemeAndTheUniversalLinkPath() {
        listOf(
            "tc://?v=2",
            "TC:anything",
            " tc://",
            "https://fearlesswallet.io/ton-connect/?v=2",
            "https://fearlesswallet.io/ton-connect",
            "HTTPS://FEARLESSWALLET.IO/TON-CONNECT/x"
        ).forEach { assertTrue(it, TonConnectLink.isCandidate(it)) }

        listOf(
            "",
            "tcp://example.com",
            "wc:abc@2?relay-protocol=irn",
            "https://fearlesswallet.io/",
            "https://example.com/ton-connect/",
            "ton://transfer/EQD4FPq-PRDieyQKkizFTRtSDyucUIqrj0v_zXJmqaDp6_0t",
            "EQD4FPq-PRDieyQKkizFTRtSDyucUIqrj0v_zXJmqaDp6_0t",
            "UQD4FPq-PRDieyQKkizFTRtSDyucUIqrj0v_zXJmqaDp6_0t",
            "0x742d35Cc6634C0532925a3b844Bc454e4438f44e"
        ).forEach { assertFalse(it, TonConnectLink.isCandidate(it)) }
    }

    private fun versionError(versionParameter: String): TonConnectException.UnsupportedVersion {
        return assertThrows(TonConnectException.UnsupportedVersion::class.java) {
            TonConnectLink.parse("tc://?id=$CLIENT_ID&r=${form(REQUEST)}$versionParameter")
        }
    }

    private fun link(
        base: String,
        clientId: String? = CLIENT_ID,
        request: String? = REQUEST,
        extra: String = ""
    ): String {
        val params = buildList {
            add("v=2")
            clientId?.let { add("id=${form(it)}") }
            request?.let { add("r=${form(it)}") }
        }
        return base + "?" + params.joinToString("&") + extra
    }

    private fun form(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val CLIENT_ID = "230f1e4df32364888a5dbd92a410266fcb974b73e30ff3e546a654fc8ee2c953"
        const val PROOF = "a3f1c9e0b7d24c58"
        const val REQUEST = """{"manifestUrl":"https://dapp.example/tonconnect-manifest.json",""" +
            """"items":[{"name":"ton_addr"},{"name":"ton_proof","payload":"$PROOF"}]}"""
    }
}
