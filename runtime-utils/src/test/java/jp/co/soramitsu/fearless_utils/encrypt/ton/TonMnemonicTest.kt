package jp.co.soramitsu.fearless_utils.encrypt.ton

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.mnemonic.Mnemonic
import java.security.SecureRandom
import java.util.Random

/** TonMnemonic against ton-kotlin 0.3.1's org.ton.mnemonic.Mnemonic, which the app used for TON phrases until 4.3.1. */
class TonMnemonicTest {

    private val random = Random(20261107)
    private val words = TonMnemonic.wordList()

    @Test
    fun `word list is the BIP39 English list ton-kotlin uses`() {
        assertEquals(Mnemonic.mnemonicWords(), words)
        assertEquals(2048, words.size)
    }

    @Test
    fun `entropy and every phrase check match ton-kotlin on random phrases`() {
        repeat(400) { index ->
            val phrase = randomPhrase(index)
            val password = randomPassword(index)
            assertArrayEquals("$index", Mnemonic.toEntropy(phrase, password), TonMnemonic.toEntropy(phrase, password))

            val entropy = Mnemonic.toEntropy(phrase)
            assertEquals("$index", Mnemonic.isBasicSeed(entropy), TonMnemonic.isBasicSeed(entropy))
            assertEquals("$index", Mnemonic.isPasswordSeed(entropy), TonMnemonic.isPasswordSeed(entropy))
            assertEquals("$index", Mnemonic.isPasswordNeeded(phrase), TonMnemonic.isPasswordNeeded(phrase))
            assertEquals("$index", Mnemonic.isValid(phrase), TonMnemonic.isValid(phrase))
            assertEquals("$index", Mnemonic.isValid(phrase, password), TonMnemonic.isValid(phrase, password))
        }
    }

    @Test
    fun `checks match ton-kotlin on phrases built to pass them`() {
        repeat(6) { index ->
            val plain = runBlocking { Mnemonic.generate(wordCount = listOf(12, 18, 24)[index % 3]) }
            assertEquals(Mnemonic.isPasswordNeeded(plain), TonMnemonic.isPasswordNeeded(plain))
            assertEquals(Mnemonic.isValid(plain, "x"), TonMnemonic.isValid(plain, "x"))
            assertTrue(TonMnemonic.isValid(plain))
        }
        repeat(2) { index ->
            val protected = runBlocking { Mnemonic.generate(password = "pass-$index", wordCount = 12) }
            for (password in listOf("pass-$index", "", "other")) {
                assertEquals(Mnemonic.isValid(protected, password), TonMnemonic.isValid(protected, password))
            }
            assertTrue(TonMnemonic.isPasswordNeeded(protected))
            assertTrue(TonMnemonic.isValid(protected, "pass-$index"))
            assertFalse(TonMnemonic.isValid(protected))
        }
    }

    // 100 000 PBKDF2 rounds per seed on each side; the entropy and phrase checks above cover far more phrases.
    @Test
    fun `seeds match ton-kotlin`() {
        repeat(12) { index ->
            val phrase = randomPhrase(index)
            val password = if (index % 4 == 0) randomPassword(index) else ""
            assertArrayEquals("$index", Mnemonic.toSeed(phrase, password), TonMnemonic.toSeed(phrase, password))
        }
        val zeros = List(24) { "abandon" }
        assertArrayEquals(Mnemonic.toSeed(zeros), TonMnemonic.toSeed(zeros))
        assertArrayEquals(Mnemonic.toSeed(emptyList()), TonMnemonic.toSeed(emptyList()))
        assertEquals(TonMnemonic.SEED_SIZE, TonMnemonic.toSeed(zeros).size)
    }

    @Test
    fun `generated phrases pass ton-kotlin's checks`() {
        for (count in listOf(12, 18, 24)) {
            val phrase = TonMnemonic.generate(wordCount = count, random = SecureRandom())
            assertEquals(count, phrase.size)
            assertTrue(phrase.all { it in words })
            assertTrue(Mnemonic.isValid(phrase))
            assertFalse(Mnemonic.isPasswordNeeded(phrase))
        }
        val protected = TonMnemonic.generate(password = "correct horse", wordCount = 12)
        assertTrue(Mnemonic.isPasswordNeeded(protected))
        assertTrue(Mnemonic.isValid(protected, "correct horse"))
        assertFalse(Mnemonic.isValid(protected))
    }

    @Test
    fun `generation draws words from the given list and rejects empty input`() {
        val tiny = listOf("abandon", "ability", "able", "about")
        val phrase = TonMnemonic.generate(wordCount = 12, wordlist = tiny)
        assertTrue(phrase.all { it in tiny })
        assertTrue(Mnemonic.isValid(phrase, wordlist = tiny))
        assertThrows(IllegalArgumentException::class.java) { TonMnemonic.generate(wordCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { TonMnemonic.generate(wordlist = emptyList()) }
    }

    @Test
    fun `isValid honours a custom word list like ton-kotlin`() {
        val phrase = runBlocking { Mnemonic.generate(wordCount = 12) }
        val withoutFirst = words.filter { it != phrase.first() }
        assertEquals(Mnemonic.isValid(phrase, wordlist = withoutFirst), TonMnemonic.isValid(phrase, wordlist = withoutFirst))
        assertFalse(TonMnemonic.isValid(phrase, wordlist = withoutFirst))
    }

    // Mostly list words, sometimes words the typed text could carry: case, accents, kana, empty and broken UTF-16.
    private fun randomPhrase(index: Int): List<String> {
        val count = listOf(12, 15, 18, 21, 24, 1, 0)[index % 7]
        return List(count) {
            when (random.nextInt(40)) {
                0 -> words[random.nextInt(words.size)].uppercase()
                1 -> "café"
                2 -> "あいこくしん"
                3 -> ""
                4 -> "bad\ud800"
                else -> words[random.nextInt(words.size)]
            }
        }
    }

    private fun randomPassword(index: Int): String = when (index % 5) {
        0 -> ""
        1 -> "TREZOR"
        2 -> "päss wörd 🔑"
        3 -> "パスワード"
        else -> buildString { repeat(random.nextInt(20)) { append((32 + random.nextInt(95)).toChar()) } }
    }
}
