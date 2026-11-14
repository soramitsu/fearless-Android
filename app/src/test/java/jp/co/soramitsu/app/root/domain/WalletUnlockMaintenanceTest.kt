package jp.co.soramitsu.app.root.domain

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateCounts
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummary
import jp.co.soramitsu.common.data.storage.encrypt.WalletStartupRecoverySummaryHolder
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealingMigration
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSession
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WalletUnlockMaintenanceTest {
    private val database = mock<SupportSQLiteDatabase>()
    private val helper = mock<SupportSQLiteOpenHelper> { on { writableDatabase } doReturn database }
    private val appDatabase = mock<AppDatabase> { on { openHelper } doReturn helper }
    private val coordinator = mock<WalletSecretMutationCoordinator>()
    private val sealing = mock<WalletSecretSealingMigration>()
    private val revalidation = mock<WalletQuarantineRevalidation>()
    private val orphans = mock<WalletOrphanSecretInventory>()
    private val guard = mock<WalletSecretAccessGuard>()
    private val summary = WalletStartupRecoverySummaryHolder()
    private val maintenance = WalletUnlockMaintenance(
        { appDatabase }, { coordinator }, { sealing }, { revalidation }, { orphans }, { guard }, summary
    )

    @Before
    fun setUp() = WalletSecretSession.clear()

    @After
    fun tearDown() = WalletSecretSession.clear()

    @Test
    fun `nothing runs while the wallet is locked`(): Unit = runBlocking {
        maintenance.runOnce()
        verify(coordinator, never()).reconcilePendingMutation()
        verify(sealing, never()).run()
        verify(revalidation, never()).reconcile(any())
    }

    @Test
    fun `after an unlock the deferred replay, sealing and both passes run in order`(): Unit = runBlocking {
        WalletSecretSession.open(ByteArray(16), ByteArray(32), WalletSecretSession.Origin.PIN_WRAP)
        whenever(guard.recoveryStateCounts()).thenReturn(WalletRecoveryStateCounts(flaggedKeyCount = 1, archivedKeyCount = 2))

        maintenance.runOnce()

        val order = inOrder(coordinator, sealing, revalidation, orphans)
        order.verify(coordinator).reconcilePendingMutation()
        order.verify(sealing).run()
        order.verify(revalidation).reconcile(database)
        order.verify(orphans).reconcile(database)
        assertEquals(WalletStartupRecoverySummary(itemCount = 1, archivedCount = 2), summary.summary.value)
    }

    @Test
    fun `each opening of the wallet key starts another run`(): Unit = runBlocking {
        val job = maintenance.observe(this)
        try {
            WalletSecretSession.open(ByteArray(16), ByteArray(32), WalletSecretSession.Origin.PIN_WRAP)
            withTimeout(10_000) {
                while (mockingDetails(sealing).invocations.none { it.method.name == "run" }) delay(10)
            }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `a failing step never stops the others`(): Unit = runBlocking {
        WalletSecretSession.open(ByteArray(16), ByteArray(32), WalletSecretSession.Origin.PIN_WRAP)
        whenever(coordinator.reconcilePendingMutation()).thenThrow(IllegalStateException("journal"))
        whenever(sealing.run()).thenThrow(IllegalStateException("sealing"))

        maintenance.runOnce()

        verify(revalidation).reconcile(database)
        verify(orphans).reconcile(database)
    }
}
