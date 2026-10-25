package jp.co.soramitsu.app.root.domain

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.Lazy
import java.security.Key
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.impl.data.repository.WalletMutationFailureReason
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinatorException
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateCounts
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummary
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummaryHolder
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.AssetsOrderMigrationIntegrityException
import jp.co.soramitsu.coredb.migrations.TonUpgradeSqlIntegrityException
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretReport
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidationReport
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import jp.co.soramitsu.tonconnect.impl.data.TonConnectMutationJournalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WalletDatabaseStartupGateTest {

    @Test
    fun `direct secure storage failure is classified separately`() {
        assertEquals(
            WalletDatabaseStartupResult.SecureStorageUnavailable,
            classifyWalletDatabaseStartupFailure(
                WalletSecureStorageUnavailableException("unavailable")
            )
        )
    }

    @Test
    fun `permanent master key loss requires recovery`() {
        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired(
                "WALLET_MASTER_KEY_RECOVERY"
            ),
            classifyWalletDatabaseStartupFailure(
                WalletSecureStorageUnavailableException(
                    message = "missing existing key",
                    kind =
                        WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
                )
            )
        )
    }

    @Test
    fun `ambiguous secure storage durability requires a fresh process`() {
        assertEquals(
            WalletDatabaseStartupResult.ProcessRestartRequired,
            classifyWalletDatabaseStartupFailure(
                WalletSecureStorageUnavailableException(
                    message = "commit readback was ambiguous",
                    kind =
                    WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
                )
            )
        )
    }

    @Test
    fun `deterministic migration integrity failures require recovery`() {
        val failures = listOf(
            AssetsOrderMigrationIntegrityException("invalid asset cache") to
                "ASSET_CACHE_MIGRATION_RECOVERY",
            TonUpgradeSqlIntegrityException("invalid TON SQL") to
                "TON_DATABASE_MIGRATION_RECOVERY",
            WalletRecoveryStateIntegrityException("invalid marker") to
                "WALLET_RECOVERY_STATE_INVALID"
        )

        failures.forEach { (failure, diagnosticCode) ->
            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    diagnosticCode
                ),
                classifyWalletDatabaseStartupFailure(
                    IllegalStateException("Room wrapper", failure)
                )
            )
        }
    }

    @Test
    fun `room wrapped secure storage failure is found through entire cause chain`() {
        val wrapped = IllegalStateException(
            "room open failed",
            RuntimeException(
                "migration failed",
                WalletSecureStorageUnavailableException("unavailable")
            )
        )

        assertEquals(
            WalletDatabaseStartupResult.SecureStorageUnavailable,
            classifyWalletDatabaseStartupFailure(wrapped)
        )
    }

    @Test
    fun `unrelated database failure remains a database error`() {
        assertEquals(
            WalletDatabaseStartupResult.DatabaseOpenFailed,
            classifyWalletDatabaseStartupFailure(
                IllegalStateException("schema mismatch")
            )
        )
    }

    @Test
    fun `malformed mutation journal requires recovery instead of retry loop`() {
        val failure = IllegalStateException(
            "room wrapper",
            WalletSecretMutationCoordinatorException(
                reason = WalletMutationFailureReason.RECOVERY_REQUIRED,
                message = "malformed pending mutation"
            )
        )

        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired(
                "WALLET_MUTATION_RECOVERY"
            ),
            classifyWalletDatabaseStartupFailure(failure)
        )
    }

    @Test
    fun `room wrapped raw mutation journal failures require recovery instead of retry loop`() {
        WalletSecretMutationJournalStore.FailureReason.entries.forEach { reason ->
            val failure = IllegalStateException(
                "Room migration wrapper",
                WalletSecretMutationJournalStore.JournalException(
                    reason = reason,
                    message = "raw mutation journal failure"
                )
            )

            assertEquals(
                reason.name,
                WalletDatabaseStartupResult.RecoveryRequired(
                    "WALLET_MUTATION_RECOVERY"
                ),
                classifyWalletDatabaseStartupFailure(failure)
            )
        }
    }

    @Test
    fun `cyclic cause graph cannot hang classification`() {
        val cyclic = CyclicFailure()

        assertEquals(
            WalletDatabaseStartupResult.DatabaseOpenFailed,
            classifyWalletDatabaseStartupFailure(cyclic)
        )
    }

    @Test
    fun `classification uses identity instead of attacker controlled equality`() {
        val equalityCalls = AtomicInteger()
        val wrapped = HostileEqualityFailure(
            WalletSecureStorageUnavailableException("unavailable"),
            equalityCalls
        )

        assertEquals(
            WalletDatabaseStartupResult.SecureStorageUnavailable,
            classifyWalletDatabaseStartupFailure(wrapped)
        )
        assertEquals(0, equalityCalls.get())
    }

    @Test
    fun `throwing cause accessor fails closed`() {
        assertEquals(
            WalletDatabaseStartupResult.DatabaseOpenFailed,
            classifyWalletDatabaseStartupFailure(ThrowingCauseFailure())
        )
    }

    @Test
    fun `unbounded generated cause chain is capped`() {
        val causeReads = AtomicInteger()

        assertEquals(
            WalletDatabaseStartupResult.DatabaseOpenFailed,
            classifyWalletDatabaseStartupFailure(
                UnboundedCauseFailure(causeReads)
            )
        )
        assertEquals(64, causeReads.get())
    }

    @Test
    fun `constructing gate does not request room or either reconciler`() {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val lazyDatabase = CountingLazy(appDatabase)
        val lazyTonConnect = CountingLazy(tonConnectRepository)
        val lazyCoordinator = CountingLazy(mutationCoordinator)
        val lazyRecoveryGuard = CountingLazy(mock<WalletSecretAccessGuard>())
        val lazyOrphanInventory =
            CountingLazy(mock<WalletOrphanSecretInventory>())
        val lazyRevalidation =
            CountingLazy(mock<WalletQuarantineRevalidation>())

        WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator,
            walletSecretAccessGuard = lazyRecoveryGuard,
            walletOrphanSecretInventory = lazyOrphanInventory,
            walletQuarantineRevalidation = lazyRevalidation
        )

        assertEquals(0, lazyDatabase.requests)
        assertEquals(0, lazyTonConnect.requests)
        assertEquals(0, lazyCoordinator.requests)
        assertEquals(0, lazyRecoveryGuard.requests)
        assertEquals(0, lazyOrphanInventory.requests)
        assertEquals(0, lazyRevalidation.requests)
        verify(encryptionUtil, never()).getPrerenceAesKey()
        verify(appDatabase, never()).openHelper
        Unit
    }

    @Test
    fun `master key is preflighted before room is requested`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val lazyDatabase = CountingLazy(appDatabase)
        val lazyTonConnect = CountingLazy(mock<TonConnectRepository>())
        val lazyCoordinator = CountingLazy(
            mock<WalletSecretMutationCoordinator>()
        )
        whenever(encryptionUtil.getPrerenceAesKey()).thenThrow(
            WalletSecureStorageUnavailableException("unavailable")
        )

        val result = WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator
        ).open()

        assertEquals(WalletDatabaseStartupResult.SecureStorageUnavailable, result)
        assertEquals(0, lazyDatabase.requests)
        assertEquals(0, lazyTonConnect.requests)
        assertEquals(0, lazyCoordinator.requests)
        verify(appDatabase, never()).openHelper
        Unit
    }

    @Test
    fun `Room opens before ordered journal reconciliation without a whole-file scan`() = runBlocking {
        val events = mutableListOf<String>()
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()

        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val recoveryGuard = mock<WalletSecretAccessGuard>()
        val orphanInventory = mock<WalletOrphanSecretInventory>()
        val revalidation = mock<WalletQuarantineRevalidation>()
        val lazyDatabase = CountingLazy(appDatabase) {
            events += "database-lazy"
        }
        val lazyCoordinator = CountingLazy(mutationCoordinator) {
            events += "coordinator-lazy"
        }
        val lazyTonConnect = CountingLazy(tonConnectRepository) {
            events += "ton-connect-lazy"
        }
        whenever(encryptionUtil.getPrerenceAesKey()).thenAnswer {
            events += "secure-storage-preflight"
            mock<Key>()
        }
        whenever(mutationCoordinator.reconcilePendingMutation()).thenAnswer {
            events += "wallet-reconcile"
            Unit
        }
        whenever(tonConnectRepository.reconcilePendingMutation()).thenAnswer {
            events += "ton-connect-reconcile"
            Unit
        }
        whenever(recoveryGuard.recoveryStateCounts()).thenAnswer {
            events += "recovery-inventory"
            NO_RECOVERY_STATE
        }
        whenever(revalidation.reconcile(database)).thenAnswer {
            events += "quarantine-revalidation"
            WalletQuarantineRevalidationReport()
        }
        whenever(orphanInventory.reconcile(database)).thenAnswer {
            events += "orphan-inventory"
            NO_ORPHANS
        }
        whenever(appDatabase.openHelper).thenAnswer {
            events += "open-helper"
            openHelper
        }
        whenever(openHelper.writableDatabase).thenAnswer {
            events += "writable-database"
            database
        }

        val result = WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator,

            CountingLazy(recoveryGuard),
            CountingLazy(orphanInventory),
            CountingLazy(revalidation)
        ).open()

        assertEquals(WalletDatabaseStartupResult.Ready, result)
        assertEquals(
            listOf(
                "secure-storage-preflight",
                "database-lazy",
                "open-helper",
                "writable-database",
                "ton-connect-lazy",
                "ton-connect-reconcile",
                "coordinator-lazy",
                "wallet-reconcile",
                "quarantine-revalidation",
                "orphan-inventory",
                "recovery-inventory"
            ),
            events
        )
        assertEquals(1, lazyDatabase.requests)
        assertEquals(1, lazyTonConnect.requests)
        assertEquals(1, lazyCoordinator.requests)
        verify(encryptionUtil).getPrerenceAesKey()

        verify(tonConnectRepository).reconcilePendingMutation()
        verify(mutationCoordinator).reconcilePendingMutation()
        verify(revalidation).reconcile(database)
        verify(orphanInventory).reconcile(database)
        Unit
    }

    @Test
    fun `orphan inventory waits across account creation staging boundary`() =
        runBlocking {
            assertOrphanInventoryWaitsForCrossStoreMutation(
                boundary = "account CREATE after secret staging"
            )
        }

    @Test
    fun `orphan inventory waits across account deletion database boundary`() =
        runBlocking {
            assertOrphanInventoryWaitsForCrossStoreMutation(
                boundary = "account DELETE after Room deletion"
            )
        }

    @Test
    fun `orphan inventory waits across TON deletion database boundary`() =
        runBlocking {
            assertOrphanInventoryWaitsForCrossStoreMutation(
                boundary = "TON DELETE after Room deletion"
            )
        }

    @Test
    fun `per-wallet recovery state never blocks startup and is summarised`() = runBlocking {
        // Before K3 one quarantine or marker in any wallet returned
        // RecoveryRequired("WALLET_SECRET_RECOVERY") and closed the whole app
        // (audit AND-S4, android-keys N1). Each item now affects its own key.
        listOf(
            WalletRecoveryStateCounts(flaggedKeyCount = 2, archivedKeyCount = 5),
            null
        ).forEach { counts ->
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()

            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
            val recoveryGuard = mock<WalletSecretAccessGuard>()
            val orphanInventory = mock<WalletOrphanSecretInventory>()
            val revalidation = mock<WalletQuarantineRevalidation>()
            val summaryHolder = WalletStartupRecoverySummaryHolder()
            whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            whenever(revalidation.reconcile(database))
                .thenReturn(WalletQuarantineRevalidationReport())
            whenever(orphanInventory.reconcile(database)).thenReturn(NO_ORPHANS)
            // Null: the inventory was out of bounds on this start.
            whenever(recoveryGuard.recoveryStateCounts()).thenReturn(counts)

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                CountingLazy(mutationCoordinator),

                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory),
                CountingLazy(revalidation),
                summaryHolder
            ).open()

            assertEquals(WalletDatabaseStartupResult.Ready, result)
            assertEquals(
                WalletStartupRecoverySummary(
                    itemCount = counts?.flaggedKeyCount,
                    archivedCount = counts?.archivedKeyCount
                ),
                summaryHolder.summary.value
            )
            assertTrue(summaryHolder.summary.value?.needsAttention == true)
            verify(tonConnectRepository).reconcilePendingMutation()
            verify(mutationCoordinator).reconcilePendingMutation()
            verify(orphanInventory).reconcile(database)
            verify(recoveryGuard).recoveryStateCounts()
        }
    }

    @Test
    fun `real quarantine and marker state opens the wallet`() = runBlocking {
        // The guard reads the same preference namespaces the 76 -> 77
        // migration of internal build 230 wrote into.
        val preferences = HashMapEncryptedPreferences()
        preferences.putEncryptedString(
            WalletSecretQuarantine.keyFor("3:ETHEREUM_SECRETS"),
            "ciphertext kept exactly"
        )
        preferences.putEncryptedString(
            WalletPublicIdentityRecovery.keyFor(4L, "4:TON_SECRETS"),
            WalletPublicIdentityRecovery.MARKER_VALUE
        )
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val revalidation = mock<WalletQuarantineRevalidation>()
        val orphanInventory = mock<WalletOrphanSecretInventory>()
        val summaryHolder = WalletStartupRecoverySummaryHolder()
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(revalidation.reconcile(database))
            .thenReturn(WalletQuarantineRevalidationReport())
        whenever(orphanInventory.reconcile(database)).thenReturn(NO_ORPHANS)

        val result = WalletDatabaseStartupGate(
            CountingLazy(appDatabase),
            encryptionUtil,
            CountingLazy(mock<TonConnectRepository>()),
            CountingLazy(mock<WalletSecretMutationCoordinator>()),

            CountingLazy(WalletSecretAccessGuard(preferences)),
            CountingLazy(orphanInventory),
            CountingLazy(revalidation),
            summaryHolder
        ).open()

        assertEquals(WalletDatabaseStartupResult.Ready, result)
        assertEquals(
            WalletStartupRecoverySummary(itemCount = 2, archivedCount = 0),
            summaryHolder.summary.value
        )
        assertEquals(
            "ciphertext kept exactly",
            preferences.getDecryptedString(WalletSecretQuarantine.keyFor("3:ETHEREUM_SECRETS"))
        )
    }

    @Test
    fun `a stray wallet recovery exception is no longer a startup recovery code`() {
        assertEquals(
            WalletDatabaseStartupResult.DatabaseOpenFailed,
            classifyWalletDatabaseStartupFailure(
                IllegalStateException(
                    "Room wrapper",
                    jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException()
                )
            )
        )
    }

    @Test
    fun `archived orphans of deleted wallets never block startup`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()

            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator =
                mock<WalletSecretMutationCoordinator>()
            val recoveryGuard = mock<WalletSecretAccessGuard>()
            val orphanInventory = mock<WalletOrphanSecretInventory>()
            val revalidation = mock<WalletQuarantineRevalidation>()
            whenever(encryptionUtil.getPrerenceAesKey())
                .thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            // An internal build's orphan markers are cleared and a 4.x
            // deleted wallet's keys are archived on this start.
            whenever(revalidation.reconcile(database)).thenReturn(
                WalletQuarantineRevalidationReport(archived = 3, markersCleared = 3)
            )
            whenever(orphanInventory.reconcile(database)).thenReturn(
                WalletOrphanSecretReport(
                    archivedCount = 3,
                    deferredCount = 0,
                    unrecognizedSecretKeyCount = 0
                )
            )
            whenever(recoveryGuard.recoveryStateCounts()).thenReturn(NO_RECOVERY_STATE)

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                CountingLazy(mutationCoordinator),

                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory),
                CountingLazy(revalidation)
            ).open()

            assertEquals(WalletDatabaseStartupResult.Ready, result)
            verify(tonConnectRepository).reconcilePendingMutation()
            verify(mutationCoordinator).reconcilePendingMutation()
            verify(revalidation).reconcile(database)
            verify(orphanInventory).reconcile(database)
            verify(recoveryGuard).recoveryStateCounts()
            Unit
        }

    @Test
    fun `failed revalidation or orphan inventory never keeps healthy wallets closed`() =
        runBlocking {
            // Both passes only release or archive in one atomic write each;
            // a failure leaves every value where it was for the next start.
            val failures = listOf(
                WalletRecoveryStateIntegrityException(
                    "orphan key inventory exceeded its bound"
                ),
                WalletSecretConcurrentMutationException("archive slot changed"),
                WalletPublicIdentityIntegrityException(
                    "A TON connection row has no safe positive wallet id"
                ),
                IllegalStateException("Chain-account rows exceed the limit")
            )
            failures.forEach { failure ->
                listOf(true, false).forEach { inventoryFails ->
                    val encryptionUtil = mock<EncryptionUtil>()
                    val appDatabase = mock<AppDatabase>()
                    val openHelper = mock<SupportSQLiteOpenHelper>()
                    val database = mock<SupportSQLiteDatabase>()
                    val recoveryGuard = mock<WalletSecretAccessGuard>()
                    val orphanInventory = mock<WalletOrphanSecretInventory>()
                    val revalidation = mock<WalletQuarantineRevalidation>()
                    whenever(encryptionUtil.getPrerenceAesKey())
                        .thenReturn(mock<Key>())
                    whenever(appDatabase.openHelper).thenReturn(openHelper)
                    whenever(openHelper.writableDatabase).thenReturn(database)
                    if (inventoryFails) {
                        whenever(revalidation.reconcile(database))
                            .thenReturn(WalletQuarantineRevalidationReport())
                        whenever(orphanInventory.reconcile(database))
                            .thenThrow(failure)
                    } else {
                        whenever(revalidation.reconcile(database))
                            .thenThrow(failure)
                        whenever(orphanInventory.reconcile(database))
                            .thenReturn(NO_ORPHANS)
                    }
                    whenever(recoveryGuard.recoveryStateCounts()).thenReturn(NO_RECOVERY_STATE)

                    val result = WalletDatabaseStartupGate(
                        CountingLazy(appDatabase),
                        encryptionUtil,
                        CountingLazy(mock<TonConnectRepository>()),
                        CountingLazy(mock<WalletSecretMutationCoordinator>()),

                        CountingLazy(recoveryGuard),
                        CountingLazy(orphanInventory),
                        CountingLazy(revalidation)
                    ).open()

                    assertEquals(WalletDatabaseStartupResult.Ready, result)
                    verify(revalidation).reconcile(database)
                    verify(orphanInventory).reconcile(database)
                    verify(recoveryGuard).recoveryStateCounts()
                }
            }
        }

    @Test
    fun `secure storage failure in a housekeeping pass is still reported`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()
            val recoveryGuard = mock<WalletSecretAccessGuard>()
            val orphanInventory = mock<WalletOrphanSecretInventory>()
            val revalidation = mock<WalletQuarantineRevalidation>()
            whenever(encryptionUtil.getPrerenceAesKey())
                .thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            whenever(revalidation.reconcile(database))
                .thenReturn(WalletQuarantineRevalidationReport())
            whenever(orphanInventory.reconcile(database)).thenThrow(
                IllegalStateException(
                    "archive write failed",
                    WalletSecureStorageUnavailableException("unavailable")
                )
            )

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(mock<TonConnectRepository>()),
                CountingLazy(mock<WalletSecretMutationCoordinator>()),

                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory),
                CountingLazy(revalidation)
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.SecureStorageUnavailable,
                result
            )
            verify(recoveryGuard, never()).recoveryStateCounts()
            Unit
        }

    @Test
    fun `pending mutation recovery failure never reports ready`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()

        val lazyDatabase = CountingLazy(appDatabase)
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(mutationCoordinator.reconcilePendingMutation()).thenThrow(
            WalletSecretMutationCoordinatorException(
                reason = WalletMutationFailureReason.RECOVERY_REQUIRED,
                message = "malformed pending mutation"
            )
        )

        val result = WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            CountingLazy(mock<TonConnectRepository>()),
            CountingLazy(mutationCoordinator),

        ).open()

        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired(
                "WALLET_MUTATION_RECOVERY"
            ),
            result
        )

        verify(mutationCoordinator).reconcilePendingMutation()
        assertEquals(1, lazyDatabase.requests)
        Unit
    }

    @Test
    fun `coordinator construction failure occurs after Room opens`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()

        val lazyDatabase = CountingLazy(appDatabase)
        val coordinatorFailure = IllegalStateException(
            "coordinator dependency unavailable"
        )
        val lazyCoordinator = ThrowingLazy<WalletSecretMutationCoordinator>(
            coordinatorFailure
        )
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)

        val result = WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            CountingLazy(mock<TonConnectRepository>()),
            lazyCoordinator,

        ).open()

        assertEquals(WalletDatabaseStartupResult.DatabaseOpenFailed, result)

        assertEquals(1, lazyCoordinator.requests)
        assertEquals(1, lazyDatabase.requests)
        Unit
    }

    @Test
    fun `wrapped TON secure storage replay failure is classified after Room opens`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()

            val lazyDatabase = CountingLazy(appDatabase)
            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
            val lazyCoordinator = CountingLazy(mutationCoordinator)
            whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            whenever(tonConnectRepository.reconcilePendingMutation()).thenThrow(
                IllegalStateException(
                    "TON journal read failed",
                    WalletSecureStorageUnavailableException("unavailable")
                )
            )

            val result = WalletDatabaseStartupGate(
                lazyDatabase,
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                lazyCoordinator,

            ).open()

            assertEquals(
                WalletDatabaseStartupResult.SecureStorageUnavailable,
                result
            )

            assertEquals(1, lazyDatabase.requests)
            assertEquals(0, lazyCoordinator.requests)
            verify(mutationCoordinator, never()).reconcilePendingMutation()
            Unit
        }

    @Test
    fun `TON replay failure is contained after room check and before account reconciliation`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()

            val lazyDatabase = CountingLazy(appDatabase)
            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
            val lazyCoordinator = CountingLazy(mutationCoordinator)
            whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            whenever(tonConnectRepository.reconcilePendingMutation()).thenThrow(
                TonConnectMutationJournalException(
                    "malformed TON mutation journal"
                )
            )

            val result = WalletDatabaseStartupGate(
                lazyDatabase,
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                lazyCoordinator,

            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "TON_CONNECT_JOURNAL_RECOVERY"
                ),
                result
            )

            assertEquals(0, lazyCoordinator.requests)
            assertEquals(1, lazyDatabase.requests)
            verify(mutationCoordinator, never()).reconcilePendingMutation()
            Unit
        }

    @Test
    fun `room migration exception is contained before reconciliation`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val lazyTonConnect = CountingLazy(tonConnectRepository)
        val lazyCoordinator = CountingLazy(mutationCoordinator)
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenThrow(
            IllegalStateException("migration validation failed")
        )

        val result = WalletDatabaseStartupGate(
            CountingLazy(appDatabase),
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator
        ).open()

        assertEquals(WalletDatabaseStartupResult.DatabaseOpenFailed, result)
        assertEquals(0, lazyTonConnect.requests)
        assertEquals(0, lazyCoordinator.requests)
        verify(tonConnectRepository, never()).reconcilePendingMutation()
        verify(mutationCoordinator, never()).reconcilePendingMutation()
        Unit
    }

    @Test
    fun `raw malformed journal from room migration reaches recovery before reconciliation`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
            val lazyTonConnect = CountingLazy(tonConnectRepository)
            val lazyCoordinator = CountingLazy(mutationCoordinator)
            whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenThrow(
                IllegalStateException(
                    "Room migration failed",
                    WalletSecretMutationJournalStore.JournalException(
                        reason = WalletSecretMutationJournalStore.FailureReason
                            .MALFORMED_STORED_JOURNAL,
                        message = "malformed persisted mutation journal"
                    )
                )
            )

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                lazyTonConnect,
                lazyCoordinator
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "WALLET_MUTATION_RECOVERY"
                ),
                result
            )
            assertEquals(0, lazyTonConnect.requests)
            assertEquals(0, lazyCoordinator.requests)
            verify(tonConnectRepository, never()).reconcilePendingMutation()
            verify(mutationCoordinator, never()).reconcilePendingMutation()
            Unit
        }

    @Test
    fun `TON replay cancellation propagates after Room opens`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()

        val lazyDatabase = CountingLazy(appDatabase)
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val lazyCoordinator = CountingLazy(mutationCoordinator)
        val cancellation = CancellationException("activity destroyed")
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(tonConnectRepository.reconcilePendingMutation()).thenThrow(
            cancellation
        )

        var observed: CancellationException? = null
        try {
            WalletDatabaseStartupGate(
                lazyDatabase,
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                lazyCoordinator,

            ).open()
        } catch (failure: CancellationException) {
            observed = failure
        }

        assertEquals(cancellation.message, observed?.message)

        assertEquals(1, lazyDatabase.requests)
        assertEquals(0, lazyCoordinator.requests)
        verify(mutationCoordinator, never()).reconcilePendingMutation()
        Unit
    }

    /**
     * Account CREATE, account DELETE, and TON DELETE all retain the same global
     * mutex across their preference/Room split boundary. Holding that mutex here
     * deterministically models each hostile boundary and proves startup cannot
     * inspect a transient ownerless active secret.
     */
    private suspend fun assertOrphanInventoryWaitsForCrossStoreMutation(
        boundary: String
    ) = coroutineScope {
        val boundaryEntered = CompletableDeferred<Unit>()
        val releaseBoundary = CompletableDeferred<Unit>()
        val mutationBoundary = async(Dispatchers.Default) {
            WalletCrossStoreMutationMutex.instance.withLock {
                boundaryEntered.complete(Unit)
                releaseBoundary.await()
            }
        }
        boundaryEntered.await()

        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()

        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val recoveryGuard = mock<WalletSecretAccessGuard>()
        val orphanInventory = mock<WalletOrphanSecretInventory>()
        val revalidation = mock<WalletQuarantineRevalidation>()
        val precedingReconciliationFinished = CompletableDeferred<Unit>()

        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(mutationCoordinator.reconcilePendingMutation()).thenAnswer {
            precedingReconciliationFinished.complete(Unit)
            Unit
        }
        whenever(revalidation.reconcile(database))
            .thenReturn(WalletQuarantineRevalidationReport())
        whenever(orphanInventory.reconcile(database)).thenReturn(NO_ORPHANS)
        whenever(recoveryGuard.recoveryStateCounts()).thenReturn(NO_RECOVERY_STATE)

        val startup = async(Dispatchers.Default) {
            WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                CountingLazy(mutationCoordinator),

                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory),
                CountingLazy(revalidation)
            ).open()
        }

        try {
            precedingReconciliationFinished.await()
            val resultWhileMutationIsSplit = withTimeoutOrNull(
                CROSS_STORE_CONTENTION_ASSERT_MILLIS
            ) {
                startup.await()
            }
            assertNull(
                "$boundary must retain startup before orphan inventory",
                resultWhileMutationIsSplit
            )
            assertFalse(startup.isCompleted)
            verify(revalidation, never()).reconcile(database)
            verify(orphanInventory, never()).reconcile(database)
            verify(recoveryGuard, never()).recoveryStateCounts()
        } finally {
            releaseBoundary.complete(Unit)
        }

        assertEquals(WalletDatabaseStartupResult.Ready, startup.await())
        verify(orphanInventory).reconcile(database)
        verify(recoveryGuard).recoveryStateCounts()
        mutationBoundary.await()
        Unit
    }

    private class CyclicFailure : RuntimeException() {
        override val cause: Throwable
            get() = this
    }

    private class HostileEqualityFailure(
        failure: Throwable,
        private val equalityCalls: AtomicInteger
    ) : RuntimeException("hostile equality", failure) {

        override fun equals(other: Any?): Boolean {
            equalityCalls.incrementAndGet()
            return this === other
        }

        override fun hashCode(): Int {
            equalityCalls.incrementAndGet()
            return 0
        }
    }

    private class ThrowingCauseFailure : RuntimeException() {
        override val cause: Throwable
            get() = error("cause accessor failed")
    }

    private class UnboundedCauseFailure(
        private val causeReads: AtomicInteger
    ) : RuntimeException() {
        override val cause: Throwable
            get() {
                causeReads.incrementAndGet()
                return UnboundedCauseFailure(causeReads)
            }
    }

    private class CountingLazy<T>(
        private val value: T,
        private val onGet: () -> Unit = {}
    ) : Lazy<T> {
        var requests = 0
            private set

        override fun get(): T {
            requests += 1
            onGet()
            return value
        }
    }

    private class ThrowingLazy<T>(
        private val failure: Throwable
    ) : Lazy<T> {
        var requests = 0
            private set

        override fun get(): T {
            requests += 1
            throw failure
        }
    }

    private companion object {
        const val CROSS_STORE_CONTENTION_ASSERT_MILLIS = 250L
        val NO_ORPHANS = WalletOrphanSecretReport(
            archivedCount = 0,
            deferredCount = 0,
            unrecognizedSecretKeyCount = 0
        )
        val NO_RECOVERY_STATE = WalletRecoveryStateCounts(
            flaggedKeyCount = 0,
            archivedKeyCount = 0
        )
    }
}
