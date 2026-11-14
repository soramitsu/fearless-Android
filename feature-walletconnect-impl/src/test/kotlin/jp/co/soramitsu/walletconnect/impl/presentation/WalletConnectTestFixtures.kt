package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectRpcRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.Preferences
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

/** The real kill-switch store over in-memory preferences: a test vetoes a family as a fetched config would. */
internal fun featureToggleStore(): ProductFeatureToggleStore {
    val values = mutableMapOf<String, Boolean>()
    val preferences = mockk<Preferences> {
        every { getBoolean(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { putBoolean(any(), any()) } answers { values[firstArg()] = secondArg() }
    }
    return ProductFeatureToggleStore(preferences, mockk(relaxed = true))
}

internal fun sessionRequest(
    id: Long,
    method: String,
    params: String,
    chainId: String?,
    topic: String = TEST_TOPIC
) = WalletConnectRequest(
    topic = topic,
    chainId = chainId,
    peerMetaData = null,
    request = WalletConnectRpcRequest(id = id, method = method, params = params)
)

internal fun session(vararg accounts: String, topic: String = TEST_TOPIC) = WalletConnectSession(
    topic = topic,
    pairingTopic = "pairing-topic",
    expiry = 0,
    requiredNamespaces = emptyMap(),
    optionalNamespaces = emptyMap(),
    namespaces = accounts.groupBy { it.substringBefore(':') }.mapValues { (_, namespaceAccounts) ->
        WalletConnectSessionNamespace(
            chains = namespaceAccounts.map { it.substringBeforeLast(':') }.distinct(),
            accounts = namespaceAccounts,
            methods = WalletConnectMethod.values().map { it.method },
            events = emptyList()
        )
    },
    metaData = null,
    acknowledged = true
)

/** Records every response and fails the next [failNextResponses] of them like an unreachable relay. */
internal class FakeSessionClient : WalletConnectSessionClient {
    var activeSession: WalletConnectSession? = null
    var pending: List<WalletConnectRequest> = emptyList()
    var failNextResponses = 0
    val responses = mutableListOf<WalletConnectResponse>()
    val verifyContexts = mutableMapOf<Long, WalletConnectVerifyContext>()
    val approved = mutableListOf<Map<String, WalletConnectSessionNamespace>>()
    val rejected = mutableListOf<Pair<Long, Long>>()
    val paired = mutableListOf<String>()
    val disconnected = mutableListOf<String>()
    var operationFailure: Exception? = null

    override fun getActiveSessionByTopic(topic: String): WalletConnectSession? {
        return activeSession?.takeIf { it.topic == topic }
    }

    override fun getPendingListOfSessionRequests(topic: String): List<WalletConnectRequest> {
        return pending.filter { it.topic == topic }
    }

    override fun getVerifyContext(topic: String, requestId: Long): WalletConnectVerifyContext? =
        verifyContexts[requestId]

    override fun respondSessionRequest(
        topic: String,
        response: WalletConnectResponse,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        responses += response
        if (failNextResponses > 0) {
            failNextResponses -= 1
            onError(IllegalStateException("relay unavailable"))
        } else {
            onSuccess()
        }
    }

    override suspend fun pair(uri: String) {
        operationFailure?.let { throw it }
        paired += uri
    }

    override suspend fun approveSession(
        proposal: WalletConnectProposal,
        namespaces: Map<String, WalletConnectSessionNamespace>
    ) {
        operationFailure?.let { throw it }
        approved += namespaces
    }

    override suspend fun rejectSession(
        proposal: WalletConnectProposal,
        code: Long,
        message: String
    ) {
        operationFailure?.let { throw it }
        rejected += proposal.id to code
    }

    override suspend fun disconnect(topic: String) {
        operationFailure?.let { throw it }
        disconnected += topic
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
