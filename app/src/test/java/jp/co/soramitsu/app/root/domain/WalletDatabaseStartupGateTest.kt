package jp.co.soramitsu.app.root.domain

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import dagger.Lazy
import java.security.Key
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.impl.data.repository.WalletMutationFailureReason
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinatorException
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
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
import org.junit.Test
import org.mockito.kotlin.any
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
    fun `integrity deadline remains retryable`() {
        assertEquals(
            WalletDatabaseStartupResult.DatabaseOpenFailed,
            classifyWalletDatabaseStartupFailure(
                WalletDatabaseIntegrityCheckException(
                    "Wallet database integrity validation exceeded its deadline"
                )
            )
        )
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

        WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator,
            walletSecretAccessGuard = lazyRecoveryGuard,
            walletOrphanSecretInventory = lazyOrphanInventory
        )

        assertEquals(0, lazyDatabase.requests)
        assertEquals(0, lazyTonConnect.requests)
        assertEquals(0, lazyCoordinator.requests)
        assertEquals(0, lazyRecoveryGuard.requests)
        assertEquals(0, lazyOrphanInventory.requests)
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
    fun `database integrity is verified before either reconciliation`() = runBlocking {
        val events = mutableListOf<String>()
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val recoveryGuard = mock<WalletSecretAccessGuard>()
        val orphanInventory = mock<WalletOrphanSecretInventory>()
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
        whenever(recoveryGuard.hasAnyRecoveryState()).thenAnswer {
            events += "recovery-inventory"
            false
        }
        whenever(orphanInventory.reconcile(database)).thenAnswer {
            events += "orphan-inventory"
            false
        }
        whenever(appDatabase.openHelper).thenAnswer {
            events += "open-helper"
            openHelper
        }
        whenever(openHelper.writableDatabase).thenAnswer {
            events += "writable-database"
            database
        }
        whenever(databaseIntegrityChecker.requireHealthy(database)).thenAnswer {
            events += "integrity-check"
            Unit
        }

        val result = WalletDatabaseStartupGate(
            lazyDatabase,
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator,
            databaseIntegrityChecker,
            CountingLazy(recoveryGuard),
            CountingLazy(orphanInventory)
        ).open()

        assertEquals(WalletDatabaseStartupResult.Ready, result)
        assertEquals(
            listOf(
                "secure-storage-preflight",
                "database-lazy",
                "open-helper",
                "writable-database",
                "integrity-check",
                "ton-connect-lazy",
                "ton-connect-reconcile",
                "coordinator-lazy",
                "wallet-reconcile",
                "orphan-inventory",
                "recovery-inventory"
            ),
            events
        )
        assertEquals(1, lazyDatabase.requests)
        assertEquals(1, lazyTonConnect.requests)
        assertEquals(1, lazyCoordinator.requests)
        verify(encryptionUtil).getPrerenceAesKey()
        verify(databaseIntegrityChecker).requireHealthy(database)
        verify(tonConnectRepository).reconcilePendingMutation()
        verify(mutationCoordinator).reconcilePendingMutation()
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
    fun `migration recovery inventory blocks wallet startup`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val recoveryGuard = mock<WalletSecretAccessGuard>()
        val orphanInventory = mock<WalletOrphanSecretInventory>()
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(orphanInventory.reconcile(database)).thenReturn(false)
        whenever(recoveryGuard.hasAnyRecoveryState()).thenReturn(true)

        val result = WalletDatabaseStartupGate(
            CountingLazy(appDatabase),
            encryptionUtil,
            CountingLazy(tonConnectRepository),
            CountingLazy(mutationCoordinator),
            databaseIntegrityChecker,
            CountingLazy(recoveryGuard),
            CountingLazy(orphanInventory)
        ).open()

        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired(
                "WALLET_SECRET_RECOVERY"
            ),
            result
        )
        verify(tonConnectRepository).reconcilePendingMutation()
        verify(mutationCoordinator).reconcilePendingMutation()
        verify(orphanInventory).reconcile(database)
        verify(recoveryGuard).hasAnyRecoveryState()
        Unit
    }

    @Test
    fun `historical orphan publication blocks current version startup`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()
            val databaseIntegrityChecker =
                mock<WalletDatabaseIntegrityChecker>()
            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator =
                mock<WalletSecretMutationCoordinator>()
            val recoveryGuard = mock<WalletSecretAccessGuard>()
            val orphanInventory = mock<WalletOrphanSecretInventory>()
            whenever(encryptionUtil.getPrerenceAesKey())
                .thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            whenever(orphanInventory.reconcile(database)).thenReturn(true)
            whenever(recoveryGuard.hasAnyRecoveryState()).thenReturn(false)

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                CountingLazy(mutationCoordinator),
                databaseIntegrityChecker,
                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory)
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "WALLET_SECRET_RECOVERY"
                ),
                result
            )
            verify(tonConnectRepository).reconcilePendingMutation()
            verify(mutationCoordinator).reconcilePendingMutation()
            verify(orphanInventory).reconcile(database)
            verify(recoveryGuard).hasAnyRecoveryState()
            Unit
        }

    @Test
    fun `hostile orphan inventory fails closed before generic recovery scan`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()
            val databaseIntegrityChecker =
                mock<WalletDatabaseIntegrityChecker>()
            val recoveryGuard = mock<WalletSecretAccessGuard>()
            val orphanInventory = mock<WalletOrphanSecretInventory>()
            whenever(encryptionUtil.getPrerenceAesKey())
                .thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(database)
            whenever(orphanInventory.reconcile(database)).thenThrow(
                WalletRecoveryStateIntegrityException(
                    "orphan key inventory exceeded its bound"
                )
            )

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(mock<TonConnectRepository>()),
                CountingLazy(mock<WalletSecretMutationCoordinator>()),
                databaseIntegrityChecker,
                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory)
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "WALLET_RECOVERY_STATE_INVALID"
                ),
                result
            )
            verify(orphanInventory).reconcile(database)
            verify(recoveryGuard, never()).hasAnyRecoveryState()
            Unit
        }

    @Test
    fun `integrity check failure never reports ready`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val lazyTonConnect = CountingLazy(tonConnectRepository)
        val lazyCoordinator = CountingLazy(mutationCoordinator)
        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(databaseIntegrityChecker.requireHealthy(database)).thenThrow(
            WalletDatabaseIntegrityCheckException(
                "database disk image is malformed"
            )
        )

        val result = WalletDatabaseStartupGate(
            CountingLazy(appDatabase),
            encryptionUtil,
            lazyTonConnect,
            lazyCoordinator,
            databaseIntegrityChecker
        ).open()

        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired(
                "DATABASE_INTEGRITY_RECOVERY"
            ),
            result
        )
        verify(databaseIntegrityChecker).requireHealthy(database)
        assertEquals(0, lazyTonConnect.requests)
        assertEquals(0, lazyCoordinator.requests)
        verify(tonConnectRepository, never()).reconcilePendingMutation()
        verify(mutationCoordinator, never()).reconcilePendingMutation()
        Unit
    }

    @Test
    fun `pending mutation recovery failure never reports ready`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
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
            databaseIntegrityChecker
        ).open()

        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired(
                "WALLET_MUTATION_RECOVERY"
            ),
            result
        )
        verify(databaseIntegrityChecker).requireHealthy(database)
        verify(mutationCoordinator).reconcilePendingMutation()
        assertEquals(1, lazyDatabase.requests)
        Unit
    }

    @Test
    fun `coordinator construction failure occurs after healthy room check`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
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
            databaseIntegrityChecker
        ).open()

        assertEquals(WalletDatabaseStartupResult.DatabaseOpenFailed, result)
        verify(databaseIntegrityChecker).requireHealthy(database)
        assertEquals(1, lazyCoordinator.requests)
        assertEquals(1, lazyDatabase.requests)
        Unit
    }

    @Test
    fun `wrapped TON secure storage replay failure is classified after healthy room check`() =
        runBlocking {
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val database = mock<SupportSQLiteDatabase>()
            val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
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
                databaseIntegrityChecker
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.SecureStorageUnavailable,
                result
            )
            verify(databaseIntegrityChecker).requireHealthy(database)
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
            val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
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
                databaseIntegrityChecker
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "TON_CONNECT_JOURNAL_RECOVERY"
                ),
                result
            )
            verify(databaseIntegrityChecker).requireHealthy(database)
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
    fun `bounded integrity check accepts a normal database and closes every cursor`() {
        val fixture = integrityCheckFixture()

        fixture.checker.requireHealthy(fixture.database)

        assertEquals(
            listOf(
                "PRAGMA page_count",
                "PRAGMA page_size",
                "PRAGMA quick_check(1)"
            ),
            fixture.queries
        )
        assertEquals(TEST_INTEGRITY_DEADLINE_MILLIS, fixture.scheduler.delayMillis)
        assertEquals(1, fixture.scheduler.cancelCalls)
        verify(fixture.cancellationSignal, never()).cancel()
        verify(fixture.pageCountCursor).close()
        verify(fixture.pageSizeCursor).close()
        verify(fixture.quickCheckCursor).close()
    }

    @Test
    fun `page count above the deterministic cap fails before further scans`() {
        val fixture = integrityCheckFixture(pageCount = 11L)

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(listOf("PRAGMA page_count"), fixture.queries)
        assertEquals(1, fixture.scheduler.cancelCalls)
        verify(fixture.cancellationSignal, never()).cancel()
        verify(fixture.pageCountCursor).close()
        verify(fixture.pageSizeCursor, never()).close()
        verify(fixture.quickCheckCursor, never()).close()
    }

    @Test
    fun `non-positive page count fails before further scans`() {
        val fixture = integrityCheckFixture(pageCount = 0L)

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(listOf("PRAGMA page_count"), fixture.queries)
        verify(fixture.pageCountCursor).close()
        verify(fixture.pageSizeCursor, never()).close()
        verify(fixture.quickCheckCursor, never()).close()
    }

    @Test
    fun `non-integer page count fails before further scans`() {
        val fixture = integrityCheckFixture(
            pageCountType = Cursor.FIELD_TYPE_STRING
        )

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(listOf("PRAGMA page_count"), fixture.queries)
        verify(fixture.pageCountCursor).close()
        verify(fixture.pageCountCursor, never()).getLong(any())
        verify(fixture.pageSizeCursor, never()).close()
        verify(fixture.quickCheckCursor, never()).close()
    }

    @Test
    fun `computed database bytes above the cap fail before quick check`() {
        val fixture = integrityCheckFixture(
            pageCount = 6L,
            pageSize = 8_192L
        )

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(
            listOf("PRAGMA page_count", "PRAGMA page_size"),
            fixture.queries
        )
        assertEquals(1, fixture.scheduler.cancelCalls)
        verify(fixture.quickCheckCursor, never()).close()
    }

    @Test
    fun `non-power-of-two page size fails before quick check`() {
        val fixture = integrityCheckFixture(pageSize = 3_000L)

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(
            listOf("PRAGMA page_count", "PRAGMA page_size"),
            fixture.queries
        )
        verify(fixture.pageCountCursor).close()
        verify(fixture.pageSizeCursor).close()
        verify(fixture.quickCheckCursor, never()).close()
    }

    @Test
    fun `integrity deadline during page count stops all later pragmas`() {
        val fixture = integrityCheckFixture(timeoutDuringPageCount = true)

        val thrown = assertThrows(
            WalletDatabaseIntegrityCheckException::class.java
        ) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(
            "Wallet database integrity validation exceeded its deadline",
            thrown.message
        )
        assertEquals(listOf("PRAGMA page_count"), fixture.queries)
        verify(fixture.cancellationSignal).cancel()
        verify(fixture.pageCountCursor).close()
        verify(fixture.pageSizeCursor, never()).close()
        verify(fixture.quickCheckCursor, never()).close()
    }

    @Test
    fun `integrity deadline cancels native query and never accepts late success`() {
        val fixture = integrityCheckFixture(timeoutDuringQuickCheck = true)

        val thrown = assertThrows(
            WalletDatabaseIntegrityCheckException::class.java
        ) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(
            "Wallet database integrity validation exceeded its deadline",
            thrown.message
        )
        verify(fixture.cancellationSignal).cancel()
        assertEquals(1, fixture.scheduler.cancelCalls)
        verify(fixture.quickCheckCursor).close()
    }

    @Test
    fun `corrupt quick check result fails closed and closes cursor`() {
        val fixture = integrityCheckFixture(
            quickCheckResult = "*** in database main ***"
        )

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(
            listOf(
                "PRAGMA page_count",
                "PRAGMA page_size",
                "PRAGMA quick_check(1)"
            ),
            fixture.queries
        )
        verify(fixture.quickCheckCursor).close()
        verify(fixture.cancellationSignal, never()).cancel()
    }

    @Test
    fun `quick check without a result row fails closed and closes cursor`() {
        val fixture = integrityCheckFixture(quickCheckHasRow = false)

        assertThrows(WalletDatabaseIntegrityCheckException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        verify(fixture.quickCheckCursor).close()
        verify(fixture.quickCheckCursor, never()).getString(any())
    }

    @Test
    fun `cursor close failure prevents a healthy integrity result`() {
        val fixture = integrityCheckFixture(
            quickCheckCloseFailure =
                IllegalStateException("cursor close failed")
        )

        assertThrows(IllegalStateException::class.java) {
            fixture.checker.requireHealthy(fixture.database)
        }

        assertEquals(1, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `oversized database is rejected before pending journals can be replayed`() =
        runBlocking {
            val integrityFixture = integrityCheckFixture(pageCount = 11L)
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
            val lazyTonConnect = CountingLazy(tonConnectRepository)
            val lazyCoordinator = CountingLazy(mutationCoordinator)
            whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(
                integrityFixture.database
            )

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                lazyTonConnect,
                lazyCoordinator,
                integrityFixture.checker
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "DATABASE_INTEGRITY_RECOVERY"
                ),
                result
            )
            assertEquals(listOf("PRAGMA page_count"), integrityFixture.queries)
            assertEquals(0, lazyTonConnect.requests)
            assertEquals(0, lazyCoordinator.requests)
            verify(tonConnectRepository, never()).reconcilePendingMutation()
            verify(mutationCoordinator, never()).reconcilePendingMutation()
            Unit
        }

    @Test
    fun `corrupt database is rejected before pending journals can be replayed`() =
        runBlocking {
            val integrityFixture = integrityCheckFixture(
                quickCheckResult = "*** in database main ***"
            )
            val encryptionUtil = mock<EncryptionUtil>()
            val appDatabase = mock<AppDatabase>()
            val openHelper = mock<SupportSQLiteOpenHelper>()
            val tonConnectRepository = mock<TonConnectRepository>()
            val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
            val lazyTonConnect = CountingLazy(tonConnectRepository)
            val lazyCoordinator = CountingLazy(mutationCoordinator)
            whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
            whenever(appDatabase.openHelper).thenReturn(openHelper)
            whenever(openHelper.writableDatabase).thenReturn(
                integrityFixture.database
            )

            val result = WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                lazyTonConnect,
                lazyCoordinator,
                integrityFixture.checker
            ).open()

            assertEquals(
                WalletDatabaseStartupResult.RecoveryRequired(
                    "DATABASE_INTEGRITY_RECOVERY"
                ),
                result
            )
            assertEquals(
                listOf(
                    "PRAGMA page_count",
                    "PRAGMA page_size",
                    "PRAGMA quick_check(1)"
                ),
                integrityFixture.queries
            )
            assertEquals(0, lazyTonConnect.requests)
            assertEquals(0, lazyCoordinator.requests)
            verify(tonConnectRepository, never()).reconcilePendingMutation()
            verify(mutationCoordinator, never()).reconcilePendingMutation()
            Unit
        }

    @Test
    fun `TON replay cancellation propagates after healthy room check`() = runBlocking {
        val encryptionUtil = mock<EncryptionUtil>()
        val appDatabase = mock<AppDatabase>()
        val openHelper = mock<SupportSQLiteOpenHelper>()
        val database = mock<SupportSQLiteDatabase>()
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
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
                databaseIntegrityChecker
            ).open()
        } catch (failure: CancellationException) {
            observed = failure
        }

        assertEquals(cancellation.message, observed?.message)
        verify(databaseIntegrityChecker).requireHealthy(database)
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
        val databaseIntegrityChecker = mock<WalletDatabaseIntegrityChecker>()
        val tonConnectRepository = mock<TonConnectRepository>()
        val mutationCoordinator = mock<WalletSecretMutationCoordinator>()
        val recoveryGuard = mock<WalletSecretAccessGuard>()
        val orphanInventory = mock<WalletOrphanSecretInventory>()
        val precedingReconciliationFinished = CompletableDeferred<Unit>()

        whenever(encryptionUtil.getPrerenceAesKey()).thenReturn(mock<Key>())
        whenever(appDatabase.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(database)
        whenever(mutationCoordinator.reconcilePendingMutation()).thenAnswer {
            precedingReconciliationFinished.complete(Unit)
            Unit
        }
        whenever(orphanInventory.reconcile(database)).thenReturn(false)
        whenever(recoveryGuard.hasAnyRecoveryState()).thenReturn(false)

        val startup = async(Dispatchers.Default) {
            WalletDatabaseStartupGate(
                CountingLazy(appDatabase),
                encryptionUtil,
                CountingLazy(tonConnectRepository),
                CountingLazy(mutationCoordinator),
                databaseIntegrityChecker,
                CountingLazy(recoveryGuard),
                CountingLazy(orphanInventory)
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
            verify(orphanInventory, never()).reconcile(database)
            verify(recoveryGuard, never()).hasAnyRecoveryState()
        } finally {
            releaseBoundary.complete(Unit)
        }

        assertEquals(WalletDatabaseStartupResult.Ready, startup.await())
        verify(orphanInventory).reconcile(database)
        verify(recoveryGuard).hasAnyRecoveryState()
        mutationBoundary.await()
        Unit
    }

    private fun integrityCheckFixture(
        pageCount: Long = 8L,
        pageCountType: Int = Cursor.FIELD_TYPE_INTEGER,
        pageSize: Long = 4_096L,
        quickCheckHasRow: Boolean = true,
        quickCheckResult: String = "ok",
        timeoutDuringPageCount: Boolean = false,
        timeoutDuringQuickCheck: Boolean = false,
        quickCheckCloseFailure: Throwable? = null
    ): IntegrityCheckFixture {
        val database = mock<SupportSQLiteDatabase>()
        val pageCountCursor = mock<Cursor>()
        val pageSizeCursor = mock<Cursor>()
        val quickCheckCursor = mock<Cursor>()
        val cancellationSignal = mock<CancellationSignal>()
        val scheduler = ControllableIntegrityDeadlineScheduler()
        val queries = mutableListOf<String>()

        whenever(pageCountCursor.moveToFirst()).thenReturn(true)
        whenever(pageCountCursor.getType(0)).thenReturn(
            pageCountType
        )
        whenever(pageCountCursor.getLong(0)).thenReturn(pageCount)
        whenever(pageSizeCursor.moveToFirst()).thenReturn(true)
        whenever(pageSizeCursor.getType(0)).thenReturn(
            Cursor.FIELD_TYPE_INTEGER
        )
        whenever(pageSizeCursor.getLong(0)).thenReturn(pageSize)
        whenever(quickCheckCursor.moveToFirst()).thenReturn(
            quickCheckHasRow
        )
        whenever(quickCheckCursor.getString(0)).thenReturn(quickCheckResult)
        quickCheckCloseFailure?.let {
            whenever(quickCheckCursor.close()).thenThrow(it)
        }
        whenever(
            database.query(
                any<SupportSQLiteQuery>(),
                any<CancellationSignal>()
            )
        ).thenAnswer { invocation ->
            val sql = invocation
                .getArgument<SupportSQLiteQuery>(0)
                .sql
            queries += sql
            when (sql) {
                "PRAGMA page_count" -> {
                    if (timeoutDuringPageCount) {
                        scheduler.fire()
                    }
                    pageCountCursor
                }
                "PRAGMA page_size" -> pageSizeCursor
                "PRAGMA quick_check(1)" -> {
                    if (timeoutDuringQuickCheck) {
                        scheduler.fire()
                    }
                    quickCheckCursor
                }
                else -> error("Unexpected integrity query: $sql")
            }
        }

        val checker = WalletDatabaseIntegrityChecker(
            limits = WalletDatabaseIntegrityLimits(
                maximumPageCount = 10L,
                maximumDatabaseBytes = 40_960L,
                deadlineMillis = TEST_INTEGRITY_DEADLINE_MILLIS
            ),
            cancellationSignalFactory = { cancellationSignal },
            deadlineScheduler = scheduler
        )
        return IntegrityCheckFixture(
            checker = checker,
            database = database,
            pageCountCursor = pageCountCursor,
            pageSizeCursor = pageSizeCursor,
            quickCheckCursor = quickCheckCursor,
            cancellationSignal = cancellationSignal,
            scheduler = scheduler,
            queries = queries
        )
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

    private data class IntegrityCheckFixture(
        val checker: WalletDatabaseIntegrityChecker,
        val database: SupportSQLiteDatabase,
        val pageCountCursor: Cursor,
        val pageSizeCursor: Cursor,
        val quickCheckCursor: Cursor,
        val cancellationSignal: CancellationSignal,
        val scheduler: ControllableIntegrityDeadlineScheduler,
        val queries: List<String>
    )

    private class ControllableIntegrityDeadlineScheduler :
        WalletDatabaseIntegrityDeadlineScheduler {

        var delayMillis: Long? = null
            private set
        var cancelCalls: Int = 0
            private set
        private var action: (() -> Unit)? = null

        override fun schedule(
            delayMillis: Long,
            action: () -> Unit
        ): WalletDatabaseIntegrityDeadline {
            check(this.action == null)
            this.delayMillis = delayMillis
            this.action = action
            return WalletDatabaseIntegrityDeadline {
                cancelCalls += 1
            }
        }

        fun fire() {
            checkNotNull(action).invoke()
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
        const val TEST_INTEGRITY_DEADLINE_MILLIS = 1_234L
    }
}
