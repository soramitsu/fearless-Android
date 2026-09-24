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
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Stages a fresh-install cohort and durably reserves its new wallet IDs before any Room row or
 * target signing secret exists. The same process-wide mutex protects current wallet creators;
 * the Room transaction checks an empty wallet set while the encrypted journal and its exact ID
 * markers are committed. Room reservations block colliding direct wallet inserts; abandoned IDs
 * remain fenced as exact Room tombstones. An interrupted preference-to-Room transition is
 * reconciled only against the exact journal and an empty wallet DB.
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
                val existingReservations = reservationCount()
                check(existingReservations in 0..MAX_RESERVATION_ROWS - walletCount) {
                    "Portable cohort tombstone quota requires explicit maintenance"
                }
                val rows = reservations()
                requireValidReservations(rows)
                check(rows.none { it.state == PortableWalletReservationLocal.PENDING }) {
                    "An orphaned Room reservation requires reconciliation"
                }
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

    /** Replays an interrupted stage or finishes a committed abandonment without copying keys. */
    suspend fun reconcile(): PortableWalletCohortJournalStore.Token? {
        return WalletCrossStoreMutationMutex.instance.withLock {
            val outcome = database.inTransaction {
                check(reservationCount() <= MAX_RESERVATION_ROWS) {
                    "Portable cohort reservation quota is exceeded"
                }
                val rows = reservations()
                requireValidReservations(rows)
                val entry = journal.load()
                if (entry == null) {
                    check(rows.none { it.state == PortableWalletReservationLocal.PENDING }) {
                        "An orphaned Room reservation requires explicit quarantine"
                    }
                    return@inTransaction ReconcileOutcome.Empty
                }
                try {
                    entry.afterImage.localMetaIdsCopy().forEach { id ->
                        check(!metaAccountExists(id) && !secretNamespaces.hasSecretNamespace(id)) {
                            "A portable cohort destination was occupied after staging"
                        }
                    }
                    val expected = rowsFor(entry.afterImage, entry.token)
                    val expectedAbandoned = expected.map { it.copy(state = PortableWalletReservationLocal.ABANDONED) }
                    val present = rows.filter {
                        it.operationId == entry.token.operationId &&
                        it.afterImageSha256 == entry.token.afterImageSha256
                    }
                    val pending = rows.filter { it.state == PortableWalletReservationLocal.PENDING }
                    when {
                        present == expectedAbandoned && pending.isEmpty() ->
                            ReconcileOutcome.FinishAbandon(entry.token)
                        present.isEmpty() && pending.isEmpty() -> {
                            check(!hasAnyWallet()) { "A wallet was published while a cohort is pending" }
                            reserve(expected)
                            requireExactReservations(entry.afterImage, entry.token)
                            ReconcileOutcome.Active(entry.token)
                        }
                        present == expected && pending == expected -> {
                            check(!hasAnyWallet()) { "A wallet was published while a cohort is pending" }
                            ReconcileOutcome.Active(entry.token)
                        }
                        else -> error("Room reservations differ from the portable cohort journal")
                    }
                } finally {
                    entry.clearSecrets()
                }
            }
            when (outcome) {
                ReconcileOutcome.Empty -> null
                is ReconcileOutcome.Active -> outcome.token
                is ReconcileOutcome.FinishAbandon -> {
                    journal.abandon(outcome.token)
                    null
                }
            }
        }
    }

    /** Commits fenced Room tombstones before removing the exact encrypted journal and markers. */
    suspend fun abandon(token: PortableWalletCohortJournalStore.Token) =
        WalletCrossStoreMutationMutex.instance.withLock {
            database.inTransaction {
                check(reservationCount() <= MAX_RESERVATION_ROWS) {
                    "Portable cohort reservation quota is exceeded"
                }
                val entry = requireNotNull(journal.load()) { "No portable cohort is pending" }
                try {
                    check(!hasAnyWallet()) { "A portable cohort wallet is already visible" }
                    check(
                        entry.token.operationId == token.operationId &&
                            entry.token.afterImageSha256 == token.afterImageSha256
                    ) { "Portable cohort commitment changed" }
                    entry.afterImage.localMetaIdsCopy().forEach { id ->
                        check(!metaAccountExists(id) && !secretNamespaces.hasSecretNamespace(id)) {
                            "A portable cohort destination was occupied before abandonment"
                        }
                    }
                    val rows = reservations()
                    requireValidReservations(rows)
                    val expected = rowsFor(entry.afterImage, token)
                    val expectedAbandoned = expected.map { it.copy(state = PortableWalletReservationLocal.ABANDONED) }
                    val present = rows.filter {
                        it.operationId == token.operationId &&
                        it.afterImageSha256 == token.afterImageSha256
                    }
                    when {
                        present == expected -> {
                            check(rows.filter { it.state == PortableWalletReservationLocal.PENDING } == expected) {
                                "Another pending Room reservation conflicts with the portable cohort"
                            }
                            check(markAbandoned(token) == expected.size) {
                                "Room did not tombstone every portable wallet ID"
                            }
                            check(reservationsFor(token) == expectedAbandoned) {
                                "Room tombstones differ from the portable cohort journal"
                            }
                        }
                        present == expectedAbandoned -> {
                            check(rows.none { it.state == PortableWalletReservationLocal.PENDING }) {
                                "Another pending Room reservation conflicts with abandonment"
                            }
                        }
                        else -> error("Room reservations differ from the portable cohort journal")
                    }
                } finally {
                    entry.clearSecrets()
                }
            }
            journal.abandon(token)
        }

    private suspend fun FreshInstallWalletInventory.requireExactReservations(
        afterImage: PortableWalletCohortAfterImage.Record,
        token: PortableWalletCohortJournalStore.Token,
    ) {
        check(reservationsFor(token) == rowsFor(afterImage, token)) {
            "Room reservations differ from the portable cohort journal"
        }
    }

    private fun rowsFor(
        afterImage: PortableWalletCohortAfterImage.Record,
        token: PortableWalletCohortJournalStore.Token,
    ): List<PortableWalletReservationLocal> {
        val ids = afterImage.localMetaIdsCopy().toList().sorted()
        val idSetDigest = idSetSha256(ids)
        return ids.map { id ->
            PortableWalletReservationLocal(id, token.operationId, token.afterImageSha256, idSetDigest)
        }
    }

    private fun requireValidReservations(rows: List<PortableWalletReservationLocal>) {
        check(rows.size <= MAX_RESERVATION_ROWS) { "Portable cohort reservation quota is exceeded" }
        check(rows == rows.sortedBy(PortableWalletReservationLocal::metaId)) {
            "Room reservation order is invalid"
        }
        check(rows.map(PortableWalletReservationLocal::metaId).toSet().size == rows.size) {
            "Room reservation IDs are duplicated"
        }
        rows.groupBy { it.operationId to it.afterImageSha256 }.values.forEach { group ->
            val operationId = group.first().operationId
            val parsed = runCatching { UUID.fromString(operationId) }.getOrNull()
            check(
                operationId.length == UUID_CHARS &&
                    parsed != null &&
                    parsed.toString() == operationId &&
                    parsed.version() == UUID_V4 &&
                    parsed.variant() == RFC_UUID_VARIANT &&
                    group.first().afterImageSha256.matches(HEX_SHA256)
            ) {
                "Room reservation token is invalid"
            }
            val ids = group.map(PortableWalletReservationLocal::metaId)
            val idSetDigest = idSetSha256(ids)
            check(
                group.all { row ->
                    row.state == PortableWalletReservationLocal.PENDING ||
                        row.state == PortableWalletReservationLocal.ABANDONED
                }
            ) { "Room reservation state is invalid" }
            check(group.all { it.idSetSha256 == idSetDigest }) {
                "Room reservation ID commitment is invalid"
            }
        }
    }

    private fun idSetSha256(sortedIds: List<Long>): String {
        check(
            sortedIds.isNotEmpty() && sortedIds.size <= MAX_COHORT_WALLETS &&
                sortedIds == sortedIds.sorted() && sortedIds.distinct().size == sortedIds.size &&
                sortedIds.all { it > 0L }
        ) { "Portable reservation IDs are invalid" }
        val bytes = ByteBuffer.allocate(ID_SET_MAGIC.size + Short.SIZE_BYTES + sortedIds.size * Long.SIZE_BYTES)
            .put(ID_SET_MAGIC)
            .putShort(sortedIds.size.toShort())
        sortedIds.forEach(bytes::putLong)
        val encoded = bytes.array()
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
            try {
                digest.joinToString("") { "%02x".format(it) }
            } finally {
                digest.fill(0)
            }
        } finally {
            encoded.fill(0)
        }
    }

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
        const val MAX_COHORT_WALLETS = 128
        const val MAX_RESERVATION_ROWS = 8_192
        const val UUID_CHARS = 36
        const val UUID_V4 = 4
        const val RFC_UUID_VARIANT = 2
        val ID_SET_MAGIC = "FPWRIDS1".toByteArray(Charsets.US_ASCII)
        val HEX_SHA256 = Regex("[0-9a-f]{64}")
    }

    private sealed interface ReconcileOutcome {
        data object Empty : ReconcileOutcome
        data class Active(val token: PortableWalletCohortJournalStore.Token) : ReconcileOutcome
        data class FinishAbandon(val token: PortableWalletCohortJournalStore.Token) : ReconcileOutcome
    }
}

