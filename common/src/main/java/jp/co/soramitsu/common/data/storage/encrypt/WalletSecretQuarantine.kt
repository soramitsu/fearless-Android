package jp.co.soramitsu.common.data.storage.encrypt

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import org.bouncycastle.util.encoders.Hex

class WalletRecoveryRequiredException :
    IllegalStateException("Wallet signing material requires recovery")

class WalletRecoveryStateIntegrityException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Durable public identity is absent or structurally invalid, so the runtime
 * cannot prove which side of an identity/secret relationship is trustworthy.
 * This is never evidence that an encrypted payload itself is corrupt.
 */
class WalletPublicIdentityIntegrityException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Durable recovery state for an active ciphertext whose database identity is
 * absent, malformed, or internally inconsistent.
 *
 * Unlike [WalletSecretQuarantine], this marker never moves, copies, decrypts,
 * or re-encrypts the active ciphertext. Its key contains only the local wallet
 * id and a SHA-256 digest of the active preference key; its value is a fixed,
 * non-secret protocol token.
 */
object WalletPublicIdentityRecovery {

    const val KEY_PREFIX = "wallet_public_identity_recovery:"
    const val MARKER_VALUE = "fearless-wallet-public-identity-recovery:v1"

    fun keyFor(metaId: Long, activeSecretKey: String): String {
        require(metaId > 0L) {
            "A public-identity recovery marker requires a valid wallet id"
        }
        require(activeSecretKey.isNotBlank()) {
            "A public-identity recovery marker requires an active secret key"
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(activeSecretKey.toByteArray(Charsets.UTF_8))
        return "$KEY_PREFIX$metaId:${Hex.toHexString(digest)}"
    }

    fun rootKeys(metaId: Long): Set<String> {
        require(metaId > 0L) {
            "A public-identity recovery lookup requires a valid wallet id"
        }

        return setOf(
            keyFor(metaId, "$metaId:ACCESS_SECRETS"),
            keyFor(metaId, "$metaId:SUBSTRATE_SECRETS"),
            keyFor(metaId, "$metaId:ETHEREUM_SECRETS"),
            keyFor(metaId, "$metaId:TON_SECRETS")
        )
    }

    fun chainAccountKey(metaId: Long, accountId: ByteArray): String {
        require(accountId.isNotEmpty()) {
            "A public-identity recovery lookup requires an account id"
        }
        return keyFor(
            metaId = metaId,
            activeSecretKey =
            "$metaId:${Hex.toHexString(accountId)}:ACCESS_SECRETS"
        )
    }
}

/**
 * Stable, non-secret location for an unreadable wallet ciphertext.
 *
 * The stored value remains encrypted exactly as it was found. Presence of this
 * key means the corresponding active signing payload needs recovery; callers
 * must never treat it as a usable signing secret.
 */
object WalletSecretQuarantine {

    const val KEY_PREFIX = "wallet_secret_quarantine:"

    fun keyFor(activeSecretKey: String): String {
        require(activeSecretKey.isNotBlank()) {
            "A wallet-secret quarantine source key cannot be blank"
        }
        require(!activeSecretKey.startsWith(KEY_PREFIX)) {
            "A quarantined wallet secret cannot be quarantined again"
        }

        return "$KEY_PREFIX$activeSecretKey"
    }

    fun legacyV1KeyForPublicKey(publicKey: ByteArray): String {
        require(publicKey.isNotEmpty()) {
            "A legacy wallet quarantine key requires a public key"
        }
        return "${KEY_PREFIX}legacy_v1_public_${Hex.toHexString(publicKey)}"
    }

    fun legacyV1KeyForMetaId(
        metaId: Long,
        publicKey: ByteArray
    ): String {
        require(metaId > 0L) {
            "A legacy wallet quarantine requires a valid wallet id"
        }
        require(publicKey.isNotEmpty()) {
            "A legacy wallet quarantine key requires a public key"
        }
        return "${KEY_PREFIX}legacy_v1_meta_${metaId}_public_" +
            Hex.toHexString(publicKey)
    }

