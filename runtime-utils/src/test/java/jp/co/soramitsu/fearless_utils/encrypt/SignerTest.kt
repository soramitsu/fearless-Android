package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.TestData
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SignerTest {

    @Test
    fun `should sign message ED25519`() {
        val messageHex = "this is a message"

        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, TestData.SEED_BYTES)

        val result = Signer.sign(MultiChainEncryption.Substrate(EncryptionType.ED25519), messageHex.toByteArray(), keypair)

        assert(Signer.verifyEd25519(messageHex.toByteArray(), result.signature, keypair.publicKey))
    }

    @Test
    fun `should sign message ECDSA`() {
        val publicKeyHex = "f65a7d560102f2019da9b9d8993f53f51cc38d50cdff3d0b8e71997d7f911ff1"
        val privateKeyHex = "ae4093af3c40f2ecc32c14d4dada9628a4a42b28ca1a5b200b89321cbc883182"

        val keypair = BaseKeypair(privateKeyHex.fromHex(), publicKeyHex.fromHex())

        val message =
            "0400340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c6302286bee0000002d00000003000000e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423ee143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
        val messageBytes = message.fromHex()

        val signatureWrapper = Signer.sign(MultiChainEncryption.Substrate(EncryptionType.ECDSA), messageBytes, keypair) as SignatureWrapper.Ecdsa

        val expected =
            "352e2738b0e361a7c59be05d52e7e7fb860bf79c03bb7858ce3e48748b00040c4dc6eadbfd526d35ba6dff1468bf61198cc5e8570a80ddc63fdebe68dc6016a41b"

        assertEquals(expected, signatureWrapper.signature.toHexString())
    }
}