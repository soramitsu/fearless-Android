package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal const val TEST_TOPIC = "session-topic"
internal const val POLKADOT_GENESIS = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
internal const val KUSAMA_GENESIS = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
internal const val MOONBEAM_GENESIS = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"

internal fun testChain(
    id: String,
    ecosystem: Ecosystem,
    addressPrefix: Int = 0
) = Chain(
    id = id,
    paraId = null,
    rank = null,
    name = id,
    minSupportedVersion = null,
    assets = emptyList(),
    nodes = emptyList(),
    explorers = emptyList(),
    externalApi = null,
    icon = "",
    addressPrefix = addressPrefix,
    isEthereumBased = ecosystem == Ecosystem.Ethereum || ecosystem == Ecosystem.EthereumBased,
    isTestNet = false,
    hasCrowdloans = false,
    parentId = null,
    supportStakingPool = false,
    isEthereumChain = ecosystem == Ecosystem.Ethereum,
    chainlinkProvider = false,
    supportNft = false,
    isUsesAppId = false,
    identityChain = null,
    ecosystem = ecosystem,
    remoteAssetsSource = null
)

internal val polkadotChain = testChain(POLKADOT_GENESIS, Ecosystem.Substrate)
internal val moonbeamChain = testChain(MOONBEAM_GENESIS, Ecosystem.EthereumBased)
internal val ethereumChain = testChain("1", Ecosystem.Ethereum)

internal fun sessionRequest(
    id: Long,
    method: String,
    params: String,
    chainId: String?,
    topic: String = TEST_TOPIC
) = Wallet.Model.SessionRequest(
    topic = topic,
    chainId = chainId,
    peerMetaData = null,
    request = Wallet.Model.SessionRequest.JSONRPCRequest(id = id, method = method, params = params)
)

internal fun session(vararg accounts: String, topic: String = TEST_TOPIC) = Wallet.Model.Session(
    pairingTopic = "pairing-topic",
    topic = topic,
    expiry = 0,
    requiredNamespaces = emptyMap(),
    optionalNamespaces = emptyMap(),
    namespaces = accounts.groupBy { it.substringBefore(':') }.mapValues { (_, namespaceAccounts) ->
        Wallet.Model.Namespace.Session(
            chains = namespaceAccounts.map { it.substringBeforeLast(':') }.distinct(),
            accounts = namespaceAccounts,
            methods = WalletConnectMethod.values().map { it.method },
            events = emptyList()
        )
    },
    metaData = null
)

/** Records every response and fails the next [failNextResponses] of them like an unreachable relay. */
internal class FakeSessionClient : WalletConnectSessionClient {
    var activeSession: Wallet.Model.Session? = null
    var pending: List<Wallet.Model.SessionRequest> = emptyList()
    var failNextResponses = 0
    val responses = mutableListOf<Wallet.Model.JsonRpcResponse>()
    val verifyContexts = mutableMapOf<Long, Wallet.Model.VerifyContext>()

    override fun getActiveSessionByTopic(topic: String): Wallet.Model.Session? {
        return activeSession?.takeIf { it.topic == topic }
    }

    override fun getPendingListOfSessionRequests(topic: String): List<Wallet.Model.SessionRequest> {
        return pending.filter { it.topic == topic }
    }

    override fun getVerifyContext(requestId: Long): Wallet.Model.VerifyContext? = verifyContexts[requestId]

    override fun respondSessionRequest(
        params: Wallet.Params.SessionRequestResponse,
        onSuccess: (Wallet.Params.SessionRequestResponse) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        responses += params.jsonRpcResponse
        if (failNextResponses > 0) {
            failNextResponses -= 1
            onError(Wallet.Model.Error(IllegalStateException("relay unavailable")))
        } else {
            onSuccess(params)
        }
    }
}

/** Waits for an error dialog, which the screens may raise from a background flow. */
internal fun BaseViewModel.awaitErrorDialog(matching: (ErrorDialogState) -> Boolean = { true }): ErrorDialogState {
    val shown = CountDownLatch(1)
    var dialog: ErrorDialogState? = null
    errorDialogStateLiveData.observeForever { event ->
        val state = event.peekContent()
        if (dialog == null && matching(state)) {
            dialog = state
            shown.countDown()
        }
    }
    assertTrue("no matching error dialog was shown", shown.await(10, TimeUnit.SECONDS))
    return requireNotNull(dialog)
}
