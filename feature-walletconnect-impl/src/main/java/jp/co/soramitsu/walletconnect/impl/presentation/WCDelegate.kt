package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/**
 * The app's view of the WalletConnect client: its events, the proposal and request the screens
 * show, and the open sessions. Events that still wait for the user are replayed to a late collector,
 * so a request that arrived while the app was starting is shown once the root screen listens.
 */
object WCDelegate {
    private const val EVENT_BUFFER = 64

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val attached = MutableStateFlow<WalletConnectClient?>(null)
    private val events = MutableSharedFlow<WalletConnectEvent>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val walletEvents: SharedFlow<WalletConnectEvent> = events.onSubscription { waitingEvents().forEach { emit(it) } }

    @Volatile
    var sessionProposalEvent: Pair<WalletConnectProposal, WalletConnectVerifyContext>? = null

    @Volatile
    var sessionRequestEvent: Pair<WalletConnectRequest, WalletConnectVerifyContext>? = null

    private val refreshes = MutableStateFlow(Event(Unit))

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessionFlow: Flow<List<WalletConnectSession>> = combine(
        attached.flatMapLatest { client -> client?.sessions ?: flowOf(emptyList()) },
        refreshes
    ) { sessions, _ -> sessions.filter { attached.value?.session(it.topic) != null } }

    val client: WalletConnectClient? get() = attached.value

    /** Connects the delegate to [client] once; later calls with the same client do nothing. */
    @Synchronized
    fun attach(client: WalletConnectClient) {
        if (attached.value === client) return
        attached.value = client
        scope.launch {
            client.events.collect { event ->
                remember(event)
                events.emit(event)
            }
        }
    }

    /** Succeeds once the app started WalletConnect and the delegate forwards the client's events. */
    fun registerDelegatesIfReady(): Result<Unit> =
        if (attached.value != null) Result.success(Unit) else Result.failure(IllegalStateException("WalletConnect is not started"))

    /** Reads the open sessions again, dropping any that expired since the list was last updated. */
    fun refreshConnections() {
        refreshes.value = Event(Unit)
    }

    fun getPendingListOfSessionRequests(topic: String): List<WalletConnectRequest> =
        attached.value?.pendingRequests(topic).orEmpty()

    private fun remember(event: WalletConnectEvent) {
        when (event) {
            is WalletConnectEvent.SessionProposal -> sessionProposalEvent = event.proposal to event.verifyContext
            is WalletConnectEvent.SessionRequest -> sessionRequestEvent = event.request to event.verifyContext
            else -> Unit
        }
    }

    // The newest proposal and the newest request of each session that still wait for an answer.
    private fun waitingEvents(): List<WalletConnectEvent> {
        val client = attached.value ?: return emptyList()
        val proposal = sessionProposalEvent?.takeIf { (pending, _) -> client.proposal(pending.id) != null }
            ?.let { (pending, verify) -> WalletConnectEvent.SessionProposal(pending, verify) }
        val requests = client.sessions.value.mapNotNull { session ->
            client.pendingRequests(session.topic).maxByOrNull { it.request.id }?.let { request ->
                val verify = client.verifyContext(request.topic, request.request.id)
                    ?: WalletConnectVerifyContext.unknown(request.peerMetaData?.url)
                WalletConnectEvent.SessionRequest(request, verify)
            }
        }
        return listOfNotNull(proposal) + requests
    }
}
