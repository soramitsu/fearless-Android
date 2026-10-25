package jp.co.soramitsu.app.root.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.domain.WalletSyncService
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppInitializerCacheFailureTest {
    @Test fun `failed chain refresh still starts local wallet services`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.chains.syncUp() } throws IOException("Registry unavailable")
        try {
            val result = fixture.initializer.invoke(InitializationStep.ChainsConfig)
            assertFalse("Rebuildable remote data must not lock the wallet behind Retry", result is InitializeResult.ErrorCanRetry)
            assertEquals(listOf(InitializationStep.ChainsConfig), (result as InitializeResult.ReadyWithWarnings).failedSteps)
            verify(exactly = 1) { fixture.registry.syncUp() }
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `failed runtime types refresh still starts local wallet services`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.types.syncTypes() } throws IOException("Types unavailable")
        try {
            val result = fixture.initializer.invoke(InitializationStep.SubstrateTypes)
            assertFalse("Cached/native wallets must remain accessible", result is InitializeResult.ErrorCanRetry)
            assertEquals(listOf(InitializationStep.SubstrateTypes), (result as InitializeResult.ReadyWithWarnings).failedSteps)
            verify(exactly = 1) { fixture.registry.syncUp() }
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `initialization cancellation propagates instead of showing an offline retry alert`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.chains.syncUp() } throws CancellationException("Screen departed")
        try {
            assertTrue(runCatching { fixture.initializer.invoke(InitializationStep.ChainsConfig) }.exceptionOrNull() is CancellationException)
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `retry refreshes remote data without duplicating local observers`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.chains.syncUp() } throws IOException("offline")
        try {
            assertTrue(fixture.initializer.invoke(InitializationStep.ChainsConfig) is InitializeResult.ReadyWithWarnings)
            coEvery { fixture.chains.syncUp() } returns Unit
            assertEquals(InitializeResult.Success, fixture.initializer.invoke(InitializationStep.ChainsConfig))
            verify(exactly = 1) { fixture.registry.syncUp() }
            verify(exactly = 1) { fixture.wallets.start() }
            io.mockk.coVerify(exactly = 2) { fixture.chains.syncUp() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `cancellation while reading cached registry stops initialization`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.repository.getChains() } throws CancellationException("cancelled")
        try {
            assertTrue(runCatching { fixture.initializer.invoke(InitializationStep.ChainsConfig) }.exceptionOrNull() is CancellationException)
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `offline app config uses the supported cached policy`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.walletRepository.getRemoteConfig() } throws IOException("offline")
        try {
            assertEquals(InitializeResult.Success, fixture.initializer.invoke())
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `cancelled app config refresh does not fall back and continue startup`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.walletRepository.getRemoteConfig() } throws CancellationException("cancelled")
        try {
            assertTrue(runCatching { fixture.initializer.invoke() }.exceptionOrNull() is CancellationException)
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `failed app policy persistence is not mistaken for a network error`() = runBlocking {
        val fixture = Fixture()
        coEvery { fixture.walletRepository.getRemoteConfig() } returns jp.co.soramitsu.common.data.network.config.AppConfigRemote("99.0.0", emptyList())
        every { fixture.preferences.putString("MIN_SUPPORTED_VERSION", any()) } throws IOException("disk full")
        try {
            val result = fixture.initializer.invoke()
            assertEquals("A failed policy save remains visible without blocking stored wallets", listOf(InitializationStep.AppConfig), (result as InitializeResult.ReadyWithWarnings).failedSteps)
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `local wallets start before any remote startup response`() = runBlocking {
        for (step in listOf(InitializationStep.AppConfig, InitializationStep.ChainsConfig, InitializationStep.SubstrateTypes)) {
            val fixture = Fixture()
            val response = CompletableDeferred<Unit>()
            when (step) {
                InitializationStep.AppConfig -> coEvery { fixture.walletRepository.getRemoteConfig() } coAnswers { response.await(); jp.co.soramitsu.common.data.network.config.AppConfigRemote("3.0.2", emptyList()) }
                InitializationStep.ChainsConfig -> coEvery { fixture.chains.syncUp() } coAnswers { response.await() }
                else -> coEvery { fixture.types.syncTypes() } coAnswers { response.await() }
            }
            val startup = async(start = CoroutineStart.UNDISPATCHED) { fixture.initializer.invoke(step) }
            try {
                assertFalse(startup.isCompleted)
                verify(exactly = 1) { fixture.registry.syncUp() }
                verify(exactly = 1) { fixture.wallets.start() }
            } finally {
                response.complete(Unit)
                startup.await()
                fixture.job.cancel()
            }
        }
    }

    @Test fun `held optional refreshes do not delay local startup completion`() = runBlocking {
        val fixture = Fixture()
        val response = CompletableDeferred<Unit>()
        coEvery { fixture.remoteAssets.invoke() } coAnswers { response.await() }
        coEvery { fixture.pendulum.fetchFeatureToggle() } coAnswers { response.await() }
        coEvery { fixture.walletRepository.updatePhishingAddresses() } coAnswers { response.await() }
        coEvery { fixture.fiat.sync() } coAnswers { response.await() }
        coEvery { fixture.prices.sync() } coAnswers { response.await() }
        val startup = async(start = CoroutineStart.UNDISPATCHED) { fixture.initializer.invoke() }
        try {
            assertTrue("Optional refreshes must not be children of awaited startup", startup.isCompleted)
            assertEquals(InitializeResult.Success, startup.await())
        } finally {
            response.complete(Unit)
            startup.await()
            fixture.job.cancel()
        }
    }

    @Test fun `unsupported app policy still refreshes the catalog and starts local wallets`() = runBlocking {
        val fixture = Fixture()
        every { fixture.preferences.getString("MIN_SUPPORTED_VERSION", any()) } returns "99.0.0"
        coEvery { fixture.walletRepository.getRemoteConfig() } throws IOException("offline")
        try {
            val result = fixture.initializer.invoke()
            assertTrue("Version policy may recommend an update without locking local wallets", result is InitializeResult.ReadyWithWarnings)
            assertTrue((result as InitializeResult.ReadyWithWarnings).unsupportedVersion)
            verify(exactly = 1) { fixture.wallets.start() }
            io.mockk.coVerify(exactly = 1) { fixture.chains.syncUp() }
            io.mockk.coVerify(exactly = 1) { fixture.types.syncTypes() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `new unsupported policy is retained and reported while wallets stay available`() = runBlocking {
        val fixture = Fixture()
        val config = jp.co.soramitsu.common.data.network.config.AppConfigRemote("99.0.0", emptyList())
        coEvery { fixture.walletRepository.getRemoteConfig() } returns config
        try {
            val result = fixture.initializer.invoke() as InitializeResult.ReadyWithWarnings
            assertTrue(result.unsupportedVersion)
            assertEquals(listOf(InitializationStep.AppConfig), result.failedSteps)
            verify(exactly = 1) { fixture.preferences.putString("MIN_SUPPORTED_VERSION", "99.0.0") }
            verify(exactly = 1) { fixture.wallets.start() }
            io.mockk.coVerify(exactly = 1) { fixture.chains.syncUp() }
        } finally { fixture.job.cancel() }
    }

    @Test fun `caller cancellation stops a pending remote refresh`() = runBlocking {
        val fixture = Fixture()
        val response = CompletableDeferred<Unit>()
        coEvery { fixture.walletRepository.getRemoteConfig() } coAnswers { response.await(); jp.co.soramitsu.common.data.network.config.AppConfigRemote("3.0.2", emptyList()) }
        val startup = async(start = CoroutineStart.UNDISPATCHED) { fixture.initializer.invoke() }
        try {
            startup.cancel()
            yield()
            assertTrue("The application scope must not replace the calling job", startup.isCompleted)
        } finally {
            response.complete(Unit)
            startup.join()
            fixture.job.cancel()
        }
    }

    @Test fun `one failed optional refresh does not suppress other refreshes or duplicate held work`() = runBlocking {
        val fixture = Fixture()
        val response = CompletableDeferred<Unit>()
        coEvery { fixture.pendulum.fetchFeatureToggle() } throws IOException("offline")
        coEvery { fixture.remoteAssets.invoke() } coAnswers { response.await() }
        coEvery { fixture.walletRepository.updatePhishingAddresses() } coAnswers { response.await() }
        coEvery { fixture.fiat.sync() } coAnswers { response.await() }
        coEvery { fixture.prices.sync() } coAnswers { response.await() }
        try {
            assertEquals(InitializeResult.Success, fixture.initializer.invoke())
            assertEquals(InitializeResult.Success, fixture.initializer.invoke())
            io.mockk.coVerify(exactly = 1) { fixture.remoteAssets.invoke() }
            io.mockk.coVerify(exactly = 1) { fixture.walletRepository.updatePhishingAddresses() }
            io.mockk.coVerify(exactly = 1) { fixture.fiat.sync() }
            io.mockk.coVerify(exactly = 1) { fixture.prices.sync() }
            verify(exactly = 1) { fixture.wallets.start() }
        } finally { response.complete(Unit); fixture.job.cancel() }
    }

    private class Fixture {
        val job = SupervisorJob()
        val walletRepository = mockk<jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository>(relaxed = true)
        val preferences = mockk<jp.co.soramitsu.common.data.storage.Preferences>(relaxed = true)
        val registry = mockk<ChainRegistry>(relaxed = true)
        val chains = mockk<ChainSyncService>(relaxed = true)
        val types = mockk<RuntimeSyncService>(relaxed = true)
        val wallets = mockk<WalletSyncService>(relaxed = true)
        val repository = mockk<ChainsRepository>(relaxed = true)
        val remoteAssets = mockk<jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsInitializer>(relaxed = true)
        val pendulum = mockk<jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario>(relaxed = true)
        val fiat = mockk<jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies>(relaxed = true)
        val prices = mockk<jp.co.soramitsu.wallet.impl.data.repository.PricesSyncService>(relaxed = true)
        private val accounts = mockk<AccountRepository>(relaxed = true)
        private val updates = mockk<UpdateSystem>(relaxed = true)
        private val discovery = mockk<AssetDiscoveryService>(relaxed = true)
        init {
            every { preferences.getString("MIN_SUPPORTED_VERSION", any()) } returns "3.0.2"
            every { preferences.getStringSet("EXCLUDED_VERSIONS", any()) } returns emptySet()
            val wallet = mockk<MetaAccount>(relaxed = true)
            every { wallet.initialized } returns true
            every { wallet.chainAccounts } returns emptyMap()
            every { accounts.allMetaAccountsFlow() } returns MutableStateFlow(listOf(wallet))
            every { repository.chainsFlow() } returns MutableStateFlow(emptyList())
            coEvery { repository.getChains() } returns emptyList()
            every { updates.start() } returns emptyFlow()
            every { discovery.isDue(any()) } returns false
            every { discovery.millisUntilDue(any()) } returns 60_000L
        }
        val initializer = AppInitializer(registry, chains, types, pendulum, wallets, updates,
            walletRepository, accounts, remoteAssets, preferences, fiat,
            prices, discovery, repository, Dispatchers.Unconfined + job)
    }
}
