package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaRpcBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcCommitment
import jp.co.soramitsu.common.data.network.solana.SolanaRpcTokenAccount
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

class SolanaRpcBalanceSourceTest {
    @Test
    fun `native SPL and Token2022 balances use selected cluster without an indexer and retain frozen ownership`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = object : SolanaRpcClient by mock(SolanaRpcClient::class.java) {
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient {
                require(rpcUrls.size == 1)
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
        val result = source.balances(WALLET, UniversalWalletRegistry.solanaDevnet, "http://127.0.0.1:8899")
        assertEquals("5000000000", result.nativeBalance.amount)
        assertEquals("18446744073709551615", result.tokenBalances[0].amount)
        assertEquals(setOf(SECOND_ACCOUNT), result.frozenTokenAccounts)
        assertEquals(listOf("spl-token", "token-2022"), result.tokenBalances.map { it.tokenProgram })
        assertEquals(1234L, result.syncedAtMillis)
        assertEquals(3, calls.size)
        assertEquals(true, calls.all { it.endsWith("@http://127.0.0.1:8899") })
    }

    @Test
    fun `empty RPC inventories are authoritative while failed partial duplicate or mismatched ownership never becomes zero`() = runBlocking {
        var rows = emptyList<SolanaRpcTokenAccount>()
        var failSecondProgram = false
        val client = object : SolanaRpcClient by mock(SolanaRpcClient::class.java) {
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient {
                require(rpcUrls.size == 1)
                return this
            }
            override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient {
                assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, expectedGenesisHash)
                return this
            }
            override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?) = 0L
            override suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment,
                rpcUrl: String?): List<SolanaRpcTokenAccount> {
                if (programAddress == TOKEN2022 && failSecondProgram) throw java.io.IOException("RPC unavailable")
                return if (programAddress == SPL) rows else emptyList()
            }
        }
        val source = SolanaRpcBalanceSource(client) { 1234 }
        assertEquals(emptyList<SolanaRpcTokenAccount>(), source.balances(WALLET).tokenBalances)
        for (bad in listOf(listOf(token(), token()), listOf(token().copy(walletOwner = SECOND_MINT)),
            listOf(token().copy(ownerProgram = TOKEN2022)), listOf(token(), token().copy(address = SECOND_ACCOUNT, decimals = 9)))) {
            rows = bad
            assertThrows(IllegalArgumentException::class.java) { runBlocking { source.balances(WALLET) } }
        }
        rows = listOf(token())
        failSecondProgram = true
        assertThrows(java.io.IOException::class.java) { runBlocking { source.balances(WALLET) } }
        Unit
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
