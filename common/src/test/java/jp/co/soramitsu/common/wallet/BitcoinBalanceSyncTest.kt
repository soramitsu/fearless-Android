package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinBalanceSync
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinBalanceSyncException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinReceiveDiscovery
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinBalanceSyncTest {

    @Test
    fun `sums confirmed and mempool balances from discovered receive addresses`() = runBlocking {
        val balances = balancesByAddress(
            0 to AddressBalance(confirmedSats = 10, mempoolSats = 2),
            1 to AddressBalance(confirmedSats = 3, mempoolSats = 5)
        )
        val client = FakeBitcoinIndexerClient(balances)
        val balanceSync = BitcoinBalanceSync(BitcoinReceiveDiscovery(client))

        val result = balanceSync.balance(
            mnemonic = MNEMONIC,
            gapLimit = 2,
            maxLookahead = 5
        )

        assertEquals(13L, result.confirmedSats)
        assertEquals(7L, result.mempoolSats)
        assertEquals(20L, result.totalSats)
        assertEquals(listOf(0, 1), result.usedAddresses.map { it.index })
        assertEquals(4, client.addressCalls.size)
    }

    @Test
    fun `rejects balance overflow across discovered addresses`() {
        val balances = balancesByAddress(
            0 to AddressBalance(confirmedSats = Long.MAX_VALUE, mempoolSats = 0),
            1 to AddressBalance(confirmedSats = Long.MAX_VALUE, mempoolSats = 0)
        )
        val client = FakeBitcoinIndexerClient(balances)
        val balanceSync = BitcoinBalanceSync(BitcoinReceiveDiscovery(client))

        val error = assertThrows(BitcoinBalanceSyncException::class.java) {
            runBlocking {
                balanceSync.balance(
                    mnemonic = MNEMONIC,
                    gapLimit = 1,
                    maxLookahead = 4
                )
            }
        }

        assertEquals(BitcoinBalanceSyncException.Code.BALANCE_OVERFLOW, error.code)
    }

    private class FakeBitcoinIndexerClient(
        private val balances: Map<String, AddressBalance>
    ) : BitcoinIndexerClient {
        val addressCalls = mutableListOf<String>()

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            addressCalls += address
            val balance = balances[address] ?: AddressBalance()

            return addressStats(
                address = address,
                confirmedSats = balance.confirmedSats,
                mempoolSats = balance.mempoolSats,
                txCount = if (balance.confirmedSats > 0 || balance.mempoolSats > 0) 1 else 0
            )
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> {
            error("Unexpected Bitcoin UTXO call")
        }

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> {
            error("Unexpected Bitcoin transaction call")
        }

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> {
            error("Unexpected Bitcoin fee-estimates call")
        }

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String {
            error("Unexpected Bitcoin broadcast call")
        }
    }

    private data class AddressBalance(
        val confirmedSats: Long = 0,
        val mempoolSats: Long = 0
    )

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        fun balancesByAddress(vararg balances: Pair<Int, AddressBalance>): Map<String, AddressBalance> {
            return balances.associate { (index, balance) ->
                val address = BitcoinKeyDerivation.deriveKey(
                    mnemonic = MNEMONIC,
                    derivationPath = BitcoinKeyDerivation.getReceivePath(index = index.toLong())
                ).address
                address to balance
            }
        }

        fun addressStats(
            address: String,
            confirmedSats: Long,
            mempoolSats: Long,
            txCount: Int
        ): BitcoinEsploraAddress {
            return BitcoinEsploraAddress(
                address = address,
                chainStats = BitcoinEsploraStats(
                    fundedTxoCount = txCount,
                    fundedTxoSum = confirmedSats,
                    spentTxoCount = 0,
                    spentTxoSum = 0,
                    txCount = txCount
                ),
                mempoolStats = BitcoinEsploraStats(
                    fundedTxoCount = if (mempoolSats > 0) 1 else 0,
                    fundedTxoSum = mempoolSats,
                    spentTxoCount = 0,
                    spentTxoSum = 0,
                    txCount = if (mempoolSats > 0) 1 else 0
                )
            )
        }
    }
}
