package jp.co.soramitsu.app.root.domain

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import dagger.Lazy
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinatorException
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.AssetsOrderMigrationIntegrityException
import jp.co.soramitsu.coredb.migrations.TonUpgradeSqlIntegrityException
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.impl.data.TonConnectMutationJournalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface WalletDatabaseStartupResult {
    data object Ready : WalletDatabaseStartupResult
    data object SecureStorageUnavailable : WalletDatabaseStartupResult
    data object ProcessRestartRequired : WalletDatabaseStartupResult
    data object DatabaseOpenFailed : WalletDatabaseStartupResult
    data class RecoveryRequired(
        val diagnosticCode: String
    ) : WalletDatabaseStartupResult
}

@Singleton
class WalletDatabaseStartupGate @Inject constructor(
    private val appDatabase: Lazy<AppDatabase>,
    private val encryptionUtil: EncryptionUtil,
    private val tonConnectRepository: Lazy<TonConnectRepository>,
    private val walletSecretMutationCoordinator:
        Lazy<WalletSecretMutationCoordinator>,
    private val databaseIntegrityChecker: WalletDatabaseIntegrityChecker =
        WalletDatabaseIntegrityChecker(),
    private val walletSecretAccessGuard: Lazy<WalletSecretAccessGuard> =
        Lazy {
            error("Wallet recovery-state inventory is unavailable")
        },
    private val walletOrphanSecretInventory:
        Lazy<WalletOrphanSecretInventory> =
        Lazy {
            error("Wallet orphan-secret inventory is unavailable")
        }
) {

    suspend fun open(): WalletDatabaseStartupResult = withContext(Dispatchers.IO) {
        try {
            // Opening a current-version Room database does not touch encrypted
            // preferences. Preflight the existing master key explicitly so a
            // lost or invalidated Android Keystore entry is handled here,
            // before PIN, signing, or export code can be created.
            encryptionUtil.getPrerenceAesKey()

            // Reconciliation can read and mutate Room. Open and bound-check the
            // database first so a corrupt or attacker-expanded current-version
            // store cannot reach either journal replayer.
            val database = appDatabase.get().openHelper.writableDatabase
            databaseIntegrityChecker.requireHealthy(database)

            // TON Connect owns its own encrypted restart journal and must
            // reconcile it before account deletion or any general Room
            // consumer can observe the database.
            tonConnectRepository.get().reconcilePendingMutation()

            // Account-level wallet mutations reconcile only after TON Connect
            // has made its cross-store state coherent. Keeping every database
            // dependency lazy prevents Activity injection from opening Room.
            walletSecretMutationCoordinator.get().reconcilePendingMutation()

            // Older version-77 installs may predate the complete orphan pass.
            // Enumerate canonical active namespaces after both journals are
            // coherent so a missing Room owner can never silently become Ready.
            // Account and TON mutations retain the same mutex across their
            // preference/Room split; joining it for both recovery inventories
            // prevents a staged CREATE or DB-first DELETE from being mistaken
            // for an historical orphan or transient recovery state.
            val (orphanRecoveryPublished, recoveryStatePresent) =
                WalletCrossStoreMutationMutex.instance.withLock {
                    walletOrphanSecretInventory.get().reconcile(database) to
                        walletSecretAccessGuard.get().hasAnyRecoveryState()
                }
            if (orphanRecoveryPublished || recoveryStatePresent) {
                WalletDatabaseStartupResult.RecoveryRequired(
                    diagnosticCode = "WALLET_SECRET_RECOVERY"
                )
            } else {
                WalletDatabaseStartupResult.Ready
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            classifyWalletDatabaseStartupFailure(failure)
        }
    }
}

internal data class WalletDatabaseIntegrityLimits(
    val maximumPageCount: Long,
    val maximumDatabaseBytes: Long,
    val deadlineMillis: Long
) {
    init {
        require(maximumPageCount > 0L)
        require(maximumDatabaseBytes > 0L)
        require(deadlineMillis > 0L)
    }

    companion object {
        val PRODUCTION = WalletDatabaseIntegrityLimits(
            // SQLite's minimum supported page size is 512 bytes. This page cap
            // and the independent byte cap therefore admit at most 512 MiB.
            maximumPageCount = 1_048_576L,
            maximumDatabaseBytes = 536_870_912L,
            deadlineMillis = 15_000L
        )
    }
}

internal fun interface WalletDatabaseIntegrityDeadline {
    fun cancel()
}

internal fun interface WalletDatabaseIntegrityDeadlineScheduler {
    fun schedule(
        delayMillis: Long,
        action: () -> Unit
    ): WalletDatabaseIntegrityDeadline
}

private object ProductionWalletDatabaseIntegrityDeadlineScheduler :
    WalletDatabaseIntegrityDeadlineScheduler {

    private val executor = ScheduledThreadPoolExecutor(
        1
    ) { runnable ->
        Thread(runnable, "wallet-db-integrity-deadline").apply {
            isDaemon = true
        }
    }.apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }

    override fun schedule(
        delayMillis: Long,
        action: () -> Unit
    ): WalletDatabaseIntegrityDeadline {
        val future = executor.schedule(
            Runnable { action() },
            delayMillis,
            TimeUnit.MILLISECONDS
        )
        return WalletDatabaseIntegrityDeadline {
            future.cancel(false)
        }
    }
}

