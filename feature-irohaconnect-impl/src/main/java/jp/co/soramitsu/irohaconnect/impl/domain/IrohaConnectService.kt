package jp.co.soramitsu.irohaconnect.impl.domain

import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectAppPayload
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectControl
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectCrypto
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectDirection
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectDirectionKeys
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectErrorCode
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectException
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectFrame
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectLink
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectNetwork
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectPermissions
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectRelay
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectWalletPayload
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectWire
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionErrorCode
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionException
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionExpectation
import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionReviewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI

/**
 * The wallet side of one Iroha Connect v1 session at a time. Every socket event, timer and person's action goes
 * through one serial event loop, so frames are handled strictly in order and sequence numbers never race. A port of
 * the browser wallet's `IrohaConnectService` (fearless-wallet-web PR #1122).
 */
@Suppress("LargeClass", "TooManyFunctions", "MagicNumber", "UnnecessaryParentheses", "ThrowsCount") // The browser wallet's IrohaConnectService, one session state machine.
class IrohaConnectService(
    private val scope: CoroutineScope,
    private val socketFactory: IrohaConnectSocketFactory,
    private val accountProvider: IrohaConnectAccountProvider,
    private val now: () -> Long = System::currentTimeMillis,
    private val makeRequestId: () -> String = { IrohaConnectEncoding.hex(IrohaConnectCrypto.randomBytes(16)) },
    private val makeWalletKeyPair: () -> Pair<ByteArray, ByteArray> = IrohaConnectCrypto::x25519KeyPair,
    private val makePingNonce: () -> Long = {
        IrohaConnectCrypto.randomBytes(4).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xff) }
    }
) {
    private val state = MutableStateFlow(IrohaConnectSnapshot())
    val snapshot: StateFlow<IrohaConnectSnapshot> = state.asStateFlow()

    private sealed class Event {
        class Connect(val uri: String, val result: CompletableDeferred<IrohaConnectFailure?>) : Event()
        class Opened(val generation: Int) : Event()
        class Binary(val generation: Int, val bytes: ByteArray) : Event()
        class Text(val generation: Int) : Event()
        class Failed(val generation: Int) : Event()
        class Closed(val generation: Int) : Event()
        class ConnectTimeout(val generation: Int) : Event()
        class KeepAlive(val generation: Int) : Event()
        class RequestExpired(val requestId: String) : Event()
        class Action(val run: suspend () -> Any?, val result: CompletableDeferred<Any?>) : Event()
    }

    private class PendingSignature(
        val requestId: String,
        val account: IrohaConnectAccount,
        val expiresAt: Long,
        val payloadHash: ByteArray,
        val timer: Job
    )

    private class ApprovalDraft(
        val session: Session,
        val account: IrohaConnectAccount,
        val permissionsField: ByteArray,
        val walletPrivateKey: ByteArray,
        val walletPublicKey: ByteArray,
        val preimage: ByteArray
    )

    private class Session(val link: IrohaConnectLink, var pingNonce: Long) {
        /** Last accepted app-to-wallet sequence. */
        var appSequence = 0L

        /** Last accepted Torii server-event sequence. */
        var serverSequence = 0L

        /** Next wallet-to-app sequence. */
        var walletSequence = 1L
        var permissions: IrohaConnectPermissions? = null
        var permissionsField: ByteArray? = null
        var keys: IrohaConnectDirectionKeys? = null
        var open = false
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var generation = 0
    private var socket: IrohaConnectSocket? = null
    private var session: Session? = null
    private var selectedAccount: IrohaConnectAccount? = null
    private var pending: PendingSignature? = null
    private var connectTimer: Job? = null
    private var keepAliveTimer: Job? = null

    init {
        scope.launch {
            for (event in events) {
                try {
                    handle(event)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    fail(IrohaConnectFailure.PROTOCOL_ERROR)
                }
            }
        }
    }

    /** Joins the session behind [uri]; returns why the link was refused, or null once the socket is opening. */
    suspend fun connect(uri: String): IrohaConnectFailure? {
        val result = CompletableDeferred<IrohaConnectFailure?>()
        events.send(Event.Connect(uri, result))
        return result.await()
    }

    /**
     * Signing reads the wallet's secrets and re-derives the key, which can take seconds on a busy device, so it runs
     * outside the event loop: keepalive pings and incoming frames go on meanwhile, and the session is checked again
     * before the approval is sent.
     */
    suspend fun approveSession(accountId: String) {
        val draft = action {
            val current = requireSession(IrohaConnectPhase.SESSION_APPROVAL)
            val account = state.value.accounts.firstOrNull { it.address == accountId }
                ?: throw IrohaConnectActionException(IrohaConnectActionError.ACCOUNT_UNAVAILABLE)
            val permissionsField = current.permissionsField ?: throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
            val (walletPrivateKey, walletPublicKey) = makeWalletKeyPair()
            if (walletPrivateKey.size != 32 || walletPublicKey.size != 32) {
                walletPrivateKey.fill(0)
                throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
            }
            // The approval stays with the account's controller key; its preimage is wallet-built, never dApp bytes.
            val preimage = IrohaConnectWire.approvalPreimage(
                sidBytes = current.link.sidBytes,
                networkIdBytes = current.link.networkIdBytes,
                appPublicKey = current.link.appPublicKey,
                walletPublicKey = walletPublicKey,
                accountId = account.address,
                permissionsField = permissionsField,
                relayToken = current.link.relayToken
            )
            ApprovalDraft(current, account, permissionsField, walletPrivateKey, walletPublicKey, preimage)
        }
        try {
            val signed = accountProvider.sign(draft.account, draft.session.link.profile.networkPrefix, draft.preimage)
            if (signed.publicKeyHex != draft.account.publicKeyHex) throw IrohaConnectActionException(IrohaConnectActionError.KEY_MISMATCH)
            action { sendApproval(draft, signed.signature) }
        } finally {
            draft.walletPrivateKey.fill(0)
        }
    }

    private fun sendApproval(draft: ApprovalDraft, signature: ByteArray) {
        val current = draft.session
        if (session !== current || state.value.phase != IrohaConnectPhase.SESSION_APPROVAL) {
            throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
        }
        var keys: IrohaConnectDirectionKeys? = IrohaConnectWire.deriveDirectionKeys(draft.walletPrivateKey, current.link.appPublicKey, current.link.sidBytes)
        try {
            send(
                IrohaConnectWire.encodeApproveFrame(
                    sidBytes = current.link.sidBytes,
                    sequence = current.walletSequence,
                    walletPublicKey = draft.walletPublicKey,
                    accountId = draft.account.address,
                    permissionsField = draft.permissionsField,
                    signature = signature
                )
            )
            current.walletSequence += 1
            current.keys = keys
            keys = null
            selectedAccount = draft.account
            publish(
                state.value.copy(
                    phase = IrohaConnectPhase.CONNECTED,
                    selectedAccountId = draft.account.address,
                    failure = null,
                    notice = null,
                    session = state.value.session?.copy(connectedAt = now())
                )
            )
            scheduleKeepAlive()
        } finally {
            keys?.wipe()
        }
    }

    suspend fun rejectSession() = action {
        val current = requireSession(IrohaConnectPhase.SESSION_APPROVAL)
        sendReject(current, "The wallet user rejected the connection.")
        closeSocket(publishIdle = true)
    }

    suspend fun approveRequest(requestId: String) {
        val (current, request, payloadHash) = action {
            val current = requireSession(IrohaConnectPhase.REQUEST_APPROVAL)
            val request = requirePending(requestId)
            if (request.expiresAt <= now()) {
                expire(request.requestId)
                throw IrohaConnectActionException(IrohaConnectActionError.REQUEST_EXPIRED)
            }
            Triple(current, request, request.payloadHash.copyOf())
        }
        try {
            // A transaction signature covers the marked hash of the payload the person just reviewed, exactly as the
            // wallet's own transfers sign. Like the approval, it is made outside the event loop.
            val signed = accountProvider.sign(request.account, current.link.profile.networkPrefix, payloadHash)
            if (signed.publicKeyHex != request.account.publicKeyHex) throw IrohaConnectActionException(IrohaConnectActionError.KEY_MISMATCH)
            action {
                if (pending !== request || session !== current) throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
                sendEncrypted(IrohaConnectWalletPayload.SignResultOk(signed.signature))
                clearPending()
                publishConnected()
            }
        } finally {
            payloadHash.fill(0)
        }
    }

    suspend fun rejectRequest(requestId: String) = action {
        val request = requirePending(requestId)
        sendEncrypted(IrohaConnectWalletPayload.SignResultErr("USER_REJECTED", "The wallet user rejected the transaction."))
        if (pending === request) clearPending()
        publishConnected()
    }

    suspend fun disconnect() = action {
        val current = session
        if (current != null && current.open) {
            try {
                when {
                    current.keys != null -> sendEncrypted(IrohaConnectWalletPayload.ControlClose(1000, "The wallet disconnected."))
                    state.value.phase == IrohaConnectPhase.SESSION_APPROVAL -> sendReject(current, "The wallet user cancelled the connection.")
                }
            } catch (_: Exception) {
                // Closing the local socket stays safe when the peer cannot receive the final frame.
            }
        }
        closeSocket(publishIdle = true)
    }

    suspend fun clearMessages() = action {
        val current = state.value
        when {
            current.phase == IrohaConnectPhase.ERROR -> publish(IrohaConnectSnapshot())
            current.failure != null || current.notice != null -> publish(current.copy(failure = null, notice = null))
        }
    }

    private suspend fun <T> action(run: suspend () -> T): T {
        val result = CompletableDeferred<Any?>()
        events.send(Event.Action(run, result))
        @Suppress("UNCHECKED_CAST")
        return result.await() as T
    }

    @Suppress("CyclomaticComplexMethod") // One branch per event kind.
    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Connect -> event.result.complete(open(event.uri))
            is Event.Opened -> if (event.generation == generation) session?.open = true
            is Event.Binary -> if (event.generation == generation) onFrame(event.bytes)
            is Event.Text -> if (event.generation == generation) fail(IrohaConnectFailure.PROTOCOL_ERROR)
            is Event.Failed -> if (event.generation == generation) fail(IrohaConnectFailure.RELAY_UNREACHABLE)
            is Event.Closed -> if (event.generation == generation && state.value.isActive) fail(IrohaConnectFailure.SESSION_ENDED)
            is Event.ConnectTimeout ->
                if (event.generation == generation && state.value.phase == IrohaConnectPhase.CONNECTING) fail(IrohaConnectFailure.OPEN_TIMEOUT)
            is Event.KeepAlive -> if (event.generation == generation) keepAlive()
            is Event.RequestExpired -> expire(event.requestId)
            is Event.Action -> try {
                event.result.complete(event.run())
            } catch (error: CancellationException) {
                event.result.cancel(error)
                throw error
            } catch (error: Exception) {
                event.result.completeExceptionally(error)
            }
        }
    }

    private fun open(uri: String): IrohaConnectFailure? {
        val link = try {
            IrohaConnectLink.parse(uri)
        } catch (error: IrohaConnectException) {
            return when (error.code) {
                IrohaConnectErrorCode.RETIRED_LINK -> IrohaConnectFailure.RETIRED_LINK
                IrohaConnectErrorCode.UNKNOWN_NETWORK -> IrohaConnectFailure.UNKNOWN_NETWORK
                IrohaConnectErrorCode.NETWORK_UNAVAILABLE -> IrohaConnectFailure.NETWORK_UNAVAILABLE
                IrohaConnectErrorCode.WRONG_NODE -> IrohaConnectFailure.WRONG_NODE
                else -> IrohaConnectFailure.INVALID_LINK
            }
        }
        // The same invitation again (a recreated screen re-reading its intent) keeps the session it already joined.
        if (session?.link?.sid == link.sid && state.value.isActive) return null
        closeSocket(publishIdle = false)

        val connection = ++generation
        session = Session(link, makePingNonce())
        val listener = object : IrohaConnectSocketListener {
            override fun onOpen() {
                events.trySend(Event.Opened(connection))
            }

            override fun onBinary(bytes: ByteArray) {
                events.trySend(Event.Binary(connection, bytes))
            }

            override fun onText() {
                events.trySend(Event.Text(connection))
            }

            override fun onFailure(error: Throwable) {
                events.trySend(Event.Failed(connection))
            }

            override fun onClosed(code: Int) {
                events.trySend(Event.Closed(connection))
            }
        }
        publish(
            IrohaConnectSnapshot(
                phase = IrohaConnectPhase.CONNECTING,
                session = IrohaConnectSessionInfo(
                    appName = DEFAULT_APP_NAME,
                    appUrl = null,
                    chainId = link.profile.chainId,
                    networkName = link.profile.network.displayName(),
                    networkId = link.networkId.literal,
                    permissions = null,
                    toriiBaseUrl = link.node
                )
            )
        )
        socket = socketFactory.open(IrohaConnectRelay.webSocketUrl(link), IrohaConnectRelay.tokenProtocol(link.token), listener)
        connectTimer = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            events.send(Event.ConnectTimeout(connection))
        }
        return null
    }

    @Suppress("NestedBlockDepth")
    private suspend fun onFrame(bytes: ByteArray) {
        try {
            val current = session ?: error("The IrohaConnect session is unavailable")
            val frame = IrohaConnectWire.decodeFrame(bytes, current.link.sidBytes)
            if (frame is IrohaConnectFrame.Control && frame.control is IrohaConnectControl.ServerEvent) {
                // Torii numbers its own events independently of either peer.
                if (frame.sequence != current.serverSequence + 1) protocolError("out-of-order relay event")
                current.serverSequence = frame.sequence
                return
            }
            if (frame.direction != IrohaConnectDirection.APP_TO_WALLET) protocolError("frame in the wrong direction")
            if (frame.sequence <= current.appSequence) protocolError("replayed frame")
            if (frame.sequence != current.appSequence + 1) protocolError("out-of-order frame")
            if (current.appSequence == 0L && !(frame is IrohaConnectFrame.Control && frame.control is IrohaConnectControl.Open)) {
                protocolError("data before the Open frame")
            }
            current.appSequence = frame.sequence
            when (frame) {
                is IrohaConnectFrame.Control -> handleControl(frame.control, current)
                is IrohaConnectFrame.Ciphertext -> handleCiphertext(frame, current)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            fail(IrohaConnectFailure.PROTOCOL_ERROR)
        }
    }

    private suspend fun handleControl(control: IrohaConnectControl, current: Session) {
        when (control) {
            is IrohaConnectControl.Open -> handleOpen(control, current)
            is IrohaConnectControl.Ping -> {
                send(IrohaConnectWire.encodePongFrame(current.link.sidBytes, current.walletSequence, control.nonce))
                current.walletSequence += 1
            }
            is IrohaConnectControl.Pong -> Unit
            is IrohaConnectControl.Close -> endByDapp()
            else -> protocolError("unsupported control")
        }
    }

    private suspend fun handleOpen(control: IrohaConnectControl.Open, current: Session) {
        if (state.value.phase != IrohaConnectPhase.CONNECTING || current.appSequence != 1L) protocolError("unexpected Open frame")
        if (!control.appPublicKey.contentEquals(current.link.appPublicKey)) protocolError("Open frame does not match the link")
        if (!control.networkIdBytes.contentEquals(current.link.networkIdBytes)) protocolError("Open frame is for another network")
        current.permissions = control.permissions
        current.permissionsField = control.permissionsField
        connectTimer?.cancel()
        connectTimer = null
        val accounts = accountProvider.accounts(current.link.profile.networkPrefix).distinctBy { it.address }
        if (session !== current) return
        val sanitize = IrohaConnectTransactionReviewer::sanitize
        publish(
            IrohaConnectSnapshot(
                phase = IrohaConnectPhase.SESSION_APPROVAL,
                accounts = accounts,
                session = IrohaConnectSessionInfo(
                    appName = sanitize(control.appMeta?.name ?: DEFAULT_APP_NAME, 128),
                    appUrl = normalizeAppUrl(control.appMeta?.url),
                    chainId = current.link.profile.chainId,
                    networkName = current.link.profile.network.displayName(),
                    networkId = current.link.networkId.literal,
                    permissions = control.permissions?.let { permissions ->
                        IrohaConnectSessionPermissions(
                            methods = permissions.methods.map { sanitize(it, 96) },
                            events = permissions.events.map { sanitize(it, 96) },
                            resources = permissions.resources?.map { sanitize(it, 160) }
                        )
                    },
                    toriiBaseUrl = current.link.node
                )
            )
        )
        scheduleKeepAlive()
    }

    private fun handleCiphertext(frame: IrohaConnectFrame.Ciphertext, current: Session) {
        val keys = current.keys
        val account = selectedAccount
        val phase = state.value.phase
        val approved = phase == IrohaConnectPhase.CONNECTED || phase == IrohaConnectPhase.REQUEST_APPROVAL
        if (keys == null || account == null || !approved) {
            protocolError("request before wallet approval")
        }
        when (val payload = IrohaConnectWire.openEnvelope(keys.appKey, current.link.sidBytes, frame)) {
            is IrohaConnectAppPayload.SignRequestTx -> handleTransactionRequest(payload.txBytes, current, account)
            // Raw requests are signed only by an account's registered off-chain signing key, inside the off-chain
            // envelope, so a dApp can never obtain a transaction signature this way. Fearless accounts have no such
            // key (no SDK can register one yet), so the request is refused without signing anything.
            is IrohaConnectAppPayload.SignRequestRaw -> refuse(
                "SIGNING_KEY_REQUIRED",
                "This account has no registered off-chain signing key. Fearless never signs raw bytes with the account key.",
                IrohaConnectNoticeCode.RAW_SIGNING_UNAVAILABLE
            )
            is IrohaConnectAppPayload.ControlClose, is IrohaConnectAppPayload.ControlReject -> endByDapp()
            is IrohaConnectAppPayload.Unknown -> Unit
        }
    }

    private fun handleTransactionRequest(
        txBytes: ByteArray,
        current: Session,
        account: IrohaConnectAccount
    ) {
        if (pending != null) {
            txBytes.fill(0)
            refuse("REQUEST_IN_FLIGHT", "Another transaction is being reviewed.", IrohaConnectNoticeCode.REQUEST_IN_PROGRESS)
            return
        }
        val permissions = current.permissions
        if (permissions != null && SIGN_TRANSACTION_METHOD !in permissions.methods) {
            txBytes.fill(0)
            refuse("PERMISSION_DENIED", "The session did not ask to sign transactions.", IrohaConnectNoticeCode.PERMISSION_DENIED)
            return
        }
        val reviewed = try {
            IrohaConnectTransactionReviewer.review(
                txBytes,
                IrohaConnectTransactionExpectation(
                    networkIdLiteral = current.link.networkId.literal,
                    networkPrefix = current.link.profile.networkPrefix,
                    authority = account.address,
                    signingPublicKeyHex = account.publicKeyHex
                )
            )
        } catch (error: IrohaConnectTransactionException) {
            refuse(error.code.resultCode, error.detail, noticeFor(error.code))
            return
        } finally {
            txBytes.fill(0)
        }
        val createdAt = now()
        val requestId = makeRequestId()
        val timer = scope.launch {
            delay(SIGNING_REQUEST_TTL_MS)
            events.send(Event.RequestExpired(requestId))
        }
        pending = PendingSignature(requestId, account, createdAt + SIGNING_REQUEST_TTL_MS, reviewed.payloadHash, timer)
        publish(
            state.value.copy(
                phase = IrohaConnectPhase.REQUEST_APPROVAL,
                failure = null,
                notice = null,
                request = IrohaConnectSigningRequest(requestId, account.address, createdAt, createdAt + SIGNING_REQUEST_TTL_MS, reviewed.review)
            )
        )
    }

    private fun noticeFor(code: IrohaConnectTransactionErrorCode) = when (code) {
        IrohaConnectTransactionErrorCode.UNSUPPORTED_TRANSACTION -> IrohaConnectNoticeCode.TRANSACTION_UNSUPPORTED
        IrohaConnectTransactionErrorCode.WRONG_AUTHORITY -> IrohaConnectNoticeCode.TRANSACTION_WRONG_ACCOUNT
        IrohaConnectTransactionErrorCode.INVALID_TRANSACTION, IrohaConnectTransactionErrorCode.WRONG_NETWORK -> IrohaConnectNoticeCode.TRANSACTION_INVALID
    }

    private fun refuse(
        code: String,
        message: String,
        notice: IrohaConnectNoticeCode
    ) {
        sendEncrypted(IrohaConnectWalletPayload.SignResultErr(code, IrohaConnectTransactionReviewer.sanitize(message, 480)))
        publish(state.value.copy(notice = IrohaConnectNotice(now(), notice)))
    }

    private fun endByDapp() {
        closeSocket(publishIdle = false)
        publish(IrohaConnectSnapshot(notice = IrohaConnectNotice(now(), IrohaConnectNoticeCode.DAPP_CLOSED)))
    }

    private fun expire(requestId: String) {
        if (pending?.requestId != requestId) return
        try {
            sendEncrypted(IrohaConnectWalletPayload.SignResultErr("REQUEST_EXPIRED", "The wallet approval timed out."))
        } catch (_: Exception) {
            // The socket close path clears the same request.
        }
        clearPending()
        if (session?.open == true) {
            publish(
                state.value.copy(
                    phase = IrohaConnectPhase.CONNECTED,
                    request = null,
                    notice = IrohaConnectNotice(now(), IrohaConnectNoticeCode.REQUEST_EXPIRED)
                )
            )
        }
    }

    private fun scheduleKeepAlive() {
        keepAliveTimer?.cancel()
        val connection = generation
        keepAliveTimer = scope.launch {
            delay(KEEP_ALIVE_INTERVAL_MS)
            events.send(Event.KeepAlive(connection))
        }
    }

    private fun keepAlive() {
        keepAliveTimer = null
        val current = session ?: return
        val phase = state.value.phase
        val keepsAlive = phase == IrohaConnectPhase.SESSION_APPROVAL || phase == IrohaConnectPhase.CONNECTED ||
            phase == IrohaConnectPhase.REQUEST_APPROVAL
        if (!current.open || !keepsAlive) return
        try {
            current.pingNonce += 1
            send(IrohaConnectWire.encodePingFrame(current.link.sidBytes, current.walletSequence, current.pingNonce))
            current.walletSequence += 1
            scheduleKeepAlive()
        } catch (_: Exception) {
            fail(IrohaConnectFailure.KEEPALIVE_FAILED)
        }
    }

    private fun requireSession(expected: IrohaConnectPhase? = null): Session {
        val current = session
        if (current == null || socket == null) throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
        if (expected != null && state.value.phase != expected) throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
        return current
    }

    private fun requirePending(requestId: String): PendingSignature = pending?.takeIf {
            it.requestId == requestId
        } ?: throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)

    private fun send(frame: ByteArray) {
        val target = socket ?: throw IrohaConnectActionException(IrohaConnectActionError.RELAY_DISCONNECTED)
        if (!target.send(frame)) throw IrohaConnectActionException(IrohaConnectActionError.RELAY_DISCONNECTED)
    }

    private fun sendReject(current: Session, reason: String) {
        send(IrohaConnectWire.encodeRejectFrame(current.link.sidBytes, current.walletSequence, USER_REJECTED_CODE, "user_rejected", reason))
        current.walletSequence += 1
    }

    private fun sendEncrypted(payload: IrohaConnectWalletPayload) {
        val current = requireSession()
        val keys = current.keys ?: throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
        send(IrohaConnectWire.sealEnvelope(keys.walletKey, current.link.sidBytes, current.walletSequence, payload))
        current.walletSequence += 1
    }

    private fun clearPending() {
        val request = pending ?: return
        request.timer.cancel()
        request.payloadHash.fill(0)
        pending = null
    }

    private fun publishConnected() {
        publish(state.value.copy(phase = IrohaConnectPhase.CONNECTED, request = null, failure = null))
    }

    private fun fail(failure: IrohaConnectFailure) {
        val previous = state.value
        closeSocket(publishIdle = false)
        publish(IrohaConnectSnapshot(phase = IrohaConnectPhase.ERROR, failure = failure, session = previous.session))
    }

    private fun closeSocket(publishIdle: Boolean) {
        generation += 1
        connectTimer?.cancel()
        connectTimer = null
        keepAliveTimer?.cancel()
        keepAliveTimer = null
        clearPending()
        socket?.let { runCatching { it.close(NORMAL_CLOSURE, "Wallet session closed") } }
        socket = null
        session?.keys?.wipe()
        session?.keys = null
        session = null
        selectedAccount = null
        if (publishIdle) publish(IrohaConnectSnapshot())
    }

    private fun publish(next: IrohaConnectSnapshot) {
        state.value = next
    }

    private fun protocolError(detail: String): Nothing =
        throw IrohaConnectException(IrohaConnectErrorCode.INVALID_FRAME, detail)

    companion object {
        const val SIGNING_REQUEST_TTL_MS = 120_000L
        const val CONNECT_TIMEOUT_MS = 20_000L

        /** Torii closes a Connect socket on any text frame, so the keepalive is a sequenced Connect Ping the dApp SDK answers. */
        const val KEEP_ALIVE_INTERVAL_MS = 20_000L
        const val SIGN_TRANSACTION_METHOD = "sign_transaction"
        private const val DEFAULT_APP_NAME = "Iroha dApp"
        private const val USER_REJECTED_CODE = 4001
        private const val NORMAL_CLOSURE = 1000

        private fun IrohaConnectNetwork.displayName() = when (this) {
            IrohaConnectNetwork.TAIRA -> "SORA Taira"
            IrohaConnectNetwork.NEXUS -> "SORA Nexus"
        }

        @Suppress("ReturnCount")
        internal fun normalizeAppUrl(value: String?): String? {
            if (value == null) return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (!uri.isAbsolute || uri.rawUserInfo != null || uri.host.isNullOrEmpty()) return null
            val loopback = uri.host == "localhost" || uri.host == "127.0.0.1" || uri.host == "[::1]"
            if (uri.scheme != "https" && !(uri.scheme == "http" && loopback)) return null
            return uri.toString()
        }
    }
}
