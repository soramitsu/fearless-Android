package jp.co.soramitsu.account.impl.data.repository

import androidx.room.withTransaction
import java.security.SecureRandom
import java.util.UUID
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Journal
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Operation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.PublicAfterImage
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.StagedMutation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.SubstrateCryptoType
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.sync.withLock
import org.ton.api.pk.PrivateKeyEd25519

/**
 * Generates collision-resistant identifiers for durable wallet mutations.
 *
 * Tests inject a deterministic implementation so occupied, malformed, repeated,
 * and exhausted candidates can be exercised without weakening production entropy.
 */
interface WalletMutationIdentifierSource {

    fun nextMetaIdCandidate(): Long

    fun nextOperationId(): String
}

/** Production identifier source backed by platform cryptographic randomness. */
class SecureWalletMutationIdentifierSource(
    private val secureRandom: SecureRandom = SecureRandom()
) : WalletMutationIdentifierSource {

    override fun nextMetaIdCandidate(): Long {
        return secureRandom.nextLong() and Long.MAX_VALUE
    }

    override fun nextOperationId(): String {
        return UUID.randomUUID().toString()
    }
}

enum class WalletMutationFailureReason {
    INVALID_ARGUMENT,
    SECRET_BINDING_FAILED,
    IDENTIFIER_ALLOCATION_EXHAUSTED,
    IDENTITY_CONFLICT,
    STATE_CONFLICT,
    RECOVERY_REQUIRED,
    DURABILITY_UNKNOWN
}

/**
 * Fail-closed coordinator error. Callers must not bypass recovery for
 * [STATE_CONFLICT], [RECOVERY_REQUIRED], or [DURABILITY_UNKNOWN].
 */
