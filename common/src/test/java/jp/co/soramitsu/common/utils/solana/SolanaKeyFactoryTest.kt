package jp.co.soramitsu.common.utils.solana

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SolanaKeyFactoryTest {

    @Test
    fun `should derive deterministic keypair`() {
        val mnemonic = "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" ")

        val solanaKeypair = SolanaKeyFactory.deriveKeypair(mnemonic)

        assertEquals(32, solanaKeypair.privateKey.size)
        assertEquals(32, solanaKeypair.publicKey.size)

        val defaultKeypair = SolanaKeyFactory.deriveKeypair(mnemonic)
        val differentAccountKeypair = SolanaKeyFactory.deriveKeypair(
            mnemonicWords = mnemonic,
            derivationPath = "m/44'/501'/1'/0'"
        )

        assertEquals(defaultKeypair.publicKey.toList(), solanaKeypair.publicKey.toList())
        assertNotEquals(defaultKeypair.publicKey.toList(), differentAccountKeypair.publicKey.toList())
    }
}
