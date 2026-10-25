package jp.co.soramitsu.common.data.secrets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonPhraseVariant
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-crypto proof, with the SR25519 library the app ships, that every
 * secret exactly as 4.2.0 (or 3.7.x for chain accounts) stored it still signs
 * after the upgrade and keeps an exportable phrase. This includes the SR25519
 * soft-junction wallets and the as-typed TON phrases that the pre-K1
 * validator quarantined (71 of 259 vectors). All key material is synthetic.
 */
@RunWith(AndroidJUnit4::class)
class ReleasedWalletSecretAcceptanceDeviceTest {

    @Test
    fun everySecretStoredByReleasedBuildsSignsAndKeepsItsPhrase() {
        val vectors = loadVectors()
        assertEquals(259, vectors.size)
        assertEquals(104, vectors.count { it.optString("cryptoType") == "SR25519" })

        vectors.forEach(::assertAccepted)
    }

    private fun assertAccepted(vector: JsonObject) {
        val case = vector["case"].asString
        val stored = vector["stored"].asString
        val publicKey = vector["publicKey"].asString.fromHex()
        val (signingCanonical, validated) = when (vector["store"].asString) {
            "SubstrateSecrets(V3)" -> {
                val cryptoType = CryptoType.valueOf(vector["cryptoType"].asString)
                val accountId = vector["accountId"].asString.fromHex()
                WalletRootSecretValidator.validateSubstrateAndSanitize(
                    stored, publicKey, cryptoType, accountId
                ) to WalletRootSecretValidator.validateSubstrate(
                    stored, publicKey, cryptoType, accountId
                ).let { it.canonical to it.assessment }
            }

            "EthereumSecrets(V3)" -> {
                val address = vector["address"].asString.fromHex()
                WalletRootSecretValidator.validateEthereumAndSanitize(
                    stored, publicKey, address
                ) to WalletRootSecretValidator.validateEthereum(
                    stored, publicKey, address
                ).let { it.canonical to it.assessment }
            }

            "TonSecrets(V3)" -> {
                WalletRootSecretValidator.validateTonAndSanitize(
                    stored, publicKey
                ) to WalletRootSecretValidator.validateTon(
                    stored, publicKey
                ).let { it.canonical to it.assessment }
            }

            "ChainAccountSecrets(V2)" -> {
                val accountId = vector["accountId"].asString.fromHex()
                val cryptoType = CryptoType.valueOf(
                    vector["cryptoType"].asString.removeSuffix("(ethereum)")
                )
                ChainAccountSecretValidator.validateAndSanitize(
                    stored, accountId, publicKey, cryptoType
                ) to ChainAccountSecretValidator.validate(
                    stored, accountId, publicKey, cryptoType
                ).let { it.canonical to it.assessment }
            }

            else -> error("Unknown store in $case")
        }
        val (canonical, assessment) = validated

        assertEquals(case, expectedAssessment(vector), assessment)
        assertEquals(case, signingCanonical, canonical)
        if (vector.getAsJsonObject("rc").has("sanitizerChangedFields") &&
            vector.getAsJsonObject("rc").getAsJsonArray("sanitizerChangedFields").size() > 0
        ) {
            // A raw private-key import stored the default path without
            // entropy; only that unbound path leaves the canonical form.
            assertNull(case, EthereumSecrets.read(canonical)[EthereumSecrets.EthereumDerivationPath])
        } else {
            assertEquals(case, stored, canonical)
        }
    }

    private fun expectedAssessment(vector: JsonObject): RecoveryAssessment {
        val stored = vector["stored"].asString
        val hasRecovery = when (vector["store"].asString) {
            "TonSecrets(V3)" -> return if (vector["typedVariant"].asString == "canonical") {
                RecoveryAssessment.Verified
            } else {
                RecoveryAssessment.VerifiedAsTyped(TonPhraseVariant.AS_TYPED)
            }

            "SubstrateSecrets(V3)" -> SubstrateSecrets.read(stored).let {
                it[SubstrateSecrets.Entropy] != null || it[SubstrateSecrets.Seed] != null
            }

            "EthereumSecrets(V3)" -> EthereumSecrets.read(stored).let {
                it[EthereumSecrets.Entropy] != null || it[EthereumSecrets.Seed] != null
            }

            else -> ChainAccountSecrets.read(stored).let {
                it[ChainAccountSecrets.Entropy] != null || it[ChainAccountSecrets.Seed] != null
            }
        }
        return if (hasRecovery) RecoveryAssessment.Verified else RecoveryAssessment.None
    }

    private fun loadVectors(): List<JsonObject> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val root = assets.open("12-validator-acceptance.json").use { stream ->
            JsonParser.parseReader(InputStreamReader(stream, Charsets.UTF_8)).asJsonObject
        }
        return root.getAsJsonArray("vectors").map { it.asJsonObject }
    }

    private fun JsonObject.optString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString
}
