package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.lifecycle.SavedStateHandle
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TONProof
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DappScreenViewModelTest {

    private val interactor = FakeInteractor()
    private val router = FakeRouter()
    private val viewModel = DappScreenViewModel(
        interactor = interactor,
        accountInteractor = unusedAccountInteractor(),
        tonConnectRouter = router,
        savedStateHandle = SavedStateHandle(mapOf(DappScreenFragment.PAYLOAD_DAPP_KEY to DAPP))
    )

    @Test
    fun shouldRejectConnect_whenTheManifestIsOnAnotherOrigin() = runBlocking {
        val request = connectRequest("https://evil.example.org/tonconnect-manifest.json")

        val authorization = viewModel.authorizeConnect(2, request, PAGE)

        assertEquals(DappScreenViewModel.ConnectAuthorization.Rejected(BridgeError.BAD_REQUEST), authorization)
        assertTrue(interactor.readManifests.isEmpty())
    }

    @Test
    fun shouldRejectConnect_whenThePageIsNotTheDappOrigin() = runBlocking {
        val request = connectRequest("https://evil.example.org/tonconnect-manifest.json")

        val authorization = viewModel.authorizeConnect(2, request, "https://evil.example.org/")

        assertEquals(DappScreenViewModel.ConnectAuthorization.Rejected(BridgeError.BAD_REQUEST), authorization)
        assertEquals(
            DappScreenViewModel.ConnectAuthorization.Rejected(BridgeError.BAD_REQUEST),
            viewModel.authorizeConnect(2, connectRequest(MANIFEST), null)
        )
        assertTrue(interactor.readManifests.isEmpty())
    }

    @Test
    fun shouldRejectConnect_whenTheProtocolVersionIsUnsupported() = runBlocking {
        val authorization = viewModel.authorizeConnect(1, connectRequest(MANIFEST), PAGE)

        assertEquals(DappScreenViewModel.ConnectAuthorization.Rejected(BridgeError.BAD_REQUEST), authorization)
    }

    @Test
    fun shouldApproveConnect_whenThePageNamesItsOwnManifest() = runBlocking {
        val authorization = viewModel.authorizeConnect(2, connectRequest(MANIFEST), "$ORIGIN/swap")

        assertEquals(DappScreenViewModel.ConnectAuthorization.Approved(APP), authorization)
        assertEquals(listOf(MANIFEST), interactor.readManifests)
    }

    @Test
    fun shouldReportAnUnknownApp_whenTheManifestCannotBeRead() = runBlocking {
        interactor.manifestFailure = IllegalStateException("404")

        val authorization = viewModel.authorizeConnect(2, connectRequest(MANIFEST), PAGE)

        assertEquals(DappScreenViewModel.ConnectAuthorization.Rejected(BridgeError.UNKNOWN_APP), authorization)
    }

    @Test
    fun shouldRefuseToSign_whenThePageHasNoConnection() {
        val error = assertThrows(BridgeError.Exception::class.java) {
            runBlocking { viewModel.sendTransaction(SEND_MESSAGE, PAGE) }
        }

        assertEquals(BridgeError.UNKNOWN_APP, error.error)
        assertTrue(interactor.preparedFor.isEmpty())
        assertTrue(router.signRequests.isEmpty())
    }

    @Test
    fun shouldRefuseToSign_whenTheCallingPageIsNotTheDappOrigin() {
        interactor.selectedWalletConnection = SELECTED_CONNECTION

        val error = assertThrows(BridgeError.Exception::class.java) {
            runBlocking { viewModel.sendTransaction(SEND_MESSAGE, "https://evil.example.org/") }
        }

        assertEquals(BridgeError.UNKNOWN_APP, error.error)
        assertTrue(router.signRequests.isEmpty())
    }

    @Test
    fun shouldNotShowTheRequest_whenValidationRejectsIt() {
        interactor.selectedWalletConnection = SELECTED_CONNECTION
        interactor.validationError = BridgeError.Exception(BridgeError.BAD_REQUEST)

        val error = assertThrows(BridgeError.Exception::class.java) {
            runBlocking { viewModel.sendTransaction(SEND_MESSAGE, PAGE) }
        }

        assertEquals(BridgeError.BAD_REQUEST, error.error)
        assertEquals(listOf(SELECTED_CONNECTION.metaId), interactor.preparedFor)
        assertTrue(router.signRequests.isEmpty())
    }

    @Test
    fun shouldSignWithTheConnectionWallet_andBroadcastOnlyAfterApproval() = runBlocking {
        interactor.selectedWalletConnection = SELECTED_CONNECTION
        router.signResult = Result.success("boc" to "hash")

        val result = viewModel.sendTransaction(SEND_MESSAGE, PAGE)

        assertEquals(Result.success("boc"), result)
        assertEquals(listOf(SELECTED_CONNECTION.metaId), interactor.preparedFor)
        assertEquals(SELECTED_CONNECTION.metaId, router.signRequests.single().metaId)
        assertEquals(listOf("boc"), interactor.broadcast)
    }

    @Test
    fun shouldNotBroadcast_whenTheUserDeclines() = runBlocking {
        interactor.selectedWalletConnection = SELECTED_CONNECTION
        router.signResult = Result.failure(IllegalStateException("User declined request"))

        val result = viewModel.sendTransaction(SEND_MESSAGE, PAGE)

        assertTrue(result.isFailure)
        assertTrue(interactor.broadcast.isEmpty())
    }

    @Test
    fun shouldUseTheConnectionMadeInThisSession_whenTheUserPickedAnotherWallet() = runBlocking {
        interactor.storedConnections += PICKED_CONNECTION
        viewModel.sessionConnection = TonConnectionIdentity(PICKED_CONNECTION)
        router.signResult = Result.failure(IllegalStateException("User declined request"))

        assertSame(PICKED_CONNECTION, viewModel.activeConnection(PAGE))
        viewModel.sendTransaction(SEND_MESSAGE, PAGE)

        assertEquals(listOf(PICKED_CONNECTION.metaId), interactor.preparedFor)
        assertEquals(PICKED_CONNECTION.metaId, router.signRequests.single().metaId)
    }

    @Test
    fun shouldFallBackToTheSelectedWalletConnection_whenTheSessionConnectionIsGone() = runBlocking {
        interactor.selectedWalletConnection = SELECTED_CONNECTION
        viewModel.sessionConnection = TonConnectionIdentity(PICKED_CONNECTION)

        assertSame(SELECTED_CONNECTION, viewModel.activeConnection(PAGE))
        assertNull(viewModel.activeConnection("https://evil.example.org/"))
        assertNull(viewModel.activeConnection(null))
    }

    private class FakeInteractor : TonConnectInteractor {
        var selectedWalletConnection: TonConnectionLocal? = null
        val storedConnections = mutableListOf<TonConnectionLocal>()
        var manifestFailure: Exception? = null
        var validationError: BridgeError.Exception? = null
        val readManifests = mutableListOf<String>()
        val preparedFor = mutableListOf<Long>()
        val broadcast = mutableListOf<String>()

        override suspend fun readManifest(url: String): AppEntity {
            readManifests += url
            manifestFailure?.let { throw it }
            return APP
        }

        override suspend fun getConnection(url: String, source: ConnectionSource): TonConnectionLocal? {
            assertEquals(ConnectionSource.WEB, source)
            return selectedWalletConnection
        }

        override suspend fun getConnection(identity: TonConnectionIdentity): TonConnectionLocal? {
            return storedConnections.firstOrNull { TonConnectionIdentity(it) == identity }
        }

        override suspend fun prepareSignRequest(params: List<String>, metaId: Long): TonConnectSignRequest {
            preparedFor += metaId
            validationError?.let { throw it }
            return SIGN_REQUEST
        }

        override suspend fun getChain(): Chain = TON_CHAIN

        override suspend fun sendBlockchainMessage(chain: Chain, boc: String) {
            broadcast += boc
        }

        override suspend fun respondDappConnectRequest(
            clientId: String?,
            request: ConnectRequest,
            signedRequest: JSONObject,
            app: AppEntity
        ): TonDappConnection? = unused()

        override suspend fun getDappsConfig(): List<DappConfig> = unused()
        override fun getConnectedDappsFlow(source: ConnectionSource): Flow<DappConfig> = emptyFlow()
        override suspend fun getConnectedDapps(source: ConnectionSource): DappConfig = unused()
        override suspend fun requestProof(
            selectedWalletId: Long,
            app: AppEntity,
            proofPayload: String
        ): TONProof.Result = unused()
        override fun eventsFlow(): Flow<BridgeEvent> = emptyFlow()
        override suspend fun disconnect(identity: TonConnectionIdentity) = unused()
        override suspend fun signMessage(
            chain: Chain,
            method: String,
            signRequest: TonConnectSignRequest,
            metaId: Long
        ): Pair<String, String> = unused()
        override suspend fun sendDappMessage(event: BridgeEvent, boc: String) = unused()
        override suspend fun respondDappError(event: BridgeEvent, error: BridgeError) = unused()
    }

    private class FakeRouter : TonConnectRouter {
        var signResult: Result<Pair<String, String>> = Result.failure(IllegalStateException("User declined request"))
        val signRequests = mutableListOf<DappModel>()

        override suspend fun openTonSignRequestWithResult(
            dapp: DappModel,
            method: String,
            signRequest: TonConnectSignRequest
        ): Result<Pair<String, String>> {
            assertEquals(BridgeMethod.SEND_TRANSACTION.title, method)
            assertSame(SIGN_REQUEST, signRequest)
            signRequests += dapp
            return signResult
        }

        override fun back() = unused()
        override fun backWithResult(vararg results: Pair<String, Any?>) = unused()
        override fun openTonConnectionInfo(dappItem: DappModel) = unused()
        override suspend fun openTonConnectionAndWaitForResult(app: AppEntity, proofPayload: String?): JSONObject =
            unused()
        override fun openRawData(payload: String) = unused()
        override fun openOperationSuccess(
            operationHash: String?,
            chainId: ChainId?,
            customMessage: String?,
            customTitle: String?
        ) = unused()
    }

    private companion object {
        const val ORIGIN = "https://app.example.com"
        const val PAGE = "$ORIGIN/"
        const val MANIFEST = "$ORIGIN/tonconnect-manifest.json"
        const val SELECTED_WALLET = 1L
        const val PICKED_WALLET = 2L

        val DAPP = DappModel(
            identifier = "app",
            chains = emptyList(),
            name = "App",
            url = PAGE,
            description = null,
            background = null,
            icon = null,
            metaId = null
        )
        val APP = AppEntity(ORIGIN, "App", "$ORIGIN/icon.png", null, null)
        val SELECTED_CONNECTION = connection(SELECTED_WALLET, "a".repeat(32))
        val PICKED_CONNECTION = connection(PICKED_WALLET, "b".repeat(32))
        val SEND_MESSAGE = BridgeEvent.Message(BridgeMethod.SEND_TRANSACTION, listOf("{}"), id = 4)
        val SIGN_REQUEST = TonConnectSignRequest(null, null, emptyList(), null)
        val TON_CHAIN = Chain(
            id = "ton",
            paraId = null,
            rank = null,
            name = "TON",
            minSupportedVersion = null,
            assets = emptyList(),
            nodes = emptyList(),
            explorers = emptyList(),
            externalApi = null,
            icon = "",
            addressPrefix = 0,
            isEthereumBased = false,
            isTestNet = false,
            hasCrowdloans = false,
            parentId = null,
            supportStakingPool = false,
            isEthereumChain = false,
            chainlinkProvider = false,
            supportNft = false,
            isUsesAppId = false,
            identityChain = null,
            ecosystem = Ecosystem.Ton,
            remoteAssetsSource = null
        )

        fun connection(metaId: Long, clientId: String) = TonConnectionLocal(
            metaId = metaId,
            clientId = clientId,
            name = "App",
            icon = "$ORIGIN/icon.png",
            url = ORIGIN,
            source = ConnectionSource.WEB
        )

        fun connectRequest(manifestUrl: String) = ConnectRequest(manifestUrl, listOf(ConnectRequest.Item.TonAddress))

        fun unused(): Nothing = error("Not used by this test")

        fun unusedAccountInteractor(): AccountInteractor = Proxy.newProxyInstance(
            AccountInteractor::class.java.classLoader,
            arrayOf(AccountInteractor::class.java)
        ) { _, method, _ -> error("${method.name} is not used by this test") } as AccountInteractor
    }
}
