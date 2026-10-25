package jp.co.soramitsu.common.data.secrets

import com.google.gson.JsonObject
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A3: every secret payload a historical release encoded is read back by the
 * RC exactly. `WalletSecretScalePreflight` requires a `0x` prefix and
 * lowercase hex and bounds every field before a schema touches the bytes, so
 * a historical encoding it did not anticipate would put a working wallet into
 * recovery.
 *
 * For each payload the RC must (1) pass the preflight for that layout,
 * (2) decode it and re-encode the identical string, and (3) for stored
 * secrets whose curve runs on the JVM, accept it through the production
 * validator without changing key material.
 */
@RunWith(Parameterized::class)
class HistoricalSecretEncodingAcceptanceTest(private val library: String) {

    private val vectors: JsonObject = CryptoGoldenVectors.secretEncodings(library)

    @Test
    fun everyHistoricalSchemaEncodingPassesPreflightAndRoundTrips() {
        val mismatches = GoldenMismatches(library, "scale_canonical")
        vectors.objects("scale_canonical").forEach { vector ->
            val schema = vector.string("schema")
            val hex = vector.string("hex")
            mismatches.check("$schema preflight", vector.string("case")) {
                val layout = LAYOUTS.getValue(schema)
                val preflight = runCatching { layout.preflight(hex) }
                when (vector.string("rc_preflight")) {
                    "accept" -> {
                        preflight.getOrThrow()
                        expectEqual(hex, layout.roundTrip(hex), "re-encoded payload")
                    }

                    // Bound cases carry an 8,193-byte field. No historical flow wrote
                    // one; the RC rejects it on purpose before decoding.
                    "reject" -> expectEqual(
                        WalletSecretScaleCorruptionException::class.java,
                        preflight.exceptionOrNull()?.javaClass,
                        "preflight failure"
                    )
                }
            }
        }
        mismatches.assertNone(minimumChecked = 600)
    }

    /**
     * 2.0.0-3.7.4 wrote the six-field meta-account layout; 4.x added an
     * optional TON keypair. A seven-field payload without TON is the six-field
     * payload plus one `00` byte, so the historical six-field bytes are
     * recovered exactly from the production encoder's output.
     */
    @Test
    fun sixFieldMetaAccountPayloadsAreRecognisedAsTheLegacyLayout() {
        val mismatches = GoldenMismatches(library, "six_field_meta_account")
        vectors.objects("scale_canonical")
            .filter { it.string("schema") == "MetaAccountSecrets" && it.string("rc_preflight") == "accept" }
            .filter { MetaAccountSecrets.read(it.string("hex"))[MetaAccountSecrets.TonKeypair] == null }
            .forEach { vector ->
                val sevenField = vector.string("hex")
                val sixField = sevenField.removeSuffix("00")
                mismatches.check("requireMetaAccountV2OrLegacyV69", vector.string("case")) {
                    WalletSecretScalePreflight.requireLegacyV69(sixField)
                    expectEqual(
                        WalletMetaAccountScaleLayout.LEGACY_V69,
                        WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(sixField),
                        "six-field layout"
                    )
                    expectEqual(
                        WalletMetaAccountScaleLayout.CURRENT_V2,
                        WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(sevenField),
                        "seven-field layout"
                    )
                }
            }
        mismatches.assertNone(minimumChecked = 25)
    }

    @Test
    fun everyStoredHistoricalSecretPassesPreflightAndRoundTrips() {
        val mismatches = GoldenMismatches(library, "stored_secrets")
        vectors.objects("stored_secrets").forEach { vector ->
            val store = vector.string("store")
            val stored = vector.string("stored")
            mismatches.check("$store preflight", vector.string("case")) {
                val layout = STORED_LAYOUTS.getValue(store)
                layout.preflight(stored)
                expectEqual(stored, layout.roundTrip(stored), "re-encoded payload")
            }
        }
        mismatches.assertNone(minimumChecked = 250)
    }

