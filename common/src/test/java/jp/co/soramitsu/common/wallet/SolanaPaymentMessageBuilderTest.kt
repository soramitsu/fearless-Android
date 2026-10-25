package jp.co.soramitsu.common.wallet

import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.SolanaPaymentMessageBuilder
import jp.co.soramitsu.common.data.network.solana.SolanaPriorityFee
import jp.co.soramitsu.common.data.network.solana.SolanaTokenDebit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class SolanaPaymentMessageBuilderTest {
    @Test
    fun `native priority and SPL ATA multi-account full-u64 bytes match two independent official SDKs`() {
        val root = JsonParser.parseString(requireNotNull(javaClass.getResource("/solana/payment-sdk-reference.json")).readText()).asJsonObject
        assertEquals("1.98.4", root["web3"].asString)
        assertEquals("0.4.14", root["splToken"].asString)
        for (element in root["vectors"].asJsonArray) {
            val v = element.asJsonObject
            val priority = SolanaPriorityFee(v["limit"].asInt, v["microLamports"].asLong)
            val result = if (v["name"].asString == "native") SolanaPaymentMessageBuilder.native(
                v["sender"].asString, v["recipient"].asString, v["amount"].asLong, v["blockhash"].asString, priority
            ) else SolanaPaymentMessageBuilder.token(v["sender"].asString, v["recipient"].asString, v["mint"].asString,
                v["debits"].asJsonArray.map { row -> SolanaTokenDebit(row.asJsonObject["account"].asString, BigInteger(row.asJsonObject["amount"].asString)) },
                v["decimals"].asInt, v["create"].asBoolean, v["blockhash"].asString, priority)
            assertEquals(v["name"].asString, v["messageBase64"].asString, result.messageBase64)
            assertEquals(v["name"].asString, v["transactionBase64"].asString, result.transactionBase64)
            assertEquals(1, Base64.getDecoder().decode(result.transactionBase64)[0].toInt())
        }
    }

    @Test
    fun `priority estimate uses bounded median with small floor and fee ceiling`() {
        assertEquals(SolanaPriorityFee(10000, 1000), SolanaPriorityFee.fromSamples(emptyList(), false))
        assertEquals(SolanaPriorityFee(200000, 2000), SolanaPriorityFee.fromSamples(listOf(10, 2000, 1000000), true))
        assertEquals(100000L, SolanaPriorityFee.fromSamples(listOf(Long.MAX_VALUE), false).microLamportsPerUnit)
        assertThrows(IllegalArgumentException::class.java) { SolanaPriorityFee.fromSamples(listOf(-1), false) }
        assertThrows(IllegalArgumentException::class.java) { SolanaPriorityFee.fromSamples(List(257) { 1 }, false) }
        assertThrows(IllegalArgumentException::class.java) { SolanaPriorityFee(200001, 1000) }
    }

    @Test
    fun `unsupported amount and duplicate sources fail before signing`() {
        val root = JsonParser.parseString(requireNotNull(javaClass.getResource("/solana/payment-sdk-reference.json")).readText()).asJsonObject
        val v = root["vectors"].asJsonArray[1].asJsonObject
        val source = v["debits"].asJsonArray[0].asJsonObject["account"].asString
        for (debits in listOf(emptyList(), listOf(SolanaTokenDebit(source, BigInteger.ZERO)),
            listOf(SolanaTokenDebit(source, BigInteger.ONE.shiftLeft(64))), List(2) { SolanaTokenDebit(source, BigInteger.ONE) })) {
            assertThrows(IllegalArgumentException::class.java) { SolanaPaymentMessageBuilder.token(v["sender"].asString, v["recipient"].asString,
                v["mint"].asString, debits, 6, true, v["blockhash"].asString, SolanaPriorityFee(200000, 1000)) }
        }
        assertThrows(IllegalArgumentException::class.java) { SolanaPaymentMessageBuilder.native(v["sender"].asString,
            v["recipient"].asString, 0, v["blockhash"].asString, SolanaPriorityFee(10000, 1000)) }
    }
}
