package jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.SeedTestCase
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.getResourceReader
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.*
import org.junit.Test

class EthereumKeypairFactoryTest {

    private val gson = Gson()

    @Test
    fun `should pass spec tests`() {
        val testCases = gson.fromJson(
            getResourceReader("crypto/BIP32HDKDEtalon.json"),
            Array<SeedTestCase>::class.java
        )

        testCases.forEach(::performTest)
    }

    private fun performTest(testCase: SeedTestCase) {
        val derivationPathOrNull = testCase.path.ifEmpty { null }

        val actualKeypair = EthereumKeypairFactory.generate(
            seed = testCase.seed.fromHex(),
            junctions = derivationPathOrNull
                ?.let { BIP32JunctionDecoder.decode(it).junctions }
                .orEmpty()
        )

        assertEquals(
            "Seed=${testCase.seed}, derivationPath=${testCase.path}",
            testCase.expectedPublicKey,
            actualKeypair.publicKey.toHexString(withPrefix = true)
        )
    }

    @Test
    fun `public key created from seed should equal the expected public key`() {
        val testCases = listOf(
            SeedTestCase(
                seed = "0x302994d7d1200c6f7f2a65eb1057400737cabc6a301fc25ba0850c2305c1f9a3",
                path = "",
                expectedPublicKey = "0x030e9c3a4ebbc615b5b5ebd5e68da51527c2eb52ba48fd82bffae7311cdfa9fd07"
            ),
            SeedTestCase(
                seed = "0xab427ec0f8b00001393e5b9e1de1da960ee1c2eaef27fdb5b708927b218fae9b",
                path = "",
                expectedPublicKey = "0x03a628d58eedbc7f190e1d1cfdac236a961b82a9d8bd8ef7d890e0b21d3c138a22"
            )
        )

        testCases.forEach { testCase ->
            val seedBytes = Hex.decode(testCase.seed.removePrefix("0x"))
            val actualKeyPair = EthereumKeypairFactory.createWithPrivateKey(seedBytes)
            val actualPublicKey = actualKeyPair.publicKey.toHexString(true)
            assertEquals(testCase.expectedPublicKey, actualPublicKey)
        }
    }
}