internal interface FreshInstallWalletInventory {
    suspend fun <T> inTransaction(block: suspend FreshInstallWalletInventory.() -> T): T
    suspend fun hasAnyWallet(): Boolean
    suspend fun metaAccountExists(metaId: Long): Boolean
    suspend fun reservationExists(metaId: Long): Boolean
    suspend fun reservationCount(): Int
    suspend fun reservations(): List<PortableWalletReservationLocal>
    suspend fun reservationsFor(token: PortableWalletCohortJournalStore.Token): List<PortableWalletReservationLocal>
    suspend fun reserve(rows: List<PortableWalletReservationLocal>)
    suspend fun markAbandoned(token: PortableWalletCohortJournalStore.Token): Int
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

    override suspend fun reservationCount(): Int = reservationDao.count()

    override suspend fun reservations(): List<PortableWalletReservationLocal> = reservationDao.all()

    override suspend fun reservationsFor(
        token: PortableWalletCohortJournalStore.Token,
    ): List<PortableWalletReservationLocal> = reservationDao.forToken(token.operationId, token.afterImageSha256)

    override suspend fun reserve(rows: List<PortableWalletReservationLocal>) = reservationDao.insertAll(rows)

    override suspend fun markAbandoned(token: PortableWalletCohortJournalStore.Token): Int =
        reservationDao.markAbandoned(token.operationId, token.afterImageSha256)
}
