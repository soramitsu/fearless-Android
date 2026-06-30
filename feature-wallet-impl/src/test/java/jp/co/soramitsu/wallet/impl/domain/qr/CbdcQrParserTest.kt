package jp.co.soramitsu.wallet.impl.domain.qr

import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CbdcQrParserTest {

    @Test
    fun `parses encoded cbdc qr payload`() {
        val content = "https://pay.example/scan?qr=${encode(validPayload())}&source=camera"

        val result = CbdcQrParser.parse(content)

        requireNotNull(result)
        assertEquals(BigDecimal("12.345"), result.transactionAmount)
        assertEquals("840", result.transactionCurrencyCode)
        assertEquals("Order #1", result.description)
        assertEquals("Merchant", result.name)
        assertEquals("BILL-7", result.billNumber)
        assertEquals("sora-account-id", result.recipientId)
    }

    @Test
    fun `returns zero amount when amount is absent`() {
        val content = "https://pay.example/scan?qr=${encode(validPayload(amount = null))}"

        val result = CbdcQrParser.parse(content)

        requireNotNull(result)
        assertEquals(BigDecimal.ZERO, result.transactionAmount)
    }

    @Test
    fun `returns null when qr query parameter is absent`() {
        assertNull(CbdcQrParser.parse("https://pay.example/scan?payload=${encode(validPayload())}"))
    }

    @Test
    fun `returns null for malformed tlv length`() {
        val malformed = "0002015303840549912.345"

        assertNull(CbdcQrParser.parse("https://pay.example/scan?qr=${encode(malformed)}"))
    }

    @Test
    fun `returns null for invalid amount`() {
        val content = "https://pay.example/scan?qr=${encode(validPayload(amount = "12.3.4"))}"

        assertNull(CbdcQrParser.parse(content))
    }

    @Test
    fun `returns null when merchant account id is missing`() {
        val payload = tlv("00", "01") +
            tlv("53", "840") +
            tlv("59", "Merchant") +
            tlv("26", tlv("01", "not-aid"))

        assertNull(CbdcQrParser.parse("https://pay.example/scan?qr=${encode(payload)}"))
    }

    @Test
    fun `returns null for oversized qr payload`() {
        val oversized = "0".repeat(4097)

        assertNull(CbdcQrParser.parse("https://pay.example/scan?qr=$oversized"))
    }

    private fun validPayload(amount: String? = "12.345"): String {
        return listOfNotNull(
            tlv("00", "01"),
            tlv("26", tlv("00", "sora-account-id")),
            tlv("53", "840"),
            amount?.let { tlv("54", it) },
            tlv("59", "Merchant"),
            tlv(
                "62",
                tlv("01", "BILL-7") +
                    tlv("08", "Order #1")
            )
        ).joinToString(separator = "")
    }

    private fun tlv(tag: String, value: String): String {
        return tag + value.length.toString().padStart(2, '0') + value
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
