package jp.co.soramitsu.account.impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import kotlinx.coroutines.sync.withLock

/**
 * Stages a fresh-install cohort and durably reserves its new wallet IDs before any Room row or
 * target signing secret exists. The same process-wide mutex protects current wallet creators;
 * the Room transaction checks an empty wallet set while the encrypted journal and its exact ID
 * markers are committed. A cross-process or bypass writer can still race after this transaction,
 * so a future installer must repeat the Room and namespace checks before inserting anything.
 * This API does not install, enable recovery, or infer missing backup/custody fields.
 */
internal class PortableWalletCohortFreshInstallStager private constructor(
    private val database: FreshInstallWalletInventory,
    private val journal: PortableWalletCohortJournalStore,
    private val secretNamespaces: WalletSecretMutationJournalStore,
    private val preferences: EncryptedPreferences,
    private val identifiers: WalletMutationIdentifierSource,
) {
    constructor(
        appDatabase: AppDatabase,
        metaAccountDao: MetaAccountDao,
        preferences: EncryptedPreferences,
        identifiers: WalletMutationIdentifierSource = SecureWalletMutationIdentifierSource(),
    ) : this(
        RoomFreshInstallWalletInventory(appDatabase, metaAccountDao),
        PortableWalletCohortJournalStore(preferences),
        WalletSecretMutationJournalStore(preferences),
        preferences,
        identifiers,
    )

    internal constructor(
        database: FreshInstallWalletInventory,
        journal: PortableWalletCohortJournalStore,
        secretNamespaces: WalletSecretMutationJournalStore,
        preferences: EncryptedPreferences,
        identifiers: WalletMutationIdentifierSource,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(database, journal, secretNamespaces, preferences, identifiers)

    /** Caller retains ownership of [semantic] and must clear it when finished. */
    suspend fun stage(semantic: ByteArray): PortableWalletCohortJournalStore.Token =
        WalletCrossStoreMutationMutex.instance.withLock {
            val snapshot = PortableWalletSemanticMaterial.decode(semantic)
            val walletCount = try {
                snapshot.wallets.size
            } finally {
                snapshot.clearSecrets()
            }
            database.inTransaction {
                check(!hasAnyWallet()) { "Portable cohort staging requires an empty local wallet set" }
                val allocated = linkedSetOf<Long>()
                repeat(walletCount) {
                    allocated += allocateUnusedId(allocated)
                }
                val afterImage = PortableWalletCohortAfterImage.create(semantic, allocated.toList())
                try {
                    journal.stage(identifiers.nextOperationId(), afterImage)
                } finally {
                    afterImage.clearSecrets()
                }
            }
        }

    private suspend fun FreshInstallWalletInventory.allocateUnusedId(allocated: Set<Long>): Long {
        repeat(MAX_ALLOCATION_ATTEMPTS) {
            val candidate = identifiers.nextMetaIdCandidate()
            if (candidate <= 0L || candidate in allocated || metaAccountExists(candidate)) return@repeat
            if (secretNamespaces.hasSecretNamespace(candidate)) return@repeat
            if (preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(candidate))) return@repeat
            return candidate
        }
        error("Unable to reserve a fresh portable wallet ID")
    }

    private companion object {
        const val MAX_ALLOCATION_ATTEMPTS = 64
    }
}

internal interface FreshInstallWalletInventory {
    suspend fun <T> inTransaction(block: suspend FreshInstallWalletInventory.() -> T): T
    suspend fun hasAnyWallet(): Boolean
    suspend fun metaAccountExists(metaId: Long): Boolean
}

private class RoomFreshInstallWalletInventory(
    private val appDatabase: AppDatabase,
    private val metaAccountDao: MetaAccountDao,
) : FreshInstallWalletInventory {
    override suspend fun <T> inTransaction(block: suspend FreshInstallWalletInventory.() -> T): T =
        appDatabase.withTransaction { block(this@RoomFreshInstallWalletInventory) }

    override suspend fun hasAnyWallet(): Boolean = metaAccountDao.getMetaAccounts().isNotEmpty()

    override suspend fun metaAccountExists(metaId: Long): Boolean = metaAccountDao.metaAccountExists(metaId)
}
