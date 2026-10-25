package jp.co.soramitsu.common.data.secrets

import com.google.gson.JsonObject
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonPhraseVariant
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every secret exactly as 4.2.0 (or 3.7.x for chain accounts) stored it must
 * keep signing after the upgrade, and a phrase or seed that 4.2.0 could export
 * must stay exportable. The pre-K1 validator rejected 71 of these 259 vectors.
 *
 * SR25519 needs the app's native library and is covered on device by
 * `ReleasedWalletSecretAcceptanceDeviceTest`.
 */
class ReleasedWalletSecretAcceptanceTest {

    private val vectors = WalletSecretVectors.family("12-validator-acceptance.json")

    @Test
    fun everyNonSr25519SecretStoredByReleasedBuildsSignsAndKeepsItsPhrase() {
        val checked = vectors.filterNot { it.optString("cryptoType") == "SR25519" }
        checked.forEach(::assertAccepted)

        assertEquals(155, checked.size)
        assertEquals(
            setOf("SubstrateSecrets(V3)", "EthereumSecrets(V3)", "TonSecrets(V3)", "ChainAccountSecrets(V2)"),
            checked.map { it["store"].asString }.toSet()
        )
    }

    @Test
    fun everyTonPhraseTypedInto420IsAcceptedAndKeptByteForByte() {
        val ton = vectors.filter { it["store"].asString == "TonSecrets(V3)" }
        assertEquals(24, ton.size)
        // These are the vectors the pre-K1 validator quarantined.
        assertEquals(20, ton.count { it.getAsJsonObject("rc")["verdict"].asString == "reject" })
        ton.forEach(::assertAccepted)
    }

    private fun assertAccepted(vector: JsonObject) {
        val case = vector["case"].asString
        val stored = vector["stored"].asString
        val publicKey = vector["publicKey"].asString.fromHex()
        val validated = when (vector["store"].asString) {
            "SubstrateSecrets(V3)" -> {
                val cryptoType = CryptoType.valueOf(vector["cryptoType"].asString)
                val accountId = vector["accountId"].asString.fromHex()
                assertEquals(
                    case,
                    WalletRootSecretValidator.validateSubstrate(stored, publicKey, cryptoType, accountId).canonical,
                    WalletRootSecretValidator.validateSubstrateAndSanitize(stored, publicKey, cryptoType, accountId)
                )
                WalletRootSecretValidator.validateSubstrate(stored, publicKey, cryptoType, accountId)
            }

            "EthereumSecrets(V3)" -> {
                val address = vector["address"].asString.fromHex()
                assertEquals(
                    case,
                    WalletRootSecretValidator.validateEthereum(stored, publicKey, address).canonical,
                    WalletRootSecretValidator.validateEthereumAndSanitize(stored, publicKey, address)
                )
                WalletRootSecretValidator.validateEthereum(stored, publicKey, address)
            }

            "TonSecrets(V3)" -> {
                assertEquals(
                    case,
                    stored,
                    WalletRootSecretValidator.validateTonAndSanitize(stored, publicKey)
                )
                WalletRootSecretValidator.validateTon(stored, publicKey)
            }

            "ChainAccountSecrets(V2)" -> {
                val accountId = vector["accountId"].asString.fromHex()
                val cryptoType = CryptoType.valueOf(
                    vector["cryptoType"].asString.removeSuffix("(ethereum)")
                )
                val validated = ChainAccountSecretValidator.validate(stored, accountId, publicKey, cryptoType)
                assertEquals(
                    case,
                    validated.canonical,
                    ChainAccountSecretValidator.validateAndSanitize(stored, accountId, publicKey, cryptoType)
                )
                assertEquals(case, expectedAssessment(vector), validated.assessment)
                assertEquals(case, stored, validated.canonical)
                return
            }

            else -> error("Unknown store in $case")
        }

        assertEquals(case, expectedAssessment(vector), validated.assessment)
        val changedFields = vector.getAsJsonObject("rc")
            .getAsJsonArray("sanitizerChangedFields")
            ?.map { it.asString }
            .orEmpty()
        if (changedFields.isEmpty()) {
            assertEquals(case, stored, validated.canonical)
        } else {
            // A raw private-key import stored the default path without
            // entropy; only that unbound path leaves the canonical form.
            assertEquals(case, listOf("EthereumDerivationPath"), changedFields)
            val original = EthereumSecrets.read(stored)
            val canonical = EthereumSecrets.read(validated.canonical)
            assertNull(case, canonical[EthereumSecrets.EthereumDerivationPath])
            assertArrayEquals(
                case,
                original[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey],
                canonical[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey]
            )
            assertArrayEquals(case, original[EthereumSecrets.Seed], canonical[EthereumSecrets.Seed])
        }
    }

    private fun expectedAssessment(vector: JsonObject): RecoveryAssessment {
        val stored = vector["stored"].asString
        return when (vector["store"].asString) {
            "TonSecrets(V3)" -> if (vector["typedVariant"].asString == "canonical") {
                RecoveryAssessment.Verified
            } else {
                RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
            }

            "SubstrateSecrets(V3)" -> SubstrateSecrets.read(stored).let {
                hasRecovery(it[SubstrateSecrets.Entropy], it[SubstrateSecrets.Seed])
            }

            "EthereumSecrets(V3)" -> EthereumSecrets.read(stored).let {
                hasRecovery(it[EthereumSecrets.Entropy], it[EthereumSecrets.Seed])
            }

            else -> ChainAccountSecrets.read(stored).let {
                hasRecovery(it[ChainAccountSecrets.Entropy], it[ChainAccountSecrets.Seed])
            }
        }
    }

    private fun hasRecovery(entropy: ByteArray?, seed: ByteArray?): RecoveryAssessment {
        return if (entropy != null || seed != null) {
            RecoveryAssessment.Verified
        } else {
            RecoveryAssessment.None
        }
    }

    private fun JsonObject.optString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString
}
