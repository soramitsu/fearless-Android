package jp.co.soramitsu.fearless_utils.encrypt.json

import com.google.gson.Gson
import com.google.gson.JsonParser
import jp.co.soramitsu.fearless_utils.encrypt.CryptoTestVectors
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.IncorrectPasswordException
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.nullableString
import jp.co.soramitsu.fearless_utils.encrypt.objects
import jp.co.soramitsu.fearless_utils.encrypt.string
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON backups (polkadot-js keyring format: scrypt + xsalsa20-poly1305, Base64 "encoded") keep opening after
 * SpongyCastle's Base64 was replaced with BouncyCastle's. Android, iOS and the browser extension share this format.
 */
class JsonBackupCompatibilityTest {

    private val decoder = JsonSeedDecoder(Gson())
    private val encoder = JsonSeedEncoder(Gson())

    @Test
    fun `opens polkadot-js and 4_2_0 Fearless backups exactly as the historical decoder did`() {
        val backups = CryptoTestVectors.json("crypto/json-backups.json").objects("backups")
        assertEquals(9, backups.size)
        backups.forEach { backup ->
            val name = backup.string("input")
            val json = backup.string("json")
            val password = backup.string("password")
            val expected = backup.getAsJsonObject("expected")
            val actual = runCatching { decoder.decode(json, password) }
            if (expected.has("error")) {
                assertEquals(name, expected.string("error"), actual.exceptionOrNull()?.javaClass?.name?.substringAfter("fearless_utils."))
            } else {
                val imported = actual.getOrThrow()
                val encryption = when (val chain = imported.multiChainEncryption) {
                    MultiChainEncryption.Ethereum -> "ethereum"
                    is MultiChainEncryption.Substrate -> chain.encryptionType.name
                }
                assertEquals(name, expected.string("encryption"), encryption)
                val keypair = expected.getAsJsonObject("keypair")
                assertEquals(name, keypair.string("privateKey"), imported.keypair.privateKey.toHexString(withPrefix = true))
                assertEquals(name, keypair.string("publicKey"), imported.keypair.publicKey.toHexString(withPrefix = true))
                assertEquals(name, expected.nullableString("seed"), imported.seed?.toHexString(withPrefix = true))
                assertEquals(name, expected.nullableString("username"), imported.username)
            }
            backup.getAsJsonObject("expectedWrongPassword")?.let { wrong ->
                val failure = runCatching { decoder.decode(json, "$password-wrong") }.exceptionOrNull()
                assertEquals(name, wrong.string("error"), failure?.javaClass?.name?.substringAfter("fearless_utils."))
            }
        }
    }

    @Test
    fun `Base64 encodes every byte string as SpongyCastle 1_58 did`() {
        val cases = CryptoTestVectors.json("legacy-crypto/base64-spongycastle-1.58.json").objects("encode")
        assertEquals(42, cases.size)
        cases.forEach { case ->
            assertEquals(case.string("base64"), Base64.toBase64String(case.string("bytes").fromHex()))
        }
    }

    @Test
    fun `Base64 decodes everything SpongyCastle 1_58 decoded correctly, to the same bytes`() {
        val cases = CryptoTestVectors.json("legacy-crypto/base64-spongycastle-1.58.json").objects("decode")
        assertEquals(45, cases.size)
        // SpongyCastle's own oddities, which never opened a backup: it rejected empty input and line-wrapped
        // Base64, and it decoded unpadded input to wrong bytes, so the poly1305 tag check then failed.
        val legacyOddities = mapOf(
            "" to "",
            "QUJDRA=" to "error",
            "QUJDRA" to "error",
            "Zm9vYg" to "error",
            "Zm9vYg=" to "error",
            "QU\nJD" to "414243",
            "QU\r\nJD\n" to "414243",
            "QU JD" to "414243"
        )
        var compared = 0
        cases.forEach { case ->
            val input = case.string("input")
            val legacy = case.string("result")
            val actual = runCatching { Base64.decode(input).toHexString() }.getOrElse { "error" }
            val expected = legacyOddities[input] ?: legacy.takeUnless { it.startsWith("error:") } ?: "error"
            assertEquals(input, expected, actual)
            if (input !in legacyOddities && !legacy.startsWith("error:")) compared++
        }
        assertEquals(18, compared)
    }

    @Test
    fun `round-trips backups written by this encoder through the decoder`() {
        val seed = CryptoTestVectors.stream("json-round-trip", 32)
        for (type in listOf(EncryptionType.ED25519, EncryptionType.ECDSA)) {
            val keypair = SubstrateKeypairFactory.generate(type, seed)
            val json = encoder.generate(
                keypair = keypair,
                seed = seed,
                password = "round-trip",
                name = "round trip",
                multiChainEncryption = MultiChainEncryption.Substrate(type),
                genesisHash = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
                address = "address"
            )
            val imported = decoder.decode(json, "round-trip")
            assertArrayEquals(keypair.privateKey, imported.keypair.privateKey)
            assertArrayEquals(keypair.publicKey, imported.keypair.publicKey)
            assertArrayEquals(seed, imported.seed)
            assertThrows(IncorrectPasswordException::class.java) { decoder.decode(json, "wrong") }
        }
    }

    @Test
    fun `a line-wrapped backup now opens and an unpadded one still does not`() {
        val backup = CryptoTestVectors.json("crypto/json-backups.json").objects("backups")
            .first { it.string("input") == "polkadotjs-ed25519-scrypt.json" }
        val document = JsonParser.parseString(backup.string("json")).asJsonObject
        val encoded = document.string("encoded")

        val wrapped = document.deepCopy().apply { addProperty("encoded", encoded.chunked(64).joinToString("\n")) }
        val imported = decoder.decode(wrapped.toString(), backup.string("password"))
        assertEquals("0xffcee385c9de9c11f2a26a6156b61d72ef12a64c4b81475a9eee9e4fa456da3d", imported.keypair.publicKey.toHexString(withPrefix = true))

        val truncated = document.deepCopy().apply { addProperty("encoded", encoded.trimEnd('=').dropLast(1)) }
        val failure = runCatching { decoder.decode(truncated.toString(), backup.string("password")) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is JsonSeedDecodingException)
    }
}
