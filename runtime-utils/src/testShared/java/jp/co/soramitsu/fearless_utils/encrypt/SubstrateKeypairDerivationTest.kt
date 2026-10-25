package jp.co.soramitsu.fearless_utils.encrypt

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicTestCase
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import org.junit.Assert

abstract class SubstrateKeypairDerivationTest {

    val gson = Gson()

    protected fun performSpecTests(
        filename: String,
        encryptionType: EncryptionType
    ) {
        val testCases = gson.fromJson(
            getResourceReader(filename),
            Array<MnemonicTestCase>::class.java
        )

        testCases.forEach { testCase ->
            val derivationPathRaw = testCase.path.ifEmpty { null }

            val derivationPath = derivationPathRaw
                ?.let { SubstrateJunctionDecoder.decode(testCase.path) }

            val result = SubstrateSeedFactory.deriveSeed(testCase.mnemonic, derivationPath?.password)

            val seed32 = result.seed.copyOf(newSize = 32)

            val actualKeypair = SubstrateKeypairFactory.generate(
                seed = seed32,
                junctions = derivationPath?.junctions.orEmpty(),
                encryptionType = encryptionType
            )

            Assert.assertEquals(
                "Mnemonic=${testCase.mnemonic}, derivationPath=${testCase.path}",
                testCase.expectedPublicKey,
                actualKeypair.publicKey.toHexString(withPrefix = true)
            )
        }
    }
}

