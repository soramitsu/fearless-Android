package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DappScreenOperationsTest {
    private val dispatcher = StandardTestDispatcher()
    private val page = "https://dapp.example/swap"
    private val app = AppEntity("https://dapp.example", "Swap", "icon", null, null)
    private val connection = TonConnectionLocal(7, "client", "Swap", "icon", app.url, ConnectionSource.WEB)
    private val request = ConnectRequest("https://dapp.example/manifest.json", listOf(ConnectRequest.Item.TonAddress))
    private val signRequest = TonConnectSignRequest(null, null, emptyList(), null)
    private val signed = JSONObject().put("event", "connect")
    private val interactor = mockk<TonConnectInteractor>(relaxed = true) {
        coEvery { readManifest(request.manifestUrl) } returns app
        coEvery { getConnection(page, ConnectionSource.WEB) } returns connection
        coEvery { getConnection(TonConnectionIdentity(connection)) } returns connection
        coEvery { respondDappConnectRequest(null, request, signed, app) } returns TonDappConnection(connection)
        coEvery { prepareSignRequest(listOf("{}"), 7) } returns signRequest
    }
    private val accounts = mockk<AccountInteractor> {
        coEvery { getMetaAccount(7) } returns mockk { every { tonPublicKey } returns ByteArray(32) { 1 } }
    }
    private val router = mockk<TonConnectRouter>(relaxed = true) {
        coEvery { openTonConnectionAndWaitForResult(app, null) } returns signed
        coEvery { openTonSignRequestWithResult(any(), "sendTransaction", signRequest) } returns Result.success("boc" to "hash")
    }
    private lateinit var model: DappScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        model = DappScreenViewModel(interactor, accounts, router, SavedStateHandle(mapOf(DappScreenFragment.PAYLOAD_DAPP_KEY to DappModel(TonDappConnection(connection)))))
    }

    @After
    fun tearDown() {
        model.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun successfulConnectBindsTheReturnedWalletAndReturnsApproval() = runTest(dispatcher) {
        assertSame(signed, model.connect(2, request, page))
        assertEquals(TonConnectionIdentity(connection), model.sessionConnection)
        coVerify { interactor.respondDappConnectRequest(null, request, signed, app) }
    }

    @Test
    fun rejectedConnectReturnsProtocolErrorWithoutOpeningApproval() = runTest(dispatcher) {
        val response = model.connect(1, request, page)
        assertEquals("connect_error", response.getString("event"))
        assertEquals(BridgeError.BAD_REQUEST.code, response.getJSONObject("payload").getInt("code"))
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
    }

    @Test
    fun failedPersistenceClosesApprovalAndPropagatesItsCause() = runTest(dispatcher) {
        coEvery { interactor.respondDappConnectRequest(null, request, signed, app) } throws IllegalArgumentException("storage")
        val result = runCatching { model.connect(2, request, page) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("storage", result.exceptionOrNull()?.cause?.message)
        assertNull(model.sessionConnection)
        verify { router.back() }
    }

    @Test
    fun emptyBatchAndUnsupportedMethodHaveDistinctProtocolErrors() = runTest(dispatcher) {
        assertError(model.send(JSONArray(), page), BridgeError.BAD_REQUEST, 0)
        assertError(model.send(message("unknown"), page), BridgeError.METHOD_NOT_SUPPORTED)
    }

    @Test
    fun signedTransactionResponseContainsRequestIdAndBroadcastBoc() = runTest(dispatcher) {
        val response = model.send(message(), page)
        assertEquals(42L, response.getLong("id"))
        assertEquals("boc", response.getString("result"))
        coVerify { interactor.sendBlockchainMessage(any(), "boc") }
    }

    @Test
    fun failedSignatureAndBroadcastBecomeUnknownErrors() = runTest(dispatcher) {
        coEvery {
            router.openTonSignRequestWithResult(any(), any(), any())
        } returns Result.failure(IllegalStateException("sign"))
        assertError(model.send(message(), page), BridgeError.UNKNOWN)
        coVerify(exactly = 0) { interactor.sendBlockchainMessage(any(), any()) }
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } returns Result.success("boc" to "hash")
        coEvery { interactor.sendBlockchainMessage(any(), any()) } throws IllegalStateException("offline")
        assertError(model.send(message(), page), BridgeError.UNKNOWN)
    }

    @Test
    fun approvalCancellationAndValidationFailuresRetainTheirErrorCodes() = runTest(dispatcher) {
        coEvery { router.openTonSignRequestWithResult(any(), any(), any()) } throws CancellationException("cancel")
        assertError(model.send(message(), page), BridgeError.USER_DECLINED_TRANSACTION)
        coEvery { interactor.prepareSignRequest(any(), any()) } throws BridgeError.Exception(BridgeError.BAD_REQUEST)
        assertError(model.send(message(), page), BridgeError.BAD_REQUEST)
        coEvery { interactor.prepareSignRequest(any(), any()) } throws IllegalStateException("unexpected")
        assertError(model.send(message(), page), BridgeError.UNKNOWN)
    }

    @Test
    fun restorationUsesBoundWalletAndDisconnectDeletesThatIdentity() = runTest(dispatcher) {
        val response = model.restoreConnection(page)
        assertEquals("connect", response.getString("event"))
        val item = response.getJSONObject("payload").getJSONArray("items").getJSONObject(0)
        assertEquals("01".repeat(32), item.getString("publicKey"))
        assertEquals(TonConnectionIdentity(connection), model.sessionConnection)
        model.disconnect(page)
        runCurrent()
        assertNull(model.sessionConnection)
        coVerify { interactor.disconnect(TonConnectionIdentity(connection)) }
    }

    @Test
    fun untrustedPageCannotRestoreOrDisconnect() = runTest(dispatcher) {
        val response = model.restoreConnection("https://evil.example")
        assertEquals(BridgeError.UNKNOWN_APP.code, response.getJSONObject("payload").getInt("code"))
        model.disconnect("https://evil.example")
        runCurrent()
        coVerify(exactly = 0) { interactor.disconnect(any()) }
    }

    private fun message(method: String = "sendTransaction") =
        JSONArray().put(JSONObject().put("method", method).put("params", JSONArray().put("{}")).put("id", "42"))

    private fun assertError(
        response: JSONObject,
        error: BridgeError,
        id: Long = 42
    ) {
        assertEquals(id, response.getLong("id"))
        assertEquals(error.code, response.getJSONObject("error").getInt("code"))
        assertEquals(error.message, response.getJSONObject("error").getString("message"))
    }
}
