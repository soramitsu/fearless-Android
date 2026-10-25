package jp.co.soramitsu.common.data.secrets

import com.google.gson.JsonObject
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.TonPhraseVariant
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

/**
 * A4 and safety invariant 6. Android 4.2.0 stored the TON phrase exactly as
 * typed or pasted and derived the key from `text.split(" ")`. A phrase with a
 * trailing space, double spaces or capitals therefore owns a different key
 * than its normalized copy, and that key is where the user's funds are.
 *
 * Proven here against the 4.2.0 vectors:
 * - the RC's TON derivation (ton-kotlin `Mnemonic.toSeed` + `PrivateKeyEd25519`,
 *   as `WalletRootSecretValidator` recovers it) reproduces every stored key
 *   from the exact stored split, typed variants included;
 * - deriving from a normalized copy reaches a different wallet for every
 *   non-canonical phrase, so a normalized derivation or export would lose funds;
 * - the stored payload holds the typed text byte for byte;
 * - the production validator accepts canonical phrases unchanged and never
 *   returns a rewritten payload.
 */
@RunWith(Parameterized::class)
class HistoricalTonPhraseAcceptanceTest(private val library: String) {

    private val crypto: JsonObject = CryptoGoldenVectors.crypto(library)
    private val encodings: JsonObject = CryptoGoldenVectors.secretEncodings(library)

    @Test
    fun exactStoredSplitDerivesEveryHistoricalTonKey() {
        val mismatches = GoldenMismatches(library, "ton")
        crypto.objects("ton").forEach { vector ->
            val typed = vector.string("typedText")
            mismatches.check("Mnemonic.toSeed(typed.split(\" \"))", "${vector.string("kind")}/${vector.string("variant")}") {
                val words = typed.split(" ")
                val seed = Mnemonic.toSeed(words)
                val privateKey = PrivateKeyEd25519(seed)
                expectEqual(vector.string("seed"), seed.hex(), "seed")
                expectEqual(vector.string("privateKey"), privateKey.key.toByteArray().hex(), "private key")
                expectEqual(vector.string("publicKey"), privateKey.publicKey().key.toByteArray().hex(), "public key")
                expectEqual(
                    vector.string("publicKey"),
                    SolanaKeyDerivation.publicKeyFromPrivateKey(privateKey.key.toByteArray()).hex(),
                    "validator public key"
                )
                expectEqual(vector["tonIsValid"].asBoolean, Mnemonic.isValid(words), "TON validity")
            }
        }
        mismatches.assertNone(minimumChecked = 24)
    }

    @Test
    fun normalizedCopyDerivesADifferentWalletForEveryTypedVariant() {
        val mismatches = GoldenMismatches(library, "ton_normalized")
        crypto.objects("ton").forEach { vector ->
            val typed = vector.string("typedText")
            mismatches.check("normalized derivation", "${vector.string("kind")}/${vector.string("variant")}") {
                val normalized = typed.trim().lowercase().split(WHITESPACE)
                val normalizedPublicKey = PrivateKeyEd25519(Mnemonic.toSeed(normalized)).publicKey().key.toByteArray()
                val sameKey = normalizedPublicKey.hex() == vector.string("publicKey")
                expectEqual(vector["sameKeyAsNormalized"].asBoolean, sameKey, "normalized copy owns the stored key")
                expectEqual(vector.string("variant") == "canonical", sameKey, "only canonical text survives normalization")
            }
        }
        mismatches.assertNone(minimumChecked = 24)
    }

    @Test
    fun storedTonSecretsHoldTheTypedTextAndItsOwnKey() {
        val mismatches = GoldenMismatches(library, "ton_stored")
        tonRows().forEach { vector ->
            mismatches.check("TonSecrets", vector.string("case")) {
                val secrets = TonSecrets.read(vector.string("stored"))
                val text = vector.string("storedText")
                expectEqual(text, secrets[TonSecrets.Seed].decodeToString(), "stored text")
                val derived = PrivateKeyEd25519(Mnemonic.toSeed(text.split(" ")))
                expectEqual(secrets[TonSecrets.PrivateKey].hex(), derived.key.toByteArray().hex(), "private key")
                expectEqual(vector.string("publicKey"), secrets[TonSecrets.PublicKey].hex(), "public key")
                expectEqual(vector.string("publicKey"), derived.publicKey().key.toByteArray().hex(), "derived public key")
            }
        }
        mismatches.assertNone(minimumChecked = 24)
    }

    /**
     * The production validator must accept every 4.2.0 TON secret and return
     * it unchanged, typed variants included (A4, fixed by android-keys K1,
     * "TON T2 as typed"): a rejection would let the startup integrity check
     * quarantine a wallet whose key is valid. The recovery assessment must
     * say the exact stored text re-derives the key, so export hands out the
     * phrase that restores the funds.
     */
    @Test
    fun validatorAcceptsHistoricalTonSecretsUnchanged() {
        val mismatches = GoldenMismatches(library, "ton_validator")
        tonRows().forEach { vector ->
            val case = vector.string("case")
            val variant = vector.string("typedVariant")
            mismatches.check("validateTonAndSanitize", case) {
                val sanitized = WalletRootSecretValidator.validateTonAndSanitize(
                    encoded = vector.string("stored"),
                    expectedPublicKey = vector.bytes("publicKey")
                )
                expectEqual(vector.string("stored"), sanitized, "sanitized payload")
            }
            mismatches.check("validateTon", case) {
                val validated = WalletRootSecretValidator.validateTon(
                    encoded = vector.string("stored"),
                    expectedPublicKey = vector.bytes("publicKey")
                )
                expectEqual(vector.string("stored"), validated.canonical, "validated payload")
                val expectedAssessment = if (variant == "canonical") {
                    RecoveryAssessment.Verified
                } else {
                    RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
                }
                expectEqual(expectedAssessment, validated.assessment, "recovery assessment")
            }
        }
        mismatches.assertNone(minimumChecked = 48)
    }

    @Test
    fun vectorsCoverEveryTypedVariantAndBothPhraseKinds() {
        assertEquals(
            setOf("canonical", "trailing-space", "leading-space", "double-space", "upper", "trailing-newline"),
            tonRows().map { it.string("typedVariant") }.toSet()
        )
        assertEquals(
            setOf("ton-mnemonic:ton-native", "ton-mnemonic:bip39"),
            tonRows().map { it.string("source") }.toSet()
        )
        assertEquals(setOf("ton-native", "bip39"), crypto.objects("ton").map { it.string("kind") }.toSet())
    }

    private fun tonRows(): List<JsonObject> =
        encodings.objects("stored_secrets").filter { it.string("store") == "TonSecrets(V3)" }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun libraries(): List<String> = CryptoGoldenVectors.LIBRARIES
    }
}
