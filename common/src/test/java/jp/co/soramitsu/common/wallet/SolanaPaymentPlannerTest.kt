package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigInteger
import java.util.Base64

class SolanaPaymentPlannerTest {
    @Test
    fun `native quote uses RPC exact message fee priority and rent while max drains to zero`() = runBlocking {
        val rpc = Client()
        val planner = SolanaPaymentPlanner(rpc)
        val quote = planner.native(SENDER, RECIPIENT, 1000000)
        assertEquals(BigInteger.valueOf(1000000), quote.amount)
        assertEquals(5000L, quote.networkFeeLamports)
        assertEquals(0L, quote.accountRentLamports)
        assertEquals(listOf(quote.message.messageBase64), rpc.feeMessages)
        assertEquals(1, rpc.blockhashReads)
        planner.revalidate(quote)
        assertEquals(1, rpc.blockhashReads)
        assertEquals(listOf(quote.message.messageBase64, quote.message.messageBase64), rpc.feeMessages)
        val max = planner.native(SENDER, RECIPIENT, 1, sendMax = true)
        assertEquals(BigInteger.valueOf(rpc.nativeBalance - rpc.fee), max.amount)
    }

    @Test
    fun `native invalid recipient rent fee change and expired blockhash fail explicitly`() = runBlocking {
        val rpc = Client()
        val planner = SolanaPaymentPlanner(rpc)
        assertThrows(SolanaAccountPreflightException::class.java) { runBlocking { planner.native(SENDER, RECIPIENT, 1) } }
        val quote = planner.native(SENDER, RECIPIENT, 1000000)
        rpc.fee += 1
        val changed = assertThrows(SolanaPaymentQuoteException::class.java) { runBlocking { planner.revalidate(quote) } }
        assertEquals(SolanaPaymentQuoteException.Code.REVIEW_CHANGED, changed.code)
        rpc.height = 201
        val expired = assertThrows(SolanaPaymentQuoteException::class.java) { runBlocking { planner.requireUnexpired(quote) } }
        assertEquals(SolanaPaymentQuoteException.Code.BLOCKHASH_EXPIRED, expired.code)
        assertEquals(2, rpc.blockhashReads)
    }

    @Test
    fun `SOL to an existing off-curve System account is quoted while tokens to it and SOL to a missing one are refused`() = runBlocking {
        // An off-curve program-derived address that none of the fixture accounts use.
        val vault = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(RECIPIENT, MINT)
        assertEquals(false, vault in listOf(SENDER, RECIPIENT, MINT, SOURCE, SECOND_SOURCE))
        val rpc = Client()
        val planner = SolanaPaymentPlanner(rpc)
        val missing = assertThrows(SolanaAccountPreflightException::class.java) { runBlocking { planner.native(SENDER, vault, 1000000) } }
        assertEquals(SolanaAccountPreflightException.Code.RECIPIENT_CANNOT_SIGN, missing.code)
        rpc.destination = info(ByteArray(0), SYSTEM)
        val quote = planner.native(SENDER, vault, 1000000)
        assertEquals(vault, quote.recipient)
        planner.revalidate(quote)
        val token = assertThrows(SolanaAccountPreflightException::class.java) {
            runBlocking { planner.token(SENDER, vault, MINT, 6, BigInteger.ONE) }
        }
        assertEquals(SolanaAccountPreflightException.Code.RECIPIENT_CANNOT_SIGN, token.code)
        rpc.destination = info(tokenData(MINT, SENDER, BigInteger.ONE))
        val tokenAccount = assertThrows(SolanaAccountPreflightException::class.java) { runBlocking { planner.native(SENDER, vault, 1000000) } }
        assertEquals(SolanaAccountPreflightException.Code.RECIPIENT_IS_NOT_WALLET, tokenAccount.code)
    }

