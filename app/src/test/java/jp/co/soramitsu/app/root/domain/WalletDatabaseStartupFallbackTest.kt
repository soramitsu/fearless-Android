package jp.co.soramitsu.app.root.domain

import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.WalletOrphanSecretInventory
import jp.co.soramitsu.coredb.migrations.WalletQuarantineRevalidation
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Startup when the optional recovery collaborators are missing or their failures carry unusual causes. */
class WalletDatabaseStartupFallbackTest {
    private val database = mockk<AppDatabase>(relaxed = true)
    private val encryption = mockk<EncryptionUtil>(relaxed = true)
    private val ton = mockk<TonConnectRepository>(relaxed = true)
    private val coordinator = mockk<WalletSecretMutationCoordinator>(relaxed = true)

    @Test
    fun withoutTheRecoveryInventoryTheGateFailsClosedAfterTheHousekeepingPasses() = runBlocking {
        val gate = WalletDatabaseStartupGate(Lazy { database }, encryption, Lazy { ton }, Lazy { coordinator })
        assertEquals(WalletDatabaseStartupResult.DatabaseOpenFailed, gate.open())
        verify(exactly = 1) { encryption.getPrerenceAesKey() }
    }

    @Test
    fun housekeepingFailuresWithoutASecureStorageCauseDoNotKeepWalletsClosed() = runBlocking {
        val unreadableCause = object : IllegalStateException("revalidation") {
            override val cause: Throwable get() = throw IllegalStateException("cause unavailable")
        }
        var deep: Throwable = IllegalStateException("root")
        repeat(70) { deep = IllegalStateException("level $it", deep) }
        val guard = mockk<WalletSecretAccessGuard> { every { recoveryStateCounts() } returns null }
        val gate = WalletDatabaseStartupGate(
            Lazy { database }, encryption, Lazy { ton }, Lazy { coordinator },
            walletSecretAccessGuard = Lazy { guard },
            walletOrphanSecretInventory = Lazy<WalletOrphanSecretInventory> { throw deep },
            walletQuarantineRevalidation = Lazy<WalletQuarantineRevalidation> { throw unreadableCause }
        )
        assertEquals(WalletDatabaseStartupResult.Ready, gate.open())
    }

    @Test
    fun identityAndConcurrencyFailuresNameTheirRecovery() {
        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired("WALLET_IDENTITY_RECOVERY"),
            classifyWalletDatabaseStartupFailure(WalletPublicIdentityIntegrityException("identity"))
        )
        assertEquals(
            WalletDatabaseStartupResult.RecoveryRequired("WALLET_STATE_CONFLICT"),
            classifyWalletDatabaseStartupFailure(RuntimeException("wrapped", WalletSecretConcurrentMutationException("conflict")))
        )
    }
}
