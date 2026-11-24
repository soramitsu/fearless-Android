package jp.co.soramitsu.app.root.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.domain.InitializeResult
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectService
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSnapshot
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URLEncoder

/** A dApp's TON Connect link opened in the app: the universal link from the wallets list, or tc://. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RootViewModelTonConnectLinkTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())
    private val store = ViewModelStore()
    private val initializer = mockk<AppInitializer>()
    private val interactor = mockk<RootInteractor>(relaxed = true)
    private val router = mockk<RootRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val ton = mockk<TonConnectInteractor>(relaxed = true)
    private val app = AppEntity("https://dapp.example", "Example", "https://dapp.example/icon.png", null, null)
    private val reply = JSONObject().put("event", "connect")

    @Before fun setUp() {
        WalletSessionLock.unlock()
        mockkObject(WCDelegate)
        every { WCDelegate.registerDelegatesIfReady() } returns Result.success(Unit)
        every { WCDelegate.refreshConnections() } returns Unit
        every { WCDelegate.walletEvents } returns MutableSharedFlow<WalletConnectEvent>()
        coEvery { initializer.invoke(InitializationStep.All) } returns InitializeResult.Success
        every { ton.eventsFlow() } returns emptyFlow()
        coEvery { ton.readManifest(MANIFEST) } returns app
        coEvery { router.openTonConnectionAndWaitForResult(app, PROOF) } returns reply
    }

    @After fun tearDown() {
        WalletSessionLock.unlock()
        store.clear()
        unmockkObject(WCDelegate)
    }

    @Test fun `the universal link takes the scanner's connect flow and replies to its client`() {
        model().externalUrlOpened(link(UNIVERSAL))

        coVerify(exactly = 1) { router.openTonConnectionAndWaitForResult(app, PROOF) }
        coVerify(exactly = 1) {
            ton.respondDappConnectRequest(CLIENT_ID, match { it.manifestUrl == MANIFEST && it.proofPayload == PROOF }, reply, app)
        }
        verify(exactly = 0) { interactor.isBuyProviderRedirectLink(any()) }
    }

    @Test fun `a tc link takes the same flow`() {
        model().externalUrlOpened(link("tc://"))

        coVerify(exactly = 1) {
            ton.respondDappConnectRequest(CLIENT_ID, match<ConnectRequest> { it.proofPayload == PROOF }, reply, app)
        }
    }

    @Test fun `a locked wallet asks for the PIN before anything is fetched or shown`() {
        WalletSessionLock.lock()
        val model = model()

        model.externalUrlOpened(link(UNIVERSAL))

        coVerify(exactly = 0) { ton.readManifest(any()) }
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }

        WalletSessionLock.unlock()

        coVerify(exactly = 1) { router.openTonConnectionAndWaitForResult(app, PROOF) }
        coVerify(exactly = 1) { ton.respondDappConnectRequest(CLIENT_ID, any(), reply, app) }
    }

    @Test fun `the approval waits until the PIN screen has been replaced`() {
        val pinScreenReplaced = CompletableDeferred<Unit>()
        coEvery { router.awaitDappRoutes() } coAnswers { pinScreenReplaced.await() }

        model().externalUrlOpened(link(UNIVERSAL))

        coVerify(exactly = 0) { ton.readManifest(any()) }

        pinScreenReplaced.complete(Unit)

        coVerify(exactly = 1) { router.openTonConnectionAndWaitForResult(app, PROOF) }
    }

    @Test fun `a wallet that locks while the manifest loads asks for the PIN again before the approval`() {
        val manifestLoaded = CompletableDeferred<AppEntity>()
        coEvery { ton.readManifest(MANIFEST) } coAnswers { manifestLoaded.await() }

        model().externalUrlOpened(link(UNIVERSAL))
        WalletSessionLock.lock()
        manifestLoaded.complete(app)

        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }

        WalletSessionLock.unlock()

        coVerify(exactly = 1) { router.openTonConnectionAndWaitForResult(app, PROOF) }
    }

    @Test fun `an invalid link is reported after the PIN and nothing is fetched`() {
        WalletSessionLock.lock()
        val model = model()
        val invalid = listOf(
            link(UNIVERSAL, version = "1"),
            link(UNIVERSAL, clientId = "short"),
            link("tc://", request = """{"manifestUrl":"http://dapp.example/manifest.json","items":[{"name":"ton_addr"}]}"""),
            link("https://fearlesswallet.io/ton-connect/v2"),
            "tc://"
        )

        invalid.forEach(model::externalUrlOpened)

        assertNull(model.errorLiveData.value)

        WalletSessionLock.unlock()

        assertEquals("text-${R.string.ton_connect_invalid_link}", model.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { ton.readManifest(any()) }
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
        coVerify(exactly = 0) { ton.respondDappConnectRequest(any(), any(), any(), any()) }
    }

    @Test fun `a link delivered again while its approval is open is ignored, and can be opened again afterwards`() {
        val approval = CompletableDeferred<JSONObject>()
        coEvery { router.openTonConnectionAndWaitForResult(app, PROOF) } coAnswers { approval.await() }
        val model = model()

        model.externalUrlOpened(link(UNIVERSAL))
        model.externalUrlOpened(link(UNIVERSAL))

        coVerify(exactly = 1) { router.openTonConnectionAndWaitForResult(app, PROOF) }

        approval.complete(reply)
        model.externalUrlOpened(link(UNIVERSAL))

        coVerify(exactly = 2) { router.openTonConnectionAndWaitForResult(app, PROOF) }
    }

    @Test fun `a manifest that cannot be read is reported and no approval opens`() {
        coEvery { ton.readManifest(MANIFEST) } throws IllegalStateException("manifest unavailable")
        val model = model()

        model.externalUrlOpened(link(UNIVERSAL))

        assertEquals("manifest unavailable", model.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
        coVerify(exactly = 0) { ton.respondDappConnectRequest(any(), any(), any(), any()) }
    }

    @Test fun `other links still reach the buy provider check`() {
        val model = model()

        model.externalUrlOpened("fearless://buy-success")

        verify(exactly = 1) { interactor.isBuyProviderRedirectLink("fearless://buy-success") }
        coVerify(exactly = 0) { ton.readManifest(any()) }
    }

    private fun model() = RootViewModel(initializer, interactor, router,
        MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED), resources, ton,
        mockk<IrohaConnectService>(relaxed = true) { every { snapshot } returns MutableStateFlow(IrohaConnectSnapshot()) })
        .also { store.put("root", it) }

    private fun link(
        base: String,
        version: String = "2",
        clientId: String = CLIENT_ID,
        request: String = REQUEST
    ): String = "$base?v=$version&id=$clientId&r=${URLEncoder.encode(request, Charsets.UTF_8.name())}&ret=none"

    private companion object {
        const val UNIVERSAL = "https://fearlesswallet.io/ton-connect/"
        const val CLIENT_ID = "230f1e4df32364888a5dbd92a410266fcb974b73e30ff3e546a654fc8ee2c953"
        const val MANIFEST = "https://dapp.example/tonconnect-manifest.json"
        const val PROOF = "a3f1c9e0b7d24c58"
        const val REQUEST = """{"manifestUrl":"$MANIFEST","items":[{"name":"ton_addr"},{"name":"ton_proof","payload":"$PROOF"}]}"""
    }
}