    fun legacyV04KeyForMetaId(
        metaId: Long,
        publicKey: ByteArray
    ): String {
        require(metaId > 0L) {
            "A legacy wallet quarantine requires a valid wallet id"
        }
        require(publicKey.isNotEmpty()) {
            "A legacy wallet quarantine key requires a public key"
        }
        return "${KEY_PREFIX}legacy_v04_meta_${metaId}_public_" +
            Hex.toHexString(publicKey)
    }

    fun rootKeys(metaId: Long, substratePublicKey: ByteArray?): Set<String> {
        require(metaId > 0) { "A wallet quarantine lookup requires a valid id" }

        return buildSet {
            add(keyFor("$metaId:ACCESS_SECRETS"))
            add(keyFor("$metaId:SUBSTRATE_SECRETS"))
            add(keyFor("$metaId:ETHEREUM_SECRETS"))
            add(keyFor("$metaId:TON_SECRETS"))
            substratePublicKey?.takeIf(ByteArray::isNotEmpty)?.let {
                add(legacyV1KeyForMetaId(metaId, it))
                add(legacyV04KeyForMetaId(metaId, it))
            }
        }
    }

    fun chainAccountKey(metaId: Long, accountId: ByteArray): String {
        require(metaId > 0) { "A chain-account quarantine lookup requires a valid wallet id" }
        require(accountId.isNotEmpty()) {
            "A chain-account quarantine lookup requires an account id"
        }

        return keyFor("$metaId:${Hex.toHexString(accountId)}:ACCESS_SECRETS")
    }
}

@Singleton
class WalletSecretAccessGuard @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences
) {

    val recoveryStateVersion: StateFlow<Long>
        get() = encryptedPreferences.walletSecretQuarantineVersion

    /**
     * Startup-wide recovery inventory. Migration may quarantine a secret or
     * publish a public-identity recovery marker for a wallet that is not the
     * currently selected account, so the launcher must not inspect only one
     * meta-account before admitting the process.
     */
    fun hasAnyRecoveryState(): Boolean {
        return try {
            encryptedPreferences.keysWithPrefixes(
                prefixes = setOf(
                    WalletSecretQuarantine.KEY_PREFIX,
                    WalletPublicIdentityRecovery.KEY_PREFIX
                ),
                maxResultCount = MAX_RECOVERY_MARKER_COUNT,
                maxKeyBytes = MAX_RECOVERY_MARKER_KEY_BYTES,
                maxTotalKeyBytes = MAX_RECOVERY_MARKER_TOTAL_BYTES,
                failOnOversizedMatch = true
            ).isNotEmpty()
        } catch (failure: WalletRecoveryStateIntegrityException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletRecoveryStateIntegrityException(
                "Wallet recovery-state inventory exceeds its safe bounds",
                failure
            )
        }
    }

    fun isRecoveryRequired(
        metaId: Long,
        substratePublicKey: ByteArray? = null,
        chainAccountId: ByteArray? = null
    ): Boolean {
        if (
            chainAccountId != null &&
            chainAccountId.size !in CHAIN_ACCOUNT_ID_BYTE_LENGTHS
        ) {
            return true
        }
        val quarantinedRoot = WalletSecretQuarantine
            .rootKeys(metaId, substratePublicKey)
            .any(encryptedPreferences::hasKey)
        val quarantinedChainAccount = chainAccountId?.let {
            encryptedPreferences.hasKey(
                WalletSecretQuarantine.chainAccountKey(metaId, it)
            )
        } == true
        val invalidRootIdentity = WalletPublicIdentityRecovery
            .rootKeys(metaId)
            .any(encryptedPreferences::hasKey)
        val invalidChainIdentity = chainAccountId?.let {
            encryptedPreferences.hasKey(
                WalletPublicIdentityRecovery.chainAccountKey(metaId, it)
            )
        } == true

        return quarantinedRoot ||
            quarantinedChainAccount ||
            invalidRootIdentity ||
            invalidChainIdentity
    }

    fun requireAccess(
        metaId: Long,
        substratePublicKey: ByteArray? = null,
        chainAccountId: ByteArray? = null
    ) {
        if (isRecoveryRequired(metaId, substratePublicKey, chainAccountId)) {
            throw WalletRecoveryRequiredException()
        }
    }

    fun legacyV1PlaintextSnapshot(
        accountAddress: String
    ): EncryptedPreferenceSnapshot? {
        require(accountAddress.isNotBlank()) {
            "A legacy wallet snapshot requires an account address"
        }
        return encryptedPreferences.getDecryptedStringSnapshot(
            "security_source_$accountAddress"
        )
    }

    fun quarantineLegacyV1AndThrow(
        metaId: Long,
        accountAddress: String,
        expectedPublicKey: ByteArray,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Nothing {
        require(accountAddress.isNotBlank()) {
            "A legacy wallet quarantine requires an account address"
        }
        require(expectedPublicKey.isNotEmpty()) {
            "A legacy wallet quarantine requires the expected public key"
        }

        val activeKey = "security_source_$accountAddress"
        encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
            sourceKey = activeKey,
            quarantineKey = WalletSecretQuarantine.legacyV1KeyForMetaId(
                metaId = metaId,
                publicKey = expectedPublicKey
            ),
            expectedSnapshot = expectedSnapshot
        )
        throw WalletRecoveryRequiredException()
    }

    private companion object {
        val CHAIN_ACCOUNT_ID_BYTE_LENGTHS = setOf(20, 32)
        const val MAX_RECOVERY_MARKER_COUNT = 8_192
        const val MAX_RECOVERY_MARKER_KEY_BYTES = 1_024
        const val MAX_RECOVERY_MARKER_TOTAL_BYTES = 1_048_576
    }
}

