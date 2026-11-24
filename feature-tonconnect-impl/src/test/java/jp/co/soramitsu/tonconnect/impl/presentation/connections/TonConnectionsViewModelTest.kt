package jp.co.soramitsu.tonconnect.impl.presentation.connections

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URLEncoder
import jp.co.soramitsu.common.R as CommonR

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TonConnectionsViewModelTest {
    @get:Rule val executor = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<TonConnectRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "res:${firstArg<Int>()}" } }
    private val first = DappModel("first", emptyList(), "Alpha", "https://alpha.example", null, null, null, 7, ConnectionSource.QR)
    private val second = first.copy(identifier = "second", name = "Beta")
    private val connected = MutableStateFlow(DappConfig(null, listOf(first, second, first.copy(name = null))))
    private val interactor = mockk<TonConnectInteractor>(relaxed = true) {
        every { getConnectedDappsFlow(ConnectionSource.QR) } returns connected
    }
    private val app = AppEntity("https://dapp.example", "Example", "https://dapp.example/icon.png", null, null)
    private val models = mutableListOf<TonConnectionsViewModel>()

    @Before
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun clearMain() {
        models.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun searchFiltersNamesCaseInsensitivelyAndTracksLiveConnections() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        assertEquals(listOf(first, second), model.state.value.items)
        model.onSearchInput("ALP")
        runCurrent()
        assertEquals(listOf(first), model.state.value.items)
        assertEquals("ALP", model.state.value.searchQuery)
        connected.value = DappConfig(null, emptyList())
        runCurrent()
        assertEquals(emptyList<DappModel>(), model.state.value.items)
    }

    @Test
    fun selectionCloseAndScannerDispatchTheirActions() = runTest(dispatcher) {
        val model = model()
        model.onDappClicked(first)
        model.onClose()
        model.onCreateNewConnection()
        verify { router.openTonConnectionInfo(first) }
        verify { router.back() }
        assertEquals(Unit, model.openScannerEvent.value?.peekContent())
    }

    @Test
    fun nonTonQrReportsScanErrorWithoutOpeningConnection() = runTest(dispatcher) {
        val model = model()
        model.qrCodeScanned("https://unrelated.example")
        runCurrent()
        assertEquals("res:${R.string.invoice_scan_error_no_info}", model.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { interactor.readManifest(any()) }
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
    }

    @Test
    fun validQrPassesProofAndSignedReplyToTheSameClient() = runTest(dispatcher) {
        val signed = JSONObject().put("event", "connect")
        coEvery { interactor.readManifest("https://dapp.example/manifest.json") } returns app
        coEvery { router.openTonConnectionAndWaitForResult(app, "challenge") } returns signed
        coEvery { interactor.respondDappConnectRequest(any(), any(), any(), any()) } returns null
        val model = model()
        model.qrCodeScanned(qrContent())
        runCurrent()
        coVerify {
            interactor.respondDappConnectRequest(
                "a".repeat(64),
                match { it.proofPayload == "challenge" && it.items.first() == ConnectRequest.Item.TonAddress },
                signed, app
            )
        }
        assertEquals(null, model.errorLiveData.value)
    }

    @Test
    fun universalLinkQrTakesTheSameConnectFlow() = runTest(dispatcher) {
        val signed = JSONObject().put("event", "connect")
        coEvery { interactor.readManifest("https://dapp.example/manifest.json") } returns app
        coEvery { router.openTonConnectionAndWaitForResult(app, "challenge") } returns signed
        coEvery { interactor.respondDappConnectRequest(any(), any(), any(), any()) } returns null
        val model = model()
        model.qrCodeScanned(qrContent(base = "https://fearlesswallet.io/ton-connect/"))
        runCurrent()
        coVerify { interactor.respondDappConnectRequest("a".repeat(64), any(), signed, app) }
        assertEquals(null, model.errorLiveData.value)
    }

    @Test
    fun malformedTonLinksAreReportedWithoutReadingAManifest() = runTest(dispatcher) {
        listOf(
            qrContent(version = "1"),
            qrContent(clientId = "short"),
            qrContent(request = "{}"),
            qrContent(base = "https://fearlesswallet.io/ton-connect/v2"),
            "tc://"
        ).forEach { content ->
            val model = model()
            model.qrCodeScanned(content)
            runCurrent()
            assertEquals(content, "res:${CommonR.string.ton_connect_invalid_link}", model.errorLiveData.value?.peekContent())
        }
        coVerify(exactly = 0) { interactor.readManifest(any()) }
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
    }

    @Test
    fun manifestFailureStopsApprovalAndReportsError() = runTest(dispatcher) {
        coEvery { interactor.readManifest(any()) } throws IllegalStateException("manifest unavailable")
        val model = model()
        model.qrCodeScanned(qrContent())
        runCurrent()
        assertEquals("manifest unavailable", model.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
    }

    private fun model() = TonConnectionsViewModel(router, resources, interactor).also { models += it }

    private fun qrContent(
        base: String = "tc://",
        version: String = "2",
        clientId: String = "a".repeat(64),
        request: String = """{"manifestUrl":"https://dapp.example/manifest.json","items":[{"name":"ton_addr"},{"name":"ton_proof","payload":"challenge"}]}"""
    ): String = "$base?v=$version&id=$clientId&r=${URLEncoder.encode(request, Charsets.UTF_8.name())}"
}
