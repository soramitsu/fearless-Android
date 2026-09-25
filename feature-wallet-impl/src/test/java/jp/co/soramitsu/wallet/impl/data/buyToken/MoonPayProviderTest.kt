package jp.co.soramitsu.wallet.impl.data.buyToken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPayProviderTest {

    @Test
    fun `builds unsigned hosted URL with manual wallet entry`() {
        val url = createMoonPayPurchaseLink(
            host = "buy.moonpay.com",
            publicKey = "pk_live_public",
            currencyCode = "DOT",
            color = "#A1b2C3",
            redirectUrl = "https://fearlesswallet.io"
        )

        assertEquals(
            "https://buy.moonpay.com/?" +
                "apiKey=pk_live_public&" +
                "currencyCode=DOT&" +
                "colorCode=%23A1b2C3&" +
                "showWalletAddressForm=true&" +
                "redirectURL=https%3A%2F%2Ffearlesswallet.io",
            url
        )
        assertFalse(Regex("[?&]walletAddress=", RegexOption.IGNORE_CASE).containsMatchIn(url))
        assertFalse(Regex("[?&]signature=", RegexOption.IGNORE_CASE).containsMatchIn(url))
        assertFalse(url.contains("secret", ignoreCase = true))
    }

    @Test
    fun `supports only the production and sandbox MoonPay hosts`() {
        listOf(
            "buy.moonpay.com.evil.example",
            "BUY.MOONPAY.COM",
            "https://buy.moonpay.com",
            "buy-staging.moonpay.com",
            "buy.moonpay.com/path",
            "buy.moonpay.com\n.evil.example"
        ).forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                validLink(host = host)
            }
        }

        assertTrue(validLink(host = "buy-sandbox.moonpay.com").startsWith("https://buy-sandbox.moonpay.com/?"))
    }

    @Test
    fun `encodes public key and currency query injection as data`() {
        val url = createMoonPayPurchaseLink(
            host = "buy.moonpay.com",
            publicKey = "pk_live_public&signature=attacker",
            currencyCode = "USDC&walletAddress=attacker",
            color = "#123456",
            redirectUrl = "https://fearlesswallet.io/return?source=android&state=safe"
        )

        assertTrue(url.contains("apiKey=pk_live_public%26signature%3Dattacker"))
        assertTrue(url.contains("currencyCode=USDC%26walletAddress%3Dattacker"))
        assertTrue(url.contains("redirectURL=https%3A%2F%2Ffearlesswallet.io%2Freturn%3Fsource%3Dandroid%26state%3Dsafe"))
        assertFalse(url.contains("&signature="))
        assertFalse(url.contains("&walletAddress="))
    }

    @Test
    fun `rejects missing oversized and control-bearing public keys`() {
        listOf("", "   ", "pk_live_bad\nkey", "x".repeat(257)).forEach { publicKey ->
            assertThrows(IllegalArgumentException::class.java) {
                validLink(publicKey = publicKey)
            }
        }
    }

    @Test
    fun `rejects malformed currency color and redirect values`() {
        listOf("", " ", "DOT\rINJECT", "X".repeat(65)).forEach { currency ->
            assertThrows(IllegalArgumentException::class.java) {
                validLink(currencyCode = currency)
            }
        }
        listOf("123456", "#12345", "#12345Z", "#123456789").forEach { color ->
            assertThrows(IllegalArgumentException::class.java) {
                validLink(color = color)
            }
        }
        listOf("", "http://fearlesswallet.io", "https://fearlesswallet.io\n.evil.example", "x".repeat(2049)).forEach { redirect ->
            assertThrows(IllegalArgumentException::class.java) {
                validLink(redirectUrl = redirect)
            }
        }
    }

    private fun validLink(
        host: String = "buy.moonpay.com",
        publicKey: String = "pk_live_public",
        currencyCode: String = "DOT",
        color: String = "#123456",
        redirectUrl: String = "https://fearlesswallet.io"
    ): String = createMoonPayPurchaseLink(
        host = host,
        publicKey = publicKey,
        currencyCode = currencyCode,
        color = color,
        redirectUrl = redirectUrl
    )
}
