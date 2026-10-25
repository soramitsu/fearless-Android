package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.TestData
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import org.junit.Test

class AndroidSignerTest {

    @Test
    fun shouldSignMessage() {
        val messageHex = "this is a message"

        val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, TestData.SEED_BYTES)

        val result = Signer.sign(MultiChainEncryption.Substrate(EncryptionType.SR25519), messageHex.toByteArray(), keypair)

        require(
            Signer.verifySr25519(
                messageHex.toByteArray(),
                result.signature,
                keypair.publicKey
            )
        )
    }
}