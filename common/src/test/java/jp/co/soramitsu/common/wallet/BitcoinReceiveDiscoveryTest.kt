package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinReceiveDiscovery
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinReceiveDiscoveryException
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BitcoinReceiveDiscoveryTest {

    @Test
    fun `scans until configured unused gap after last used receive address`() = runBlocking {
        val client = FakeBitcoinIndexerClient(usedAddresses = usedAddresses(0, 2))
        val discovery = BitcoinReceiveDiscovery(client)

        val result = discovery.discover(
            mnemonic = MNEMONIC,
            gapLimit = 3,
            maxLookahead = 20
        )

        assertEquals(3, result.gapLimit)
        assertEquals(2, result.lastUsedIndex)
        assertEquals(3, result.nextReceiveIndex)
        assertEquals(listOf(0, 2), result.usedAddresses.map { it.index })
        assertEquals(6, client.addressCalls.size)
    }

    @Test
    fun `uses registry gap limit when no override is provided`() = runBlocking {
        val client = FakeBitcoinIndexerClient()
        val discovery = BitcoinReceiveDiscovery(client)

        val result = discovery.discover(mnemonic = MNEMONIC)

        assertEquals(UniversalWalletRegistry.bitcoinMainnet.defaultGapLimit, result.gapLimit)
        assertEquals(UniversalWalletRegistry.bitcoinMainnet.defaultGapLimit, client.addressCalls.size)
    }

    @Test
    fun `derives testnet receive addresses through testnet indexer network`() = runBlocking {
        val client = FakeBitcoinIndexerClient()
        val discovery = BitcoinReceiveDiscovery(client)

        val result = discovery.discover(
            mnemonic = MNEMONIC,
            network = BitcoinKeyDerivation.Network.Testnet,
            gapLimit = 1
        )

        assertTrue(result.addresses.first().address.startsWith("tb1q"))
        assertEquals(listOf(BitcoinIndexerRoutes.Network.Testnet), client.networkCalls)
    }

    @Test
    fun `rejects unsafe discovery parameters before indexer calls`() {
        val client = FakeBitcoinIndexerClient()
        val discovery = BitcoinReceiveDiscovery(client)

        assertThrows(BitcoinReceiveDiscoveryException::class.java) {
            runBlocking {
                discovery.discover(mnemonic = "", gapLimit = 2)
            }
        }
        assertThrows(BitcoinReceiveDiscoveryException::class.java) {
            runBlocking {
                discovery.discover(mnemonic = MNEMONIC, gapLimit = 0)
            }
        }
        assertThrows(BitcoinReceiveDiscoveryException::class.java) {
            runBlocking {
                discovery.discover(mnemonic = MNEMONIC, gapLimit = 101)
            }
        }
        assertThrows(BitcoinReceiveDiscoveryException::class.java) {
            runBlocking {
                discovery.discover(mnemonic = MNEMONIC, gapLimit = 5, maxLookahead = 4)
            }
        }
        assertEquals(emptyList<String>(), client.addressCalls)
    }

    @Test
    fun `fails when max lookahead is exhausted before the unused gap is reached`() {
        val client = FakeBitcoinIndexerClient(usedAddresses = usedAddresses(0))
        val discovery = BitcoinReceiveDiscovery(client)

        val error = assertThrows(BitcoinReceiveDiscoveryException::class.java) {
            runBlocking {
                discovery.discover(mnemonic = MNEMONIC, gapLimit = 3, maxLookahead = 3)
            }
        }

        assertEquals(BitcoinReceiveDiscoveryException.Code.LOOKAHEAD_EXHAUSTED, error.code)
    }

    @Test
    fun `rejects impossible transaction counts from indexer responses`() {
        val client = FakeBitcoinIndexerClient { address ->
            addressStats(address, chainTxCount = Int.MAX_VALUE, mempoolTxCount = 1)
        }
        val discovery = BitcoinReceiveDiscovery(client)

        val error = assertThrows(BitcoinReceiveDiscoveryException::class.java) {
            runBlocking {
                discovery.discover(mnemonic = MNEMONIC, gapLimit = 1)
            }
        }

        assertEquals(BitcoinReceiveDiscoveryException.Code.INVALID_TRANSACTION_COUNT, error.code)
    }

    private class FakeBitcoinIndexerClient(
        private val usedAddresses: Set<String> = emptySet(),
        private val response: (String) -> BitcoinEsploraAddress = { address ->
            addressStats(address, chainTxCount = if (address in usedAddresses) 1 else 0)
        }
    ) : BitcoinIndexerClient {
        val addressCalls = mutableListOf<String>()
        val networkCalls = mutableListOf<BitcoinIndexerRoutes.Network>()

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            addressCalls += address
            networkCalls += network
            return response(address)
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

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        fun usedAddresses(vararg indexes: Int): Set<String> {
            return indexes.map { index ->
                BitcoinKeyDerivation.deriveKey(
                    mnemonic = MNEMONIC,
                    derivationPath = BitcoinKeyDerivation.getReceivePath(index = index.toLong())
                ).address
            }.toSet()
        }

        fun addressStats(
            address: String,
            chainTxCount: Int = 0,
            mempoolTxCount: Int = 0
        ): BitcoinEsploraAddress {
            return BitcoinEsploraAddress(
                address = address,
                chainStats = BitcoinEsploraStats(
                    fundedTxoCount = chainTxCount,
                    fundedTxoSum = 0,
                    spentTxoCount = 0,
                    spentTxoSum = 0,
                    txCount = chainTxCount
                ),
                mempoolStats = BitcoinEsploraStats(
                    fundedTxoCount = mempoolTxCount,
                    fundedTxoSum = 0,
                    spentTxoCount = 0,
                    spentTxoSum = 0,
                    txCount = mempoolTxCount
                )
            )
        }
    }
}
