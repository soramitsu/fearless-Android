package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.TestData
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class KeyFactoryTest {

    @Test
    fun `should generate keypair`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, TestData.SEED_BYTES)

        val actualPrivate = Hex.toHexString(keypair.privateKey)
        val actualPublic = Hex.toHexString(keypair.publicKey)

        assertEquals(TestData.PUBLIC_KEY, actualPublic)
        assertEquals(actualPrivate.length, TestData.PRIVATE_KEY.length)
    }
}