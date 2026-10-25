package jp.co.soramitsu.fearless_utils.encrypt.mnemonic

import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.text.Normalizer.normalize
import java.util.Arrays
import kotlin.math.floor

private val DELIMITER_REGEX = "[\\s,]+".toRegex()
private val SPACE = EnglishWordList.INSTANCE.space.toString()

object MnemonicCreator {

    fun randomMnemonic(length: Mnemonic.Length): Mnemonic {
        val entropy = ByteArray(length.byteLength)

        return try {
            SecureRandom().nextBytes(entropy)
            fromEntropy(entropy)
        } finally {
            Arrays.fill(entropy, 0.toByte())
        }
    }

    fun fromWords(words: String): Mnemonic {
        val normalizedWords = normalizeWords(words)

        return Mnemonic(
            words = normalizedWords,
            wordList = toWordList(normalizedWords),
            entropy = generateEntropy(normalizedWords)
        )
    }

    fun fromEntropy(entropy: ByteArray): Mnemonic {
        val entropyCopy = entropy.copyOf()
        val words = generateWords(entropyCopy)

        return Mnemonic(
            words = words,
            wordList = toWordList(words),
            entropy = entropyCopy
        )
    }

    private fun toWordList(normalizedWords: String) = normalizedWords.split(SPACE)

    private fun normalizeWords(original: String): String {
        val normalized = normalize(original, Normalizer.Form.NFKD)

        val startIndex = normalized.indexOfFirst { it.isLetter() }
        val endIndex = normalized.indexOfLast { it.isLetter() }

        if (startIndex == -1 || endIndex == -1) {
            throw Bip39Exception()
        }

        return normalized.substring(startIndex, endIndex + 1)
            .replace(DELIMITER_REGEX, SPACE)
    }

    private fun generateWords(entropy: ByteArray): String {
        validateEntropySize(entropy)

        val entropyBits = bytesToBinaryString(entropy)
        val checksumBits = deriveChecksumBits(entropy)
        val mnemonicBits = entropyBits + checksumBits

        return mnemonicBits.chunked(11).joinToString(SPACE) { bits ->
            EnglishWordList.INSTANCE.getWord(Integer.parseInt(bits, 2))
        }
    }

    private fun generateEntropy(mnemonicWords: String): ByteArray {
        val words = mnemonicWords.split(" ")

        if (words.size % 3 != 0) {
            throw Bip39Exception()
        }

        val bits = words.joinToString(separator = "") {
            val index = EnglishWordList.INSTANCE.getIndex(it)

            if (index == -1) {
                throw Bip39Exception()
            }

            index.toString(radix = 2).padStart(length = 11, padChar = '0')
        }

        val dividerIndex = floor(bits.length.toDouble() / 33) * 32
        val entropyBits = bits.substring(0, dividerIndex.toInt())
        val checksumBits = bits.substring(dividerIndex.toInt())

        val entropyBytes = binaryStringToByteArray(entropyBits)

        if (entropyBytes.size < 16) {
            throw Bip39Exception()
        }

        if (entropyBytes.size > 32) {
            throw Bip39Exception()
        }

        if (entropyBytes.size % 4 != 0) {
            throw Bip39Exception()
        }

        val newChecksum = deriveChecksumBits(entropyBytes)

        if (newChecksum != checksumBits) {
            throw Bip39Exception()
        }

        return entropyBytes
    }

    private fun binaryStringToByteArray(str: String): ByteArray {
        val byteArray = mutableListOf<Byte>()
        val tempStringBuilder = StringBuilder()

        str.forEach { c ->
            tempStringBuilder.append(c)

            val tempString = tempStringBuilder.toString()

            if (tempString.length == 8) {
                val temp = tempStringBuilder.toString()

                if (temp.isNotEmpty()) {
                    val tempInteger = Integer.parseInt(tempString, 2)
                    byteArray.add(tempInteger.toByte())
                }

                tempStringBuilder.clear()
            }
        }

        return byteArray.toByteArray()
    }

    private fun deriveChecksumBits(entropy: ByteArray): String {
        val ent = entropy.size * 8
        val cs = ent / 32
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)

        return bytesToBinaryString(hash).substring(0, cs)
    }

    private fun validateEntropySize(entropy: ByteArray) {
        if (entropy.size < 16 || entropy.size > 32 || entropy.size % 4 != 0) {
            throw Bip39Exception()
        }
    }

    private fun bytesToBinaryString(bytes: ByteArray): String {
        return bytes.toUByteArray().joinToString("") { x ->
            x.toString(2).padStart(length = 8, padChar = '0')
        }
    }
}
