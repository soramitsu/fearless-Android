package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinP2wpkhAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinP2wpkhAddressTest {
    @Test
    fun `accepts BIP173 mainnet and testnet vectors in either uniform case`() {
        assertEquals(MAIN, BitcoinP2wpkhAddress.normalize(MAIN, Network.Mainnet))
        assertEquals(MAIN, BitcoinP2wpkhAddress.normalize(" ${MAIN.uppercase()} ", Network.Mainnet))
        assertEquals(TEST, BitcoinP2wpkhAddress.normalize(TEST, Network.Testnet))
    }

    @Test
    fun `rejects checksum mutation mixed case wrong network and unsupported witness output types`() {
        listOf(
            MAIN.dropLast(1) + "q", MAIN.replaceFirst("bc", "BC"), TEST,
            "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
            "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k4phyt0jr8mvj0sv3qz4z",
            "bc1q" + "q".repeat(38), MAIN + "?bad"
        ).forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { BitcoinP2wpkhAddress.normalize(value, Network.Mainnet) }
        }
    }

    private companion object {
        const val MAIN = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
        const val TEST = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