    @Test
    fun `SPL quote allocates multiple exact accounts in full-u64 range and separates creation rent`() = runBlocking {
        val rpc = Client()
        val planner = SolanaPaymentPlanner(rpc)
        val quote = planner.token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE.shiftLeft(63) + BigInteger.TEN)
        assertEquals(2, quote.tokenDebits.size)
        assertEquals(BigInteger.ONE.shiftLeft(63) + BigInteger.TEN, quote.tokenDebits.fold(BigInteger.ZERO) { sum, debit -> sum + debit.amount })
        assertEquals(true, quote.createsAssociatedAccount)
        assertEquals(2039280L, quote.accountRentLamports)
        assertEquals(5000L, quote.networkFeeLamports)
        planner.revalidate(quote)
        assertEquals(1, rpc.blockhashReads)
        val max = planner.token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE, sendMax = true)
        assertEquals(BigInteger.ONE.shiftLeft(64) - BigInteger.ONE, max.amount)
    }

    @Test
    fun `ATA creation flips and wrong owner mint token program or frozen account abort before signing`() = runBlocking {
        for (bad in listOf(
            info(tokenData(MINT, SENDER, BigInteger.ONE)),
            info(tokenData(SENDER, RECIPIENT, BigInteger.ONE)),
            info(tokenData(MINT, RECIPIENT, BigInteger.ONE), TOKEN2022),
            info(tokenData(MINT, RECIPIENT, BigInteger.ONE).also { it[108] = 2 })
        )) {
            val rpc = Client()
            rpc.destination = bad
            assertThrows(SolanaAccountPreflightException::class.java) {
                runBlocking { SolanaPaymentPlanner(rpc).token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE) }
            }
        }
        val rpc = Client()
        val planner = SolanaPaymentPlanner(rpc)
        val quote = planner.token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE)
        rpc.destination = info(tokenData(MINT, RECIPIENT, BigInteger.ZERO))
        val error = assertThrows(SolanaPaymentQuoteException::class.java) { runBlocking { planner.revalidate(quote) } }
        assertEquals(SolanaPaymentQuoteException.Code.REVIEW_CHANGED, error.code)
        val existing = planner.token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE)
        assertEquals(false, existing.createsAssociatedAccount)
        assertEquals(0L, existing.accountRentLamports)
        rpc.destination = null
        assertThrows(SolanaPaymentQuoteException::class.java) { runBlocking { planner.revalidate(existing) } }
        Unit
    }

    @Test
    fun `source inventory change or insufficient native rent cannot produce a token quote`() = runBlocking {
        val rpc = Client()
        rpc.sourceAmountOverride = BigInteger.ONE
        val planner = SolanaPaymentPlanner(rpc)
        val error = assertThrows(SolanaPaymentQuoteException::class.java) { runBlocking { planner.token(SENDER, RECIPIENT, MINT, 6, BigInteger.TEN) } }
        assertEquals(SolanaPaymentQuoteException.Code.REVIEW_CHANGED, error.code)
        rpc.sourceAmountOverride = null
        rpc.nativeBalance = 1000000
        assertThrows(SolanaAccountPreflightException::class.java) { runBlocking { planner.token(SENDER, RECIPIENT, MINT, 6, BigInteger.TEN) } }
        Unit
    }

    @Test
    fun `a Token-2022 mint is refused with its own code before wallet checks token reads or a message`() = runBlocking {
        val rpc = Client()
        rpc.mintProgram = TOKEN2022
        for (sendMax in listOf(false, true)) {
            val error = assertThrows(SolanaPaymentQuoteException::class.java) {
                runBlocking { SolanaPaymentPlanner(rpc).token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE, sendMax = sendMax) }
            }
            assertEquals(SolanaPaymentQuoteException.Code.UNSUPPORTED_TOKEN, error.code)
        }
        assertEquals(listOf(MINT, MINT), rpc.accountReads)
        assertEquals(0, rpc.tokenAccountReads)
        assertEquals(0, rpc.blockhashReads)
        assertEquals(emptyList<String>(), rpc.feeMessages)
        // A standard SPL mint still quotes normally.
        rpc.mintProgram = SPL
        assertEquals(BigInteger.ONE, SolanaPaymentPlanner(rpc).token(SENDER, RECIPIENT, MINT, 6, BigInteger.ONE).amount)
    }

    private class Client : SolanaRpcClient by mock(SolanaRpcClient::class.java) {
        var mintProgram = SPL
        val accountReads = mutableListOf<String>()
        var tokenAccountReads = 0
        var nativeBalance = 10000000L
        var fee = 5000L
        var height = 100L
        var blockhashReads = 0
        var destination: SolanaRpcAccountInfo? = null
        var sourceAmountOverride: BigInteger? = null
        val feeMessages = mutableListOf<String>()
        val sources = listOf(
            SolanaRpcTokenAccount(SOURCE, SENDER, MINT, SPL, BigInteger.ONE.shiftLeft(63), 6, false),
            SolanaRpcTokenAccount(SECOND_SOURCE, SENDER, MINT, SPL, BigInteger.ONE.shiftLeft(63) - BigInteger.ONE, 6, false)
        )
        override suspend fun accountInfo(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaRpcAccountInfo? = when (address.also { accountReads += it }) {
            SENDER -> info(ByteArray(0), SYSTEM).copy(lamports = nativeBalance)
            RECIPIENT -> null
            MINT -> info(ByteArray(82).also { it[44] = 6; it[45] = 1 }, mintProgram)
            SOURCE, SECOND_SOURCE -> info(tokenData(MINT, SENDER, sourceAmountOverride ?: sources.single { it.address == address }.amount))
            else -> destination
        }
        override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?) = nativeBalance
        override suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment, rpcUrl: String?) =
            sources.also { tokenAccountReads++ }
        override suspend fun minimumBalanceForRentExemption(dataLength: Int, commitment: SolanaRpcCommitment, rpcUrl: String?) = if (dataLength == 0) 890880L else 2039280L
        override suspend fun recentPrioritizationFees(rpcUrl: String?) = listOf(1000L, 1250L, 2000L)
        override suspend fun latestBlockhash(commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaLatestBlockhashResponse {
            blockhashReads++
            return SolanaLatestBlockhashResponse(SolanaRpcContext(slot = 100), SolanaLatestBlockhash(SYSTEM, 200))
        }
        override suspend fun feeForMessage(messageBase64: String, commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaFeeForMessageResponse {
            feeMessages += messageBase64
            return SolanaFeeForMessageResponse(SolanaRpcContext(slot = 100), fee)
        }
        override suspend fun blockHeight(commitment: SolanaRpcCommitment, rpcUrl: String?) = height
    }

    private companion object {
        const val SENDER = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
        const val RECIPIENT = "9hSR6S7WPtxmTojgo6GG3k4yDPecgJY292j7xrsUGWBu"
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SOURCE = "J6SUEb4E6DSQWtBpYNMzDsZMGMGdDZ7tPpxbBwFLhb8r"
        const val SECOND_SOURCE = "BNrmzq2GNxwxHf8dSiaEJdtUiQBg4VGCFi9ovhAK6WrK"
        const val SYSTEM = SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS
        const val SPL = SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
        const val TOKEN2022 = SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS
        fun info(bytes: ByteArray, program: String = SPL) = SolanaRpcAccountInfo(program, 2039280, false, Base64.getEncoder().encodeToString(bytes))
        fun tokenData(mint: String, owner: String, amount: BigInteger) = ByteArray(165).also {
            SolanaPublicKeyCodec.decode(mint).copyInto(it)
            SolanaPublicKeyCodec.decode(owner).copyInto(it, 32)
            for (index in 0..7) it[64 + index] = amount.shiftRight(8 * index).toByte()
            it[108] = 1
        }
    }
}
