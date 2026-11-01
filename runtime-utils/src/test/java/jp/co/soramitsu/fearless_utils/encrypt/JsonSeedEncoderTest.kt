package jp.co.soramitsu.fearless_utils.encrypt

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.TestData
import jp.co.soramitsu.fearless_utils.common.TestAddressBytes
import jp.co.soramitsu.fearless_utils.common.TestGeneses
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

private const val PASSWORD = "12345"
private const val NAME = "test"

@RunWith(MockitoJUnitRunner::class)
class JsonSeedEncoderTest {
    private val gson = Gson()

    private val decoder = JsonSeedDecoder(gson)
    private val encoder = JsonSeedEncoder(gson)

    @Test
    fun `should encode ed25519`() {
        performTest(MultiChainEncryption.Substrate(EncryptionType.ED25519))
    }

    @Test
    fun `should encode ecdsa`() {
        performTest(MultiChainEncryption.Substrate(EncryptionType.ECDSA))
    }

    @Test
    fun `should encode ethereum`() {
        performTest(MultiChainEncryption.Ethereum)
    }

    @Test
    fun `should encode a derived ed25519 account with its own secret`() {
        performDerivedTest(EncryptionType.ED25519)
    }

    @Test
    fun `should encode a derived ecdsa account with its own secret`() {
        performDerivedTest(EncryptionType.ECDSA)
    }

    @Test
    @Ignore("sr25519 is not supported in unit tests")
    fun `should encode sr25519`() {
        performTest(MultiChainEncryption.Substrate(EncryptionType.SR25519))
    }

    private fun performTest(multiChainEncryption: MultiChainEncryption) {
        val seedExpected = TestData.SEED_BYTES
        val keypairExpected = SubstrateKeypairFactory.generate(multiChainEncryption.encryptionType, seedExpected)

        val address = keypairExpected.publicKey.toAddress(TestAddressBytes.WESTEND)

        val json = encoder.generate(
            keypair = keypairExpected,
            seed = seedExpected,
            password = PASSWORD,
            name = NAME,
            multiChainEncryption = multiChainEncryption,
            address = address,
            genesisHash = TestGeneses.WESTEND
        )

        val decoded = decoder.decode(json, PASSWORD)

        with(decoded) {
            assert(keypairExpected.publicKey.contentEquals(keypair.publicKey))
            assert(keypairExpected.privateKey.contentEquals(keypair.privateKey))
            assertEquals(NAME, username)
            seed?.let {
                assertArrayEquals(seedExpected, it)
            }
        }
    }

    // The app passes the root seed with a derived keypair. The file must still open the derived
    // account, and must not carry the root seed, which opens every account derived from it.
    private fun performDerivedTest(encryptionType: EncryptionType) {
        val rootSeed = TestData.SEED_BYTES
        val junctions = SubstrateJunctionDecoder.decode("//1").junctions
        val keypairExpected = SubstrateKeypairFactory.generate(encryptionType, rootSeed, junctions)

        val json = encoder.generate(
            keypair = keypairExpected,
            seed = rootSeed,
            password = PASSWORD,
            name = NAME,
            multiChainEncryption = MultiChainEncryption.Substrate(encryptionType),
            address = keypairExpected.publicKey.toAddress(TestAddressBytes.WESTEND),
            genesisHash = TestGeneses.WESTEND
        )

        val decoded = decoder.decode(json, PASSWORD)

        assertArrayEquals(keypairExpected.publicKey, decoded.keypair.publicKey)
        assertArrayEquals(keypairExpected.privateKey, decoded.keypair.privateKey)
        assertFalse(rootSeed.contentEquals(decoded.seed))
    }
}
