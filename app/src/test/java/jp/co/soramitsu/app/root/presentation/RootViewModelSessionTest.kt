package jp.co.soramitsu.app.root.presentation

import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectService
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSnapshot
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.Date
import java.util.TimerTask
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonChainId
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelSessionTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val store = ViewModelStore()
    private val initializer = mockk<AppInitializer>()
    private val interactor = mockk<RootInteractor>(relaxed = true)
    private val router = mockk<RootRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val ton = mockk<TonConnectInteractor>(relaxed = true)
    private val connections = MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED)

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

    @Test fun `a retryable startup failure names the step that could not refresh`() {
        coEvery { initializer.invoke(InitializationStep.All) } returns
            InitializeResult.ErrorCanRetry(IllegalStateException("offline"), InitializationStep.SubstrateTypes)
        val model = model()
        assertEquals(InitializationStep.SubstrateTypes, model.showNoInternetConnectionAlert.value?.peekContent())
        assertNull(model.showUnsupportedAppVersionAlert.value)
        verify(exactly = 0) { ton.eventsFlow() }
    }

    @Test fun `background stops connections and the foreground allows them again`() {
        val model = model()
        model.noticeInBackground()
        assertEquals(ChainConnection.ExternalRequirement.STOPPED, connections.value)
        model.noticeInForeground()
        assertEquals(ChainConnection.ExternalRequirement.ALLOWED, connections.value)
        verify(exactly = 0) { router.openPincodeCheck() }
        assertFalse(WalletSessionLock.isLocked())
    }

    @Test fun `a long stay in the background locks the wallet on return`() {
        val model = model()
        model.noticeInBackground()
        RootViewModel::class.java.getDeclaredField("timeInBackground").apply { isAccessible = true }.set(model, Date(0))
        model.noticeInForeground()
        assertTrue(WalletSessionLock.isLocked())
        verify(exactly = 1) { router.openPincodeCheck() }
    }

    @Test fun `a language change keeps connections and returns to the wallet once`() {
        val model = model()
        model.noticeLanguageLanguage()
        model.noticeInBackground()
        assertEquals(ChainConnection.ExternalRequirement.ALLOWED, connections.value)
        model.restoredAfterConfigChange()
        model.restoredAfterConfigChange()
        verify(exactly = 1) { router.returnToWallet() }
    }

    @Test fun `only the buy provider redirect reports a completed purchase`() {
        every { interactor.isBuyProviderRedirectLink("fearless://buy-success") } returns true
        every { interactor.isBuyProviderRedirectLink("https://elsewhere") } returns false
        val model = model()
        model.externalUrlOpened("https://elsewhere")
        assertNull(model.messageLiveData.value)
        model.externalUrlOpened("fearless://buy-success")
        assertEquals("text-${R.string.buy_completed}", model.messageLiveData.value?.peekContent())
    }

    @Test fun `inactivity locks the wallet and opens the PIN graph`() {
        val model = model()
        model.onUserInteractedWithApp()
        model.onUserInteractedWithApp()
        val task = RootViewModel::class.java.getDeclaredField("timerTask").apply { isAccessible = true }.get(model) as TimerTask
        task.run()
        assertTrue(WalletSessionLock.isLocked())
        verify(exactly = 1) { router.openNavGraph() }
    }

    @Test fun `the connecting bar follows network availability and clearing forbids connections`() {
        val model = model()
        model.onConnectionLost()
        assertTrue(model.showConnectingBar.value)
        model.onNetworkAvailable()
        assertFalse(model.showConnectingBar.value)
        store.clear()
        assertEquals(ChainConnection.ExternalRequirement.FORBIDDEN, connections.value)
    }

    @Test fun `a disconnect request removes the connection`() {
        every { ton.eventsFlow() } returns flowOf(event(BridgeMethod.DISCONNECT))
        model()
        coVerify(exactly = 1) { ton.disconnect(CONNECTION.identity()) }
    }

    @Test fun `an approved transfer that broadcasts reports success`() {
        val event = event(BridgeMethod.SEND_TRANSACTION)
        every { ton.eventsFlow() } returns flowOf(event)
        coEvery { ton.prepareSignRequest(any(), any()) } returns SIGN_REQUEST
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } returns Result.success("boc" to "0xhash")
        model()
        verify(exactly = 1) {
            router.openOperationSuccess("0xhash", tonChainId, "text-${R.string.success_message_transaction_sent}", any())
        }
    }

    @Test fun `a failed broadcast is shown and never reported as sent`() {
        every { ton.eventsFlow() } returns flowOf(event(BridgeMethod.SEND_TRANSACTION))
        coEvery { ton.prepareSignRequest(any(), any()) } returns SIGN_REQUEST
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } returns Result.success("boc" to "0xhash")
        coEvery { ton.sendBlockchainMessage(any(), any()) } throws IllegalStateException("broadcast failed")
        val model = model()
        assertEquals("broadcast failed", model.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { ton.sendDappMessage(any(), any()) }
        verify(exactly = 0) { router.openOperationSuccess(any(), any(), any(), any()) }
    }

    @Test fun `a failed reply to the dApp is shown and never reported as sent`() {
        every { ton.eventsFlow() } returns flowOf(event(BridgeMethod.SEND_TRANSACTION))
        coEvery { ton.prepareSignRequest(any(), any()) } returns SIGN_REQUEST
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } returns Result.success("boc" to "0xhash")
        coEvery { ton.sendDappMessage(any(), any()) } throws IllegalStateException("bridge closed")
        val model = model()
        assertEquals("bridge closed", model.errorLiveData.value?.peekContent())
        verify(exactly = 0) { router.openOperationSuccess(any(), any(), any(), any()) }
    }

    @Test fun `an unexpected failure while handling a request is shown`() {
        every { ton.eventsFlow() } returns flowOf(event(BridgeMethod.SEND_TRANSACTION))
        coEvery { ton.prepareSignRequest(any(), any()) } throws IllegalArgumentException("malformed")
        val model = model()
        assertEquals("malformed", model.errorLiveData.value?.peekContent())
    }

    @Test fun `wallet connect proposals and pending requests open their screens`() {
        val proposal = mockk<WalletConnectProposal> { every { pairingTopic } returns "pairing" }
        val pending = mockk<WalletConnectRequest> { every { topic } returns "pending-topic" }
        val answered = mockk<WalletConnectRequest> { every { topic } returns "answered-topic" }
        coEvery { interactor.getPendingListOfSessionRequests("pending-topic") } returns listOf(pending)
        coEvery { interactor.getPendingListOfSessionRequests("answered-topic") } returns emptyList()
        every { WCDelegate.walletEvents } returns MutableSharedFlow<WalletConnectEvent>(replay = 4).apply {
            tryEmit(WalletConnectEvent.SessionProposal(proposal, mockk(relaxed = true)))
            tryEmit(WalletConnectEvent.SessionRequest(pending, mockk(relaxed = true)))
            tryEmit(WalletConnectEvent.SessionRequest(answered, mockk(relaxed = true)))
            tryEmit(WalletConnectEvent.ConnectionChanged(true))
        }
        model()
        verify(exactly = 1) { router.openWalletConnectSessionProposal("pairing") }
        verify(exactly = 1) { router.openWalletConnectSessionRequest("pending-topic") }
        verify(exactly = 0) { router.openWalletConnectSessionRequest("answered-topic") }
    }

    private fun model() = RootViewModel(initializer, interactor, router, connections, resources, ton,
        mockk<IrohaConnectService>(relaxed = true) { every { snapshot } returns MutableStateFlow(IrohaConnectSnapshot()) })
        .also { store.put("root-${System.nanoTime()}", it) }

    private fun event(method: BridgeMethod) = BridgeEvent(
        eventId = 1_700_000_000_000_002L,
        message = BridgeEvent.Message(method, listOf("{\"messages\":[]}"), id = 6),
        connection = CONNECTION
    )

    private companion object {
        val CONNECTION = TonDappConnection(
            metaId = 4L,
            clientId = "d".repeat(64),
            name = "App",
            icon = "https://app.example.com/icon.png",
            url = "https://app.example.com",
            source = ConnectionSource.QR
        )
        val SIGN_REQUEST = TonConnectSignRequest(null, null, emptyList(), null)
    }
}
