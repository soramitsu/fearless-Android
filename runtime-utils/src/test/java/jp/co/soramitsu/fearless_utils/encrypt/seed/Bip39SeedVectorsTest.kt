package jp.co.soramitsu.fearless_utils.encrypt.seed

import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.nullableString
import jp.co.soramitsu.fearless_utils.encrypt.objects
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.string
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

/** BIP39 seeds from SeedCreator, which runs PBKDF2-HMAC-SHA512 on BouncyCastle since 4.3.2 (SpongyCastle before). */
class Bip39SeedVectorsTest {

    @Test
    fun `Ethereum seeds match the official BIP39 vectors with passphrase TREZOR`() {
        val vectors = CryptoTestVectors.json("crypto/bip39-trezor-english.json").objects("vectors")
        assertEquals(24, vectors.size)
        vectors.forEach { vector ->
            val result = EthereumSeedFactory.deriveSeed(vector.string("mnemonic"), "TREZOR")
            assertEquals(vector.string("mnemonic"), vector.string("seed"), result.seed.toHexString())
            assertEquals(vector.string("entropy"), result.mnemonic.entropy.toHexString())
        }
    }

    @Test
    fun `Substrate seeds match the substrate-bip39 vectors`() {
        val vectors = CryptoTestVectors.json("crypto/substrate-bip39.json").objects("vectors")
        assertEquals(24, vectors.size)
        vectors.forEach { vector ->
            val result = SubstrateSeedFactory.deriveSeed(vector.string("mnemonic"), "Substrate")
            assertEquals(vector.string("mnemonic"), vector.string("seed"), result.seed.toHexString())
            assertEquals(vector.string("entropy"), result.mnemonic.entropy.toHexString())
        }
    }

    @Test
    fun `Japanese vectors normalize words and passphrase with NFKD`() {
        val vectors = CryptoTestVectors.json("crypto/bip39-japanese.json").objects("vectors")
        assertEquals(24, vectors.size)
        vectors.forEach { vector ->
            val words = Normalizer.normalize(vector.string("mnemonic"), Normalizer.Form.NFKD).encodeToByteArray()
            val seed = SeedCreator.deriveSeed(words, vector.string("passphrase"))
            assertEquals(vector.string("entropy"), vector.string("seed"), seed.toHexString())
        }
    }

    @Test
    fun `NFKD-equivalent passphrases derive the seed Python's hashlib derives`() {
        // hashlib.pbkdf2_hmac("sha512", entropy, NFKD("mnemonic" + passphrase).encode("utf-8"), 2048), entropy = 16 zero bytes.
        val entropy = ByteArray(16)
        val groups = mapOf(
            listOf("Café", "Café") to
                "9656342dbc648cdbe88664f55068b666c6a71633cb86f2ab17dfdd542e24f4094910b398bb8df43b4d227b11c9f4496a947ad3db8d0011b0843a961931539d43",
            listOf("パスワード", "ﾊﾟｽﾜｰﾄﾞ") to
                "fbf2b6bbbf281001cdb760aea42d385894e9e54c79753ab6e8358e869751b1787d81c340c17c02f87c3fa5d216652e20494638df27f12bd90954a10b7c2f0cda",
            listOf("Ångström", "Ångström") to
                "0a4ae5ecb8a226f278e1f6c2f73dd9b5831835f9f3caa99865f684030ec770eb50b8e038551ed7c6319ca75f56d4e3c17ca26edff9eb05cd8154f2ba2cb93a18",
            listOf("ﬁne Ⅳ", "fine IV") to
                "53a4c556dda7f66c9892e0f69fb805e588b25e3ca92167cf6323673b4a349c4d8e7d5dd119dc6eed2272309058cd0a2a828e8234968f76d1ccedf857dbdc5a8f",
            listOf("㍍", "メートル") to
                "537bce6c61ea66b4767a2b885c99c9257734006d69aaf519c09f7d1a3f239fd88e7e16b0ecae5db42bd3315234e46e6adde2a352f88d7557a677230b93c89b9a"
        )
        groups.forEach { (passphrases, expected) ->
            passphrases.forEach { passphrase ->
                assertEquals(passphrase, expected, SeedCreator.deriveSeed(entropy, passphrase).toHexString())
            }
        }
        val words = MnemonicCreator.fromEntropy(entropy).words
        assertEquals(
            "d17e00ec5acd5587f2d46c7df2ba3939331f2dee1afc1e833fed89e6a6b392fe5293b2fdd76976a9968bc74ac86cf7c9f69d0c1f0efe75e9dff5d07a7dc107d3",
            EthereumSeedFactory.deriveSeed(words, "Café").seed.toHexString()
        )
        assertEquals(
            "f08480d8226982bddbb85db9a5eee0efcff46a26828552fac64a7b491ee4c56b54874508c55faa908064dfedefe5aa7fc985bd13419811c66b51a7c8abcfbc7e",
            EthereumSeedFactory.deriveSeed(words, "ﾊﾟｽﾜｰﾄﾞ").seed.toHexString()
        )
    }

    @Test
    fun `a null passphrase is the empty passphrase`() {
        val entropy = CryptoTestVectors.stream("null-passphrase", 32)
        assertArrayEquals(SeedCreator.deriveSeed(entropy, ""), SeedCreator.deriveSeed(entropy, null))
        assertArrayEquals(SeedCreator.deriveSeed(entropy, ""), SeedCreator.deriveSeed(entropy))
    }

    @Test
    fun `reproduces every seed SpongyCastle 1_58 derived`() {
        val cases = CryptoTestVectors.json("legacy-crypto/bip39-seed-spongycastle-1.58.json").objects("cases")
        assertEquals(88, cases.size)
        cases.forEach { case ->
            val password = when (case.string("kind")) {
                "entropy" -> case.string("input").fromHex()
                "words" -> case.string("input").encodeToByteArray()
                else -> error("Unknown kind ${case.string("kind")}")
            }
            val seed = SeedCreator.deriveSeed(password, case.nullableString("passphrase"))
            assertEquals(case.toString(), case.string("seed"), seed.toHexString())
        }
    }
}
