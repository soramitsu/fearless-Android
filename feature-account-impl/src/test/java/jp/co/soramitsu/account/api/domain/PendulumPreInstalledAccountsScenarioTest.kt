package jp.co.soramitsu.account.api.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.FeatureToggleConfig
import jp.co.soramitsu.common.data.network.config.PolkaswapRemoteConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendulumPreInstalledAccountsScenarioTest {

    private val stored = mutableMapOf<String, Boolean>()
    private val preferences = mockk<Preferences> {
        every { contains(any()) } answers { firstArg<String>() in stored }
        every { getBoolean(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
        every { putBoolean(any(), any()) } answers { stored[firstArg()] = secondArg() }
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { createAccount(any()) } returns WALLET_ID
    }
    private val toggleStore = mockk<ProductFeatureToggleStore>(relaxed = true)
    private var remoteToggle = FeatureToggleConfig(pendulumCaseEnabled = true)
    private val remoteConfig = object : RemoteConfigFetcher {
        override suspend fun getAppConfig(noCache: String): AppConfigRemote = error("Not used")
        override suspend fun getPolkaswapConfig(): PolkaswapRemoteConfig = error("Not used")
        override suspend fun getFeatureToggle(noCache: String): FeatureToggleConfig = remoteToggle
    }

    @Test
    fun `a remote true cannot enable pre-installed wallets in release builds`() = runBlocking {
        val release = scenario(remoteEnableAllowed = false)
        release.fetchFeatureToggle()

        assertFalse(release.isFeatureEnabled())
        assertTrue(release.import(QR_CONTENT).isFailure)
        coVerify(exactly = 0) { accountRepository.createAccount(any()) }
    }

    @Test
    fun `release builds keep pendulum mode off for a wallet imported earlier`() = runBlocking {
        scenario(remoteEnableAllowed = true).run {
            fetchFeatureToggle()
            import(QR_CONTENT).getOrThrow()
        }

        val release = scenario(remoteEnableAllowed = false)
        release.fetchFeatureToggle()
        assertFalse(release.isPendulumMode(WALLET_ID))
    }

    @Test
    fun `debug builds follow the remote toggle, which can always turn it off`() = runBlocking {
        val debug = scenario(remoteEnableAllowed = true)
        debug.fetchFeatureToggle()
        assertTrue(debug.isFeatureEnabled())
        assertEquals(WALLET_ID, debug.import(QR_CONTENT).getOrThrow())
        assertTrue(debug.isPendulumMode(WALLET_ID))

        remoteToggle = FeatureToggleConfig(pendulumCaseEnabled = false)
        debug.fetchFeatureToggle()
        assertFalse(debug.isFeatureEnabled())
        assertFalse(debug.isPendulumMode(WALLET_ID))
        assertTrue(debug.import(QR_CONTENT).isFailure)
    }

    @Test
    fun `only debug builds compile in the remote switch`() = runBlocking {
        val compiled = PendulumPreInstalledAccountsScenario(accountRepository, preferences, remoteConfig, toggleStore)
        compiled.fetchFeatureToggle()

        assertEquals(BuildConfig.DEBUG, compiled.isFeatureEnabled())
    }

    private fun scenario(remoteEnableAllowed: Boolean) = PendulumPreInstalledAccountsScenario(
        accountRepository,
        preferences,
        remoteConfig,
        toggleStore,
        remoteEnableAllowed
    )

    private companion object {
        const val WALLET_ID = 7L
        val QR_CONTENT = "pendulum test phrase".toByteArray().toHexString()
    }
}