class WalletSecretMutationCoordinatorException(
    val reason: WalletMutationFailureReason,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Serializes and reconciles mutations that span encrypted preferences and Room.
 *
 * New signing material and its journal are always durable before a public
 * database row is inserted or updated. A process death at any later boundary is
 * recovered by [reconcilePendingMutation] from an exact public before/after image.
 */
class WalletSecretMutationCoordinator private constructor(
    private val database: WalletMutationDatabase,
    private val journalStore: WalletSecretMutationJournalStore,
    private val encryptedPreferences: EncryptedPreferences,
    private val identifierSource: WalletMutationIdentifierSource
) {

    constructor(
        appDatabase: AppDatabase,
        metaAccountDao: MetaAccountDao,
        journalStore: WalletSecretMutationJournalStore,
        encryptedPreferences: EncryptedPreferences,
        identifierSource: WalletMutationIdentifierSource =
            SecureWalletMutationIdentifierSource()
    ) : this(
        database = RoomWalletMutationDatabase(appDatabase, metaAccountDao),
        journalStore = journalStore,
        encryptedPreferences = encryptedPreferences,
        identifierSource = identifierSource
    )

    internal constructor(
        database: WalletMutationDatabase,
        journalStore: WalletSecretMutationJournalStore,
        encryptedPreferences: EncryptedPreferences,
        identifierSource: WalletMutationIdentifierSource,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit
    ) : this(
        database,
        journalStore,
        encryptedPreferences,
        identifierSource
    )

    /**
     * Creates a wallet from an id-less prototype and exact encoded V3 secrets.
     *
     * The prototype's position is intentionally ignored. A positive primary key
     * and the deterministic next position are reserved under the process mutex.
     */
    suspend fun create(
        prototype: MetaAccountLocal,
        substrateSecretPlaintext: String?,
        ethereumSecretPlaintext: String?,
        tonSecretPlaintext: String?
    ): Long = WalletCrossStoreMutationMutex.instance.withLock {
        reconcilePendingMutationLocked()
        requireArgument(prototype.id == 0L) {
            "A wallet creation prototype must not contain a database id"
        }

        val prepared = prepareCreate(prototype)
        val secretPlaintexts = buildSecretPlaintexts(
            metaId = prepared.metaId,
            substrateSecretPlaintext = substrateSecretPlaintext,
            ethereumSecretPlaintext = ethereumSecretPlaintext,
            tonSecretPlaintext = tonSecretPlaintext
        )
        val journal = Journal(
            operationId = identifierSource.nextOperationId(),
            operation = Operation.CREATE,
            metaId = prepared.metaId,
            beforeImage = null,
            afterImage = prepared.afterImage,
            selectedMetaIdAfterDelete = null,
            chainAccountIdsHex = emptySet(),
            secretKeysToPut = secretPlaintexts.keys,
            secretKeysToRemove = emptySet()
        )

        validateJournal(journal)
        validateSecretBindings(journal, secretPlaintexts)
        stage(journal, secretPlaintexts)
        reconcileStagedMutation(
            StagedMutation(
                journal = journal,
                secretPlaintexts = secretPlaintexts
            )
        )

        prepared.metaId
    }

    /**
     * Adds Ethereum signing material to an exact existing public wallet image.
     *
     * Only the Ethereum identity and an intentional is-backed-up change may
     * differ between [existing] and [after].
     */
    suspend fun addEvm(
        existing: MetaAccountLocal,
        after: MetaAccountLocal,
        ethereumSecretPlaintext: String
    ): Long = WalletCrossStoreMutationMutex.instance.withLock {
        reconcilePendingMutationLocked()
        requireArgument(existing.id > 0L && after.id == existing.id) {
            "An EVM addition requires matching positive wallet ids"
        }

        val beforeImage = existing.toPublicImage()
        val afterImage = after.toPublicImage()
        val secretPlaintexts = mapOf(
            secretKey(existing.id, ETHEREUM_SECRET_SUFFIX) to ethereumSecretPlaintext
        )
        val journal = Journal(
            operationId = identifierSource.nextOperationId(),
            operation = Operation.ADD_EVM,
            metaId = existing.id,
            beforeImage = beforeImage,
            afterImage = afterImage,
            selectedMetaIdAfterDelete = null,
            chainAccountIdsHex = emptySet(),
            secretKeysToPut = secretPlaintexts.keys,
            secretKeysToRemove = emptySet()
        )

        validateJournal(journal)
        validateSecretBindings(journal, secretPlaintexts)
        database.inTransaction {
            val accounts = requireHealthySelection()
            val current = getMetaAccount(existing.id)
                ?: stateConflict("The wallet changed before its EVM mutation was staged")
            requireState(current.toPublicImage() == beforeImage) {
                "The wallet changed before its EVM mutation was staged"
            }
            requireNoIdentityConflict(
                metaId = existing.id,
                image = afterImage,
                reason = WalletMutationFailureReason.IDENTITY_CONFLICT
            )
            requireExpectedSelection(accounts, current)
        }
        stage(journal, secretPlaintexts)
        reconcileStagedMutation(
            StagedMutation(
                journal = journal,
                secretPlaintexts = secretPlaintexts
            )
        )

        existing.id
    }

    /**
     * Durably deletes a wallet and every exact active/quarantined secret key
     * derived by [WalletSecretMutationJournalStore].
     */
    suspend fun delete(metaId: Long) =
        WalletCrossStoreMutationMutex.instance.withLock {
            reconcilePendingMutationLocked()
            if (journalCall { journalStore.hasTonConnectMutationJournal() }) {
                recoveryRequired(
                    "A TON Connect mutation must be reconciled before wallet deletion"
                )
            }
            requireArgument(metaId > 0L) {
                "A wallet deletion requires a positive wallet id"
            }

            val prepared = prepareDelete(metaId)
            val secretKeysToRemove = journalCall {
                journalStore.deletionSecretKeys(
                    metaId = metaId,
                    beforeImage = prepared.beforeImage,
                    chainAccountIdsHex = prepared.chainAccountIdsHex,
                    tonConnectSecretKeys = prepared.tonConnectSecretKeys
                )
            }
            val journal = Journal(
                operationId = identifierSource.nextOperationId(),
                operation = Operation.DELETE,
                metaId = metaId,
                beforeImage = prepared.beforeImage,
                afterImage = null,
                selectedMetaIdAfterDelete =
                prepared.selectedMetaIdAfterDelete,
                chainAccountIdsHex = prepared.chainAccountIdsHex,
                secretKeysToPut = emptySet(),
                secretKeysToRemove = secretKeysToRemove
            )

            validateJournal(journal)
            stage(journal, emptyMap())
            reconcileStagedMutation(
                StagedMutation(
                    journal = journal,
                    secretPlaintexts = emptyMap()
                )
            )
        }

    /** Replays and finalizes the one active mutation, if present. */
    suspend fun reconcilePendingMutation() =
        WalletCrossStoreMutationMutex.instance.withLock {
            reconcilePendingMutationLocked()
        }

    private suspend fun prepareCreate(prototype: MetaAccountLocal): PreparedCreate {
        return database.inTransaction {
            val accounts = requireHealthySelection()
            val metaId = allocateMetaId()
            val nextPosition = getNextPosition()
            requireState(nextPosition >= 0) {
                "The wallet position space is exhausted or corrupt"
            }
            val afterImage = prototype
                .copyWith(
                    id = metaId,
                    position = nextPosition,
                    isSelected = prototype.isSelected || accounts.isEmpty()
                )
                .toPublicImage()
            requireNoIdentityConflict(
                metaId = metaId,
                image = afterImage,
                reason = WalletMutationFailureReason.IDENTITY_CONFLICT
            )

            PreparedCreate(metaId, afterImage)
        }
    }

    private suspend fun WalletMutationDatabase.allocateMetaId(): Long {
        repeat(MAX_META_ID_ALLOCATION_ATTEMPTS) {
            val candidate = try {
                identifierSource.nextMetaIdCandidate()
            } catch (failure: Exception) {
                throw WalletSecretMutationCoordinatorException(
                    reason = WalletMutationFailureReason.IDENTIFIER_ALLOCATION_EXHAUSTED,
                    message = "Unable to generate a wallet id",
                    cause = failure
                )
            }
            if (candidate <= 0L) return@repeat
            if (metaAccountExists(candidate)) return@repeat
            if (journalCall { journalStore.hasSecretNamespace(candidate) }) return@repeat

            return candidate
        }

        throw WalletSecretMutationCoordinatorException(
            reason = WalletMutationFailureReason.IDENTIFIER_ALLOCATION_EXHAUSTED,
            message = "Unable to reserve an unused wallet id"
        )
    }

    private suspend fun prepareDelete(metaId: Long): PreparedDelete {
        return database.inTransaction {
            val accounts = requireHealthySelection()
            val target = getMetaAccount(metaId)
                ?: throw WalletSecretMutationCoordinatorException(
                    reason = WalletMutationFailureReason.INVALID_ARGUMENT,
                    message = "The wallet to delete does not exist"
                )
            requireUnambiguousLegacySecretOwnership(
                accounts = accounts,
                target = target
            )
            val beforeImage = target.toPublicImage()
            val chainAccountIdsHex = getChainAccountIds(metaId)
                .map { it.toCanonicalHex() }
                .toSortedSet()
                .toCollection(linkedSetOf())
            val tonConnectSecretKeys =
                tonConnectDeletionSecretKeys(metaId)
            val selectedMetaIdAfterDelete = expectedSelectedMetaIdAfterDelete(
                accounts = accounts,
                target = target
            )

            PreparedDelete(
                beforeImage = beforeImage,
                selectedMetaIdAfterDelete = selectedMetaIdAfterDelete,
                chainAccountIdsHex = chainAccountIdsHex,
                tonConnectSecretKeys = tonConnectSecretKeys
            )
        }
    }

    private suspend fun WalletMutationDatabase.tonConnectDeletionSecretKeys(
        metaId: Long
    ): Set<String> {
        val connections = getTonConnectionSecretOwners(metaId)
        requireState(
            connections.size <=
                TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET
        ) {
            "A wallet has too many TON Connect rows for bounded deletion"
        }
        val scopedNamespaceKeys = journalCall {
            journalStore.tonConnectScopedDeletionKeys(metaId)
        }

        val keys = scopedNamespaceKeys.toCollection(linkedSetOf())
        connections.forEach { connection ->
            val scopedKey = try {
                TonConnectStorageKeys.requireValidClientId(
                    connection.clientId
                )
                TonConnectStorageKeys.scoped(
                    metaId = metaId,
                    url = connection.url,
                    source = connection.source
                )
            } catch (failure: IllegalArgumentException) {
                throw WalletSecretMutationCoordinatorException(
                    reason = WalletMutationFailureReason.STATE_CONFLICT,
                    message = "A TON Connect row has an invalid secret identity",
                    cause = failure
                )
            }
            keys += scopedKey
            keys += WalletSecretQuarantine.keyFor(scopedKey)
        }

        connections
            .map(TonConnectionSecretOwner::clientId)
            .toSortedSet()
            .forEach { clientId ->
                if (!hasOtherTonConnectionOwner(metaId, clientId)) {
                    val legacyKey = TonConnectStorageKeys.legacy(clientId)
                    keys += legacyKey
                    keys += WalletSecretQuarantine.keyFor(legacyKey)
                }
            }

        return keys
    }

    private fun requireUnambiguousLegacySecretOwnership(
        accounts: List<MetaAccountLocal>,
        target: MetaAccountLocal
    ) {
        val publicKey = target.substratePublicKey
        val cryptoType = target.substrateCryptoType
        val accountId = target.substrateAccountId
        val presentCount = listOf(publicKey, cryptoType, accountId)
            .count { it != null }
        requireState(presentCount == 0 || presentCount == 3) {
            "Wallet deletion cannot prove a complete Substrate identity"
        }
        if (presentCount == 0) return

        val requiredPublicKey = requireNotNull(publicKey)
        val requiredCryptoType = requireNotNull(cryptoType)
        val requiredAccountId = requireNotNull(accountId)
        val expectedPublicKeyBytes = when (requiredCryptoType) {
            CryptoType.SR25519,
            CryptoType.ED25519 -> SUBSTRATE_PUBLIC_KEY_BYTES
            CryptoType.ECDSA -> ECDSA_PUBLIC_KEY_BYTES
        }
        requireState(
            requiredPublicKey.size == expectedPublicKeyBytes &&
                requiredAccountId.size == SUBSTRATE_ACCOUNT_ID_BYTES &&
                requiredPublicKey.substrateAccountId()
                    .contentEquals(requiredAccountId)
        ) {
            "Wallet deletion cannot prove its Substrate public identity"
        }

        val duplicateOwner = accounts.any { candidate ->
            candidate.id != target.id &&
                (
                    candidate.substratePublicKey
                        .contentEqualsNullable(requiredPublicKey) ||
                        candidate.substrateAccountId
                        .contentEqualsNullable(requiredAccountId)
                    )
        }
        requireState(!duplicateOwner) {
            "Wallet deletion cannot uniquely own globally keyed legacy material"
        }
    }

    private suspend fun reconcilePendingMutationLocked() {
        requireDurableStorageHealthy()
        val staged = journalCall {
            journalStore.loadStagedMutation()
        } ?: return

        reconcileStagedMutation(staged)
    }

    private suspend fun reconcileStagedMutation(staged: StagedMutation) {
        requireDurableStorageHealthy()
        validateSecretBindings(
            journal = staged.journal,
            secretPlaintexts = staged.secretPlaintexts
        )

        when (staged.journal.operation) {
            Operation.CREATE -> applyCreate(staged.journal)
            Operation.ADD_EVM -> applyAddEvm(staged.journal)
            Operation.DELETE -> applyDelete(staged.journal)
        }

        journalCall {
            when (staged.journal.operation) {
                Operation.DELETE -> {
                    journalStore.completeDelete(staged.journal.operationId)
                }
                Operation.CREATE,
                Operation.ADD_EVM -> {
                    journalStore.clear(staged)
                }
            }
        }
    }

    private suspend fun applyCreate(journal: Journal) {
        val afterImage = journal.afterImage
            ?: recoveryRequired("A staged creation is missing its public after-image")
        val afterLocal = afterImage.toMetaAccount(journal.metaId)

        database.inTransaction {
            val accountsBefore = requireHealthySelection()
            val selectedIdsBefore = accountsBefore.selectedIds()
            val current = getMetaAccount(journal.metaId)

            when {
                current == null -> {
                    requireState(getNextPosition() == afterImage.position) {
                        "A staged wallet position conflicts with current database state"
                    }
                    requireState(!metaAccountExists(journal.metaId)) {
                        "A staged wallet id conflicts with current database state"
                    }
                    requireNoIdentityConflict(journal.metaId, afterImage)
                    requireDurableStorageHealthy()
                    val insertedId = insertMetaAccount(afterLocal)
                    requireState(insertedId == journal.metaId) {
                        "Room did not preserve the staged wallet id"
                    }
                    if (afterImage.isSelected) {
                        requireDurableStorageHealthy()
                        selectMetaAccount(journal.metaId)
                    }
                }
                current.toPublicImage().cryptographicIdentity() ==
                    afterImage.cryptographicIdentity() -> {
                    requireNoIdentityConflict(journal.metaId, afterImage)
                }
                else -> {
                    stateConflict("A staged wallet creation conflicts with an existing row")
                }
            }

            val accountsAfter = requireHealthySelection()
            if (current == null) {
                requireExactTarget(journal.metaId, afterImage)
            } else {
                requireCryptographicTarget(journal.metaId, afterImage)
            }
            requireNoIdentityConflict(journal.metaId, afterImage)
            if (current != null) {
                requireState(accountsAfter.selectedIds() == selectedIdsBefore) {
                    "Creation replay changed a newer wallet selection"
                }
            } else if (afterImage.isSelected) {
                requireState(accountsAfter.selectedIds() == setOf(journal.metaId)) {
                    "A staged selected wallet was not selected exactly"
                }
            } else {
                requireState(accountsAfter.selectedIds() == selectedIdsBefore) {
                    "An unselected wallet creation changed the selected wallet"
                }
            }
        }
    }

    private suspend fun applyAddEvm(journal: Journal) {
        val beforeImage = journal.beforeImage
            ?: recoveryRequired("A staged EVM addition is missing its before-image")
        val afterImage = journal.afterImage
            ?: recoveryRequired("A staged EVM addition is missing its after-image")
        val afterLocal = afterImage.toMetaAccount(journal.metaId)

        database.inTransaction {
            val accountsBefore = requireHealthySelection()
            val selectedIdsBefore = accountsBefore.selectedIds()
            val current = getMetaAccount(journal.metaId)
                ?: stateConflict("A staged EVM addition targets a missing wallet")
            val currentImage = current.toPublicImage()
            val currentIdentity = currentImage.cryptographicIdentity()
            val expectedIsBackedUp = when {
                currentImage == beforeImage -> {
                    requireNoIdentityConflict(journal.metaId, afterImage)
                    requireDurableStorageHealthy()
                    updateMetaAccount(afterLocal)
                    afterImage.isBackedUp
                }
                currentIdentity == beforeImage.cryptographicIdentity() -> {
                    requireNoIdentityConflict(journal.metaId, afterImage)
                    // The journal owns this metadata field only when it records
                    // an explicit transition. Otherwise a newer metadata write
                    // must survive identity reconciliation.
                    val merged = current.withEthereumIdentity(
                        image = afterImage,
                        resolvedIsBackedUp = if (
                            beforeImage.isBackedUp != afterImage.isBackedUp
                        ) {
                            afterImage.isBackedUp
                        } else {
                            current.isBackedUp
                        }
                    )
                    requireDurableStorageHealthy()
                    updateMetaAccount(merged)
                    merged.isBackedUp
                }
                currentIdentity == afterImage.cryptographicIdentity() -> {
                    requireNoIdentityConflict(journal.metaId, afterImage)
                    // Room already committed the transition. Preserve metadata
                    // that may have changed before journal cleanup was retried.
                    current.isBackedUp
                }
                else -> {
                    stateConflict("A staged EVM addition conflicts with current wallet state")
                }
            }

            val accountsAfter = requireHealthySelection()
            requireAddEvmTarget(
                metaId = journal.metaId,
                expected = afterImage,
                expectedIsBackedUp = expectedIsBackedUp
            )
            requireNoIdentityConflict(journal.metaId, afterImage)
            requireState(accountsAfter.selectedIds() == selectedIdsBefore) {
                "An EVM addition changed the selected wallet"
            }
        }
    }

    private suspend fun applyDelete(journal: Journal) {
        val beforeImage = journal.beforeImage
            ?: recoveryRequired("A staged deletion is missing its public before-image")

        database.inTransaction {
            val accountsBefore = requireHealthySelection()
            val current = getMetaAccount(journal.metaId)

            if (current != null) {
                requireState(
                    current.toPublicImage().cryptographicIdentity() ==
                        beforeImage.cryptographicIdentity()
                ) {
                    "A staged deletion conflicts with the current wallet identity"
                }
                val actualChainAccountIdsHex = getChainAccountIds(journal.metaId)
                    .map { it.toCanonicalHex() }
                    .toSet()
                requireState(actualChainAccountIdsHex == journal.chainAccountIdsHex) {
                    "A staged deletion conflicts with current chain-account state"
                }
                val expectedSelectedMetaId = expectedSelectedMetaIdAfterDelete(
                    accounts = accountsBefore,
                    target = current
                )
                requireDurableStorageHealthy()
                requireState(deleteMetaAccountAndSelectSuccessor(journal.metaId)) {
                    "Room did not delete the staged wallet"
                }

                val accountsAfter = requireHealthySelection()
                val expectedSelectedIds = expectedSelectedMetaId
                    ?.let(::setOf)
                    .orEmpty()
                requireState(accountsAfter.selectedIds() == expectedSelectedIds) {
                    "A staged deletion produced an unexpected selected wallet"
                }
            } else {
                requireState(getChainAccountIds(journal.metaId).isEmpty()) {
                    "A deleted wallet still owns chain-account rows"
                }
                requireState(!hasAssets(journal.metaId)) {
                    "A deleted wallet still owns asset rows"
                }
                // Metadata, ordering, and selection may legitimately change
                // after Room committed the deletion but before secret cleanup.
                // The current healthy selection is authoritative on replay.
                requireHealthySelection()
            }

            requireState(getMetaAccount(journal.metaId) == null) {
                "A staged wallet remains after deletion"
            }
        }
    }

    private suspend fun WalletMutationDatabase.requireExactTarget(
        metaId: Long,
        expected: PublicAfterImage
    ) {
        requireState(getMetaAccount(metaId)?.toPublicImage() == expected) {
            "Room did not commit the exact staged wallet image"
        }
    }

    private suspend fun WalletMutationDatabase.requireCryptographicTarget(
        metaId: Long,
        expected: PublicAfterImage
    ) {
        requireState(
            getMetaAccount(metaId)
                ?.toPublicImage()
                ?.cryptographicIdentity() ==
                expected.cryptographicIdentity()
        ) {
            "Room did not commit the staged wallet identity"
        }
    }

    private suspend fun WalletMutationDatabase.requireAddEvmTarget(
        metaId: Long,
        expected: PublicAfterImage,
        expectedIsBackedUp: Boolean
    ) {
        val actual = getMetaAccount(metaId)?.toPublicImage()
        requireState(
            actual?.let {
                it.cryptographicIdentity() == expected.cryptographicIdentity() &&
                    it.isBackedUp == expectedIsBackedUp
            } == true
        ) {
            "Room did not commit the staged EVM wallet transition"
        }
    }

    private suspend fun WalletMutationDatabase.requireNoIdentityConflict(
        metaId: Long,
        image: PublicAfterImage,
        reason: WalletMutationFailureReason = WalletMutationFailureReason.STATE_CONFLICT
    ) {
        if (
            hasIdentityConflict(
                metaId = metaId,
                substrateAccountId = image.substrateAccountIdHex?.decodeCanonicalHex(),
                ethereumAddress = image.ethereumAddressHex?.decodeCanonicalHex(),
                tonPublicKey = image.tonPublicKeyHex?.decodeCanonicalHex()
            )
        ) {
            throw WalletSecretMutationCoordinatorException(
                reason = reason,
                message = "Another wallet owns the requested public identity"
            )
        }
    }

    private suspend fun WalletMutationDatabase.requireHealthySelection(): List<MetaAccountLocal> {
        val accounts = getMetaAccounts()
        requireState(accounts.all { it.id > 0L && it.position >= 0 }) {
            "Wallet ordering contains an invalid id or position"
        }
        requireState(accounts.map(MetaAccountLocal::id).toSet().size == accounts.size) {
            "Wallet storage contains duplicate ids"
        }
        val expectedSelectedCount = if (accounts.isEmpty()) 0 else 1
        requireState(
            accounts.count(MetaAccountLocal::isSelected) == expectedSelectedCount
        ) {
            "Wallet storage must select exactly one account when wallets exist"
        }
        return accounts
    }

    private fun requireExpectedSelection(
        accounts: List<MetaAccountLocal>,
        target: MetaAccountLocal
    ) {
        if (target.isSelected) {
            requireState(accounts.selectedIds() == setOf(target.id)) {
                "The expected selected wallet is not selected exactly"
            }
        }
    }

    private fun expectedSelectedMetaIdAfterDelete(
        accounts: List<MetaAccountLocal>,
        target: MetaAccountLocal
    ): Long? {
        return if (target.isSelected) {
            accounts.asSequence()
                .filter { it.id != target.id }
                .sortedWith(compareBy<MetaAccountLocal>({ it.position }, { it.id }))
                .firstOrNull()
                ?.id
        } else {
            accounts.singleOrNull(MetaAccountLocal::isSelected)?.id
        }
    }

    private fun validateSecretBindings(
        journal: Journal,
        secretPlaintexts: Map<String, String>
    ) {
        try {
            when (journal.operation) {
                Operation.CREATE -> {
                    val afterImage = journal.afterImage
                        ?: recoveryRequired("A creation journal has no public image")
                    bindOptionalSubstrate(
                        image = afterImage,
                        plaintext = secretPlaintexts[
                            secretKey(journal.metaId, SUBSTRATE_SECRET_SUFFIX)
                        ]
                    )
                    bindOptionalEthereum(
                        image = afterImage,
                        plaintext = secretPlaintexts[
                            secretKey(journal.metaId, ETHEREUM_SECRET_SUFFIX)
                        ]
                    )
                    bindOptionalTon(
                        image = afterImage,
                        plaintext = secretPlaintexts[
                            secretKey(journal.metaId, TON_SECRET_SUFFIX)
                        ]
                    )
                }
                Operation.ADD_EVM -> {
                    val afterImage = journal.afterImage
                        ?: recoveryRequired("An EVM journal has no public after-image")
                    bindOptionalEthereum(
                        image = afterImage,
                        plaintext = secretPlaintexts[
                            secretKey(journal.metaId, ETHEREUM_SECRET_SUFFIX)
                        ]
                    )
                }
                Operation.DELETE -> {
                    requireSecretBinding(secretPlaintexts.isEmpty()) {
                        "A deletion journal unexpectedly contains staged plaintexts"
                    }
                }
            }
        } catch (failure: WalletSecretMutationCoordinatorException) {
            throw failure
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) {
                throw failure
            }
            throw WalletSecretMutationCoordinatorException(
                reason = WalletMutationFailureReason.SECRET_BINDING_FAILED,
                message = "Wallet signing material does not match its public identity",
                cause = failure
            )
        }
    }

    private fun bindOptionalSubstrate(
        image: PublicAfterImage,
        plaintext: String?
    ) {
        val publicKeyHex = image.substratePublicKeyHex
        if (publicKeyHex == null) {
            requireSecretBinding(plaintext == null) {
                "Substrate signing material exists without a Substrate identity"
            }
            return
        }
        val encodedSecret = plaintext
            ?: secretBindingFailed("A Substrate identity has no signing material")

        val secrets = decodeCanonicalSecret(
            plaintext = encodedSecret,
            preflight = WalletSecretScalePreflight::requireSubstrateV3,
            decode = SubstrateSecrets::read,
            encode = { it.toHexString() }
        )
        val keypairStruct = secrets[SubstrateSecrets.SubstrateKeypair]
        val privateKey = keypairStruct[KeyPairSchema.PrivateKey]
        val publicKey = keypairStruct[KeyPairSchema.PublicKey]
        val nonce = keypairStruct[KeyPairSchema.Nonce]
        val cryptoType = image.substrateCryptoType
            ?: secretBindingFailed("A Substrate identity has no crypto type")

        requireSecretBinding(privateKey.size == PRIVATE_KEY_BYTES) {
            "A Substrate private key has an invalid length"
        }
        requireSecretBinding(publicKey.toCanonicalHex() == publicKeyHex) {
            "A Substrate secret has a different public key"
        }
        requireSecretBinding(
            publicKey.substrateAccountId().toCanonicalHex() ==
                image.substrateAccountIdHex
        ) {
            "A Substrate public key has a different account id"
        }

        val keypair = Keypair(publicKey, privateKey, nonce)
        val encryptionType = when (cryptoType) {
            SubstrateCryptoType.SR25519 -> {
                requireSecretBinding(nonce?.size == SR25519_NONCE_BYTES) {
                    "An SR25519 secret has no valid nonce"
                }
                requireSecretBinding(publicKey.size == SUBSTRATE_PUBLIC_KEY_BYTES) {
                    "An SR25519 public key has an invalid length"
                }
                EncryptionType.SR25519
            }
            SubstrateCryptoType.ED25519 -> {
                requireSecretBinding(nonce == null) {
                    "An ED25519 secret contains an unexpected nonce"
                }
                requireSecretBinding(publicKey.size == SUBSTRATE_PUBLIC_KEY_BYTES) {
                    "An ED25519 public key has an invalid length"
                }
                EncryptionType.ED25519
            }
            SubstrateCryptoType.ECDSA -> {
                requireSecretBinding(nonce == null) {
                    "An ECDSA secret contains an unexpected nonce"
                }
                val derivedPublicKey = EthereumKeypairFactory
                    .createWithPrivateKey(privateKey)
                    .publicKey
                requireSecretBinding(derivedPublicKey.contentEquals(publicKey)) {
                    "An ECDSA private key has a different public key"
                }
                EncryptionType.ECDSA
            }
        }

        if (encryptionType != EncryptionType.ECDSA) {
            val signature = Signer.sign(
                multiChainEncryption = MultiChainEncryption.Substrate(encryptionType),
                message = KEYPAIR_BINDING_MESSAGE,
                keypair = keypair
            ).signature
            val verified = when (encryptionType) {
                EncryptionType.SR25519 -> Signer.verifySr25519(
                    message = KEYPAIR_BINDING_MESSAGE,
                    signature = signature,
                    publicKeyBytes = publicKey
                )
                EncryptionType.ED25519 -> Signer.verifyEd25519(
                    message = KEYPAIR_BINDING_MESSAGE,
                    signature = signature,
                    publicKeyBytes = publicKey
                )
                EncryptionType.ECDSA -> error("ECDSA is verified by public-key derivation")
            }
            requireSecretBinding(verified) {
                "A Substrate private key cannot prove its public key"
            }
        }

        bindSubstrateRecoveryMaterial(
            secrets = secrets,
            expectedPrivateKey = privateKey,
            expectedPublicKey = publicKey,
            expectedNonce = nonce,
            encryptionType = encryptionType
        )
    }

    private fun bindOptionalEthereum(
        image: PublicAfterImage,
        plaintext: String?
    ) {
        val publicKeyHex = image.ethereumPublicKeyHex
        if (publicKeyHex == null) {
            requireSecretBinding(plaintext == null) {
                "Ethereum signing material exists without an Ethereum identity"
            }
            return
        }
        val encodedSecret = plaintext
            ?: secretBindingFailed("An Ethereum identity has no signing material")

        val secrets = decodeCanonicalSecret(
            plaintext = encodedSecret,
            preflight = WalletSecretScalePreflight::requireEthereumV3,
            decode = EthereumSecrets::read,
            encode = { it.toHexString() }
        )
        val keypair = secrets[EthereumSecrets.EthereumKeypair]
        val privateKey = keypair[KeyPairSchema.PrivateKey]
        val publicKey = keypair[KeyPairSchema.PublicKey]
        val nonce = keypair[KeyPairSchema.Nonce]
        requireSecretBinding(privateKey.size == PRIVATE_KEY_BYTES && nonce == null) {
            "An Ethereum secret has an invalid private-key shape"
        }
        val derivedPublicKey = EthereumKeypairFactory
            .createWithPrivateKey(privateKey)
            .publicKey
        requireSecretBinding(derivedPublicKey.contentEquals(publicKey)) {
            "An Ethereum private key has a different public key"
        }
        requireSecretBinding(publicKey.toCanonicalHex() == publicKeyHex) {
            "An Ethereum secret has a different public key"
        }
        requireSecretBinding(
            publicKey.ethereumAddressFromPublicKey().toCanonicalHex() ==
                image.ethereumAddressHex
        ) {
            "An Ethereum public key has a different address"
        }

        bindEthereumRecoveryMaterial(
            secrets = secrets,
            expectedPrivateKey = privateKey,
            expectedPublicKey = publicKey
        )
    }

    private fun bindOptionalTon(
        image: PublicAfterImage,
        plaintext: String?
    ) {
        val publicKeyHex = image.tonPublicKeyHex
        if (publicKeyHex == null) {
            requireSecretBinding(plaintext == null) {
                "TON signing material exists without a TON identity"
            }
            return
        }
        val encodedSecret = plaintext
            ?: secretBindingFailed("A TON identity has no signing material")

        val secrets = decodeCanonicalSecret(
            plaintext = encodedSecret,
            preflight = WalletSecretScalePreflight::requireTonV3,
            decode = TonSecrets::read,
            encode = { it.toHexString() }
        )
        val seed = secrets[TonSecrets.Seed]
        val privateKey = secrets[TonSecrets.PrivateKey]
        val publicKey = secrets[TonSecrets.PublicKey]
        requireSecretBinding(
            seed.isNotEmpty() &&
                seed.size <= MAX_TON_SEED_BYTES &&
                privateKey.size == PRIVATE_KEY_BYTES &&
                publicKey.size == TON_PUBLIC_KEY_BYTES
        ) {
            "A TON secret has an invalid key shape"
        }
        val derivedPublicKey = SolanaKeyDerivation.publicKeyFromPrivateKey(privateKey)
        requireSecretBinding(derivedPublicKey.contentEquals(publicKey)) {
            "A TON private key has a different public key"
        }
        requireSecretBinding(publicKey.toCanonicalHex() == publicKeyHex) {
            "A TON secret has a different public key"
        }

        val mnemonicText = seed.decodeToString(throwOnInvalidSequence = true)
        requireSecretBinding(
            mnemonicText.isNotEmpty() &&
                mnemonicText.length <= MAX_TON_MNEMONIC_CHARS &&
                mnemonicText == mnemonicText.trim() &&
                mnemonicText.split(' ').joinToString(" ") == mnemonicText
        ) {
            "A TON mnemonic is not canonical UTF-8 words"
        }
        val mnemonicWords = mnemonicText.split(' ')
        requireSecretBinding(mnemonicWords.size in VALID_MNEMONIC_WORD_COUNTS) {
            "A TON mnemonic has an invalid word count"
        }
        val validBip39Mnemonic = runCatching {
            MnemonicCreator.fromWords(mnemonicText).words == mnemonicText
        }.getOrDefault(false)
        val validTonMnemonic = runCatching {
            org.ton.mnemonic.Mnemonic.isValid(mnemonicWords)
        }.getOrDefault(false)
        requireSecretBinding(validBip39Mnemonic || validTonMnemonic) {
            "A TON mnemonic is invalid"
        }
        val mnemonicSeed = org.ton.mnemonic.Mnemonic.toSeed(mnemonicWords)
        val derivedPrivateKey = PrivateKeyEd25519(mnemonicSeed)
        requireSecretBinding(
            derivedPrivateKey.key.toByteArray().contentEquals(privateKey) &&
                derivedPrivateKey.publicKey().key.toByteArray().contentEquals(publicKey)
        ) {
            "A TON mnemonic derives a different keypair"
        }
    }

    private fun bindSubstrateRecoveryMaterial(
        secrets: jp.co.soramitsu.fearless_utils.scale.EncodableStruct<SubstrateSecrets>,
        expectedPrivateKey: ByteArray,
        expectedPublicKey: ByteArray,
        expectedNonce: ByteArray?,
        encryptionType: EncryptionType
    ) {
        val entropy = secrets[SubstrateSecrets.Entropy]
        val storedSeed = secrets[SubstrateSecrets.Seed]
        val derivationPath = secrets[SubstrateSecrets.SubstrateDerivationPath]
        requireSecretBinding(
            derivationPath == null || derivationPath.length <= MAX_DERIVATION_PATH_CHARS
        ) {
            "A Substrate derivation path is oversized"
        }
        val decodedPath = derivationPath
            ?.takeIf(String::isNotEmpty)
            ?.let(SubstrateJunctionDecoder::decode)

        val entropySeed = entropy?.let {
            val mnemonic = MnemonicCreator.fromEntropy(it)
            SubstrateSeedFactory.deriveSeed32(
                mnemonicWords = mnemonic.words,
                password = decodedPath?.password
            ).seed
        }
        if (entropySeed != null && storedSeed != null) {
            requireSecretBinding(entropySeed.contentEquals(storedSeed)) {
                "Substrate entropy and seed describe different recovery material"
            }
        }

        val recoverySeed = entropySeed ?: storedSeed ?: return
        requireSecretBinding(recoverySeed.size == PRIVATE_KEY_BYTES) {
            "A Substrate recovery seed has an invalid length"
        }
        val recoveredKeypair = SubstrateKeypairFactory.generate(
            encryptionType = encryptionType,
            seed = recoverySeed,
            junctions = decodedPath?.junctions.orEmpty()
        )
        val recoveredNonce = (recoveredKeypair as? Sr25519Keypair)?.nonce
        requireSecretBinding(
            recoveredKeypair.privateKey.contentEquals(expectedPrivateKey) &&
                recoveredKeypair.publicKey.contentEquals(expectedPublicKey) &&
                recoveredNonce.contentEqualsNullable(expectedNonce)
        ) {
            "Substrate recovery material derives a different keypair"
        }
    }

    private fun bindEthereumRecoveryMaterial(
        secrets: jp.co.soramitsu.fearless_utils.scale.EncodableStruct<EthereumSecrets>,
        expectedPrivateKey: ByteArray,
        expectedPublicKey: ByteArray
    ) {
        val entropy = secrets[EthereumSecrets.Entropy]
        val storedSeed = secrets[EthereumSecrets.Seed]
        val derivationPath = secrets[EthereumSecrets.EthereumDerivationPath]
        requireSecretBinding(
            derivationPath == null || derivationPath.length <= MAX_DERIVATION_PATH_CHARS
        ) {
            "An Ethereum derivation path is oversized"
        }
        val decodedPath = derivationPath
            ?.takeIf(String::isNotEmpty)
            ?.let(BIP32JunctionDecoder::decode)

        if (storedSeed != null) {
            requireSecretBinding(
                storedSeed.size == PRIVATE_KEY_BYTES &&
                    storedSeed.contentEquals(expectedPrivateKey)
            ) {
                "An Ethereum recovery seed differs from its private key"
            }
        }
        if (entropy != null) {
            val requiredPath = decodedPath
                ?: secretBindingFailed("Ethereum entropy requires a derivation path")
            val mnemonic = MnemonicCreator.fromEntropy(entropy)
            val seed = EthereumSeedFactory.deriveSeed32(
                mnemonicWords = mnemonic.words,
                password = requiredPath.password
            ).seed
            val recoveredKeypair = EthereumKeypairFactory.generate(
                seed = seed,
                junctions = requiredPath.junctions
            )
            requireSecretBinding(
                recoveredKeypair.privateKey.contentEquals(expectedPrivateKey) &&
                    recoveredKeypair.publicKey.contentEquals(expectedPublicKey)
            ) {
                "Ethereum entropy derives a different keypair"
            }
        }
    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
        return when {
            this == null || other == null -> this == null && other == null
            else -> contentEquals(other)
        }
    }

    private inline fun <T> decodeCanonicalSecret(
        plaintext: String,
        preflight: (String) -> Unit,
        decode: (String) -> T,
        encode: (T) -> String
    ): T {
        requireSecretBinding(
            plaintext.length <= MAX_SECRET_PLAINTEXT_CHARS &&
                CANONICAL_SCALE_HEX.matches(plaintext)
        ) {
            "A wallet secret is not canonical bounded SCALE hexadecimal"
        }
        preflight(plaintext)
        val decoded = decode(plaintext)
        requireSecretBinding(encode(decoded) == plaintext) {
            "A wallet secret contains a non-canonical SCALE payload"
        }
        return decoded
    }

    private fun buildSecretPlaintexts(
        metaId: Long,
        substrateSecretPlaintext: String?,
        ethereumSecretPlaintext: String?,
        tonSecretPlaintext: String?
    ): Map<String, String> {
        return buildMap {
            substrateSecretPlaintext?.let {
                put(secretKey(metaId, SUBSTRATE_SECRET_SUFFIX), it)
            }
            ethereumSecretPlaintext?.let {
                put(secretKey(metaId, ETHEREUM_SECRET_SUFFIX), it)
            }
            tonSecretPlaintext?.let {
                put(secretKey(metaId, TON_SECRET_SUFFIX), it)
            }
        }
    }

    private fun validateJournal(journal: Journal) {
        journalCall {
            journalStore.validate(journal)
        }
    }

    private fun stage(journal: Journal, secretPlaintexts: Map<String, String>) {
        journalCall {
            journalStore.stage(journal, secretPlaintexts)
        }
    }

    private inline fun <T> journalCall(action: () -> T): T {
        try {
            requireDurableStorageHealthy()
            return action()
        } catch (failure: WalletSecretMutationJournalStore.JournalException) {
            val reason = when (failure.reason) {
                WalletSecretMutationJournalStore.FailureReason.INVALID_ARGUMENT -> {
                    WalletMutationFailureReason.INVALID_ARGUMENT
                }
                WalletSecretMutationJournalStore.FailureReason.CONFLICT -> {
                    WalletMutationFailureReason.STATE_CONFLICT
                }
                WalletSecretMutationJournalStore.FailureReason.MALFORMED_STORED_JOURNAL,
                WalletSecretMutationJournalStore.FailureReason.UNSUPPORTED_VERSION -> {
                    WalletMutationFailureReason.RECOVERY_REQUIRED
                }
                WalletSecretMutationJournalStore.FailureReason.AMBIGUOUS_DURABILITY -> {
                    WalletMutationFailureReason.DURABILITY_UNKNOWN
                }
            }
            throw WalletSecretMutationCoordinatorException(
                reason = reason,
                message = "Wallet mutation persistence requires recovery",
                cause = failure
            )
        }
    }

    private fun requireDurableStorageHealthy() {
        encryptedPreferences.requireDurableStorageHealthy()
    }

    private fun MetaAccountLocal.toPublicImage(): PublicAfterImage {
        return PublicAfterImage(
            name = name,
            substratePublicKeyHex = substratePublicKey?.toCanonicalHex(),
            substrateAccountIdHex = substrateAccountId?.toCanonicalHex(),
            substrateCryptoType = substrateCryptoType?.toJournalCryptoType(),
            ethereumPublicKeyHex = ethereumPublicKey?.toCanonicalHex(),
            ethereumAddressHex = ethereumAddress?.toCanonicalHex(),
            tonPublicKeyHex = tonPublicKey?.toCanonicalHex(),
            isSelected = isSelected,
            position = position,
            isBackedUp = isBackedUp,
            googleBackupAddress = googleBackupAddress,
            initialized = initialized
        )
    }

    private fun PublicAfterImage.toMetaAccount(metaId: Long): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = substratePublicKeyHex?.decodeCanonicalHex(),
            substrateCryptoType = substrateCryptoType?.toCoreCryptoType(),
            substrateAccountId = substrateAccountIdHex?.decodeCanonicalHex(),
            ethereumPublicKey = ethereumPublicKeyHex?.decodeCanonicalHex(),
            ethereumAddress = ethereumAddressHex?.decodeCanonicalHex(),
            tonPublicKey = tonPublicKeyHex?.decodeCanonicalHex(),
            name = name,
            isSelected = isSelected,
            position = position,
            isBackedUp = isBackedUp,
            googleBackupAddress = googleBackupAddress,
            initialized = initialized
        ).apply {
            id = metaId
        }
    }

    private fun MetaAccountLocal.withEthereumIdentity(
        image: PublicAfterImage,
        resolvedIsBackedUp: Boolean
    ): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = substratePublicKey?.clone(),
            substrateCryptoType = substrateCryptoType,
            substrateAccountId = substrateAccountId?.clone(),
            ethereumPublicKey = image.ethereumPublicKeyHex?.decodeCanonicalHex(),
            ethereumAddress = image.ethereumAddressHex?.decodeCanonicalHex(),
            tonPublicKey = tonPublicKey?.clone(),
            name = name,
            isSelected = isSelected,
            position = position,
            isBackedUp = resolvedIsBackedUp,
            googleBackupAddress = googleBackupAddress,
            initialized = initialized
        ).apply {
            id = this@withEthereumIdentity.id
        }
    }

    private fun PublicAfterImage.cryptographicIdentity(): CryptographicIdentity {
        return CryptographicIdentity(
            substratePublicKeyHex = substratePublicKeyHex,
            substrateAccountIdHex = substrateAccountIdHex,
            substrateCryptoType = substrateCryptoType,
            ethereumPublicKeyHex = ethereumPublicKeyHex,
            ethereumAddressHex = ethereumAddressHex,
            tonPublicKeyHex = tonPublicKeyHex
        )
    }

    private fun MetaAccountLocal.copyWith(
        id: Long,
        position: Int,
        isSelected: Boolean
    ): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = substratePublicKey?.clone(),
            substrateCryptoType = substrateCryptoType,
            substrateAccountId = substrateAccountId?.clone(),
            ethereumPublicKey = ethereumPublicKey?.clone(),
            ethereumAddress = ethereumAddress?.clone(),
            tonPublicKey = tonPublicKey?.clone(),
            name = name,
            isSelected = isSelected,
            position = position,
            isBackedUp = isBackedUp,
            googleBackupAddress = googleBackupAddress,
            initialized = initialized
        ).apply {
            this.id = id
        }
    }

    private fun CryptoType.toJournalCryptoType(): SubstrateCryptoType {
        return when (this) {
            CryptoType.SR25519 -> SubstrateCryptoType.SR25519
            CryptoType.ED25519 -> SubstrateCryptoType.ED25519
            CryptoType.ECDSA -> SubstrateCryptoType.ECDSA
        }
    }

    private fun SubstrateCryptoType.toCoreCryptoType(): CryptoType {
        return when (this) {
            SubstrateCryptoType.SR25519 -> CryptoType.SR25519
            SubstrateCryptoType.ED25519 -> CryptoType.ED25519
            SubstrateCryptoType.ECDSA -> CryptoType.ECDSA
        }
    }

    private fun ByteArray.toCanonicalHex(): String {
        return joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(HEX_RADIX).padStart(2, '0')
        }
    }

    private fun String.decodeCanonicalHex(): ByteArray {
        require(length % 2 == 0 && LOWERCASE_HEX.matches(this)) {
            "A public identity is not canonical hexadecimal"
        }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(HEX_RADIX).toByte()
        }
    }

    private fun List<MetaAccountLocal>.selectedIds(): Set<Long> {
        return asSequence()
            .filter(MetaAccountLocal::isSelected)
            .map(MetaAccountLocal::id)
            .toSet()
    }

    private fun secretKey(metaId: Long, suffix: String): String {
        return "$metaId:$suffix"
    }

    private inline fun requireArgument(value: Boolean, lazyMessage: () -> String) {
        if (!value) {
            throw WalletSecretMutationCoordinatorException(
                reason = WalletMutationFailureReason.INVALID_ARGUMENT,
                message = lazyMessage()
            )
        }
    }

    private inline fun requireSecretBinding(value: Boolean, lazyMessage: () -> String) {
        if (!value) {
            secretBindingFailed(lazyMessage())
        }
    }

    private fun secretBindingFailed(message: String): Nothing {
        throw WalletSecretMutationCoordinatorException(
            reason = WalletMutationFailureReason.SECRET_BINDING_FAILED,
            message = message
        )
    }

    private inline fun requireState(value: Boolean, lazyMessage: () -> String) {
        if (!value) {
            stateConflict(lazyMessage())
        }
    }

    private fun stateConflict(message: String): Nothing {
        throw WalletSecretMutationCoordinatorException(
            reason = WalletMutationFailureReason.STATE_CONFLICT,
            message = message
        )
    }

    private fun recoveryRequired(message: String): Nothing {
        throw WalletSecretMutationCoordinatorException(
            reason = WalletMutationFailureReason.RECOVERY_REQUIRED,
            message = message
        )
    }

    private data class PreparedCreate(
        val metaId: Long,
        val afterImage: PublicAfterImage
    )

    private data class PreparedDelete(
        val beforeImage: PublicAfterImage,
        val selectedMetaIdAfterDelete: Long?,
        val chainAccountIdsHex: Set<String>,
        val tonConnectSecretKeys: Set<String>
    )

    private data class CryptographicIdentity(
        val substratePublicKeyHex: String?,
        val substrateAccountIdHex: String?,
        val substrateCryptoType: SubstrateCryptoType?,
        val ethereumPublicKeyHex: String?,
        val ethereumAddressHex: String?,
        val tonPublicKeyHex: String?
    )

    companion object {
        internal const val MAX_META_ID_ALLOCATION_ATTEMPTS = 64

        private const val SUBSTRATE_SECRET_SUFFIX = "SUBSTRATE_SECRETS"
        private const val ETHEREUM_SECRET_SUFFIX = "ETHEREUM_SECRETS"
        private const val TON_SECRET_SUFFIX = "TON_SECRETS"
        private const val PRIVATE_KEY_BYTES = 32
        private const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
        private const val SUBSTRATE_PUBLIC_KEY_BYTES = 32
        private const val ECDSA_PUBLIC_KEY_BYTES = 33
        private const val SR25519_NONCE_BYTES = 32
        private const val TON_PUBLIC_KEY_BYTES = 32
        private const val MAX_TON_SEED_BYTES = 8_192
        private const val MAX_TON_MNEMONIC_CHARS = 8_192
        private const val MAX_DERIVATION_PATH_CHARS = 2_048
        private const val MAX_SECRET_PLAINTEXT_CHARS = 1_048_576
        private const val HEX_RADIX = 16

        private val KEYPAIR_BINDING_MESSAGE =
            "fearless-wallet-secret-binding-v1".encodeToByteArray()
        private val VALID_MNEMONIC_WORD_COUNTS = setOf(12, 15, 18, 21, 24)
        private val CANONICAL_SCALE_HEX = Regex("^0x(?:[0-9a-f]{2})+$")
        private val LOWERCASE_HEX = Regex("^[0-9a-f]+$")
    }
}

