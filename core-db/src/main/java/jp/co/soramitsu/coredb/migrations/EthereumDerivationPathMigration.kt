package jp.co.soramitsu.coredb.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.WalletMetaAccountScaleLayout
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.common.data.storage.encrypt.requireSnapshotMovesReady
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.isValidEthereumCompressedPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.runBlocking

internal class EthereumDerivationPathSecretCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

private class SeparatedSubstrateSecretCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

internal interface EthereumDerivationPathCryptography {

    fun decodePath(path: String): JunctionDecoder.DecodeResult

    fun deriveKeypair(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair

    fun deriveAddress(publicKey: ByteArray): ByteArray
}

internal fun interface Db31WalletIdentityUpdater {

    fun update(
        database: SupportSQLiteDatabase,
        metaId: Long,
        publicKey: ByteArray,
        address: ByteArray
    ): Int
}

internal data class EthereumDerivationPathMigrationLimits(
    val maxAssetRows: Int
) {
    init {
        require(maxAssetRows in 1 until Int.MAX_VALUE)
    }

    companion object {
        val PRODUCTION = EthereumDerivationPathMigrationLimits(
            maxAssetRows = 1_048_576
        )
    }
}

class EthereumDerivationPathMigration internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val cryptography: EthereumDerivationPathCryptography,
    private val walletRootSecretValidation: WalletRootSecretValidation,
    private val limits: EthereumDerivationPathMigrationLimits =
        EthereumDerivationPathMigrationLimits.PRODUCTION,
    private val walletIdentityUpdater: Db31WalletIdentityUpdater =
        ProductionDb31WalletIdentityUpdater
) : Migration(31, 32) {

    constructor(encryptedPreferences: EncryptedPreferences) : this(
        encryptedPreferences = encryptedPreferences,
        cryptography = ProductionEthereumDerivationPathCryptography,
        walletRootSecretValidation = WalletRootSecretValidator
    )

    internal constructor(
        encryptedPreferences: EncryptedPreferences,
        walletIdentityUpdater: Db31WalletIdentityUpdater
    ) : this(
        encryptedPreferences = encryptedPreferences,
        cryptography = ProductionEthereumDerivationPathCryptography,
        walletRootSecretValidation = WalletRootSecretValidator,
        walletIdentityUpdater = walletIdentityUpdater
    )

    internal constructor(
        encryptedPreferences: EncryptedPreferences,
        limits: EthereumDerivationPathMigrationLimits
    ) : this(
        encryptedPreferences = encryptedPreferences,
        cryptography = ProductionEthereumDerivationPathCryptography,
        walletRootSecretValidation = WalletRootSecretValidator,
        limits = limits
    )

    override fun migrate(database: SupportSQLiteDatabase) = runBlocking {
        Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(database)
        encryptedPreferences.requireDurableStorageHealthy()
        requireBoundedMigrationTableRows(
            database = database,
            tableName = "assets",
            maximumRows = limits.maxAssetRows,
            rowDescription = "DB31 asset rows"
        )
        WalletSecretMigrationPreflight.requireSafeToMutate(
            database = database,
            encryptedPreferences = encryptedPreferences,
            includeTonPublicKey = false
        )

        val ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        val decodedEthereumDerivationPath = performCryptography(
            operation = "default Ethereum derivation-path decoding"
        ) {
            cryptography.decodePath(ethereumDerivationPath)
        }

        // Encrypted preferences are not part of Room's SQL transaction. The
        // first pass performs every decode, identity check and destination
        // conflict check without writing either store. Therefore a malformed
        // or conflicting late wallet cannot strand mutations from an earlier
        // wallet when Room rolls its transaction back.
        listOf(false, true).forEach { executeActions ->
            forEachBoundedWalletPublicIdentity(
                database = database,
                includeTonPublicKey = false
            ) { identity ->
                val account = MigratingEthereumAccount(
                    metaId = identity.metaId,
                    substratePublicKey = identity.substratePublicKey,
                    substrateCryptoType = identity.substrateCryptoType,
                    substrateAccountId = identity.substrateAccountId,
                    ethereumPublicKey = identity.ethereumPublicKey,
                    ethereumAddress = identity.ethereumAddress
                )
                val activeRootSecretKeys =
                    presentMigrationRootSecretKeys(account.metaId)
                val recoveryStates =
                    activeRootSecretKeys.map { activeSecretKey ->
                        hasValidPublicIdentityRecovery(
                            metaId = account.metaId,
                            activeSecretKey = activeSecretKey
                        )
                    }
                if (
                    recoveryStates.isNotEmpty() &&
                    recoveryStates.all { it }
                ) {
                    return@forEachBoundedWalletPublicIdentity
                }
                requireUnambiguousRootSecretRepresentation(
                    metaId = account.metaId,
                    activeRootSecretKeys = activeRootSecretKeys
                )

                try {
                    // Complete every public-identity check before any ciphertext
                    // replacement or quarantine. An identity failure must leave
                    // both the DB row and every active ciphertext byte-for-byte
                    // untouched; only fixed recovery markers may be persisted.
                    val oldAddress =
                        validateHistoricalEthereumIdentity(account)
                    validateRequiredSubstrateIdentity(
                        account = account,
                        activeRootSecretKeys = activeRootSecretKeys
                    )
                    val v2Preparation = prepareV2SecretMigration(
                        account = account,
                        ethereumDerivationPath = ethereumDerivationPath,
                        decodedEthereumDerivationPath =
                            decodedEthereumDerivationPath
                    )

                    val separatedPreparation = if (
                        v2Preparation.preparedMigration == null
                    ) {
                        prepareSeparatedEthereumRetry(account)
                    } else {
                        SeparatedEthereumRetryPreparation.Absent
                    }

                    v2Preparation.quarantine?.let {
                        val snapshotMoves = listOf(
                            EncryptedPreferenceSnapshotMove(
                                sourceKey = it.activeSecretKey,
                                destinationKey =
                                    WalletSecretQuarantine.keyFor(
                                        it.activeSecretKey
                                    ),
                                expectedSnapshot = it.expectedSnapshot
                            )
                        )
                        if (executeActions) {
                            encryptedPreferences
                                .replaceEncryptedStringsForStatesDurably(
                                    expectedStates =
                                        expectedSeparatedRootAbsenceStates(
                                            account.metaId
                                        ),
                                    valuesToPut = emptyMap(),
                                    keysToRemove = emptySet(),
                                    snapshotMoves = snapshotMoves
                                )
                        } else {
                            encryptedPreferences.requireSnapshotMovesReady(
                                snapshotMoves
                            )
                        }
                    }
                    v2Preparation.preparedMigration
                        ?.takeIf { executeActions }
                        ?.let { prepared ->
                            // Keep durable storage failures outside the
                            // corrupt-payload catch path. A failed commit must
                            // roll Room back and must never quarantine an
                            // otherwise valid wallet.
                            encryptedPreferences
                                .replaceEncryptedStringsForStatesDurably(
                                    expectedStates = buildMap {
                                        put(
                                            prepared.activeSecretKey,
                                            prepared.expectedSnapshot
                                        )
                                        putAll(
                                            expectedSeparatedRootAbsenceStates(
                                                account.metaId
                                            )
                                        )
                                    },
                                    valuesToPut = if (
                                        prepared.expectedSnapshot.plaintext ==
                                        prepared.encodedSecrets
                                    ) {
                                        emptyMap()
                                    } else {
                                        mapOf(
                                            prepared.activeSecretKey to
                                                prepared.encodedSecrets
                                        )
                                    },
                                    keysToRemove = emptySet()
                                )
                        }

                    val separatedEthereumIdentity =
                        executeSeparatedEthereumRetry(
                            preparation = separatedPreparation,
                            executeAction = executeActions
                        )
                    val targetEthereumIdentity =
                        v2Preparation.preparedMigration?.ethereumIdentity
                            ?: separatedEthereumIdentity
                    if (targetEthereumIdentity == null) {
                        return@forEachBoundedWalletPublicIdentity
                    }
                    if (!executeActions) {
                        return@forEachBoundedWalletPublicIdentity
                    }

                    val updatedRows = walletIdentityUpdater.update(
                        database = database,
                        metaId = account.metaId,
                        publicKey = targetEthereumIdentity.publicKey,
                        address = targetEthereumIdentity.address
                    )
                    if (updatedRows != 1) {
                        throw WalletSecretConcurrentMutationException(
                            "The DB31 wallet identity update matched " +
                                "$updatedRows rows instead of one"
                        )
                    }

                    // Read the old address from the transactional database, not
                    // preferences. If a prior attempt durably wrote either the
                    // v2 or separated v3 secret but Room later rolled its SQL
                    // back, retry still knows which asset rows must be updated.
                    oldAddress?.let {
                        database.updateAccountIdForAsset(
                            metaId = account.metaId,
                            oldAddress = it,
                            ethereumAddress = targetEthereumIdentity.address
                        )
                    }
                } catch (failure: WalletPublicIdentityIntegrityException) {
                    if (executeActions) {
                        markPublicIdentityRecovery(
                            metaId = account.metaId,
                            activeSecretKeys = activeRootSecretKeys
                        )
                    }
                }
            }
        }
    }

    private fun prepareV2SecretMigration(
        account: MigratingEthereumAccount,
        ethereumDerivationPath: String,
        decodedEthereumDerivationPath: JunctionDecoder.DecodeResult
    ): V2SecretPreparation {
        val metaId = account.metaId
        val activeSecretKey = "$metaId:ACCESS_SECRETS"
        if (!encryptedPreferences.hasKey(activeSecretKey)) {
            return V2SecretPreparation()
        }

        // Storage/provider access is outside the record-local catch. A transient
        // decrypt failure is not evidence that this one payload is corrupt.
        val snapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(activeSecretKey)
        ) {
            "A DB31 wallet secret disappeared during migration"
        }
        val encoded = snapshot.plaintext
        val prepared = try {
            prepareDecodedV2SecretMigration(
                encoded = encoded,
                account = account,
                activeSecretKey = activeSecretKey,
                expectedSnapshot = snapshot,
                ethereumDerivationPath = ethereumDerivationPath,
                decodedEthereumDerivationPath = decodedEthereumDerivationPath
            )
        } catch (failure: EthereumDerivationPathSecretCorruptionException) {
            null
        } catch (failure: WalletRootSecretCorruptionException) {
            null
        }

        return if (prepared == null && encodedHasEntropy(encoded)) {
            V2SecretPreparation(
                quarantine = PendingSecretQuarantine(
                    activeSecretKey = activeSecretKey,
                    expectedSnapshot = snapshot
                )
            )
        } else {
            V2SecretPreparation(preparedMigration = prepared)
        }
    }

    private fun presentMigrationRootSecretKeys(metaId: Long): List<String> {
        return listOf(
            "$metaId:ACCESS_SECRETS",
            "$metaId:SUBSTRATE_SECRETS",
            "$metaId:ETHEREUM_SECRETS"
        ).filter(encryptedPreferences::hasKey)
    }

    private fun requireUnambiguousRootSecretRepresentation(
        metaId: Long,
        activeRootSecretKeys: List<String>
    ) {
        val accessSecretKey = "$metaId:ACCESS_SECRETS"
        if (
            accessSecretKey in activeRootSecretKeys &&
            activeRootSecretKeys.any { it != accessSecretKey }
        ) {
            throw WalletSecretConcurrentMutationException(
                "A DB31 wallet has simultaneous V2 and V3 root secrets"
            )
        }
    }

    private fun expectedSeparatedRootAbsenceStates(
        metaId: Long
    ): Map<String, EncryptedPreferenceSnapshot?> {
        return mapOf(
            "$metaId:SUBSTRATE_SECRETS" to null,
            "$metaId:ETHEREUM_SECRETS" to null
        )
    }

    private fun validateHistoricalEthereumIdentity(
        account: MigratingEthereumAccount
    ): ByteArray? {
        val publicKey = account.ethereumPublicKey
        val address = account.ethereumAddress
        ensurePublicIdentity(
            (publicKey == null) == (address == null),
            "A DB31 Ethereum identity is only partially present"
        )
        if (publicKey == null) return null

        val exactAddress = checkNotNull(address)
        ensurePublicIdentity(
            publicKey.hasEthereumPublicKeyShape() &&
                exactAddress.size == ETHEREUM_ADDRESS_BYTES,
            "A DB31 Ethereum identity has an invalid shape"
        )
        val derivedAddress =
            performCryptography("durable Ethereum address derivation") {
                cryptography.deriveAddress(publicKey)
            }
        ensureOperational(
            derivedAddress.size == ETHEREUM_ADDRESS_BYTES,
            "The Ethereum provider returned an invalid durable address"
        )
        ensurePublicIdentity(
            derivedAddress.contentEquals(exactAddress),
            "A DB31 Ethereum public key does not match its address"
        )
        return derivedAddress
    }

    private fun validateRequiredSubstrateIdentity(
        account: MigratingEthereumAccount,
        activeRootSecretKeys: List<String>
    ) {
        if (activeRootSecretKeys.isEmpty()) return

        val publicKey = account.substratePublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "An active DB31 root secret has no durable Substrate public key"
            )
        val cryptoType = account.substrateCryptoType
            ?: throw WalletPublicIdentityIntegrityException(
                "An active DB31 root secret has no durable Substrate crypto type"
            )
        val accountId = account.substrateAccountId
            ?: throw WalletPublicIdentityIntegrityException(
                "An active DB31 root secret has no durable Substrate account id"
            )
        val expectedPublicKeyBytes = when (cryptoType) {
            CryptoType.SR25519,
            CryptoType.ED25519 -> SUBSTRATE_PUBLIC_KEY_BYTES
            CryptoType.ECDSA -> ECDSA_PUBLIC_KEY_BYTES
        }
        ensurePublicIdentity(
            publicKey.size == expectedPublicKeyBytes &&
                accountId.size == SUBSTRATE_ACCOUNT_ID_BYTES,
            "An active DB31 root secret has a malformed Substrate identity"
        )
        val derivedAccountId = performCryptography(
            operation = "durable DB31 Substrate account-id derivation"
        ) {
            publicKey.substrateAccountId()
        }
        ensureOperational(
            derivedAccountId.size == SUBSTRATE_ACCOUNT_ID_BYTES,
            "The Substrate provider returned an invalid account id"
        )
        ensurePublicIdentity(
            derivedAccountId.contentEquals(accountId),
            "A DB31 Substrate public key does not match its account id"
        )
    }

    private fun markPublicIdentityRecovery(
        metaId: Long,
        activeSecretKeys: List<String>
    ) {
        val missingMarkers = buildMap {
            activeSecretKeys.forEach { activeSecretKey ->
                val markerKey = WalletPublicIdentityRecovery.keyFor(
                    metaId = metaId,
                    activeSecretKey = activeSecretKey
                )
                if (
                    !hasValidPublicIdentityRecovery(
                        metaId = metaId,
                        activeSecretKey = activeSecretKey
                    )
                ) {
                    put(
                        markerKey,
                        WalletPublicIdentityRecovery.MARKER_VALUE
                    )
                }
            }
        }
        if (missingMarkers.isNotEmpty()) {
            val expectedMarkerStates:
                Map<String, EncryptedPreferenceSnapshot?> =
                missingMarkers.keys.associateWith { null }
            encryptedPreferences.replaceEncryptedStringsForStatesDurably(
                expectedStates = expectedMarkerStates,
                valuesToPut = missingMarkers,
                keysToRemove = emptySet()
            )
        }
    }

    private fun hasValidPublicIdentityRecovery(
        metaId: Long,
        activeSecretKey: String
    ): Boolean {
        return WalletSecretMigrationPreflight.hasValidPublicIdentityRecovery(
            encryptedPreferences = encryptedPreferences,
            metaId = metaId,
            activeSecretKey = activeSecretKey
        )
    }

    private fun prepareDecodedV2SecretMigration(
        encoded: String,
        account: MigratingEthereumAccount,
        activeSecretKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot,
        ethereumDerivationPath: String,
        decodedEthereumDerivationPath: JunctionDecoder.DecodeResult
    ): PreparedEthereumSecretMigration? {
        val secrets = decodeHistoricalV2Secrets(encoded)
        val entropy = secrets.entropy ?: return null
        ensureLocal(
            entropy.size in VALID_ENTROPY_LENGTHS,
            "A DB31 wallet entropy value has an invalid length"
        )

        val substratePublicKey = account.substratePublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "A DB31 wallet secret has no durable Substrate public key"
            )
        val substrateCryptoType = account.substrateCryptoType
            ?: throw WalletPublicIdentityIntegrityException(
                "A DB31 wallet secret has no durable Substrate crypto type"
            )
        val substrateAccountId = account.substrateAccountId
            ?: throw WalletPublicIdentityIntegrityException(
                "A DB31 wallet secret has no durable Substrate account id"
            )
        walletRootSecretValidation.validateSubstrateAndSanitize(
            encoded = SubstrateSecrets(
                substrateKeyPair = secrets.substrateKeypair,
                entropy = entropy,
                seed = secrets.seed,
                substrateDerivationPath = secrets.substrateDerivationPath
            ).toHexString(),
            expectedPublicKey = substratePublicKey,
            expectedCryptoType = substrateCryptoType,
            expectedAccountId = substrateAccountId
        )

        val newEthereumKeypair = performCryptography(
            operation = "DB31 Ethereum key derivation"
        ) {
            cryptography.deriveKeypair(
                entropy = entropy.clone(),
                decodedPath = decodedEthereumDerivationPath
            )
        }
        ensureOperational(
            newEthereumKeypair.privateKey.size == PRIVATE_KEY_BYTES &&
                newEthereumKeypair.publicKey.hasEthereumPublicKeyShape(),
            "The Ethereum provider returned an invalid keypair"
        )
        val ethereumAddress = performCryptography(
            operation = "derived Ethereum address calculation"
        ) {
            cryptography.deriveAddress(newEthereumKeypair.publicKey)
        }
        ensureOperational(
            ethereumAddress.size == ETHEREUM_ADDRESS_BYTES,
            "The Ethereum provider returned an invalid address"
        )
        validateHistoricalOrCorrectedEthereumBinding(
            secrets = secrets,
            account = account,
            correctedKeypair = newEthereumKeypair,
            correctedAddress = ethereumAddress,
            correctedPath = ethereumDerivationPath
        )

        val newSecrets = MetaAccountSecrets(
            substrateKeyPair = secrets.substrateKeypair,
            entropy = entropy,
            seed = secrets.seed,
            substrateDerivationPath = secrets.substrateDerivationPath,
            ethereumKeypair = newEthereumKeypair,
            ethereumDerivationPath = ethereumDerivationPath,
            tonKeypair = null
        )

        return PreparedEthereumSecretMigration(
            activeSecretKey = activeSecretKey,
            expectedSnapshot = expectedSnapshot,
            encodedSecrets = newSecrets.toHexString(),
            ethereumIdentity = EthereumIdentity(
                publicKey = newEthereumKeypair.publicKey,
                address = ethereumAddress
            )
        )
    }

    /**
     * Avoids quarantining an intentionally non-migrating payload with no
     * entropy while still making the corrupt-payload branch explicit.
     */
    private fun encodedHasEntropy(encoded: String): Boolean {
        return try {
            decodeHistoricalV2Secrets(encoded).entropy != null
        } catch (_: EthereumDerivationPathSecretCorruptionException) {
            true
        }
    }

    private fun validateHistoricalOrCorrectedEthereumBinding(
        secrets: HistoricalV2Secrets,
        account: MigratingEthereumAccount,
        correctedKeypair: FearlessKeypair,
        correctedAddress: ByteArray,
        correctedPath: String
    ) {
        val ethereumKeypair = secrets.ethereumKeypair
        if (ethereumKeypair == null) {
            if (
                account.ethereumPublicKey != null ||
                account.ethereumAddress != null
            ) {
                throw WalletPublicIdentityIntegrityException(
                    "A DB31 Ethereum identity exists without its historical secret"
                )
            }
            ensureLocal(
                secrets.ethereumDerivationPath == null,
                "A DB31 Ethereum path exists without an Ethereum keypair"
            )
            return
        }

        val expectedPublicKey = account.ethereumPublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "A DB31 Ethereum secret has no durable public key"
            )
        val expectedAddress = account.ethereumAddress
            ?: throw WalletPublicIdentityIntegrityException(
                "A DB31 Ethereum secret has no durable address"
            )
        ensurePublicIdentity(
            expectedPublicKey.hasEthereumPublicKeyShape() &&
                expectedAddress.size == ETHEREUM_ADDRESS_BYTES,
            "A DB31 Ethereum identity has an invalid shape"
        )
        val durableAddress = performCryptography(
            operation = "DB31 durable Ethereum identity derivation"
        ) {
            cryptography.deriveAddress(expectedPublicKey)
        }
        ensureOperational(
            durableAddress.size == ETHEREUM_ADDRESS_BYTES,
            "The Ethereum provider returned an invalid durable address"
        )
        ensurePublicIdentity(
            durableAddress.contentEquals(expectedAddress),
            "A DB31 Ethereum public key does not match its address"
        )

        val isCorrectedRetry =
            secrets.ethereumDerivationPath == correctedPath &&
                ethereumKeypair.privateKey.contentEquals(
                    correctedKeypair.privateKey
                ) &&
                ethereumKeypair.publicKey.contentEquals(
                    correctedKeypair.publicKey
                )
        if (isCorrectedRetry) {
            walletRootSecretValidation.validateEthereumAndSanitize(
                encoded = EthereumSecrets(
                    entropy = secrets.entropy,
                    seed = ethereumKeypair.privateKey,
                    ethereumKeypair = ethereumKeypair,
                    ethereumDerivationPath =
                        secrets.ethereumDerivationPath
                ).toHexString(),
                expectedPublicKey = correctedKeypair.publicKey,
                expectedAddress = correctedAddress
            )
            return
        }

        if (!ethereumKeypair.publicKey.contentEquals(expectedPublicKey)) {
            throw EthereumDerivationPathSecretCorruptionException(
                "A DB31 Ethereum secret matches neither historical nor corrected identity"
            )
        }

        // DB31 exists precisely because the historical entropy/path could
        // derive a different Ethereum keypair. Prove the retained private key
        // owns the durable public identity without applying the corrected
        // recovery rule until the replacement has been committed.
        walletRootSecretValidation.validateEthereumAndSanitize(
            encoded = EthereumSecrets(
                entropy = null,
                seed = ethereumKeypair.privateKey,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = null
            ).toHexString(),
            expectedPublicKey = expectedPublicKey,
            expectedAddress = expectedAddress
        )
    }

    private fun prepareSeparatedEthereumRetry(
        account: MigratingEthereumAccount
    ): SeparatedEthereumRetryPreparation {
        val metaId = account.metaId
        val separatedSecretKey = "$metaId:ETHEREUM_SECRETS"
        if (!encryptedPreferences.hasKey(separatedSecretKey)) {
            return SeparatedEthereumRetryPreparation.Absent
        }
        val substrateSecretKey = "$metaId:SUBSTRATE_SECRETS"

        val snapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(
                separatedSecretKey
            )
        ) {
            "A separated Ethereum secret disappeared during migration"
        }
        val encoded = snapshot.plaintext
        val substrateSnapshot = if (
            encryptedPreferences.hasKey(substrateSecretKey)
        ) {
            checkNotNull(
                encryptedPreferences.getDecryptedStringSnapshot(
                    substrateSecretKey
                )
            ) {
                "A paired Substrate secret disappeared during migration"
            }
        } else {
            null
        }
        val encodedSubstrate = substrateSnapshot?.plaintext
        var quarantineSubstrate = false
        val identity = try {
            val substrateEncoded = encodedSubstrate
                ?: throw EthereumDerivationPathSecretCorruptionException(
                    "A separated Ethereum retry has no paired Substrate secret"
                )
            val substrateSecrets = decodeSeparatedSubstrate(substrateEncoded)
            val substratePublicKey = account.substratePublicKey
                ?: throw WalletPublicIdentityIntegrityException(
                    "A DB31 retry has no durable Substrate public key"
                )
            val substrateCryptoType = account.substrateCryptoType
                ?: throw WalletPublicIdentityIntegrityException(
                    "A DB31 retry has no durable Substrate crypto type"
                )
            val substrateAccountId = account.substrateAccountId
                ?: throw WalletPublicIdentityIntegrityException(
                    "A DB31 retry has no durable Substrate account id"
                )
            try {
                walletRootSecretValidation.validateSubstrateAndSanitize(
                    encoded = substrateEncoded,
                    expectedPublicKey = substratePublicKey,
                    expectedCryptoType = substrateCryptoType,
                    expectedAccountId = substrateAccountId
                )
            } catch (failure: WalletRootSecretCorruptionException) {
                throw SeparatedSubstrateSecretCorruptionException(
                    "The paired Substrate retry secret is corrupt",
                    failure
                )
            }
            val sharedEntropy =
                substrateSecrets[SubstrateSecrets.Entropy]
            sharedEntropy?.let {
                ensureSeparatedSubstrateLocal(
                    it.size in VALID_ENTROPY_LENGTHS,
                    "The paired Substrate retry entropy has an invalid length"
                )
            }

            val secrets = decodeSeparatedEthereum(encoded)
            val keypairStruct = secrets[EthereumSecrets.EthereumKeypair]
            val keypair = keypairStruct.toKeypair()
            ensureLocal(
                keypair.privateKey.size == PRIVATE_KEY_BYTES &&
                    keypair.publicKey.hasEthereumPublicKeyShape() &&
                    keypairStruct[KeyPairSchema.Nonce] == null,
                "A separated Ethereum secret has an invalid keypair shape"
            )
            val ethereumEntropy = secrets[EthereumSecrets.Entropy]
            val ethereumDerivationPath =
                secrets[EthereumSecrets.EthereumDerivationPath]
            val expectedIdentity = if (sharedEntropy == null) {
                ensureLocal(
                    ethereumEntropy == null,
                    "A keypair-only separated retry has unexpected Ethereum entropy"
                )
                ensureLocal(
                    ethereumDerivationPath == null,
                    "A keypair-only separated retry has an unbound Ethereum path"
                )
                EthereumIdentity(
                    publicKey = account.ethereumPublicKey
                        ?: throw WalletPublicIdentityIntegrityException(
                            "A keypair-only separated retry has no durable public key"
                        ),
                    address = account.ethereumAddress
                        ?: throw WalletPublicIdentityIntegrityException(
                            "A keypair-only separated retry has no durable address"
                        )
                )
            } else {
                ensureLocal(
                    ethereumEntropy != null &&
                        ethereumEntropy.contentEquals(sharedEntropy),
                    "Separated Ethereum and Substrate retry entropy differ"
                )
                ensureLocal(
                    ethereumDerivationPath ==
                        BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                    "A separated Ethereum retry has an unexpected derivation path"
                )
                val expectedKeypair = performCryptography(
                    operation = "separated retry Ethereum key derivation"
                ) {
                    cryptography.deriveKeypair(
                        entropy = sharedEntropy.clone(),
                        decodedPath = performCryptography(
                            operation =
                                "separated retry derivation-path decoding"
                        ) {
                            cryptography.decodePath(
                                BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                            )
                        }
                    )
                }
                ensureLocal(
                    keypair.privateKey.contentEquals(
                        expectedKeypair.privateKey
                    ) &&
                        keypair.publicKey.contentEquals(
                            expectedKeypair.publicKey
                        ),
                    "A separated Ethereum retry is not bound to its Substrate entropy"
                )
                val address = performCryptography(
                    operation = "separated Ethereum address calculation"
                ) {
                    cryptography.deriveAddress(expectedKeypair.publicKey)
                }
                ensureOperational(
                    address.size == ETHEREUM_ADDRESS_BYTES,
                    "The Ethereum provider returned an invalid address"
                )
                EthereumIdentity(
                    publicKey = expectedKeypair.publicKey,
                    address = address
                )
            }
            walletRootSecretValidation.validateEthereumAndSanitize(
                encoded = encoded,
                expectedPublicKey = expectedIdentity.publicKey,
                expectedAddress = expectedIdentity.address
            )
            expectedIdentity
        } catch (failure: EthereumDerivationPathSecretCorruptionException) {
            null
        } catch (failure: WalletRootSecretCorruptionException) {
            null
        } catch (failure: SeparatedSubstrateSecretCorruptionException) {
            quarantineSubstrate = true
            null
        }

        return if (identity == null) {
            val snapshotMoves = buildList {
                if (
                    substrateSnapshot != null &&
                    quarantineSubstrate
                ) {
                    add(
                        EncryptedPreferenceSnapshotMove(
                            sourceKey = substrateSecretKey,
                            destinationKey =
                                WalletSecretQuarantine.keyFor(
                                    substrateSecretKey
                                ),
                            expectedSnapshot = substrateSnapshot
                        )
                    )
                }
                add(
                    EncryptedPreferenceSnapshotMove(
                        sourceKey = separatedSecretKey,
                        destinationKey =
                            WalletSecretQuarantine.keyFor(
                                separatedSecretKey
                            ),
                        expectedSnapshot = snapshot
                    )
                )
            }
            SeparatedEthereumRetryPreparation.Quarantine(
                expectedStates = buildMap {
                    put("$metaId:ACCESS_SECRETS", null)
                    if (!quarantineSubstrate) {
                        put(substrateSecretKey, substrateSnapshot)
                    }
                },
                snapshotMoves = snapshotMoves
            )
        } else {
            SeparatedEthereumRetryPreparation.Valid(
                ethereumIdentity = identity,
                expectedStates = linkedMapOf(
                    "$metaId:ACCESS_SECRETS" to null,
                    substrateSecretKey to checkNotNull(substrateSnapshot),
                    separatedSecretKey to snapshot
                )
            )
        }
    }

    private fun executeSeparatedEthereumRetry(
        preparation: SeparatedEthereumRetryPreparation,
        executeAction: Boolean
    ): EthereumIdentity? {
        return when (preparation) {
            SeparatedEthereumRetryPreparation.Absent -> null

            is SeparatedEthereumRetryPreparation.Valid -> {
                // Bind both validated ciphertext snapshots before publishing
                // their derived identity into the transactional database.
                if (executeAction) {
                    encryptedPreferences.replaceEncryptedStringsForStatesDurably(
                        expectedStates = preparation.expectedStates,
                        valuesToPut = emptyMap(),
                        keysToRemove = emptySet()
                    )
                }
                preparation.ethereumIdentity
            }

            is SeparatedEthereumRetryPreparation.Quarantine -> {
                // A corrupt paired Substrate secret invalidates the Ethereum
                // retry proof. Move both exact ciphertexts in one durable CAS
                // so a race or commit failure cannot quarantine only one.
                if (executeAction) {
                    encryptedPreferences.replaceEncryptedStringsForStatesDurably(
                        expectedStates = preparation.expectedStates,
                        valuesToPut = emptyMap(),
                        keysToRemove = emptySet(),
                        snapshotMoves = preparation.snapshotMoves
                    )
                } else {
                    encryptedPreferences.requireSnapshotMovesReady(
                        preparation.snapshotMoves
                    )
                }
                null
            }
        }
    }

    private fun decodeSeparatedSubstrate(
        encoded: String
    ): EncodableStruct<SubstrateSecrets> {
        return try {
            WalletSecretScalePreflight.requireSubstrateV3(encoded)
            SubstrateSecrets.read(encoded)
        } catch (failure: Exception) {
            throw SeparatedSubstrateSecretCorruptionException(
                "Unable to decode a paired Substrate retry secret",
                failure
            )
        }
    }

    private fun decodeSeparatedEthereum(
        encoded: String
    ): EncodableStruct<EthereumSecrets> {
        return try {
            WalletSecretScalePreflight.requireEthereumV3(encoded)
            EthereumSecrets.read(encoded)
        } catch (failure: Exception) {
            throw EthereumDerivationPathSecretCorruptionException(
                "Unable to decode a separated Ethereum retry secret",
                failure
            )
        }
    }

    private fun ensureSeparatedSubstrateLocal(
        condition: Boolean,
        message: String
    ) {
        if (!condition) {
            throw SeparatedSubstrateSecretCorruptionException(message)
        }
    }

    private data class PreparedEthereumSecretMigration(
        val activeSecretKey: String,
        val expectedSnapshot: EncryptedPreferenceSnapshot,
        val encodedSecrets: String,
        val ethereumIdentity: EthereumIdentity
    )

    private data class PendingSecretQuarantine(
        val activeSecretKey: String,
        val expectedSnapshot: EncryptedPreferenceSnapshot
    )

    private data class V2SecretPreparation(
        val preparedMigration: PreparedEthereumSecretMigration? = null,
        val quarantine: PendingSecretQuarantine? = null
    )

    private sealed interface SeparatedEthereumRetryPreparation {
        data object Absent : SeparatedEthereumRetryPreparation

        data class Valid(
            val ethereumIdentity: EthereumIdentity,
            val expectedStates:
                Map<String, EncryptedPreferenceSnapshot?>
        ) : SeparatedEthereumRetryPreparation

        data class Quarantine(
            val expectedStates:
                Map<String, EncryptedPreferenceSnapshot?>,
            val snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ) : SeparatedEthereumRetryPreparation
    }

    private data class EthereumIdentity(
        val publicKey: ByteArray,
        val address: ByteArray
    )

    private data class MigratingEthereumAccount(
        val metaId: Long,
        val substratePublicKey: ByteArray?,
        val substrateCryptoType: CryptoType?,
        val substrateAccountId: ByteArray?,
        val ethereumPublicKey: ByteArray?,
        val ethereumAddress: ByteArray?
    )

    private fun SupportSQLiteDatabase.updateAccountIdForAsset(
        metaId: Long,
        oldAddress: ByteArray,
        ethereumAddress: ByteArray
    ) {
        compileStatement(
            "UPDATE OR REPLACE assets " +
                "INDEXED BY `$RELEASED_ASSET_META_ID_INDEX` " +
                "SET accountId=? WHERE metaId=? AND accountId=?"
        ).use { statement ->
            statement.bindBlob(1, ethereumAddress)
            statement.bindLong(2, metaId)
            statement.bindBlob(3, oldAddress)
            statement.executeUpdateDelete()
        }
    }

    private fun decodeHistoricalV2Secrets(
        encoded: String
    ): HistoricalV2Secrets {
        return try {
            when (
                WalletSecretScalePreflight
                    .requireMetaAccountV2OrLegacyV69(encoded)
            ) {
                WalletMetaAccountScaleLayout.LEGACY_V69 -> {
                    val decoded = MetaAccountSecretsV31.read(encoded)
                    HistoricalV2Secrets(
                        entropy = decoded[MetaAccountSecretsV31.Entropy],
                        seed = decoded[MetaAccountSecretsV31.Seed],
                        substrateKeypair =
                        decoded[MetaAccountSecretsV31.SubstrateKeypair]
                            .toKeypair(),
                        substrateDerivationPath =
                        decoded[MetaAccountSecretsV31.SubstrateDerivationPath],
                        ethereumKeypair =
                        decoded[MetaAccountSecretsV31.EthereumKeypair]
                            ?.toKeypair(),
                        ethereumDerivationPath =
                        decoded[MetaAccountSecretsV31.EthereumDerivationPath]
                    )
                }

                WalletMetaAccountScaleLayout.CURRENT_V2 -> {
                    val decoded = MetaAccountSecrets.read(encoded)
                    ensureLocal(
                        decoded[MetaAccountSecrets.TonKeypair] == null,
                        "A DB31 compatibility payload contains a non-historical TON keypair"
                    )
                    HistoricalV2Secrets(
                        entropy = decoded[MetaAccountSecrets.Entropy],
                        seed = decoded[MetaAccountSecrets.Seed],
                        substrateKeypair =
                        decoded[MetaAccountSecrets.SubstrateKeypair]
                            .toKeypair(),
                        substrateDerivationPath =
                        decoded[MetaAccountSecrets.SubstrateDerivationPath],
                        ethereumKeypair =
                        decoded[MetaAccountSecrets.EthereumKeypair]?.toKeypair(),
                        ethereumDerivationPath =
                        decoded[MetaAccountSecrets.EthereumDerivationPath]
                    )
                }
            }
        } catch (failure: EthereumDerivationPathSecretCorruptionException) {
            throw failure
        } catch (failure: Exception) {
            throw EthereumDerivationPathSecretCorruptionException(
                "Unable to decode the bounded DB31 wallet secret",
                failure
            )
        }
    }

    private fun EncodableStruct<KeyPairSchema>.toKeypair(): FearlessKeypair {
        return Keypair(
            publicKey = this[KeyPairSchema.PublicKey],
            privateKey = this[KeyPairSchema.PrivateKey],
            nonce = this[KeyPairSchema.Nonce]
        )
    }

    private inline fun <T> performCryptography(
        operation: String,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "Wallet cryptography is unavailable during $operation",
                failure
            )
        }
    }

    private fun ensureLocal(condition: Boolean, message: String) {
        if (!condition) {
            throw EthereumDerivationPathSecretCorruptionException(message)
        }
    }

    private fun ensurePublicIdentity(condition: Boolean, message: String) {
        if (!condition) {
            throw WalletPublicIdentityIntegrityException(message)
        }
    }

    private fun ensureOperational(condition: Boolean, message: String) {
        if (!condition) {
            throw WalletSecureStorageUnavailableException(message)
        }
    }

    private fun ByteArray.hasEthereumPublicKeyShape(): Boolean {
        return isValidEthereumCompressedPublicKey()
    }

    private data class HistoricalV2Secrets(
        val entropy: ByteArray?,
        val seed: ByteArray?,
        val substrateKeypair: FearlessKeypair,
        val substrateDerivationPath: String?,
        val ethereumKeypair: FearlessKeypair?,
        val ethereumDerivationPath: String?
    )

    /**
     * Frozen DB31 payload: six fields ending at EthereumDerivationPath.
     * Do not replace this with the evolving runtime MetaAccountSecrets schema.
     */
    internal object MetaAccountSecretsV31 :
        Schema<MetaAccountSecretsV31>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()
        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()
        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }

    private companion object {
        const val PRIVATE_KEY_BYTES = 32
        const val SUBSTRATE_PUBLIC_KEY_BYTES = 32
        const val ECDSA_PUBLIC_KEY_BYTES = 33
        const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
        const val ETHEREUM_ADDRESS_BYTES = 20
        val VALID_ENTROPY_LENGTHS = setOf(16, 20, 24, 28, 32)
    }
}

