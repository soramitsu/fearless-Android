package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.SubstrateKeypairDerivationTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LocalSubstrateKeypairDerivationTest : SubstrateKeypairDerivationTest() {

    @Test
    fun `should  pass ed25519 tests`() {
        performSpecTests("crypto/ed25519HDKD.json", EncryptionType.ED25519)
    }

    @Test
    fun `should pass ecdsa tests`() {
        performSpecTests("crypto/ecdsaHDKD.json", EncryptionType.ECDSA)
    }
}