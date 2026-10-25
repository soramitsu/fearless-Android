package jp.co.soramitsu.coredb.secrets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A3 for sr25519, which needs the shipped native library: the RC must derive
 * every sr25519 key 4.2.0 derived (shared-features 1.1.1.44-FLW, AL-4
 * vectors), import its JSON exports, and keep signing with every sr25519
 * secret 4.2.0 stored.
 *
 * Soft derivation draws a random nonce, so for paths ending in a soft
 * junction only the private and public key are compared (finding F5). The
 * production validators no longer compare that nonce (android-libs AL-5,
 * android-keys K1), so every stored secret, soft paths included, must be
 * accepted unchanged and its recovery material reported as verified.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalSr25519GoldenVectorTest {

    private val vectors: JsonObject = load()

    @Test
    fun sr25519KeypairsMatchHistoricalLibrary() {
        val failures = mutableListOf<String>()
        val nativeAbortPaths = mutableSetOf<String>()
        vectors.objects("keypairs").forEach { vector ->
            val label = "${vector.string("kind")} ${vector.nullableString("seed") ?: vector.nullableString("mnemonic")} " +
                "'${vector.string("path")}'"
            runCatching {
                when (vector.string("kind")) {
                    "anchor" -> {
                        val decoded = SubstrateJunctionDecoder.decode(vector.string("path"))
                        val seed = SubstrateSeedFactory.deriveSeed32(vector.string("mnemonic"), decoded.password).seed
                        val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed, decoded.junctions)
                        expectEqual(vector.string("publicKey"), keypair.publicKey.hex(), "anchor public key")
                    }

                    "substrate" -> {
                        val expected = vector.getAsJsonObject("expected")
                        if (abortsNativeDerivation(vector.string("path"))) {
                            // AL-4 finding 6: both libraries hash a junction longer than 32
                            // bytes to 16 bytes and the native create_cc panics, which aborts
                            // the process on Android. 4.2.0 could never create such a wallet.
                            expectEqual("java.lang.IllegalStateException", expected.string("error"), "host oracle error")
                            nativeAbortPaths += vector.string("path")
                            return@runCatching
                        }
                        val actual = runCatching {
                            SubstrateKeypairFactory.generate(
                                encryptionType = EncryptionType.SR25519,
                                seed = vector.bytes("seed"),
                                junctions = junctions(vector.string("path"))
                            )
                        }
                        if (expected.has("error")) {
                            expectEqual(expected.string("error"), actual.exceptionOrNull()?.libraryRelativeName(), "error")
                        } else {
                            val keypair = actual.getOrThrow()
                            expectEqual(expected.string("privateKey"), keypair.privateKey.hex(), "private key")
                            expectEqual(expected.string("publicKey"), keypair.publicKey.hex(), "public key")
                            if (vector["nonceDeterministic"].asBoolean) {
                                expectEqual(expected.string("nonce"), (keypair as Sr25519Keypair).nonce.hex(), "nonce")
                            }
                        }
                    }

                    else -> error("Unknown sr25519 keypair kind")
                }
            }.onFailure { failures += "$LIBRARY keypairs $label: ${it.message}" }
        }
        assertNoFailures(failures, vectors.objects("keypairs").size, minimum = 85)
        assertEquals(setOf("//" + "x".repeat(40)), nativeAbortPaths)
    }

    @Test
    fun sr25519JsonExportsImportExactly() {
        val decoder = JsonSeedDecoder(Gson())
        val failures = mutableListOf<String>()
        vectors.objects("json_keyring").forEach { vector ->
            runCatching {
                val expected = vector.getAsJsonObject("expected")
                val imported = decoder.decode(vector.string("json"), vector.string("password"))
                val keypair = expected.getAsJsonObject("keypair")
                expectEqual("SR25519", (imported.multiChainEncryption as MultiChainEncryption.Substrate).encryptionType.name, "encryption")
                expectEqual(keypair.string("privateKey"), imported.keypair.privateKey.hex(), "private key")
                expectEqual(keypair.string("publicKey"), imported.keypair.publicKey.hex(), "public key")
                expectEqual(keypair.string("nonce"), (imported.keypair as Sr25519Keypair).nonce.hex(), "nonce")
                expectEqual(expected.nullableString("username"), imported.username, "username")
            }.onFailure { failures += "$LIBRARY json_keyring ${vector.string("input")}: ${it.message}" }
        }
        assertNoFailures(failures, vectors.objects("json_keyring").size, minimum = 3)
    }

    /**
     * Every sr25519 secret 4.2.0 stored still signs: its keypair verifies, and
     * its recovery material derives the stored private and public key.
     */
    @Test
    fun storedSr25519SecretsStillSignAndMatchTheirRecoveryMaterial() {
        val failures = mutableListOf<String>()
        storedRows().forEach { vector ->
            runCatching {
                val stored = StoredSr25519.read(vector)
                expectEqual(vector.string("publicKey"), stored.keypair.publicKey.hex(), "stored public key")
                val message = "fearless-upgrade-matrix-v1|android|${vector.string("case")}".encodeToByteArray()
                val signature = Signer.sign(
                    MultiChainEncryption.Substrate(EncryptionType.SR25519),
                    message,
                    stored.keypair
                ).signature
                expectEqual(true, Signer.verifySr25519(message, signature, stored.keypair.publicKey), "signature verifies")

                stored.recover()?.let { recovered ->
                    expectEqual(stored.keypair.privateKey.hex(), recovered.privateKey.hex(), "recovered private key")
                    expectEqual(stored.keypair.publicKey.hex(), recovered.publicKey.hex(), "recovered public key")
                    if (vector["lastJunctionSoft"]?.takeUnless(JsonElement::isJsonNull)?.asBoolean != true) {
                        expectEqual(stored.keypair.nonce.hex(), recovered.nonce.hex(), "recovered nonce")
                    }
                }
            }.onFailure { failures += "$LIBRARY stored ${vector.string("case")}: ${it.message}" }
        }
        assertNoFailures(failures, storedRows().size, minimum = 104)
    }

    @Test
    fun productionValidatorsAcceptStoredSr25519SecretsUnchanged() {
        val failures = mutableListOf<String>()
        var softPathRows = 0
        storedRows().forEach { vector ->
            if (vector["lastJunctionSoft"]?.takeUnless(JsonElement::isJsonNull)?.asBoolean == true) {
                softPathRows++
            }
            runCatching {
                expectEqual(vector.string("stored"), validateSigning(vector), "sanitized payload")
                val validated = validateWithRecovery(vector)
                expectEqual(vector.string("stored"), validated.first, "validated payload")
                val expectedAssessment = if (StoredSr25519.read(vector).hasRecoveryMaterial) {
                    RecoveryAssessment.Verified
                } else {
                    RecoveryAssessment.None
                }
                expectEqual(expectedAssessment, validated.second, "recovery assessment")
            }.onFailure { failures += "$LIBRARY validator ${vector.string("case")}: ${it.message}" }
        }
        assertNoFailures(failures, storedRows().size, minimum = 104)
        assertTrue("soft-path rows are covered", softPathRows > 0)
    }

    private fun validateSigning(vector: JsonObject): String = when (vector.string("store")) {
        "SubstrateSecrets(V3)" -> WalletRootSecretValidator.validateSubstrateAndSanitize(
            encoded = vector.string("stored"),
            expectedPublicKey = vector.bytes("publicKey"),
            expectedCryptoType = CryptoType.SR25519,
            expectedAccountId = vector.bytes("accountId")
        )

        "ChainAccountSecrets(V2)" -> ChainAccountSecretValidator.validateAndSanitize(
            encoded = vector.string("stored"),
            expectedAccountId = vector.bytes("accountId"),
            expectedPublicKey = vector.bytes("publicKey"),
            expectedCryptoType = CryptoType.SR25519
        )

        else -> error("Unexpected sr25519 store ${vector.string("store")}")
    }

    private fun validateWithRecovery(vector: JsonObject): Pair<String, RecoveryAssessment> =
        when (vector.string("store")) {
            "SubstrateSecrets(V3)" -> WalletRootSecretValidator.validateSubstrate(
                encoded = vector.string("stored"),
                expectedPublicKey = vector.bytes("publicKey"),
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = vector.bytes("accountId")
            ).let { it.canonical to it.assessment }

            "ChainAccountSecrets(V2)" -> ChainAccountSecretValidator.validate(
                encoded = vector.string("stored"),
                expectedAccountId = vector.bytes("accountId"),
                expectedPublicKey = vector.bytes("publicKey"),
                expectedCryptoType = CryptoType.SR25519
            ).let { it.canonical to it.assessment }

            else -> error("Unexpected sr25519 store ${vector.string("store")}")
        }

    private fun storedRows(): List<JsonObject> = vectors.objects("stored_secrets")

    /** Stored keypair plus the recovery material 4.2.0 kept next to it. */
    private class StoredSr25519(
        val keypair: Sr25519Keypair,
        val entropy: ByteArray?,
        val seed: ByteArray?,
        val path: String?
    ) {
        val hasRecoveryMaterial: Boolean
            get() = entropy != null || seed != null

        fun recover(): Sr25519Keypair? {
            val decoded = path?.takeIf(String::isNotEmpty)?.let(SubstrateJunctionDecoder::decode)
            val recoverySeed = when {
                entropy != null -> SubstrateSeedFactory.deriveSeed32(
                    MnemonicCreator.fromEntropy(entropy.copyOf()).words,
                    decoded?.password
                ).seed

                seed != null -> seed
                else -> return null
            }
            return SubstrateKeypairFactory.generate(
                EncryptionType.SR25519,
                recoverySeed,
                decoded?.junctions.orEmpty()
            ) as Sr25519Keypair
        }

        companion object {
            fun read(vector: JsonObject): StoredSr25519 {
                val stored = vector.string("stored")
                return when (vector.string("store")) {
                    "SubstrateSecrets(V3)" -> SubstrateSecrets.read(stored).let {
                        StoredSr25519(
                            keypair = it[SubstrateSecrets.SubstrateKeypair].toKeypair(),
                            entropy = it[SubstrateSecrets.Entropy],
                            seed = it[SubstrateSecrets.Seed],
                            path = it[SubstrateSecrets.SubstrateDerivationPath]
                        )
                    }

                    "ChainAccountSecrets(V2)" -> ChainAccountSecrets.read(stored).let {
                        StoredSr25519(
                            keypair = it[ChainAccountSecrets.Keypair].toKeypair(),
                            entropy = it[ChainAccountSecrets.Entropy],
                            seed = it[ChainAccountSecrets.Seed],
                            path = it[ChainAccountSecrets.DerivationPath]
                        )
                    }

                    else -> error("Unexpected sr25519 store ${vector.string("store")}")
                }
            }

            private fun EncodableStruct<KeyPairSchema>.toKeypair() = Sr25519Keypair(
                privateKey = this[KeyPairSchema.PrivateKey],
                publicKey = this[KeyPairSchema.PublicKey],
                nonce = checkNotNull(this[KeyPairSchema.Nonce]) { "stored sr25519 keypair has no nonce" }
            )
        }
    }

    private fun junctions(path: String) =
        if (path.isEmpty()) emptyList() else SubstrateJunctionDecoder.decode(path).junctions

    private fun abortsNativeDerivation(path: String): Boolean =
        runCatching { junctions(path) }.getOrNull().orEmpty().any { it.chaincode.size != SR25519_CHAIN_CODE_BYTES }

    private fun load(): JsonObject {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val document = assets.open("crypto-golden/$LIBRARY-sr25519.json").use {
            JsonParser.parseReader(InputStreamReader(it, Charsets.UTF_8)).asJsonObject
        }
        check(document["schema"].asString == "fearless-upgrade-matrix/crypto-golden/v1")
        check(document["synthetic_only"].asBoolean)
        return document
    }

    private fun assertNoFailures(failures: List<String>, checked: Int, minimum: Int) {
        if (failures.isNotEmpty()) {
            throw AssertionError("${failures.size} of $checked vectors differ:\n" + failures.joinToString("\n"))
        }
        assertEquals(true, checked >= minimum)
    }

    private fun expectEqual(expected: Any?, actual: Any?, what: String) {
        if (expected != actual) throw AssertionError("$what: expected $expected, got $actual")
    }

    private companion object {
        const val LIBRARY = "shared-features-1.1.1.44-FLW"

        const val SR25519_CHAIN_CODE_BYTES = 32
    }
}

private fun JsonObject.objects(member: String): List<JsonObject> =
    getAsJsonArray(member).map(JsonElement::getAsJsonObject)

private fun JsonObject.string(member: String): String = get(member).asString

private fun JsonObject.nullableString(member: String): String? =
    get(member)?.takeUnless(JsonElement::isJsonNull)?.asString

private fun JsonObject.bytes(member: String): ByteArray = string(member).fromHex()

private fun ByteArray?.hex(): String? = this?.toHexString(withPrefix = true)

private fun Throwable.libraryRelativeName(): String = javaClass.name.removePrefix("jp.co.soramitsu.fearless_utils.")