private object ProductionDb31WalletIdentityUpdater :
    Db31WalletIdentityUpdater {

    override fun update(
        database: SupportSQLiteDatabase,
        metaId: Long,
        publicKey: ByteArray,
        address: ByteArray
    ): Int {
        val contentValues = ContentValues().apply {
            put(MetaAccountLocal.Table.Column.ETHEREUM_ADDRESS, address)
            put(MetaAccountLocal.Table.Column.ETHEREUM_PUBKEY, publicKey)
        }
        return database.update(
            MetaAccountLocal.TABLE_NAME,
            SQLiteDatabase.CONFLICT_REPLACE,
            contentValues,
            "id=?",
            arrayOf(metaId.toString())
        )
    }
}

private object ProductionEthereumDerivationPathCryptography :
    EthereumDerivationPathCryptography {

    override fun decodePath(path: String): JunctionDecoder.DecodeResult {
        return BIP32JunctionDecoder.decode(path)
    }

    override fun deriveKeypair(
        entropy: ByteArray,
        decodedPath: JunctionDecoder.DecodeResult
    ): FearlessKeypair {
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val ethereumSeed = EthereumSeedFactory.deriveSeed32(
            mnemonic.words,
            password = decodedPath.password
        ).seed
        return EthereumKeypairFactory.generate(
            ethereumSeed,
            junctions = decodedPath.junctions
        )
    }

    override fun deriveAddress(publicKey: ByteArray): ByteArray {
        return publicKey.ethereumAddressFromPublicKey()
    }
}
