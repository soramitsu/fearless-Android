package jp.co.soramitsu.common.wallet

import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.SolanaRpcAccountParser
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class SolanaRpcAccountParserTest {
    @Test
    fun `account owner executable and bounded binary data remain exact`() {
        val parsed = SolanaRpcAccountParser.account(JsonParser.parseString("""{
            "owner":"$SYSTEM", "lamports":890880, "executable":false, "data":["AQID", "base64"]
        }"""))!!
        assertEquals(SYSTEM, parsed.ownerProgram)
        assertEquals(890880L, parsed.lamports)
        assertEquals(false, parsed.executable)
        assertEquals("AQID", parsed.dataBase64)
        assertEquals(null, SolanaRpcAccountParser.account(JsonParser.parseString("null")))
        for (bad in listOf("{\"owner\":\"bad\"}", """{"owner":"$SYSTEM","lamports":-1,"executable":false,"data":["", "base64"]}""",
            """{"owner":"$SYSTEM","lamports":0,"executable":"false","data":["", "base64"]}""",
            """{"owner":"$SYSTEM","lamports":0,"executable":false,"data":["AA", "base64"]}""")) {
            assertThrows(RuntimeException::class.java) { SolanaRpcAccountParser.account(JsonParser.parseString(bad)) }
        }
    }

    @Test
    fun `token balance binds exact wallet mint account and program with full unsigned precision`() {
        val rows = SolanaRpcAccountParser.tokenAccounts(JsonParser.parseString(tokenRows()), WALLET, PROGRAM)
        assertEquals(1, rows.size)
        assertEquals(WALLET, rows.single().walletOwner)
        assertEquals(MINT, rows.single().mint)
        assertEquals(PROGRAM, rows.single().ownerProgram)
        assertEquals(ACCOUNT, rows.single().address)
        assertEquals(BigInteger("18446744073709551615"), rows.single().amount)
        assertEquals(9, rows.single().decimals)
        assertEquals(false, rows.single().frozen)
    }

    @Test
    fun `wrong wallet program malformed mint duplicates and overflow cannot masquerade as complete token balances`() {
        for (bad in listOf(tokenRows().replace(WALLET, MINT), tokenRows().replace(PROGRAM, SYSTEM),
            tokenRows().replace("\"mint\":\"$MINT\"", "\"mint\":\"bad\""),
            tokenRows().replace("18446744073709551615", "18446744073709551616"),
            tokenRows().replace("\"state\":\"initialized\"", "\"state\":\"uninitialized\""),
            tokenRows().replace("\"decimals\":9", "\"decimals\":9.5"),
            "[${tokenRows().removePrefix("[").removeSuffix("]")},${tokenRows().removePrefix("[").removeSuffix("]")}]")) {
            assertThrows(RuntimeException::class.java) {
                SolanaRpcAccountParser.tokenAccounts(JsonParser.parseString(bad), WALLET, PROGRAM)
            }
        }
        assertEquals(emptyList<Any>(), SolanaRpcAccountParser.tokenAccounts(JsonParser.parseString("[]"), WALLET, PROGRAM))
    }

    private fun tokenRows() = """[{"pubkey":"$ACCOUNT","account":{"owner":"$PROGRAM","executable":false,"lamports":2039280,
        "data":{"parsed":{"type":"account","info":{"owner":"$WALLET","mint":"$MINT","state":"initialized",
        "tokenAmount":{"amount":"18446744073709551615","decimals":9}}}}}}]""".trimIndent()

    private companion object {
        const val SYSTEM = "11111111111111111111111111111111"
        const val WALLET = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
        const val MINT = "So11111111111111111111111111111111111111112"
        const val ACCOUNT = "9GmvNe93LCNZpC88BSA3emPuTrSq7oep74BjTk11jBQe"
        const val PROGRAM = SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
    }
}
