package jp.co.soramitsu.fearless_utils.encrypt.ton

import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.EnglishWordList
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom

/**
 * TON's native mnemonic scheme (the one of tonweb-mnemonic and ton-kotlin's Mnemonic object), on BouncyCastle.
 *
 * - entropy = HMAC-SHA512(key = UTF-8 of the words joined by single spaces, data = UTF-8 of the password)
 * - seed = the first 32 bytes of PBKDF2-HMAC-SHA512(entropy, "TON default seed", 100000 rounds)
 * - a phrase is a basic seed when PBKDF2(entropy, "TON seed version", 390 rounds) starts with 0
 * - a phrase needs a password when, without one, PBKDF2(entropy, "TON fast seed version", 1 round) starts with 1
 *   and it is not a basic seed
 *
 * Words are not normalized, trimmed or checked by [toSeed]; the bytes typed are the bytes hashed.
 */
object TonMnemonic {

    const val DEFAULT_ITERATIONS = 100_000
    const val DEFAULT_SALT = "TON default seed"
    const val DEFAULT_BASIC_ITERATIONS = 390
    const val DEFAULT_BASIC_SALT = "TON seed version"
    const val DEFAULT_PASSWORD_ITERATIONS = 1
    const val DEFAULT_PASSWORD_SALT = "TON fast seed version"
    const val DEFAULT_WORD_COUNT = 24
    const val SEED_SIZE = 32

    private const val WORD_LIST_SIZE = 2048
    private const val DERIVED_KEY_BITS = 512
    private const val WORD_SEPARATOR = " "

    private val englishWords: List<String> = List(WORD_LIST_SIZE) { EnglishWordList.INSTANCE.getWord(it) }
    private val englishWordSet: Set<String> = englishWords.toHashSet()

    /** The BIP39 English list, which TON uses as its default word list. */
    fun wordList(): List<String> = englishWords

    /** HMAC-SHA512 of the joined words, keyed by them, over the password: 64 bytes. */
    fun toEntropy(mnemonic: List<String>, password: String = ""): ByteArray {
        val key = mnemonic.joinToString(WORD_SEPARATOR).encodeToByteArray()
        val data = password.encodeToByteArray()
        val mac = HMac(SHA512Digest())
        try {
            mac.init(KeyParameter(key))
            mac.update(data, 0, data.size)
            val entropy = ByteArray(mac.macSize)
            mac.doFinal(entropy, 0)
            return entropy
        } finally {
            key.fill(0)
            data.fill(0)
        }
    }

    /** The 32-byte Ed25519 private key (seed) of a TON phrase. */
    fun toSeed(mnemonic: List<String>, password: String = ""): ByteArray {
        val entropy = toEntropy(mnemonic, password)
        try {
            val derived = pbkdf2(entropy, DEFAULT_SALT, DEFAULT_ITERATIONS)
            try {
                return derived.copyOf(SEED_SIZE)
            } finally {
                derived.fill(0)
            }
        } finally {
            entropy.fill(0)
        }
    }

    /** TON's basic-seed check of a 64-byte entropy. */
    fun isBasicSeed(entropy: ByteArray): Boolean =
        firstDerivedByte(entropy, DEFAULT_BASIC_SALT, DEFAULT_BASIC_ITERATIONS) == 0

    /** TON's password-seed check of a 64-byte entropy. */
    fun isPasswordSeed(entropy: ByteArray): Boolean =
        firstDerivedByte(entropy, DEFAULT_PASSWORD_SALT, DEFAULT_PASSWORD_ITERATIONS) == 1

    /** True when the phrase only opens with a password. */
    fun isPasswordNeeded(mnemonic: List<String>): Boolean {
        val entropy = toEntropy(mnemonic)
        try {
            return isPasswordSeed(entropy) && !isBasicSeed(entropy)
        } finally {
            entropy.fill(0)
        }
    }

    /** ton-kotlin's `Mnemonic.isValid`: every word is listed, the password fits, and the result is a basic seed. */
    fun isValid(
        mnemonic: List<String>,
        password: String = "",
        wordlist: List<String> = englishWords
    ): Boolean {
        val allowed = if (wordlist === englishWords) englishWordSet else wordlist.toHashSet()
        if (!mnemonic.all { word -> word in allowed }) return false
        if (password.isNotEmpty() && !isPasswordNeeded(mnemonic)) return false

        val entropy = toEntropy(mnemonic, password)
        try {
            return isBasicSeed(entropy)
        } finally {
            entropy.fill(0)
        }
    }

    /**
     * A new TON phrase: [wordCount] words drawn uniformly by [random] until TON's checks pass, the same checks
     * as ton-kotlin's `Mnemonic.generate`. Unlike ton-kotlin, every word comes from the secure generator.
     */
    fun generate(
        password: String = "",
        wordCount: Int = DEFAULT_WORD_COUNT,
        wordlist: List<String> = englishWords,
        random: SecureRandom = SecureRandom()
    ): List<String> {
        require(wordCount > 0) { "Word count must be positive" }
        require(wordlist.isNotEmpty()) { "Word list must not be empty" }

        while (true) {
            val mnemonic = List(wordCount) { wordlist[random.nextInt(wordlist.size)] }
            if (password.isEmpty() || isPasswordNeeded(mnemonic)) {
                val entropy = toEntropy(mnemonic, password)
                val basic = try {
                    isBasicSeed(entropy)
                } finally {
                    entropy.fill(0)
                }
                if (basic) return mnemonic
            }
        }
    }

    private fun firstDerivedByte(entropy: ByteArray, salt: String, iterations: Int): Int {
        val derived = pbkdf2(entropy, salt, iterations)
        try {
            return derived[0].toInt()
        } finally {
            derived.fill(0)
        }
    }

    private fun pbkdf2(password: ByteArray, salt: String, iterations: Int): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(password, salt.encodeToByteArray(), iterations)
        return (generator.generateDerivedParameters(DERIVED_KEY_BITS) as KeyParameter).key
    }
}
