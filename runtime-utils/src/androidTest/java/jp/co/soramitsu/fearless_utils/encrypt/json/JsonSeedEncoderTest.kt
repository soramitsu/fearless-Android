package jp.co.soramitsu.fearless_utils.encrypt.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.TestData
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

private val PASSWORD = "12345"
private val NAME = "name"

private const val ADDRESS_TYPE_WESTEND: Short = 42
private const val GENESIS_HASH_WESTEND =
    "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"


@RunWith(AndroidJUnit4::class)
class JsonSeedEncoderTest {

    val gson = Gson()

    @Test
    fun shouldEncodeWithSr25519() {
        val keypairExpected =
            SubstrateKeypairFactory.generate(EncryptionType.SR25519, TestData.SEED_BYTES)

        require(keypairExpected is Sr25519Keypair)

        val decoder = JsonSeedDecoder(gson)
        val encoder = JsonSeedEncoder(gson)

        val address = keypairExpected.publicKey.toAddress(ADDRESS_TYPE_WESTEND)

        val json = encoder.generate(
            keypair = keypairExpected,
            seed = null,
            password = PASSWORD,
            name = NAME,
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.SR25519),
            address = address,
            genesisHash = GENESIS_HASH_WESTEND
        )

        val decoded = decoder.decode(json, PASSWORD)

        with(decoded) {
            val keypair = keypair

            require(keypair is Sr25519Keypair)

            require(keypairExpected.publicKey.contentEquals(keypair.publicKey))
            require(keypairExpected.privateKey.contentEquals(keypair.privateKey))
            require(keypairExpected.nonce.contentEquals(keypair.nonce))
            require(NAME == username)
            require(seed == null)
        }
    }
}