package jp.co.soramitsu.app.root.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.app.root.domain.NotSupportedAppVersionException
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelStartupTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val store = ViewModelStore()
    private val initializer = mockk<AppInitializer>()
    private val ton = mockk<TonConnectInteractor>()

    @Before fun setUp() {
        mockkObject(WCDelegate)
        every { WCDelegate.registerDelegatesIfReady() } returns Result.success(Unit)
        every { WCDelegate.refreshConnections() } returns Unit
        every { ton.eventsFlow() } returns emptyFlow()
    }

    @After fun tearDown() {
        store.clear()
        unmockkObject(WCDelegate)
    }

    @Test fun `cache warning starts connection observers and retries the failed step only`() {
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.ReadyWithWarnings(listOf(InitializationStep.ChainsConfig))
        coEvery { initializer.invoke(InitializationStep.ChainsConfig) } returns InitializeResult.Success
        val model = model()
        assertEquals(InitializationStep.ChainsConfig, model.showNoInternetConnectionAlert.value?.peekContent())
        model.retryLoadConfigClicked(InitializationStep.ChainsConfig)
        model.retryLoadConfigClicked(InitializationStep.ChainsConfig)
        coVerify(exactly = 1) { initializer.invoke(InitializationStep.All) }
        coVerify(exactly = 2) { initializer.invoke(InitializationStep.ChainsConfig) }
        verify(exactly = 1) { ton.eventsFlow() }
        verify(exactly = 1) { WCDelegate.registerDelegatesIfReady() }
    }

    @Test fun `unsupported version remains its own alert and observers wait for successful retry`() {
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.ErrorCanRetry(NotSupportedAppVersionException(), InitializationStep.AppConfig)
        coEvery { initializer.invoke(InitializationStep.AppConfig) } returns InitializeResult.Success
        val model = model()
        assertNotNull(model.showUnsupportedAppVersionAlert.value)
        assertNull(model.showNoInternetConnectionAlert.value)
        verify(exactly = 0) { ton.eventsFlow() }
        model.retryLoadConfigClicked(InitializationStep.AppConfig)
        verify(exactly = 1) { ton.eventsFlow() }
    }

    @Test fun `unsupported policy guidance leaves observers and wallet session running`() {
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.ReadyWithWarnings(listOf(InitializationStep.AppConfig), unsupportedVersion = true)
        val model = model()
        assertNotNull(model.showUnsupportedAppVersionAlert.value)
        assertNull(model.showNoInternetConnectionAlert.value)
        verify(exactly = 1) { ton.eventsFlow() }
        model.updateAppClicked()
        assertNotNull(model.openPlayMarket.value)
        assertNull("Opening the store must not close access to the wallet", model.closeApp.value)
    }

    private fun model() = RootViewModel(initializer, mockk(relaxed = true), mockk(relaxed = true),
        MutableStateFlow(ChainConnection.ExternalRequirement.FORBIDDEN), mockk(relaxed = true), ton)
        .also { store.put("root", it) }
}
