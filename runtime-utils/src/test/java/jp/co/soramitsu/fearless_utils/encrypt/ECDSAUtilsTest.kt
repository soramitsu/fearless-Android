package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class ECDSAUtilsTest {

    @Test
    fun `should create compressed public key with leading zero bits`() {
        val seed =
            BigInteger("92cf62b905b27f71494c539e50545b3a3265d9b34a6865a2460c242b75cfc9b9", 16)
        val expectedPublicKey = "020cddfb851af41912813cc47cb5f57b170beb8dfce1fe605ab4555143d2771cfc"

        val publicKey = Hex.toHexString(ECDSAUtils.compressedPublicKeyFromPrivate(seed))

        assertEquals(expectedPublicKey, publicKey)
    }
}