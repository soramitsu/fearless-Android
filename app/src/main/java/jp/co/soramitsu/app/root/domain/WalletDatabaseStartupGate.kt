package jp.co.soramitsu.app.root.domain

import dagger.Lazy
import java.util.Collections
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinatorException
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummary
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummaryHolder
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.AssetsOrderMigrationIntegrityException
import jp.co.soramitsu.coredb.migrations.TonUpgradeSqlIntegrityException
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.impl.data.TonConnectMutationJournalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface WalletDatabaseStartupResult {
    /** Startup boundaries completed; this does not certify untouched database pages. */
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
    private val walletSecretAccessGuard: Lazy<WalletSecretAccessGuard> =
        Lazy {
            error("Wallet recovery-state inventory is unavailable")
        },
    private val walletOrphanSecretInventory:
        Lazy<WalletOrphanSecretInventory> =
        Lazy {
            error("Wallet orphan-secret inventory is unavailable")
        },
    private val walletQuarantineRevalidation:
        Lazy<WalletQuarantineRevalidation> =
        Lazy {
            error("Wallet quarantine revalidation is unavailable")
        },
    private val walletStartupRecoverySummaryHolder:
        WalletStartupRecoverySummaryHolder = WalletStartupRecoverySummaryHolder()
) {

    suspend fun open(): WalletDatabaseStartupResult = withContext(Dispatchers.IO) {
        try {
            // Opening a current-version Room database does not touch encrypted
            // preferences. Preflight the existing master key explicitly so a
            // lost or invalidated Android Keystore entry is handled here,
            // before PIN, signing, or export code can be created.
            encryptionUtil.getPrerenceAesKey()

            // Open Room before journal reconciliation. Rebuildable cache size
            // must not decide whether protected wallets can be opened. Each
            // journal validates its durable intent and exact affected state
            // before changing Room or encrypted preferences.
            val database = appDatabase.get().openHelper.writableDatabase

            // TON Connect owns its own encrypted restart journal and must
            // reconcile it before account deletion or any general Room
            // consumer can observe the database.
            tonConnectRepository.get().reconcilePendingMutation()

            // Account-level wallet mutations reconcile only after TON Connect
            // has made its cross-store state coherent. Keeping every database
            // dependency lazy prevents Activity injection from opening Room.
            walletSecretMutationCoordinator.get().reconcilePendingMutation()

            // Re-evaluate what earlier builds flagged with this build's
            // validators, then archive the secrets deleted wallets left
            // behind. Both run after the journals are coherent and inside the
            // mutex account and TON mutations hold across their preference/Room
            // split, so a staged CREATE or DB-first DELETE is never mistaken
            // for an orphan. Neither pass creates recovery state: a quarantine
            // that proves its identity is released, and an orphan is archived.
            val recoveryCounts =
                WalletCrossStoreMutationMutex.instance.withLock {
                    runStartupHousekeeping {
                        walletQuarantineRevalidation.get().reconcile(database)
                    }
                    runStartupHousekeeping {
                        walletOrphanSecretInventory.get().reconcile(database)
                    }
                    walletSecretAccessGuard.get().recoveryStateCounts()
                }
            // What is still flagged affects only its own key (addendum U0.1,
            // U0.2): the wallet list opens and the wallet screens point at
            // the items instead of the whole app stopping.
            walletStartupRecoverySummaryHolder.publish(
                WalletStartupRecoverySummary(
                    itemCount = recoveryCounts?.flaggedKeyCount,
                    archivedCount = recoveryCounts?.archivedKeyCount
                )
            )
            WalletDatabaseStartupResult.Ready
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            classifyWalletDatabaseStartupFailure(failure)
        }
    }
}

/**
 * The quarantine revalidation and the orphan inventory only release or
 * archive, each in one atomic write, so skipping a pass leaves every value
 * where it was and the next start tries again. A failure in either must not
 * keep healthy wallets closed (addendum U0.1). Secure-storage failures still
 * propagate: they stop every other startup step as well and have their own
 * guided result.
 */
private inline fun runStartupHousekeeping(pass: () -> Unit) {
    try {
        pass()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        if (failure.hasSecureStorageCause()) throw failure
    }
}

private fun Throwable.hasSecureStorageCause(): Boolean {
    var current: Throwable? = this
    val visited = Collections.newSetFromMap(
        IdentityHashMap<Throwable, Boolean>()
    )
    repeat(MAX_FAILURE_CAUSE_DEPTH) {
        val candidate = current ?: return false
        if (!visited.add(candidate)) return false
        if (candidate is WalletSecureStorageUnavailableException) return true
        current = try {
            candidate.cause
        } catch (_: Throwable) {
            return false
        }
    }
    return false
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
        else -> null
    }
}

private const val MAX_FAILURE_CAUSE_DEPTH = 64