internal class WalletDatabaseIntegrityCheckException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Runs SQLite's whole-database validation only after proving a deterministic
 * page/byte bound. A framework [CancellationSignal] is armed for the complete
 * pragma sequence, so native SQLite work is interrupted at the deadline rather
 * than merely cancelling the surrounding coroutine.
 */
class WalletDatabaseIntegrityChecker internal constructor(
    private val limits: WalletDatabaseIntegrityLimits,
    private val cancellationSignalFactory: () -> CancellationSignal,
    private val deadlineScheduler: WalletDatabaseIntegrityDeadlineScheduler
) {

    @Inject
    internal constructor() : this(
        limits = WalletDatabaseIntegrityLimits.PRODUCTION,
        cancellationSignalFactory = ::CancellationSignal,
        deadlineScheduler =
            ProductionWalletDatabaseIntegrityDeadlineScheduler
    )

    internal fun requireHealthy(database: SupportSQLiteDatabase) {
        val cancellationSignal = cancellationSignalFactory()
        val deadlineState = AtomicReference(DeadlineState.ACTIVE)
        val deadline = deadlineScheduler.schedule(limits.deadlineMillis) {
            if (
                deadlineState.compareAndSet(
                    DeadlineState.ACTIVE,
                    DeadlineState.TIMED_OUT
                )
            ) {
                cancellationSignal.cancel()
            }
        }

        try {
            try {
                val pageCount = readPositiveIntegerPragma(
                    database = database,
                    query = PAGE_COUNT_QUERY,
                    cancellationSignal = cancellationSignal,
                    description = "page count"
                )
                requireDeadlineActive(deadlineState)
                if (pageCount > limits.maximumPageCount) {
                    throw WalletDatabaseIntegrityCheckException(
                        "Wallet database exceeds the safe page-count limit"
                    )
                }

                val pageSize = readPositiveIntegerPragma(
                    database = database,
                    query = PAGE_SIZE_QUERY,
                    cancellationSignal = cancellationSignal,
                    description = "page size"
                )
                requireDeadlineActive(deadlineState)
                if (
                    pageSize !in MIN_SQLITE_PAGE_BYTES..MAX_SQLITE_PAGE_BYTES ||
                    (pageSize and (pageSize - 1L)) != 0L
                ) {
                    throw WalletDatabaseIntegrityCheckException(
                        "Wallet database reports an invalid SQLite page size"
                    )
                }
                if (
                    pageCount > limits.maximumDatabaseBytes / pageSize
                ) {
                    throw WalletDatabaseIntegrityCheckException(
                        "Wallet database exceeds the safe byte-size limit"
                    )
                }

                database.query(
                    QUICK_CHECK_QUERY,
                    cancellationSignal
                ).use { cursor ->
                    if (
                        !cursor.moveToFirst() ||
                        cursor.getString(0) != SQLITE_OK
                    ) {
                        throw WalletDatabaseIntegrityCheckException(
                            "Wallet database integrity check failed"
                        )
                    }
                }

                if (
                    !deadlineState.compareAndSet(
                        DeadlineState.ACTIVE,
                        DeadlineState.COMPLETED
                    )
                ) {
                    throw deadlineFailure()
                }
            } catch (failure: Throwable) {
                if (deadlineState.get() == DeadlineState.TIMED_OUT) {
                    if (
                        failure is WalletDatabaseIntegrityCheckException &&
                        failure.message == DEADLINE_FAILURE_MESSAGE
                    ) {
                        throw failure
                    }
                    throw deadlineFailure(failure)
                }
                throw failure
            }
        } finally {
            deadlineState.compareAndSet(
                DeadlineState.ACTIVE,
                DeadlineState.COMPLETED
            )
            deadline.cancel()
        }
    }

    private fun readPositiveIntegerPragma(
        database: SupportSQLiteDatabase,
        query: SupportSQLiteQuery,
        cancellationSignal: CancellationSignal,
        description: String
    ): Long {
        return database.query(query, cancellationSignal).use { cursor ->
            if (
                !cursor.moveToFirst() ||
                cursor.getType(0) != Cursor.FIELD_TYPE_INTEGER
            ) {
                throw WalletDatabaseIntegrityCheckException(
                    "Wallet database returned an invalid $description"
                )
            }
            cursor.getLong(0).also { value ->
                if (value <= 0L) {
                    throw WalletDatabaseIntegrityCheckException(
                        "Wallet database returned a non-positive $description"
                    )
                }
            }
        }
    }

    private fun requireDeadlineActive(
        deadlineState: AtomicReference<DeadlineState>
    ) {
        if (deadlineState.get() != DeadlineState.ACTIVE) {
            throw deadlineFailure()
        }
    }

    private fun deadlineFailure(
        cause: Throwable? = null
    ): WalletDatabaseIntegrityCheckException {
        return WalletDatabaseIntegrityCheckException(
            DEADLINE_FAILURE_MESSAGE,
            cause
        )
    }

    private enum class DeadlineState {
        ACTIVE,
        TIMED_OUT,
        COMPLETED
    }

    private companion object {
        const val SQLITE_OK = "ok"
        const val DEADLINE_FAILURE_MESSAGE =
            "Wallet database integrity validation exceeded its deadline"
        const val MIN_SQLITE_PAGE_BYTES = 512L
        const val MAX_SQLITE_PAGE_BYTES = 65_536L

        val PAGE_COUNT_QUERY = SimpleSQLiteQuery("PRAGMA page_count")
        val PAGE_SIZE_QUERY = SimpleSQLiteQuery("PRAGMA page_size")
        val QUICK_CHECK_QUERY = SimpleSQLiteQuery("PRAGMA quick_check(1)")
    }
}

