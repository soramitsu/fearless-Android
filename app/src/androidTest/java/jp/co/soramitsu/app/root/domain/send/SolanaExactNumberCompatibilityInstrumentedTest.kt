package jp.co.soramitsu.app.root.domain.send

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException
import java.math.BigInteger

/** Actual production parsing/planning on ART, using only public synthetic inputs and a no-network client. */
@RunWith(AndroidJUnit4::class)
class SolanaExactNumberCompatibilityInstrumentedTest {
    @Test fun absentAccountAndEmptyTokenInventoryRemainHealthyControls() {
        assertNull(account("null"))
        assertEquals(emptyList<Any>(), tokens("[]"))
    }

    @Test fun accountParserPreservesSignedLongMaximumAndRejectsUnsignedOverflow() {
        val parsed = checkNotNull(account(accountJson(Long.MAX_VALUE.toString())))
        assertEquals(Long.MAX_VALUE, parsed.lamports)
        assertEquals(SYSTEM, parsed.ownerProgram)
        assertEquals("AQID", parsed.dataBase64)
        assertThrows(ArithmeticException::class.java) { account(accountJson("9223372036854775808")) }
        assertThrows(IllegalArgumentException::class.java) { account(accountJson("-1")) }
    }

    @Test fun tokenParserPreservesUnsignedAmountAndChecksLamportsAndDecimalWidth() {
        val parsed = tokens(tokenJson()).single()
        assertEquals(BigInteger("18446744073709551615"), parsed.amount)
        assertEquals(255, parsed.decimals)
        assertEquals(SENDER, parsed.walletOwner)
        assertEquals(MINT, parsed.mint)
        assertThrows(ArithmeticException::class.java) { tokens(tokenJson(lamports = "9223372036854775808")) }
        assertThrows(ArithmeticException::class.java) { tokens(tokenJson(decimals = "2147483648")) }
        assertThrows(IllegalArgumentException::class.java) { tokens(tokenJson(decimals = "256")) }
    }

    @Test fun nativeSweepUsesExactDifferenceAndBuildsTheSameReviewedMessage() = runBlocking {
        val client = NoNetworkClient()
        val quote = SolanaPaymentPlanner(client).native(SENDER, RECIPIENT, 1L, sendMax = true)
        assertEquals(BigInteger.valueOf(9995000L), quote.amount)
        assertEquals(5000L, quote.networkFeeLamports)
        assertEquals(2, client.feeReads)
        assertEquals(0, client.broadcasts)
        assertEquals(SolanaPaymentMessageBuilder.native(SENDER, RECIPIENT, 9995000L, SYSTEM, quote.priority).messageBase64,
            quote.message.messageBase64)
    }

    @Test fun nativeRevalidationReadsExistingQuoteWithoutChangingBlockhashOrBroadcasting() = runBlocking {
        val client = NoNetworkClient()
        val planner = SolanaPaymentPlanner(client)
        val quote = planner.native(SENDER, RECIPIENT, 1000000L)
        planner.revalidate(quote)
        assertEquals(1, client.blockhashReads)
        assertEquals(2, client.feeReads)
        assertEquals(0, client.broadcasts)
    }

    @Test fun revalidationRejectsOutOfRangeNativeAmountRatherThanWrapping() = runBlocking {
        val client = NoNetworkClient()
        val planner = SolanaPaymentPlanner(client)
        val quote = planner.native(SENDER, RECIPIENT, 1000000L)
        assertThrows(ArithmeticException::class.java) {
            runBlocking { planner.revalidate(quote.copy(amount = BigInteger.ONE.shiftLeft(63))) }
        }
        assertEquals(0, client.broadcasts)
    }

    private fun account(json: String): SolanaRpcAccountInfo? = invokeParser("account",
        arrayOf(JsonElement::class.java), arrayOf(JsonParser.parseString(json))) as SolanaRpcAccountInfo?

    @Suppress("UNCHECKED_CAST")
    private fun tokens(json: String): List<SolanaRpcTokenAccount> = invokeParser("tokenAccounts",
        arrayOf(JsonElement::class.java, String::class.java, String::class.java),
        arrayOf(JsonParser.parseString(json), SENDER, SPL)) as List<SolanaRpcTokenAccount>

    private fun invokeParser(name: String, types: Array<Class<*>>, args: Array<Any>): Any? {
        val type = Class.forName("jp.co.soramitsu.common.data.network.solana.SolanaRpcAccountParser")
        return try { type.getDeclaredMethod(name, *types).invoke(type.getField("INSTANCE").get(null), *args) }
        catch (failure: InvocationTargetException) { throw checkNotNull(failure.cause) }
    }

    private fun accountJson(lamports: String) = """{"owner":"$SYSTEM","lamports":$lamports,"executable":false,"data":["AQID","base64"]}"""
    private fun tokenJson(lamports: String = "2039280", decimals: String = "255") = """[{"pubkey":"$RECIPIENT","account":{
        "owner":"$SPL","executable":false,"lamports":$lamports,"data":{"parsed":{"type":"account","info":{
        "owner":"$SENDER","mint":"$MINT","state":"initialized","tokenAmount":{"amount":"18446744073709551615","decimals":$decimals}}}}}}]"""

    private class NoNetworkClient : SolanaRpcClient {
        var feeReads = 0
        var blockhashReads = 0
        var broadcasts = 0
        override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient = this
        override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?) = 10000000L
        override suspend fun accountInfo(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaRpcAccountInfo? =
            if (address == SENDER) SolanaRpcAccountInfo(SYSTEM, 10000000L, false, "") else null
        override suspend fun blockHeight(commitment: SolanaRpcCommitment, rpcUrl: String?) = 100L
        override suspend fun recentPrioritizationFees(rpcUrl: String?) = listOf(1000L, 1250L, 2000L)
        override suspend fun latestBlockhash(commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaLatestBlockhashResponse {
            blockhashReads++
            return SolanaLatestBlockhashResponse(SolanaRpcContext(slot = 100), SolanaLatestBlockhash(SYSTEM, 200))
        }
        override suspend fun feeForMessage(messageBase64: String, commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaFeeForMessageResponse {
            feeReads++
            return SolanaFeeForMessageResponse(SolanaRpcContext(slot = 100), 5000L)
        }
        override suspend fun minimumBalanceForRentExemption(dataLength: Int, commitment: SolanaRpcCommitment, rpcUrl: String?) = 890880L
        override suspend fun accountExists(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?) = false
        override suspend fun simulateTransaction(transactionBase64: String, options: SolanaSimulationOptions, rpcUrl: String?): SolanaSimulationResponse =
            error("No simulation is permitted in this offline fixture")
        override suspend fun sendRawTransaction(transactionBase64: String, options: SolanaBroadcastOptions, rpcUrl: String?): String {
            broadcasts++
            error("No broadcast is permitted in this offline fixture")
        }
    }

    private companion object {
        const val SENDER = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
        const val RECIPIENT = "9hSR6S7WPtxmTojgo6GG3k4yDPecgJY292j7xrsUGWBu"
        const val MINT = "So11111111111111111111111111111111111111112"
        const val SYSTEM = SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS
        const val SPL = SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
    }
}