internal interface WalletMutationDatabase {

    suspend fun <T> inTransaction(block: suspend WalletMutationDatabase.() -> T): T

    suspend fun getMetaAccounts(): List<MetaAccountLocal>

    suspend fun getMetaAccount(metaId: Long): MetaAccountLocal?

    suspend fun metaAccountExists(metaId: Long): Boolean

    suspend fun hasIdentityConflict(
        metaId: Long,
        substrateAccountId: ByteArray?,
        ethereumAddress: ByteArray?,
        tonPublicKey: ByteArray?
    ): Boolean

    suspend fun getChainAccountIds(metaId: Long): List<ByteArray>

    suspend fun getTonConnectionSecretOwners(
        metaId: Long
    ): List<TonConnectionSecretOwner>

    suspend fun hasOtherTonConnectionOwner(
        metaId: Long,
        clientId: String
    ): Boolean

    suspend fun hasAssets(metaId: Long): Boolean

    suspend fun getNextPosition(): Int

    suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long

    suspend fun updateMetaAccount(metaAccount: MetaAccountLocal)

    suspend fun selectMetaAccount(metaId: Long)

    suspend fun deleteMetaAccountAndSelectSuccessor(metaId: Long): Boolean
}

private class RoomWalletMutationDatabase(
    private val appDatabase: AppDatabase,
    private val metaAccountDao: MetaAccountDao
) : WalletMutationDatabase {

    override suspend fun <T> inTransaction(
        block: suspend WalletMutationDatabase.() -> T
    ): T {
        return appDatabase.withTransaction {
            block(this@RoomWalletMutationDatabase)
        }
    }

    override suspend fun getMetaAccounts(): List<MetaAccountLocal> {
        return metaAccountDao.getMetaAccounts()
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccountLocal? {
        return metaAccountDao.getMetaAccount(metaId)
    }

    override suspend fun metaAccountExists(metaId: Long): Boolean {
        return metaAccountDao.metaAccountExists(metaId)
    }

    override suspend fun hasIdentityConflict(
        metaId: Long,
        substrateAccountId: ByteArray?,
        ethereumAddress: ByteArray?,
        tonPublicKey: ByteArray?
    ): Boolean {
        return metaAccountDao.hasIdentityConflict(
            metaId = metaId,
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            tonPublicKey = tonPublicKey
        )
    }

    override suspend fun getChainAccountIds(metaId: Long): List<ByteArray> {
        return metaAccountDao.getChainAccountIds(metaId)
    }

    override suspend fun getTonConnectionSecretOwners(
        metaId: Long
    ): List<TonConnectionSecretOwner> {
        val clientId = boundedTonTextProjection(
            column = "clientId",
            alias = "boundedClientId",
            maxBytes = MAX_TON_CLIENT_ID_BYTES
        )
        val url = boundedTonTextProjection(
            column = "url",
            alias = "boundedUrl",
            maxBytes = TonConnectStorageKeys.MAX_URL_BYTES
        )
        val source = boundedTonTextProjection(
            column = "source",
            alias = "boundedSource",
            maxBytes = MAX_TON_SOURCE_BYTES
        )
        return appDatabase.openHelper.writableDatabase.query(
            "SELECT $clientId, $url, $source FROM ton_connection " +
                "WHERE metaId = ? ORDER BY rowid ASC " +
                "LIMIT ${TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET + 1}",
            arrayOf(metaId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (
                        size >=
                        TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET
                    ) {
                        throw WalletSecretMutationCoordinatorException(
                            reason = WalletMutationFailureReason.STATE_CONFLICT,
                            message =
                            "A wallet has too many TON Connect rows for deletion"
                        )
                    }
                    add(
                        TonConnectionSecretOwner(
                            clientId = cursor.readBoundedTonText(
                                alias = "boundedClientId",
                                maxBytes = MAX_TON_CLIENT_ID_BYTES
                            ),
                            url = cursor.readBoundedTonText(
                                alias = "boundedUrl",
                                maxBytes = TonConnectStorageKeys.MAX_URL_BYTES
                            ),
                            source = cursor.readBoundedTonText(
                                alias = "boundedSource",
                                maxBytes = MAX_TON_SOURCE_BYTES
                            )
                        )
                    )
                }
            }
        }
    }

    override suspend fun hasOtherTonConnectionOwner(
        metaId: Long,
        clientId: String
    ): Boolean {
        TonConnectStorageKeys.requireValidClientId(clientId)
        return appDatabase.openHelper.writableDatabase.query(
            "SELECT 1 FROM ton_connection " +
                "WHERE metaId != ? AND clientId = ? LIMIT 1",
            arrayOf<Any?>(metaId, clientId)
        ).use { it.moveToFirst() }
    }

    override suspend fun hasAssets(metaId: Long): Boolean {
        return metaAccountDao.hasMetaAccountAssets(metaId)
    }

    override suspend fun getNextPosition(): Int {
        return metaAccountDao.getNextPosition()
    }

    override suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long {
        return metaAccountDao.insertMetaAccount(metaAccount)
    }

    override suspend fun updateMetaAccount(metaAccount: MetaAccountLocal) {
        metaAccountDao.updateMetaAccount(metaAccount)
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        metaAccountDao.selectMetaAccount(metaId)
    }

    override suspend fun deleteMetaAccountAndSelectSuccessor(metaId: Long): Boolean {
        return metaAccountDao.deleteMetaAccountAndSelectSuccessor(metaId)
    }

    private fun boundedTonTextProjection(
        column: String,
        alias: String,
        maxBytes: Int
    ): String {
        val byteLength = "length(CAST($column AS BLOB))"
        return "$byteLength AS ${alias}ByteLength, " +
            "CASE WHEN $column IS NULL THEN NULL " +
            "WHEN $byteLength <= $maxBytes THEN $column ELSE '' END AS $alias"
    }

    private fun android.database.Cursor.readBoundedTonText(
        alias: String,
        maxBytes: Int
    ): String {
        val lengthIndex = getColumnIndexOrThrow("${alias}ByteLength")
        if (isNull(lengthIndex)) {
            invalidTonConnectionRow()
        }
        val byteLength = getLong(lengthIndex)
        if (byteLength < 0L || byteLength > maxBytes.toLong()) {
            invalidTonConnectionRow()
        }
        val value = getString(getColumnIndexOrThrow(alias))
        if (value.toByteArray(Charsets.UTF_8).size.toLong() != byteLength) {
            invalidTonConnectionRow()
        }
        return value
    }

    private fun invalidTonConnectionRow(): Nothing {
        throw WalletSecretMutationCoordinatorException(
            reason = WalletMutationFailureReason.STATE_CONFLICT,
            message = "A TON Connect row exceeds its bounded identity schema"
        )
    }

    private companion object {
        const val MAX_TON_CLIENT_ID_BYTES = 64
        const val MAX_TON_SOURCE_BYTES = 3
    }
}

internal data class TonConnectionSecretOwner(
    val clientId: String,
    val url: String,
    val source: String
)