internal fun classifyWalletDatabaseStartupFailure(
    failure: Throwable
): WalletDatabaseStartupResult {
    var current: Throwable? = failure
    val visited = Collections.newSetFromMap(
        IdentityHashMap<Throwable, Boolean>()
    )

    repeat(MAX_FAILURE_CAUSE_DEPTH) {
        val candidate = current
            ?: return WalletDatabaseStartupResult.DatabaseOpenFailed
        if (!visited.add(candidate)) {
            return WalletDatabaseStartupResult.DatabaseOpenFailed
        }
        if (candidate is WalletSecureStorageUnavailableException) {
            return when (candidate.kind) {
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS ->
                    WalletDatabaseStartupResult.RecoveryRequired(
                        diagnosticCode = "WALLET_MASTER_KEY_RECOVERY"
                    )
                WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED ->
                    WalletDatabaseStartupResult.ProcessRestartRequired
                WalletSecureStorageFailureKind.RETRYABLE ->
                    WalletDatabaseStartupResult.SecureStorageUnavailable
            }
        }
        val diagnosticCode = candidate.permanentStartupDiagnosticCode()
        if (diagnosticCode != null) {
            return WalletDatabaseStartupResult.RecoveryRequired(
                diagnosticCode = diagnosticCode
            )
        }
        current = try {
            candidate.cause
        } catch (_: Throwable) {
            return WalletDatabaseStartupResult.DatabaseOpenFailed
        }
    }

    return WalletDatabaseStartupResult.DatabaseOpenFailed
}

private fun Throwable.permanentStartupDiagnosticCode(): String? {
    return when (this) {
        is WalletSecretMutationCoordinatorException ->
            "WALLET_MUTATION_RECOVERY"
        // Room 76 -> 77 reads this store directly, before the account
        // coordinator can translate its fail-closed exception. Treat every raw
        // journal failure as recovery-required so malformed or unsupported
        // durable intent cannot trap startup in an endless generic retry loop.
        is WalletSecretMutationJournalStore.JournalException ->
            "WALLET_MUTATION_RECOVERY"
        is TonConnectMutationJournalException ->
            "TON_CONNECT_JOURNAL_RECOVERY"
        is WalletRecoveryRequiredException ->
            "WALLET_SECRET_RECOVERY"
        is WalletPublicIdentityIntegrityException ->
            "WALLET_IDENTITY_RECOVERY"
        is WalletSecretConcurrentMutationException ->
            "WALLET_STATE_CONFLICT"
        is WalletRecoveryStateIntegrityException ->
            "WALLET_RECOVERY_STATE_INVALID"
        is AssetsOrderMigrationIntegrityException ->
            "ASSET_CACHE_MIGRATION_RECOVERY"
        is TonUpgradeSqlIntegrityException ->
            "TON_DATABASE_MIGRATION_RECOVERY"
        is WalletDatabaseIntegrityCheckException -> {
            if (message == DATABASE_INTEGRITY_DEADLINE_MESSAGE) {
                null
            } else {
                "DATABASE_INTEGRITY_RECOVERY"
            }
        }
        else -> null
    }
}

private const val DATABASE_INTEGRITY_DEADLINE_MESSAGE =
    "Wallet database integrity validation exceeded its deadline"
private const val MAX_FAILURE_CAUSE_DEPTH = 64