    /**
     * Signing keeps working (safety invariant 4) only if the production
     * validator accepts what 4.2.0 stored. Covers every non-sr25519 Substrate,
     * Ethereum and chain-account row; sr25519 rows run on a device and TON
     * rows in HistoricalTonPhraseAcceptanceTest.
     */
    @Test
    fun storedNonSr25519SecretsAreAcceptedWithoutChangingKeyMaterial() {
        val mismatches = GoldenMismatches(library, "validator_acceptance")
        vectors.objects("stored_secrets")
            .filter { it.nullableString("cryptoType") != CryptoType.SR25519.name }
            .filter { it.string("store") != "TonSecrets(V3)" }
            .forEach { vector ->
                mismatches.check(vector.string("store"), vector.string("case")) {
                    val stored = vector.string("stored")
                    val sanitized = validate(vector)
                    val changedFields = vector.getAsJsonArray("rc_sanitizer_changed_fields_2026_10_10").strings()
                    if (changedFields.isEmpty()) {
                        expectEqual(stored, sanitized, "sanitized payload")
                    } else {
                        // Raw-key Ethereum imports stored the default path without entropy; the
                        // RC drops that unusable path. The key itself must be untouched.
                        expectEqual(listOf("EthereumDerivationPath"), changedFields, "changed fields")
                        val before = EthereumSecrets.read(stored)[EthereumSecrets.EthereumKeypair]
                        val after = EthereumSecrets.read(sanitized)[EthereumSecrets.EthereumKeypair]
                        expectEqual(before.toHexString(), after.toHexString(), "Ethereum keypair")
                    }
                }
            }
        mismatches.assertNone(minimumChecked = 131)
    }

    @Test
    fun storedSecretsCoverEveryHistoricalStore() {
        val stores = vectors.objects("stored_secrets").map { it.string("store") }.toSet()
        assertEquals(STORED_LAYOUTS.keys, stores)
        assertTrue(vectors.objects("scale_canonical").map { it.string("schema") }.toSet() == LAYOUTS.keys)
    }

    private fun validate(vector: JsonObject): String {
        val stored = vector.string("stored")
        val publicKey = vector.bytes("publicKey")
        return when (vector.string("store")) {
            "SubstrateSecrets(V3)" -> WalletRootSecretValidator.validateSubstrateAndSanitize(
                encoded = stored,
                expectedPublicKey = publicKey,
                expectedCryptoType = CryptoType.valueOf(vector.string("cryptoType")),
                expectedAccountId = vector.bytes("accountId")
            )

            "EthereumSecrets(V3)" -> WalletRootSecretValidator.validateEthereumAndSanitize(
                encoded = stored,
                expectedPublicKey = publicKey,
                expectedAddress = vector.bytes("address")
            )

            "ChainAccountSecrets(V2)" -> ChainAccountSecretValidator.validateAndSanitize(
                encoded = stored,
                expectedAccountId = vector.bytes("accountId"),
                expectedPublicKey = publicKey,
                expectedCryptoType = CryptoType.ECDSA.takeIf { vector.string("cryptoType") == "ECDSA(ethereum)" }
                    ?: CryptoType.valueOf(vector.string("cryptoType"))
            )

            else -> error("No JVM validator for ${vector.string("store")}")
        }
    }

    private class Layout(val preflight: (String) -> Unit, val roundTrip: (String) -> String)

    companion object {
        private val LAYOUTS = mapOf(
            "KeyPairSchema" to Layout(WalletSecretScalePreflight::requireKeyPairV2) {
                KeyPairSchema.read(it).toHexString()
            },
            "SubstrateSecrets" to Layout(WalletSecretScalePreflight::requireSubstrateV3) {
                SubstrateSecrets.read(it).toHexString()
            },
            "EthereumSecrets" to Layout(WalletSecretScalePreflight::requireEthereumV3) {
                EthereumSecrets.read(it).toHexString()
            },
            "TonSecrets" to Layout(WalletSecretScalePreflight::requireTonV3) {
                TonSecrets.read(it).toHexString()
            },
            "ChainAccountSecrets" to Layout(WalletSecretScalePreflight::requireChainAccountV2) {
                ChainAccountSecrets.read(it).toHexString()
            },
            "MetaAccountSecrets" to Layout(WalletSecretScalePreflight::requireMetaAccountV2) {
                MetaAccountSecrets.read(it).toHexString()
            }
        )

        private val STORED_LAYOUTS = mapOf(
            "SubstrateSecrets(V3)" to LAYOUTS.getValue("SubstrateSecrets"),
            "EthereumSecrets(V3)" to LAYOUTS.getValue("EthereumSecrets"),
            "TonSecrets(V3)" to LAYOUTS.getValue("TonSecrets"),
            "ChainAccountSecrets(V2)" to LAYOUTS.getValue("ChainAccountSecrets")
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun libraries(): List<String> = CryptoGoldenVectors.LIBRARIES
    }
}
