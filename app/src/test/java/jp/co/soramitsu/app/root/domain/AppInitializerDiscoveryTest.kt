package jp.co.soramitsu.app.root.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.domain.LegacyNetworkAccountUpgrade
import jp.co.soramitsu.account.impl.domain.WalletSyncService
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The background asset-discovery schedule and what wakes it: catalog, account and pull-to-refresh changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppInitializerDiscoveryTest {

    private class Fixture(dispatcher: CoroutineDispatcher) {
        val job = SupervisorJob()
        val discovery = mockk<AssetDiscoveryService>(relaxed = true)
        val repository = mockk<ChainsRepository>(relaxed = true)
        val chainsFlow = MutableStateFlow<List<Chain>>(emptyList())
        val accounts = MutableStateFlow(listOf(wallet(1)))
        val pendulum = mockk<jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario>(relaxed = true)
        var upgradeChains: Flow<Set<String>>? = null
        private val accountRepository = mockk<AccountRepository>(relaxed = true)
        private val preferences = mockk<jp.co.soramitsu.common.data.storage.Preferences>(relaxed = true)
        private val updates = mockk<UpdateSystem>(relaxed = true)
        private val upgrade = mockk<LegacyNetworkAccountUpgrade> {
            every { observeWallets(any(), any()) } answers { upgradeChains = secondArg(); Job() }
        }
        init {
            every { preferences.getString("MIN_SUPPORTED_VERSION", any()) } returns "3.0.2"
            every { preferences.getStringSet("EXCLUDED_VERSIONS", any()) } returns emptySet()
            every { accountRepository.allMetaAccountsFlow() } returns accounts
            every { repository.chainsFlow() } returns chainsFlow
            coEvery { repository.getChains() } returns emptyList()
            every { updates.start() } returns emptyFlow()
            every { discovery.isDue(any()) } returns false
            every { discovery.millisUntilDue(any()) } returns HOUR
        }
        val initializer = AppInitializer(
            mockk<ChainRegistry>(relaxed = true), mockk<ChainSyncService>(relaxed = true), mockk<RuntimeSyncService>(relaxed = true),
            pendulum, mockk<WalletSyncService>(relaxed = true), updates,
            mockk(relaxed = true), accountRepository, mockk(relaxed = true), preferences, mockk(relaxed = true),
            mockk(relaxed = true), discovery, repository, dispatcher + job,
            legacyNetworkAccountUpgrade = upgrade,
            unlockMaintenance = mockk(relaxed = true)
        )
    }

    @Test
    fun aDueSweepScansBatchesWithAPauseUntilTheCatalogIsDone() = sweep { f ->
        every { f.discovery.isDue(any()) } returnsMany listOf(true, true, false)
        coEvery { f.discovery.scanNextBatch(any()) } returnsMany listOf(
            ProductionAssetDiscoverySweep.BatchResult(listOf("polkadot"), hasMore = true),
            ProductionAssetDiscoverySweep.BatchResult(listOf("kusama"), hasMore = false)
        )
        f.initializer.invoke()
        runCurrent()
        coVerify(exactly = 1) { f.discovery.scanNextBatch(any()) }
        advanceTimeBy(ProductionAssetDiscoverySweep.CONTINUATION_DELAY_MILLIS + 1)
        runCurrent()
        coVerify(exactly = 2) { f.discovery.scanNextBatch(any()) }
        verify(exactly = 0) { f.discovery.resetForFullSweep() }
    }

    @Test
    fun accountKeyChangesAndPullToRefreshRestartTheSweep() = sweep { f ->
        f.initializer.invoke()
        runCurrent()
        f.accounts.value = listOf(wallet(1), wallet(2, chainAccount = true))
        runCurrent()
        verify(exactly = 1) { f.discovery.resetForFullSweep() }

        BalanceUpdateTrigger.invoke(null, force = true)
        runCurrent()
        verify(exactly = 2) { f.discovery.resetForFullSweep() }
    }

    @Test
    fun aChangedCatalogRestartsTheSweepAndFeedsTheAccountUpgrade() = sweep { f ->
        f.initializer.invoke()
        runCurrent()
        f.chainsFlow.value = listOf(chain("polkadot", "wss://rpc.polkadot"))
        runCurrent()
        verify(exactly = 1) { f.discovery.resetForFullSweep() }
        assertEquals(setOf("polkadot"), f.upgradeChains!!.first())
    }

    @Test
    fun aRefreshThatChangesTheCatalogWakesTheSweepOnce() = sweep { f ->
        coEvery { f.repository.getChains() } returnsMany listOf(emptyList(), listOf(chain("kusama", "wss://rpc.kusama")))
        f.initializer.invoke(InitializationStep.ChainsConfig)
        runCurrent()
        verify(exactly = 1) { f.discovery.resetForFullSweep() }
    }

    @Test
    fun anUnreadableCatalogAndAFailedOptionalRefreshDoNotStopStartup() = sweep { f ->
        coEvery { f.repository.getChains() } throws IOException("database busy")
        coEvery { f.pendulum.fetchFeatureToggle() } throws IOException("offline")
        assertEquals(InitializeResult.Success, f.initializer.invoke(InitializationStep.ChainsConfig))
        runCurrent()
        verify(exactly = 0) { f.discovery.resetForFullSweep() }
    }

    private fun sweep(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            block(fixture)
        } finally {
            fixture.job.cancel()
        }
    }

    private companion object {
        const val HOUR = 60L * 60L * 1_000L

        fun wallet(id: Long, chainAccount: Boolean = false) = mockk<MetaAccount>(relaxed = true) {
            every { this@mockk.id } returns id
            every { initialized } returns true
            every { substratePublicKey } returns ByteArray(32) { id.toByte() }
            every { chainAccounts } returns if (chainAccount) {
                mapOf("polkadot" to mockk<MetaAccount.ChainAccount>(relaxed = true) {
                    every { publicKey } returns ByteArray(32) { 9 }
                    every { accountId } returns ByteArray(32) { 8 }
                })
            } else {
                emptyMap()
            }
        }

        fun chain(id: String, node: String) = mockk<Chain>(relaxed = true) {
            every { this@mockk.id } returns id
            every { isTestNet } returns false
            every { ecosystem } returns Ecosystem.Substrate
            every { remoteAssetsSource } returns null
            every { nodes } returns listOf(mockk<ChainNode>(relaxed = true) {
                every { isDefault } returns true
                every { url } returns node
            })
            every { assets } returns emptyList()
        }
    }
}
