package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.nativeWalletChains
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientAddressExtTest {
    private val bitcoin = nativeWalletChains().single { it.id == UniversalWalletRegistry.bitcoinMainnet.id }
    private val solana = nativeWalletChains().single { it.id == UniversalWalletRegistry.solanaMainnet.id }
    private val substrateAddress = ByteArray(32) { 2 }.toAddress(0.toShort())

    @Test
    fun `solana recipients are checked as base58 keys instead of the legacy substrate format`() {
        // The legacy ecosystem value alone rejects every Solana address.
        assertFalse(solana.isValidAddress(SOLANA))
        for (chain in listOf(solana, solana.copy(id = UniversalWalletRegistry.solanaMainnet.chainId))) {
            assertTrue(chain.isValidRecipientAddress(SOLANA))
            assertTrue(chain.isValidRecipientAddress(" $PDA "))
            for (invalid in listOf("", substrateAddress, BITCOIN, SOLANA.take(30), SOLANA + "1", "0" + SOLANA.drop(1), "I" + SOLANA.drop(1))) {
                assertFalse(invalid, chain.isValidRecipientAddress(invalid))
            }
        }
        assertTrue(solana.isAppOwnedPaymentNetwork())
    }

    @Test
    fun `bitcoin recipients must be payable addresses of the chain's own network`() {
        assertFalse(bitcoin.isValidAddress(BITCOIN))
        assertTrue(bitcoin.isValidRecipientAddress(BITCOIN))
        for (invalid in listOf(SOLANA, substrateAddress, "tb1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", BITCOIN.uppercase().dropLast(1))) {
            assertFalse(invalid, bitcoin.isValidRecipientAddress(invalid))
        }
        assertTrue(bitcoin.isAppOwnedPaymentNetwork())
    }

    @Test
    fun `the same Bitcoin address in either case is the same account and other formats compare exactly`() {
        assertTrue(bitcoin.isSameRecipientAddress(BITCOIN, BITCOIN.uppercase()))
        assertTrue(bitcoin.isSameRecipientAddress(" ${BITCOIN.uppercase()} ", BITCOIN))
        assertFalse(bitcoin.isSameRecipientAddress(BITCOIN, "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertTrue(solana.isSameRecipientAddress(SOLANA, SOLANA))
        assertFalse(solana.isSameRecipientAddress(SOLANA, SOLANA.lowercase()))
    }

    @Test
    fun `other chains keep their ecosystem check`() {
        val substrate = solana.copy(id = "polkadot-fixture", externalApi = null, nodes = emptyList())
        assertFalse(substrate.isAppOwnedPaymentNetwork())
        assertTrue(substrate.isValidRecipientAddress(substrateAddress))
        assertFalse(substrate.isValidRecipientAddress(SOLANA))
    }

    private companion object {
        const val SOLANA = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        // An associated token account address: a valid key format, judged against the chain only when quoted.
        const val PDA = "J6SUEb4E6DSQWtBpYNMzDsZMGMGdDZ7tPpxbBwFLhb8r"
        const val BITCOIN = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
    }
}
