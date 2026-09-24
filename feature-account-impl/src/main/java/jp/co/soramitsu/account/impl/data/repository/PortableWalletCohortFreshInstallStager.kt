package jp.co.soramitsu.account.impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.PortableWalletReservationDao
import jp.co.soramitsu.coredb.model.PortableWalletReservationLocal
import kotlinx.coroutines.sync.withLock

/**
 * Stages a fresh-install cohort and durably reserves its new wallet IDs before any Room row or
 * target signing secret exists. The same process-wide mutex protects current wallet creators;
 * the Room transaction checks an empty wallet set while the encrypted journal and its exact ID
 * markers are committed. Room reservations block colliding direct wallet inserts; an interrupted
 * preference-to-Room transition is reconciled only against the exact journal and empty wallet DB.
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
        RoomFreshInstallWalletInventory(appDatabase, metaAccountDao, appDatabase.portableWalletReservationDao()),
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
                check(reservations().isEmpty()) { "An orphaned Room reservation requires reconciliation" }
                val allocated = linkedSetOf<Long>()
                repeat(walletCount) {
                    allocated += allocateUnusedId(allocated)
                }
                val afterImage = PortableWalletCohortAfterImage.create(semantic, allocated.toList())
                try {
                    val token = journal.stage(identifiers.nextOperationId(), afterImage)
                    reserve(rowsFor(afterImage, token))
                    requireExactReservations(afterImage, token)
                    token
                } finally {
                    afterImage.clearSecrets()
                }
            }
        }

    /** Replays an interrupted preference commit without publishing a wallet or copying keys. */
    suspend fun reconcile(): PortableWalletCohortJournalStore.Token? {
        return WalletCrossStoreMutationMutex.instance.withLock {
            database.inTransaction {
                val entry = journal.load()
                if (entry == null) {
                    check(reservations().isEmpty()) { "An orphaned Room reservation requires explicit quarantine" }
                    return@inTransaction null
                }
                try {
                    check(!hasAnyWallet()) { "A wallet was published while a cohort is pending" }
                    entry.afterImage.localMetaIdsCopy().forEach { id ->
                        check(!metaAccountExists(id) && !secretNamespaces.hasSecretNamespace(id)) {
                            "A portable cohort destination was occupied after staging"
                        }
                    }
                    val expected = rowsFor(entry.afterImage, entry.token)
                    val present = reservations()
                    when {
                        present.isEmpty() -> reserve(expected)
                        present != expected -> error("Room reservations differ from the portable cohort journal")
                    }
                    requireExactReservations(entry.afterImage, entry.token)
                    entry.token
                } finally {
                    entry.clearSecrets()
                }
            }
        }
    }

    /** Abandons only an exact, still-uninstalled cohort. A failed Room commit leaves IDs fenced. */
    suspend fun abandon(token: PortableWalletCohortJournalStore.Token) =
        WalletCrossStoreMutationMutex.instance.withLock {
            database.inTransaction {
                val entry = requireNotNull(journal.load()) { "No portable cohort is pending" }
                try {
                    check(!hasAnyWallet()) { "A portable cohort wallet is already visible" }
                    requireExactReservations(entry.afterImage, token)
                    check(
                        entry.token.operationId == token.operationId &&
                            entry.token.afterImageSha256 == token.afterImageSha256
                    ) { "Portable cohort commitment changed" }
                    journal.abandon(token)
                    check(release(token) == entry.afterImage.localMetaIdsCopy().size) {
                        "Room did not release every portable wallet ID"
                    }
                } finally {
                    entry.clearSecrets()
                }
            }
        }

    private suspend fun FreshInstallWalletInventory.requireExactReservations(
        afterImage: PortableWalletCohortAfterImage.Record,
        token: PortableWalletCohortJournalStore.Token,
    ) {
        check(reservations() == rowsFor(afterImage, token)) {
            "Room reservations differ from the portable cohort journal"
        }
    }

    private fun rowsFor(
        afterImage: PortableWalletCohortAfterImage.Record,
        token: PortableWalletCohortJournalStore.Token,
    ): List<PortableWalletReservationLocal> = afterImage.localMetaIdsCopy().map { id ->
        PortableWalletReservationLocal(id, token.operationId, token.afterImageSha256)
    }.sortedBy(PortableWalletReservationLocal::metaId)

    private suspend fun FreshInstallWalletInventory.allocateUnusedId(allocated: Set<Long>): Long {
        repeat(MAX_ALLOCATION_ATTEMPTS) {
            val candidate = identifiers.nextMetaIdCandidate()
            if (candidate <= 0L || candidate in allocated) return@repeat
            if (metaAccountExists(candidate) || reservationExists(candidate)) return@repeat
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
    suspend fun reservationExists(metaId: Long): Boolean
    suspend fun reservations(): List<PortableWalletReservationLocal>
    suspend fun reserve(rows: List<PortableWalletReservationLocal>)
    suspend fun release(token: PortableWalletCohortJournalStore.Token): Int
}

private class RoomFreshInstallWalletInventory(
    private val appDatabase: AppDatabase,
    private val metaAccountDao: MetaAccountDao,
    private val reservationDao: PortableWalletReservationDao,
) : FreshInstallWalletInventory {
    override suspend fun <T> inTransaction(block: suspend FreshInstallWalletInventory.() -> T): T =
        appDatabase.withTransaction { block(this@RoomFreshInstallWalletInventory) }

    override suspend fun hasAnyWallet(): Boolean = metaAccountDao.getMetaAccounts().isNotEmpty()

    override suspend fun metaAccountExists(metaId: Long): Boolean = metaAccountDao.metaAccountExists(metaId)

    override suspend fun reservationExists(metaId: Long): Boolean = reservationDao.contains(metaId)

    override suspend fun reservations(): List<PortableWalletReservationLocal> = reservationDao.all()

    override suspend fun reserve(rows: List<PortableWalletReservationLocal>) = reservationDao.insertAll(rows)

    override suspend fun release(token: PortableWalletCohortJournalStore.Token): Int =
        reservationDao.deleteExact(token.operationId, token.afterImageSha256)
}