internal const val MAX_WALLET_SECRET_PLAINTEXT_CHARS = 1_048_576

internal inline fun <T> EncryptedPreferences.readWalletSecretOrQuarantine(
    activeSecretKey: String,
    decode: (String) -> T
): T? {
    val quarantineKey = WalletSecretQuarantine.keyFor(activeSecretKey)
    if (hasKey(quarantineKey)) {
        throw WalletRecoveryRequiredException()
    }
    if (!hasKey(activeSecretKey)) return null

    val snapshot = checkNotNull(
        getDecryptedStringSnapshot(activeSecretKey)
    ) {
        "A wallet secret disappeared during validation"
    }
    val encodedSecret = snapshot.plaintext
    return try {
        check(
            encodedSecret.isNotEmpty() &&
                encodedSecret.length <= MAX_WALLET_SECRET_PLAINTEXT_CHARS
        ) {
            "Wallet secret payload is empty or exceeds the safe decode limit"
        }
        decode(encodedSecret)
    } catch (failure: WalletSecureStorageUnavailableException) {
        throw failure
    } catch (failure: WalletRecoveryRequiredException) {
        throw failure
    } catch (failure: Exception) {
        quarantineEncryptedStringSnapshotDurably(
            sourceKey = activeSecretKey,
            quarantineKey = quarantineKey,
            expectedSnapshot = snapshot
        )
        throw WalletRecoveryRequiredException()
    }
}

/**
 * Reads a payload whose validator has an explicit record-local corruption
 * type. Storage access and durable mutation stay outside the validation catch;
 * unrelated runtime, database-identity, provider, and native failures
 * propagate without touching the active ciphertext.
 */
internal inline fun <T> EncryptedPreferences
    .readValidatedWalletSecretOrQuarantine(
        activeSecretKey: String,
        isLocalCorruption: (Exception) -> Boolean,
        decodeAndValidate: (String) -> T
    ): T? {
    val quarantineKey = WalletSecretQuarantine.keyFor(activeSecretKey)
    if (hasKey(quarantineKey)) {
        throw WalletRecoveryRequiredException()
    }
    if (!hasKey(activeSecretKey)) return null

    val snapshot = checkNotNull(
        getDecryptedStringSnapshot(activeSecretKey)
    ) {
        "A wallet secret disappeared during validation"
    }
    val encodedSecret = snapshot.plaintext
    val validated = try {
        decodeAndValidate(encodedSecret)
    } catch (failure: WalletSecureStorageUnavailableException) {
        throw failure
    } catch (failure: WalletRecoveryRequiredException) {
        throw failure
    } catch (failure: Exception) {
        if (isLocalCorruption(failure)) {
            null
        } else {
            throw failure
        }
    }

    if (validated == null) {
        quarantineEncryptedStringSnapshotDurably(
            sourceKey = activeSecretKey,
            quarantineKey = quarantineKey,
            expectedSnapshot = snapshot
        )
        throw WalletRecoveryRequiredException()
    }

    return validated
}
