package jp.co.soramitsu.common.wallet

import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.SolanaPdaCurve
import jp.co.soramitsu.common.data.network.solana.SolanaTokenProgram
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaPdaCurveTest {
    @Test
    fun `curve membership matches the Solana dalek oracle including noncanonical and sign edge cases`() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/solana/curve-dalek-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val vectors = fixture["vectors"].asJsonArray
        assertEquals(523, vectors.size())
        vectors.forEach { item ->
            val vector = item.asJsonObject
            val hex = vector["hex"].asString
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertEquals(hex, vector["onCurve"].asBoolean, SolanaPdaCurve.isDecompressible(bytes))
        }
    }

    @Test
    fun `PDA membership includes small order and reduced field encodings unlike signature validation`() {
        assertTrue(SolanaPdaCurve.isDecompressible(ByteArray(32)))
        assertTrue(SolanaPdaCurve.isDecompressible(ByteArray(32).apply { this[0] = 1 }))
        assertTrue(SolanaPdaCurve.isDecompressible(ByteArray(32).apply { this[0] = 1; this[31] = 0x80.toByte() }))
        // y=p reduces to zero, matching Dalek's field decoder.
        assertTrue(SolanaPdaCurve.isDecompressible(ByteArray(32) { 0xff.toByte() }.apply {
            this[0] = 0xed.toByte(); this[31] = 0x7f
        }))
        assertFalse(SolanaPdaCurve.isDecompressible(ByteArray(31)))
        assertFalse(SolanaPdaCurve.isDecompressible(ByteArray(33)))
    }

    @Test
    fun `all public owners and token programs derive exact independent web3 associated addresses`() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/solana/ata-web3-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val vectors = fixture["vectors"].asJsonArray
        assertEquals(64, vectors.size())
        vectors.forEach { item ->
            val vector = item.asJsonObject
            val program = if (vector["tokenProgram"].asString == SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS) {
                SolanaTokenProgram.SplToken
            } else {
                SolanaTokenProgram.Token2022
            }
            assertEquals(vector["owner"].asString, vector["address"].asString,
                SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
                    vector["owner"].asString, vector["mint"].asString, program))
        }
    }
}
