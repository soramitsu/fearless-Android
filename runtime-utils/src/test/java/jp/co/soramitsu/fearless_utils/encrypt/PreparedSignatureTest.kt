package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import org.junit.Assert.*
import org.junit.Test

class PreparedSignatureTest {
    @Test fun `prepared signatures match legacy Ed25519 substrate ECDSA and Ethereum encodings`() {
        for (encryption in listOf(MultiChainEncryption.Substrate(EncryptionType.ED25519),
            MultiChainEncryption.Substrate(EncryptionType.ECDSA), MultiChainEncryption.Ethereum)) {
            val type = (encryption as? MultiChainEncryption.Substrate)?.encryptionType ?: EncryptionType.ECDSA
            val key = SubstrateKeypairFactory.generate(type, ByteArray(32) { 7 })
            val message = ByteArray(317) { (it % 255).toByte() }
            val expected = Signer.sign(encryption, message, key)
            val prepared = Signer.prepareSigning(encryption, message, key)
            message.fill(0)
            assertArrayEquals(expected.signature, prepared.sign { it() }.signature)
            assertThrows(IllegalStateException::class.java) { prepared.sign { it() } }
        }
    }

    @Test fun `denied final authorization never invokes the prepared primitive`() {
        var calls = 0
        val prepared = PreparedSignature { calls++; SignatureWrapper.Ed25519(ByteArray(64)) }
        assertThrows(IllegalStateException::class.java) { prepared.sign { error("expired") } }
        assertEquals(0, calls)
    }

    @Test fun `primitive failure is consumed and cannot be automatically retried`() {
        var calls = 0
        val prepared = PreparedSignature { calls++; error("primitive failure") }
        assertThrows(IllegalStateException::class.java) { prepared.sign { it() } }
        assertThrows(IllegalStateException::class.java) { prepared.sign { it() } }
        assertEquals(1, calls)
    }
}
