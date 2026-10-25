package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSpendableAccountScanner
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock

class BitcoinSpendableAccountScannerTest {
    @Test
    fun `scan includes funded receive and change paths with pending separate and next owned change`() = runBlocking {
        val receive0 = key(0, 0)
        val receive1 = key(0, 1)
        val change0 = key(1, 0)
        val balances = mapOf(receive0 to (40000L to true), receive1 to (10000L to true), change0 to (7000L to false))
        val requested = mutableListOf<String>()
        val client = object : BitcoinIndexerClient by mock(BitcoinIndexerClient::class.java) {
            override suspend fun address(address: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): BitcoinEsploraAddress {
                val balance = balances[address]
                val value = balance?.first ?: 0L
                val stats = BitcoinEsploraStats(if (value > 0) 1 else 0, value, 0, 0, if (value > 0) 1 else 0)
                return BitcoinEsploraAddress(address, if (balance?.second == true) stats else EMPTY, if (balance?.second == false) stats else EMPTY)
            }
            override suspend fun utxos(address: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): List<BitcoinEsploraUtxo> {
                requested += address
                val (value, confirmed) = balances.getValue(address)
                return listOf(BitcoinEsploraUtxo((balances.keys.indexOf(address) + 1).toString().padStart(64, '0'), 0, value, BitcoinEsploraTxStatus(confirmed)))
            }
        }
        val result = BitcoinSpendableAccountScanner(client).scan(MNEMONIC, receive0, BitcoinKeyDerivation.Network.Mainnet, gapLimit = 2)
        assertEquals(57000L, result.totalSats)
        assertEquals(50000L, result.spendableSats)
        assertEquals(7000L, result.pendingSats)
        assertEquals(listOf(receive0, receive1, change0), requested)
        assertEquals(listOf("m/84'/0'/0'/0/0", "m/84'/0'/0'/0/1", "m/84'/0'/0'/1/0"), result.sources.map { it.derivationPath })
        assertEquals(key(1, 1), result.nextChangeAddress)
        assertEquals("m/84'/0'/0'/1/1", result.nextChangePath)
    }

    @Test
    fun `mismatched root is rejected before any network operation`() {
        val client = object : BitcoinIndexerClient by mock(BitcoinIndexerClient::class.java) {
            override suspend fun address(address: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): BitcoinEsploraAddress = error("Network must not run")
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { BitcoinSpendableAccountScanner(client).scan(MNEMONIC, key(0, 1), BitcoinKeyDerivation.Network.Mainnet) }
        }
    }

    private fun key(change: Long, index: Long) = BitcoinKeyDerivation.deriveKey(MNEMONIC,
        derivationPath = BitcoinKeyDerivation.getReceivePath(BitcoinKeyDerivation.Network.Mainnet, index, change)).address

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val EMPTY = BitcoinEsploraStats(0, 0, 0, 0, 0)
    }
}
