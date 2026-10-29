package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaRpcBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcCommitment
import jp.co.soramitsu.common.data.network.solana.SolanaRpcTokenAccount
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

class SolanaRpcBalanceSourceTest {
    @Test
    fun `native SPL and Token2022 balances use selected cluster without an indexer and retain frozen ownership`() = runBlocking {
        val calls = mutableListOf<String>()
        val endpoints = mutableListOf<List<String>>()
        val client = object : SolanaRpcClient by mock(SolanaRpcClient::class.java) {
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient {
                endpoints += rpcUrls
                return this
            }
            override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient {
                assertEquals(UniversalWalletRegistry.solanaDevnet.genesisHash, expectedGenesisHash)
                return this
            }
            override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?): Long {
                calls += "$address@$rpcUrl"
                return 5000000000
            }
            override suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment,
                rpcUrl: String?): List<SolanaRpcTokenAccount> {
                calls += "$programAddress@$rpcUrl"
                return if (programAddress == SPL) listOf(token()) else listOf(token().copy(address = SECOND_ACCOUNT,
                    mint = SECOND_MINT, ownerProgram = TOKEN2022, amount = BigInteger.TEN, frozen = true, decimals = 255))
            }
        }
        val source: SolanaBalanceSource = SolanaRpcBalanceSource(client) { 1234 }
        val result = source.balances(WALLET, UniversalWalletRegistry.solanaDevnet, "http://127.0.0.1:8899",
            fallbackUrls = listOf("http://127.0.0.1:8900", "http://127.0.0.1:8899"))
        assertEquals(listOf(listOf("http://127.0.0.1:8899", "http://127.0.0.1:8900")), endpoints)
        assertEquals("5000000000", result.nativeBalance.amount)
        assertEquals("18446744073709551615", result.tokenBalances[0].amount)
        assertEquals(setOf(SECOND_ACCOUNT), result.frozenTokenAccounts)
        assertEquals(listOf("spl-token", "token-2022"), result.tokenBalances.map { it.tokenProgram })
        assertEquals(1234L, result.syncedAtMillis)
        // Requests follow the configured order with failover instead of pinning one endpoint.
        assertEquals(3, calls.size)
        assertEquals(true, calls.all { it.endsWith("@null") })
    }

    @Test
    fun `empty RPC inventories are authoritative while failed partial duplicate or mismatched ownership never becomes zero`() = runBlocking {
        var rows = emptyList<SolanaRpcTokenAccount>()
        var failSecondProgram = false
        var failBalance = false
        val client = object : SolanaRpcClient by mock(SolanaRpcClient::class.java) {
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient {
                assertEquals(listOf(UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL), rpcUrls)
                return this
            }
            override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient {
                assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, expectedGenesisHash)
                return this
            }
            override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?): Long {
                if (failBalance) throw java.io.IOException("RPC unavailable")
                return 7L
            }
            override suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment,
                rpcUrl: String?): List<SolanaRpcTokenAccount> {
                if (programAddress == TOKEN2022 && failSecondProgram) throw java.io.IOException("RPC unavailable")
                return if (programAddress == SPL) rows else emptyList()
            }
        }
        val source = SolanaRpcBalanceSource(client) { 1234 }
        val empty = source.balances(WALLET)
        assertEquals(emptyList<SolanaRpcTokenAccount>(), empty.tokenBalances)
        assertEquals(true, empty.tokensAvailable)
        // An inconsistent or partial token inventory is unknown, never an empty one: SOL is still reported.
        for (bad in listOf(listOf(token(), token()), listOf(token().copy(walletOwner = SECOND_MINT)),
            listOf(token().copy(ownerProgram = TOKEN2022)), listOf(token(), token().copy(address = SECOND_ACCOUNT, decimals = 9)))) {
            rows = bad
            assertUnknownTokens(source.balances(WALLET))
        }
        rows = listOf(token())
        failSecondProgram = true
        assertUnknownTokens(source.balances(WALLET))
        // Without the native balance nothing is reported at all.
        failBalance = true
        assertThrows(java.io.IOException::class.java) { runBlocking { source.balances(WALLET) } }
        Unit
    }

    @Test
    fun `refused token account reads still report SOL and leave every token unknown`() = runBlocking {
        val refused = retrofit2.HttpException(retrofit2.Response.error<Any>(403, "Request blocked".toResponseBody(null)))
        val client = object : SolanaRpcClient by mock(SolanaRpcClient::class.java) {
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient = this
            override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient = this
            override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?) = 2_500_000_000L
            override suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment,
                rpcUrl: String?): List<SolanaRpcTokenAccount> = throw refused
        }
        val result = SolanaRpcBalanceSource(client) { 1234 }.balances(WALLET)
        assertEquals("2500000000", result.nativeBalance.amount)
        assertUnknownTokens(result)
    }

    private fun assertUnknownTokens(result: jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncResult) {
        assertEquals(false, result.tokensAvailable)
        assertEquals(emptyList<Any>(), result.tokenBalances)
        assertEquals(emptySet<String>(), result.frozenTokenAccounts)
        assertEquals(listOf(result.nativeBalance), result.balances)
    }

    private fun token() = SolanaRpcTokenAccount(ACCOUNT, WALLET, MINT, SPL, BigInteger("18446744073709551615"), 6, false)

    private companion object {
        const val WALLET = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
        const val ACCOUNT = "J6SUEb4E6DSQWtBpYNMzDsZMGMGdDZ7tPpxbBwFLhb8r"
        const val SECOND_ACCOUNT = "BNrmzq2GNxwxHf8dSiaEJdtUiQBg4VGCFi9ovhAK6WrK"
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SECOND_MINT = "So11111111111111111111111111111111111111112"
        const val SPL = SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
        const val TOKEN2022 = SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS
    }
}
