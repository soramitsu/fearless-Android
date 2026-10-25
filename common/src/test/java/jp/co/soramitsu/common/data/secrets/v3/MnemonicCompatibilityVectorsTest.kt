package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.WalletSecretVectors
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The RC re-implemented entropy-to-words without the `jp.co.soramitsu:bip39`
 * library that 4.2.0 used. Every stored entropy is re-derived through it when
 * a wallet's phrase is assessed, exported or backed up, so any divergence
 * would mark whole cohorts of wallets as unverified or, worse, export a phrase
 * for another account. These vectors were produced by running the released
 * 4.2.0 library (shared-features `7508bc83`) and include the Trezor set.
 */
class MnemonicCompatibilityVectorsTest {

    private val vectors = WalletSecretVectors.family("02-bip39.json")

    @Test
    fun entropyToWordsMatchesTheReleasedLibraryForEveryPhraseLength() {
        val fromEntropy = vectors.filter { it["kind"].asString == "fromEntropy" }
        val wordCounts = fromEntropy.map { vector ->
            val entropy = vector["entropy"].asString.fromHex()
            val words = vector["words"].asString

            assertEquals(words, MnemonicCreator.fromEntropy(entropy.copyOf()).words)
            assertArrayEquals(words, entropy, MnemonicCreator.fromWords(words).entropy)
            words.split(' ').size
        }

        assertEquals(setOf(12, 15, 18, 21, 24), wordCounts.toSet())
    }

    @Test
    fun wordsToEntropyAgreesWithTheReleasedLibrary() {
        val fromWords = vectors.filter { it["kind"].asString == "fromWords" }
        assertEquals(18, fromWords.size)
        fromWords.forEach { vector ->
            val input = vector["input"].asString
            val released = vector.getAsJsonObject("prod")
            if (released.has("entropy")) {
                val mnemonic = MnemonicCreator.fromWords(input)
                assertEquals(input, released["words"].asString, mnemonic.words)
                assertArrayEquals(input, released["entropy"].asString.fromHex(), mnemonic.entropy)
            } else {
                // 4.2.0 rejected these too (an empty phrase crashed it instead).
                assertThrows(input, Bip39Exception::class.java) {
                    MnemonicCreator.fromWords(input)
                }
            }
        }
    }

    @Test
    fun entropyOfAnInvalidLengthIsRejected() {
        val invalid = vectors.filter { it["kind"].asString == "fromEntropy-invalid-length" }
        assertEquals(5, invalid.size)
        invalid.forEach { vector ->
            val size = vector["size"].asInt
            assertThrows("$size bytes", Bip39Exception::class.java) {
                MnemonicCreator.fromEntropy(ByteArray(size) { 0x55 })
            }
        }
    }
}
