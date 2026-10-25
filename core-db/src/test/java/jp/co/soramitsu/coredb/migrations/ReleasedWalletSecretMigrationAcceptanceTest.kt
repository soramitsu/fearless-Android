package jp.co.soramitsu.coredb.migrations

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The 76 -> 77 integrity pass must keep every secret exactly as 4.2.0 (or
 * 3.7.x for chain accounts) stored it: nothing a released build wrote may be
 * quarantined, or rewritten beyond dropping a path that binds nothing. The
 * vectors are common's golden vectors from the production-parity oracle; the
 * pre-K1 migration quarantined the 20 TON phrases typed with extra spaces,
 * capitals or a newline. SR25519 needs the shipped native library and is
 * covered on device by `ReleasedWalletSecretMigrationAcceptanceDeviceTest`.
 */
class ReleasedWalletSecretMigrationAcceptanceTest {

    private val vectors = loadVectors()
        .filterNot { it.optString("cryptoType") == "SR25519" }

    @Test
    fun migrationKeepsEveryNonSr25519SecretStoredByReleasedBuilds() {
        assertEquals(155, vectors.size)
        assertEquals(
            20,
            vectors.count { it.getAsJsonObject("rc")["verdict"].asString == "reject" }
        )

        vectors.forEach { vector ->
            val case = vector["case"].asString
            val stored = vector["stored"].asString
            val kept = validateAsMigrationDoes(vector)
            if (vector.sanitizerChangedFields().isEmpty()) {
                assertEquals(case, stored, kept)
            } else {
                // A raw private-key import stored the default path without
                // entropy; only that unbound path leaves the payload.
                assertEquals(case, listOf("EthereumDerivationPath"), vector.sanitizerChangedFields())
                assertNull(case, EthereumSecrets.read(kept)[EthereumSecrets.EthereumDerivationPath])
            }
        }
    }

    @Test
    fun tonPhraseTypedInto420IsKeptByteForByte() {
        val ton = vectors.filter { it["store"].asString == "TonSecrets(V3)" }
        assertEquals(24, ton.size)
        assertEquals(
            setOf(
                "canonical",
                "trailing-space",
                "leading-space",
                "double-space",
                "upper",
                "trailing-newline"
            ),
            ton.map { it["typedVariant"].asString }.toSet()
        )

        ton.forEach { vector ->
            assertEquals(
                vector["case"].asString,
                vector["stored"].asString,
                WalletSecretIntegrityValidator.validateTonV3(
                    encoded = vector["stored"].asString,
                    identity = identity(tonPublicKey = vector["publicKey"].asString.fromHex())
                )
            )
        }
    }

    private fun validateAsMigrationDoes(vector: JsonObject): String {
        val stored = vector["stored"].asString
        val publicKey = vector["publicKey"].asString.fromHex()
        return when (vector["store"].asString) {
            "SubstrateSecrets(V3)" -> WalletSecretIntegrityValidator.validateSubstrateV3(
                encoded = stored,
                identity = identity(
                    substratePublicKey = publicKey,
                    substrateCryptoType = CryptoType.valueOf(vector["cryptoType"].asString),
                    substrateAccountId = vector["accountId"].asString.fromHex()
                )
            )

            "EthereumSecrets(V3)" -> WalletSecretIntegrityValidator.validateEthereumV3(
                encoded = stored,
                identity = identity(
                    ethereumPublicKey = publicKey,
                    ethereumAddress = vector["address"].asString.fromHex()
                )
            )

            "TonSecrets(V3)" -> WalletSecretIntegrityValidator.validateTonV3(
                encoded = stored,
                identity = identity(tonPublicKey = publicKey)
            )

            // The default validation WalletSecretIntegrityMigration applies to
            // every chain-account secret.
            "ChainAccountSecrets(V2)" -> ChainAccountSecretValidator.validateAndSanitize(
                encoded = stored,
                expectedAccountId = vector["accountId"].asString.fromHex(),
                expectedPublicKey = publicKey,
                expectedCryptoType = CryptoType.valueOf(
                    vector["cryptoType"].asString.removeSuffix("(ethereum)")
                )
            )

            else -> error("Unknown store in ${vector["case"].asString}")
        }
    }

    private fun identity(
        substratePublicKey: ByteArray? = null,
        substrateCryptoType: CryptoType? = null,
        substrateAccountId: ByteArray? = null,
        ethereumPublicKey: ByteArray? = null,
        ethereumAddress: ByteArray? = null,
        tonPublicKey: ByteArray? = null
    ) = WalletPublicIdentity(
        metaId = 1,
        substratePublicKey = substratePublicKey,
        substrateCryptoType = substrateCryptoType,
        substrateCryptoTypeWasPresent = substrateCryptoType != null,
        substrateAccountId = substrateAccountId,
        ethereumPublicKey = ethereumPublicKey,
        ethereumAddress = ethereumAddress,
        tonPublicKey = tonPublicKey
    )

    private fun JsonObject.sanitizerChangedFields(): List<String> =
        getAsJsonObject("rc")
            .getAsJsonArray("sanitizerChangedFields")
            ?.map { it.asString }
            .orEmpty()

    private fun JsonObject.optString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun loadVectors(): List<JsonObject> {
        // core-db's test resources include common/src/test/resources/wallet-secret-vectors.
        val stream = checkNotNull(javaClass.getResourceAsStream("/12-validator-acceptance.json")) {
            "Missing the released wallet-secret vectors"
        }
        val root = InputStreamReader(stream, Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject
        val vectors = root.getAsJsonArray("vectors").map { it.asJsonObject }
        check(vectors.size == root["count"].asInt) { "Vector count mismatch" }
        return vectors
    }
}
