package jp.co.soramitsu.common.data.secrets.legacy

import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray

/**
 * Exact encrypted-preference contract shipped by Fearless Wallet 0.4.x.
 *
 * The asynchronous 1.0.0 in-app conversion was not a Room migration, so a
 * device that jumps directly from database version 9 can still contain these
 * keys when Room reaches the v28 account migration.
 */
object LegacyWalletV04Secrets {

    private val legacyKeyPattern =
        Regex("^(private|seed|entropy|derivation)_.+")
    private val signingDataKeyPattern = Regex("^private_.+")

    fun isLegacyKey(key: String): Boolean = legacyKeyPattern.matches(key)

    fun isSigningDataKey(key: String): Boolean =
        signingDataKeyPattern.matches(key)

    fun privateKey(address: String): String = key(PRIVATE_PREFIX, address)

    fun seedKey(address: String): String = key(SEED_PREFIX, address)

    fun entropyKey(address: String): String = key(ENTROPY_PREFIX, address)

    fun derivationKey(address: String): String =
        key(DERIVATION_PREFIX, address)

    fun allKeys(address: String): Set<String> = setOf(
        privateKey(address),
        seedKey(address),
        entropyKey(address),
        derivationKey(address)
    )

    fun decodeSigningData(encoded: String): SigningData {
        WalletSecretScalePreflight.requireLegacyV04SigningData(encoded)
        val decoded = SigningDataSchema.read(encoded)
        return SigningData(
            privateKey = decoded[SigningDataSchema.PrivateKey],
            publicKey = decoded[SigningDataSchema.PublicKey],
            nonce = decoded[SigningDataSchema.Nonce]
        )
    }

    data class SigningData(
        val privateKey: ByteArray,
        val publicKey: ByteArray,
        val nonce: ByteArray?
    )

    private fun key(prefix: String, address: String): String {
        require(address.isNotBlank()) {
            "A legacy wallet preference requires an account address"
        }
        return "$prefix$address"
    }

    private object SigningDataSchema : Schema<SigningDataSchema>() {
        val PrivateKey by byteArray()
        val PublicKey by byteArray()
        val Nonce by byteArray().optional()
    }

    private const val PRIVATE_PREFIX = "private_"
    private const val SEED_PREFIX = "seed_"
    private const val ENTROPY_PREFIX = "entropy_"
    private const val DERIVATION_PREFIX = "derivation_"
}
