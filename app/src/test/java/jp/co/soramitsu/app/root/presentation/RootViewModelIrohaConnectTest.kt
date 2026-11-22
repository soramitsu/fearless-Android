package jp.co.soramitsu.app.root.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectPhase
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectService
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSnapshot
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelIrohaConnectTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val store = ViewModelStore()
    private val initializer = mockk<AppInitializer>()
    private val router = mockk<RootRouter>(relaxed = true)
    private val ton = mockk<TonConnectInteractor>(relaxed = true)
    private val snapshots = MutableStateFlow(IrohaConnectSnapshot())
    private val iroha = mockk<IrohaConnectService>(relaxed = true) { every { snapshot } returns snapshots }

    @Before fun setUp() {
        WalletSessionLock.unlock()
        mockkObject(WCDelegate)
        every { WCDelegate.registerDelegatesIfReady() } returns Result.success(Unit)
        every { WCDelegate.refreshConnections() } returns Unit
        every { WCDelegate.walletEvents } returns MutableSharedFlow<WalletConnectEvent>()
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.Success
        every { ton.eventsFlow() } returns emptyFlow()
    }

    @After fun tearDown() {
        WalletSessionLock.unlock()
        store.clear()
        unmockkObject(WCDelegate)
    }

    private fun model() = RootViewModel(
        initializer,
        mockk(relaxed = true) { every { isBuyProviderRedirectLink(any()) } returns false },
        router,
        MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED),
        mockk(relaxed = true),
        ton,
        iroha
    ).also { store.put("root-${System.nanoTime()}", it) }

    @Test fun `an Iroha Connect link opens the session screen only after the PIN`() {
        WalletSessionLock.lock()
        val model = model()
        val link = "irohaconnect://connect?sid=s&v=1&role=wallet"
        model.externalUrlOpened(link)
        verify(exactly = 0) { router.openIrohaConnect(any()) }
        WalletSessionLock.unlock()
        verify(exactly = 1) { router.openIrohaConnect(link) }
        model.externalUrlOpened("https://elsewhere.example/connect")
        verify(exactly = 1) { router.openIrohaConnect(any()) }
    }

    @Test fun `a background session brings its screen back for an Open or a request`() {
        model()
        verify(exactly = 0) { router.openIrohaConnect(any()) }
        snapshots.value = IrohaConnectSnapshot(phase = IrohaConnectPhase.SESSION_APPROVAL)
        verify(exactly = 1) { router.openIrohaConnect(null) }
        snapshots.value = IrohaConnectSnapshot(phase = IrohaConnectPhase.CONNECTED)
        snapshots.value = IrohaConnectSnapshot(phase = IrohaConnectPhase.CONNECTED, selectedAccountId = "a")
        verify(exactly = 1) { router.openIrohaConnect(null) }
        snapshots.value = IrohaConnectSnapshot(phase = IrohaConnectPhase.REQUEST_APPROVAL)
        verify(exactly = 2) { router.openIrohaConnect(null) }
    }
}
