package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected keys come from the frozen historical library, not from the new verifier. */
class WalletIdentityVerifierTest {
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
    private val golden = javaClass.classLoader!!.getResourceAsStream(
        "crypto-golden/shared-features-1.1.1.44-FLW.json"
    )!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test
    fun historicalSubstrateSeedsAndCustomPathsRecoverTheirRecordedIdentities() {
        val vectors = golden.rows("keypairs").filter {
            it.text("kind") == "substrate" && it.text("cryptoType") in listOf("ED25519", "ECDSA") &&
                it.getAsJsonObject("expected").has("publicKey")
        }
        assertTrue(vectors.size >= 6)
        for (vector in vectors) {
            val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
            val identity = WalletKeyIdentity.Substrate(
                publicKey, publicKey.substrateAccountId(), CryptoType.valueOf(vector.text("cryptoType"))
            )
            val secret = verifier.verifySeed(identity, vector.text("seed"), vector.text("path"))
            assertEquals(identity.scope, secret.scope)
            assertEquals(secret.encoded, verifier.validate(identity, secret.encoded))
            assertTrue(!secret.toString().contains(vector.text("seed")))
        }
    }

    @Test
    fun historicalSubstratePhraseRestoresTheSameSeedAndHardDerivationPath() {
        for (crypto in listOf(CryptoType.ED25519, CryptoType.ECDSA)) {
            val vector = golden.rows("keypairs").first {
                it.text("kind") == "substrate" && it.text("cryptoType") == crypto.name &&
                    it.text("path") == "//polkadot" && it.getAsJsonObject("expected").has("publicKey")
            }
            val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
            val identity = WalletKeyIdentity.Substrate(publicKey, publicKey.substrateAccountId(), crypto)
            val secret = verifier.verifyMnemonic(identity, DEV_PHRASE, "//polkadot")
            assertEquals(RecoveryAssessment.Verified, WalletRootSecretValidator.validateSubstrate(
                secret.encoded, publicKey, crypto, identity.accountId
            ).assessment)
        }
    }

    @Test
    fun historicalSubstratePhraseWithNoPathCanAlsoBeRecoveredAfterAnUnmatchedCustomPath() {
        for (crypto in listOf(CryptoType.ED25519, CryptoType.ECDSA)) {
            val vector = golden.rows("keypairs").first {
                it.text("kind") == "substrate" && it.text("cryptoType") == crypto.name &&
                    it.text("path").isEmpty() && it.getAsJsonObject("expected").has("publicKey")
            }
            val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
            val identity = WalletKeyIdentity.Substrate(publicKey, publicKey.substrateAccountId(), crypto)
            val defaultSecret = verifier.verifyMnemonic(identity, DEV_PHRASE)
            val fallbackSecret = verifier.verifyMnemonic(identity, DEV_PHRASE, "//different")
            assertEquals(defaultSecret.encoded, fallbackSecret.encoded)
        }
    }

    @Test
    fun historicalEthereumMnemonicAndPrivateKeyMatchTheRecordedWallet() {
        val vectors = golden.rows("keypairs").filter {
            it.text("kind") == "ethereum" && it.getAsJsonObject("expected").has("publicKey")
        }
        assertTrue(vectors.isNotEmpty())
        for (vector in vectors) {
            val expected = vector.getAsJsonObject("expected")
            val publicKey = expected.bytes("publicKey")
            val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
            val phrase = verifier.verifyMnemonic(identity, vector.text("mnemonic"), vector.text("path"))
            val key = verifier.verifySeed(identity, expected.text("privateKey"))
            for (secret in listOf(phrase, key)) {
                assertEquals(secret.encoded, verifier.validate(identity, secret.encoded))
            }
        }
    }

    @Test
    fun matchingEthereumNetworkAccountUsesTheHistoricalChainSecretShape() {
        val vector = golden.rows("keypairs").first { it.text("kind") == "ethereum" }
        val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
        val identity = WalletKeyIdentity.Chain(publicKey, publicKey.ethereumAddressFromPublicKey(), CryptoType.ECDSA)
        val secret = verifier.verifyMnemonic(identity, vector.text("mnemonic"), vector.text("path"))
        assertEquals(RecoveryAssessment.Verified, ChainAccountSecretValidator.validate(
            secret.encoded, identity.accountId, identity.publicKey, identity.cryptoType
        ).assessment)
    }

    @Test
    fun everyHistoricalTonTypedVariantPreservesTheTextThatOwnsTheFunds() {
        val vectors = golden.rows("ton")
        assertEquals(24, vectors.size)
        for (vector in vectors) {
            val identity = WalletKeyIdentity.Ton(vector.bytes("publicKey"))
            val secret = verifier.verifyMnemonic(identity, vector.text("typedText"))
            val stored = TonSecrets.read(secret.encoded)
            assertArrayEquals(vector.text("typedText").encodeToByteArray(), stored[TonSecrets.Seed])
            assertArrayEquals(identity.publicKey, stored[TonSecrets.PublicKey])
        }
    }

    @Test
    fun historicalJsonBackupsRequireTheirPasswordAndTheRecordedSigningType() {
        val vectors = golden.rows("json_keyring").filter {
            it.getAsJsonObject("expected").get("encryption")?.asString in listOf("ED25519", "ECDSA")
        }
        assertTrue(vectors.isNotEmpty())
        for (vector in vectors) {
            val expected = vector.getAsJsonObject("expected")
            val publicKey = expected.getAsJsonObject("keypair").bytes("publicKey")
            val identity = WalletKeyIdentity.Substrate(
                publicKey, publicKey.substrateAccountId(), CryptoType.valueOf(expected.text("encryption"))
            )
            val secret = verifier.verifyJson(identity, vector.text("json"), vector.text("password"))
            assertEquals(secret.encoded, verifier.validate(identity, secret.encoded))
            assertThrows(Exception::class.java) { verifier.verifyJson(identity, vector.text("json"), "wrong") }
        }
    }

    @Test
    fun wrongPhraseAndMalformedDurableIdentityNeverProduceAReplacement() {
        val vector = golden.rows("keypairs").first { it.text("kind") == "ethereum" }
        val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
        val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
        assertThrows(WalletRecoveryIdentityMismatch::class.java) {
            verifier.verifyMnemonic(identity, "abandon ".repeat(11) + "about")
        }
        val secret = verifier.verifyMnemonic(identity, vector.text("mnemonic"), vector.text("path"))
        assertThrows(IllegalStateException::class.java) {
            verifier.validate(WalletKeyIdentity.Ethereum(publicKey, ByteArray(20)), secret.encoded)
        }
        assertThrows(IllegalArgumentException::class.java) { verifier.verifySeed(identity, "00") }
    }

    private fun JsonObject.rows(key: String) = getAsJsonArray(key).map { it.asJsonObject }
    private fun JsonObject.text(key: String) = get(key).asString
    private fun JsonObject.bytes(key: String) = Hex.decode(text(key).removePrefix("0x"))

    private companion object {
        const val DEV_PHRASE = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
    }
}
