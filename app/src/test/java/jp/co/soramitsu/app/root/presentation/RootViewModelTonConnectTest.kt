package jp.co.soramitsu.app.root.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTonConnectTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val store = ViewModelStore()
    private val initializer = mockk<AppInitializer>()
    private val router = mockk<RootRouter>(relaxed = true)
    private val ton = mockk<TonConnectInteractor>(relaxed = true)

    @Before fun setUp() {
        mockkObject(WCDelegate)
        every { WCDelegate.registerDelegatesIfReady() } returns Result.success(Unit)
        every { WCDelegate.refreshConnections() } returns Unit
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.Success
        every { ton.eventsFlow() } returns flowOf(EVENT)
    }

    @After fun tearDown() {
        store.clear()
        unmockkObject(WCDelegate)
    }

    @Test fun `a request that fails validation is answered with its error and never shown`() {
        coEvery { ton.prepareSignRequest(EVENT.message.params, WALLET_ID) } throws BridgeError.Exception(BridgeError.BAD_REQUEST)

        model()

        coVerify(exactly = 1) { ton.respondDappError(EVENT, BridgeError.BAD_REQUEST) }
        coVerify(exactly = 0) { router.openTonSignRequestWithResult(any(), any(), any()) }
        coVerify(exactly = 0) { ton.sendBlockchainMessage(any(), any()) }
    }

    @Test fun `a valid request is shown for the connection wallet and broadcast once approved`() {
        coEvery { ton.prepareSignRequest(EVENT.message.params, WALLET_ID) } returns SIGN_REQUEST
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } returns Result.success("boc" to "hash")

        model()

        coVerify(exactly = 1) {
            router.openTonSignRequestWithResult(match { it.metaId == WALLET_ID }, BridgeMethod.SEND_TRANSACTION.title, SIGN_REQUEST)
        }
        coVerify(exactly = 1) { ton.sendBlockchainMessage(any(), "boc") }
        coVerify(exactly = 1) { ton.sendDappMessage(EVENT, "boc") }
        coVerify(exactly = 0) { ton.respondDappError(any(), any()) }
    }

    @Test fun `a declined request is answered with an error and not broadcast`() {
        coEvery { ton.prepareSignRequest(EVENT.message.params, WALLET_ID) } returns SIGN_REQUEST
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } returns Result.failure(IllegalStateException("User declined request"))

        model()

        coVerify(exactly = 1) { ton.respondDappError(EVENT, BridgeError.UNKNOWN) }
        coVerify(exactly = 0) { ton.sendBlockchainMessage(any(), any()) }
    }

    private fun model() = RootViewModel(initializer, mockk(relaxed = true), router,
        MutableStateFlow(ChainConnection.ExternalRequirement.FORBIDDEN), mockk(relaxed = true), ton)
        .also { store.put("root", it) }

    private companion object {
        const val WALLET_ID = 2L
        val EVENT = BridgeEvent(
            eventId = 1_700_000_000_000_001L,
            message = BridgeEvent.Message(BridgeMethod.SEND_TRANSACTION, listOf("{\"messages\":[]}"), id = 5),
            connection = TonDappConnection(
                metaId = WALLET_ID,
                clientId = "c".repeat(64),
                name = "App",
                icon = "https://app.example.com/icon.png",
                url = "https://app.example.com",
                source = ConnectionSource.QR
            )
        )
        val SIGN_REQUEST = TonConnectSignRequest(null, null, emptyList(), null)
    }
}
