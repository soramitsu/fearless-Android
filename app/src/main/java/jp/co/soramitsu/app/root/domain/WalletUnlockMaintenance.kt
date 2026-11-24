package jp.co.soramitsu.app.root.domain

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummary
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummaryHolder
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealingMigration
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSession
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What waits for SK: after every unlock (each time [WalletSecretSession]
 * opens), in order,
 * 1. the mutation journal replay the start-up gate deferred because it needed SK;
 * 2. the sealing migration, which resumes until every wallet secret is sealed
 *    and then retires the legacy PIN;
 * 3. the quarantine revalidation and the orphan inventory again, which left
 *    every sealed value alone before the PIN.
 *
 * Every step leaves storage as it was when it fails and runs again after the
 * next unlock, so nothing here can keep a wallet from opening.
 */
@Singleton
class WalletUnlockMaintenance @Inject constructor(
    private val appDatabase: Lazy<AppDatabase>,
    private val coordinator: Lazy<WalletSecretMutationCoordinator>,
    private val sealing: Lazy<WalletSecretSealingMigration>,
    private val revalidation: Lazy<WalletQuarantineRevalidation>,
    private val orphans: Lazy<WalletOrphanSecretInventory>,
    private val accessGuard: Lazy<WalletSecretAccessGuard>,
    private val summaryHolder: WalletStartupRecoverySummaryHolder
) {

    fun observe(scope: CoroutineScope): Job = WalletSecretSession.openings
        .filter { it > 0 }
        .conflate()
        .onEach { runOnce() }
        .launchIn(scope)

    suspend fun runOnce(): Unit = withContext(Dispatchers.IO) {
        if (!WalletSecretSession.isOpen()) return@withContext
        step { coordinator.get().reconcilePendingMutation() }
        step { sealing.get().run() }
        step {
            val database = appDatabase.get().openHelper.writableDatabase
            WalletCrossStoreMutationMutex.instance.withLock {
                step { revalidation.get().reconcile(database) }
                step { orphans.get().reconcile(database) }
                accessGuard.get().recoveryStateCounts()?.let { counts ->
                    summaryHolder.publish(WalletStartupRecoverySummary(counts.flaggedKeyCount, counts.archivedKeyCount))
                }
            }
        }
    }

    private inline fun step(action: () -> Unit) {
        try {
            action()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Left exactly as it was; the next unlock runs it again.
        }
    }
}
