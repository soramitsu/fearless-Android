package jp.co.soramitsu.fearless_utils.wsrpc

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketState
import jp.co.soramitsu.fearless_utils.wsrpc.exception.ConnectionClosedException
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.string
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestSendGuard
import jp.co.soramitsu.fearless_utils.wsrpc.request.RespondableSendable
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.socket.ObservableState
import jp.co.soramitsu.fearless_utils.wsrpc.socket.RpcSocket
import jp.co.soramitsu.fearless_utils.wsrpc.socket.RpcSocketListener
import jp.co.soramitsu.fearless_utils.wsrpc.socket.SocketTransportPolicy
import jp.co.soramitsu.fearless_utils.wsrpc.socket.StateObserver
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.Event
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.RespondableSubscription
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange

class SocketService(
    val jsonMapper: Gson,
    private val logger: Logger,
    private val webSocketFactory: WebSocketFactory,
    private val reconnector: Reconnector,
    private val requestExecutor: RequestExecutor,
    private val transportPolicy: SocketTransportPolicy = SocketTransportPolicy.Platform
) {

    @Volatile private var socket: RpcSocket? = null
    private var lifecycleIdentity = Any()
    private var socketIdentity: Any? = null
    private var reconnectIdentity: Any? = null
    private val deferredCallbacks = mutableListOf<() -> Unit>()
    private val guardedRequests = mutableSetOf<RespondableSendable>()

    private val stateContainer = ObservableState(initialState = State.Disconnected)

    /** Opaque identity for one owner's current desired configuration; never serialized. */
    class Configuration internal constructor(
        internal val owner: SocketService,
        internal val isCurrent: () -> Boolean
    ) {
        override fun toString(): String = "SocketConfiguration"
    }

    /** State is observed with its exact configuration identity, never reconstructed from URL text. */
    class ConfigurationSnapshot internal constructor(
        private val owner: SocketService,
        internal val transitionIdentity: Any,
        val configuration: Configuration?,
        val applied: Boolean,
        val state: State
    ) {
        /** Retained observations expire on a new transport lifecycle or connection phase. */
        fun isCurrent(): Boolean = owner.isCurrentObservation(this)
        override fun toString(): String = "SocketConfigurationSnapshot(${state.javaClass.simpleName})"
    }

    private var configuration: Configuration? = null
    private var configurationApplied = false
    private var reusableConnectedIdentity: Any? = null

    /** Retires an earlier queued configuration without opening or closing any transport. */
    fun admitConfiguration(isCurrent: () -> Boolean): Configuration = synchronized(this) {
        Configuration(this, isCurrent).also {
            reusableConnectedIdentity = socketIdentity.takeIf { stateContainer.getState() is State.Connected }
            configuration = it
            configurationApplied = false
        }
    }

    fun configurationSnapshot(): ConfigurationSnapshot = synchronized(this) {
        ConfigurationSnapshot(this, lifecycleIdentity, configuration, configurationApplied && configurationIsCurrent(), stateContainer.getState())
    }

    /**
     * Applies URL and demand together under the existing service lock. Guarded request callbacks
     * are still dispatched only after that lock is released. A reentrant newer configuration wins.
     */
    fun configure(current: Configuration, url: String, connect: Boolean): ConfigurationSnapshot? {
        require(current.owner === this) { "Socket configuration belongs to another service" }
        val callbacks = synchronized(this) {
            if (configuration !== current || !configurationIsCurrent()) return null
            val firstApplication = !configurationApplied
            configurationApplied = true
            val pending = mutableListOf<() -> Unit>()
            fun consume(event: Event) {
                if (configuration === current && configurationIsCurrent()) pending += updateStateLocked(event)
            }
            val state = stateContainer.getState()
            when {
                state is State.Disconnected -> consume(Event.Start(url, remainPaused = !connect))
                state is State.Paused -> {
                    if (connect) consume(Event.Start(url, remainPaused = false))
                    else if (state.url != url) consume(Event.SwitchUrl(url))
                }
                !connect -> {
                    consume(Event.Pause)
                    if (configuration === current && currentUrl(stateContainer.getState()) != url) {
                        consume(Event.SwitchUrl(url))
                    }
                }
                currentUrl(state) != url ||
                    (state is State.Connecting && (socket == null || firstApplication)) ||
                    (state is State.WaitingForReconnect && firstApplication) ||
                    (state is State.Connected && firstApplication &&
                        (socketIdentity == null || socketIdentity !== reusableConnectedIdentity)) -> consume(Event.SwitchUrl(url))
            }
            pending
        }
        dispatchCallbacks(callbacks)
        return synchronized(this) {
            if (configuration === current && configurationIsCurrent()) ConfigurationSnapshot(this, lifecycleIdentity, current, configurationApplied, stateContainer.getState()) else null
        }
    }

    private fun isCurrentObservation(observed: ConfigurationSnapshot): Boolean = synchronized(this) {
        val state = stateContainer.getState()
        observed.applied && configurationApplied && configuration === observed.configuration &&
            configurationIsCurrent() && lifecycleIdentity === observed.transitionIdentity &&
            state.javaClass == observed.state.javaClass && currentUrl(state) == currentUrl(observed.state)
    }

    private fun configurationIsCurrent(): Boolean = try {
        configuration?.isCurrent?.invoke() ?: false
    } catch (_: Exception) {
        false
    }

    private fun currentUrl(state: State): String? = when (state) {
        is State.Connected -> state.url
        is State.Connecting -> state.url
        is State.WaitingForReconnect -> state.url
        is State.Paused -> state.url
        State.Disconnected -> null
    }

    private fun updateUnboundState(event: Event) {
        val callbacks = synchronized(this) {
            configuration = null
            configurationApplied = false
            reusableConnectedIdentity = null
            updateStateLocked(event)
        }
        dispatchCallbacks(callbacks)
    }

    fun started() = stateContainer.getState() !is State.Disconnected

    /**
     * Initiate new connection to the given url.
     * Starts a disconnected or paused service; start(..., remainPaused = true) preserves pause.
     * Meaning, this is the first method that should be called after create [SocketService] instance
     *
     * @param url - url to connect to
     * @param remainPaused - true if socket should start in [SocketStateMachine.State.Paused] state,
     * delaying connection until [resume] is called
     */
    fun start(url: String, remainPaused: Boolean = false) {
        updateUnboundState(Event.Start(url, remainPaused))
    }

    /**
     * Closes connection and forgets about all requests and subscriptions.
     * Should be used when socket connection is no longer needed.
     */
    fun stop() {
        updateUnboundState(Event.Stop)
    }

    /**
     * Seamlessly switches connection url saving all pending & active requests or subscriptions
     * Should be used instead of
     * ```
     *     stop()
     *     start(newURl)
     * ```
     */
    fun switchUrl(url: String) {
        updateUnboundState(Event.SwitchUrl(url))
    }

    /**
     * Closes connection but saves all subscriptions and [DeliveryType.AT_LEAST_ONCE] requests.
     * Reports [ConnectionClosedException] to [DeliveryType.AT_MOST_ONCE] requests.
     */
    fun pause() {
        updateUnboundState(Event.Pause)
    }

    /**
     * Resumes connection from [SocketStateMachine.State.Paused] state,
     * recovering all subscriptions and [DeliveryType.AT_LEAST_ONCE] requests
     */
    fun resume() {
        updateUnboundState(Event.Resume)
    }

    fun addStateObserver(observer: StateObserver) {
        // Initial observer callbacks can reenter this service. Keep the same lock order as
        // transitions (service before ObservableState), then flush deferred guard callbacks.
        synchronized(this) { stateContainer.addObserver(observer) }
        dispatchCallbacks(emptyList())
    }

    fun removeStateObserver(observer: StateObserver) = stateContainer.removeObserver(observer)

    fun subscribe(
        request: RuntimeRequest,
        callback: ResponseListener<SubscriptionChange>,
        unsubscribeMethod: String
    ): Cancellable {
        return executeRequest(
            request,
            DeliveryType.ON_RECONNECT,
            SubscribedCallback(request.id, unsubscribeMethod, callback)
        )
    }

    fun executeRequest(
        runtimeRequest: RuntimeRequest,
        deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE,
        callback: ResponseListener<RpcResponse>
    ): Cancellable {
        val sendable = RespondableSendable(runtimeRequest, deliveryType, callback)

        updateState(Event.Send(sendable))

        return RequestCancellable(sendable)
    }

    /** Guarded mutations are never automatically replayed, including failed connection attempts. */
    fun executeGuardedRequest(
        runtimeRequest: RuntimeRequest,
        sendGuard: RequestSendGuard,
        callback: ResponseListener<RpcResponse>
    ): Cancellable {
        val sendable = RespondableSendable(runtimeRequest, DeliveryType.AT_MOST_ONCE, callback, sendGuard)
        val callbacks = synchronized(this) {
            if (stateContainer.getState() is State.Disconnected) {
                sendable.completeGuarded()
                listOf<() -> Unit>({ callback.onError(ConnectionClosedException()) })
            } else {
                guardedRequests += sendable
                updateStateLocked(Event.Send(sendable))
            }
        }
        dispatchCallbacks(callbacks)
        return RequestCancellable(sendable)
    }

    // The immutable identity belongs to one actual socket, including repeated use of the same URL.
    private fun socketListener(identity: Any) = object : RpcSocketListener {
        override fun onResponse(rpcResponse: RpcResponse) = fromSocket(identity, Event.SendableResponse(rpcResponse))
        override fun onResponse(subscriptionChange: SubscriptionChange) = fromSocket(identity, Event.SubscriptionResponse(subscriptionChange))
        override fun onConnected() = fromSocket(identity, Event.Connected)
        override fun onStateChanged(newState: WebSocketState) {
            if (newState == WebSocketState.CLOSED) fromSocket(identity, Event.ConnectionError(ConnectionClosedException()))
        }
        override fun onError(error: Throwable) = fromSocket(identity, Event.ConnectionError(error))
    }

    private fun fromSocket(identity: Any, event: Event) {
        val callbacks = synchronized(this) {
            if (socketIdentity !== identity) return
            updateStateLocked(event)
        }
        dispatchCallbacks(callbacks)
    }

    private fun updateState(event: Event) {
        val callbacks = synchronized(this) { updateStateLocked(event) }
        dispatchCallbacks(callbacks)
    }

    private fun dispatchCallbacks(callbacks: List<() -> Unit>) {
        if (Thread.holdsLock(this)) {
            deferredCallbacks += callbacks
            return
        }
        val ready = synchronized(this) {
            (callbacks + deferredCallbacks).also { deferredCallbacks.clear() }
        }
        ready.forEach { it() }
    }

    private fun updateStateLocked(event: Event): MutableList<() -> Unit> {
        val callbacks = mutableListOf<() -> Unit>()
        var state = stateContainer.getState()
        val terminated = mutableListOf<RespondableSendable>()
        if (event is Event.ConnectionError || event is Event.Stop || event is Event.SwitchUrl || event is Event.Pause) {
            // Observers run synchronously during state publication. Retire callback authority before
            // publishing the replacement state, not merely when disconnect side effects run.
            socketIdentity = null
            reconnectIdentity = null
            // Remove before transition so connecting/paused guarded requests cannot survive
            // a failed attempt and later replay when the socket reconnects.
            val pending = guardedRequests.toList()
            guardedRequests.clear()
            pending.forEach { request ->
                state = SocketStateMachine.transition(state, Event.Cancel(request)).first
                if (request.completeGuarded()) terminated += request
            }
        }
        val (newState, sideEffects) = SocketStateMachine.transition(state, event)
        val lifecycleEvent = event is Event.Start || event is Event.Stop || event is Event.SwitchUrl ||
            event is Event.Pause || event is Event.Resume || event is Event.ConnectionError || event is Event.ReadyToReconnect
        if (lifecycleEvent && (newState !== state || sideEffects.isNotEmpty())) lifecycleIdentity = Any()
        val transitionIdentity = lifecycleIdentity
        stateContainer.setState(newState)

        logger.log("[STATE MACHINE][TRANSITION] ${event.javaClass.simpleName} : ${state.javaClass.simpleName} -> ${newState.javaClass.simpleName}")

        sideEffects.forEach {
            val lifecycleEffect = it is SideEffect.Connect || it is SideEffect.Disconnect ||
                it is SideEffect.ScheduleReconnect || it is SideEffect.SendSendables || it is SideEffect.Unsubscribe ||
                it is SideEffect.RespondToSubscription
            // A reentrant observer/callback can issue a newer lifecycle intent. Do not execute the
            // older transport effect or subscription publication after that intent takes effect.
            if (!lifecycleEffect || lifecycleIdentity === transitionIdentity) consumeSideEffect(it, callbacks, transitionIdentity)
        }
        // Complete state changes first and invoke guarded callbacks after releasing the monitor.
        terminated.forEach { request -> callbacks += { request.callback.onError(ConnectionClosedException()) } }
        return callbacks
    }

    private fun consumeSideEffect(sideEffect: SideEffect, callbacks: MutableList<() -> Unit>, transitionIdentity: Any) {
        logger.log("[STATE MACHINE][SIDE EFFECT] ${sideEffect.javaClass.simpleName}")

        when (sideEffect) {
            is SideEffect.ResponseToSendable -> respondToRequest(
                sideEffect.sendable,
                sideEffect.response,
                callbacks,
                transitionIdentity
            )
            is SideEffect.RespondSendablesError -> respondError(
                sideEffect.sendables,
                sideEffect.error,
                callbacks
            )
            is SideEffect.RespondToSubscription -> respondToSubscription(
                sideEffect.subscription,
                sideEffect.change
            )
            is SideEffect.SendSendables -> sendToSocket(sideEffect.sendables)
            is SideEffect.Connect -> {
                // Pure current-version predicate only. No lane-owned code calls this service.
                // A changed durable intent cannot start an older held configuration's transport.
                if (configuration == null || (configurationApplied && configurationIsCurrent())) connectToSocket(sideEffect.url, callbacks)
            }
            is SideEffect.ScheduleReconnect -> scheduleReconnect(sideEffect.attempt)
            is SideEffect.Disconnect -> disconnect()
            is SideEffect.Unsubscribe -> unsubscribe(sideEffect.subscription)
        }
    }

    private fun respondToRequest(
        sendable: SocketStateMachine.Sendable,
        response: RpcResponse,
        callbacks: MutableList<() -> Unit>,
        transitionIdentity: Any
    ) {
        require(sendable is RespondableSendable)
        // ON_RECONNECT will register again on the new socket. An observer may have switched
        // synchronously after this response was admitted but before registration was delivered.
        if (sendable.callback is SubscribedCallback && lifecycleIdentity !== transitionIdentity) return

        if (sendable.sendGuard == null || sendable.completeGuarded()) {
            guardedRequests.remove(sendable)
            if (sendable.sendGuard == null) sendable.callback.onNext(response)
            else callbacks += { sendable.callback.onNext(response) }
        }
    }

    private fun respondError(sendables: Set<SocketStateMachine.Sendable>, throwable: Throwable, callbacks: MutableList<() -> Unit>) {
        sendables.forEach {
            require(it is RespondableSendable)

            if (it.sendGuard == null || it.completeGuarded()) {
                guardedRequests.remove(it)
                if (it.sendGuard == null) it.callback.onError(throwable)
                else callbacks += { it.callback.onError(throwable) }
            }
        }
    }

    private fun respondToSubscription(
        subscription: SocketStateMachine.Subscription,
        response: SubscriptionChange
    ) {
        require(subscription is RespondableSubscription)

        subscription.callback.onNext(response)
    }

    private fun sendToSocket(sendables: Set<SocketStateMachine.Sendable>) {
        val targetSocket = socket
        requestExecutor.execute {
            sendables.forEach { sendable ->
                require(sendable is RespondableSendable)
                val guard = sendable.sendGuard
                if (guard == null) {
                    // Preserve the existing delivery behavior for ordinary reads and legacy requests.
                    socket!!.sendRpcRequest(sendable.request)
                } else {
                    try {
                        // Never hold SocketService's monitor while acquiring an authority lock.
                        // The source-pinned transport invokes this only after its writer queue,
                        // compression and frame serialization, around actual output write/flush.
                        val cancel = checkNotNull(targetSocket).sendGuardedRpcRequest(sendable.request, RequestSendGuard { send ->
                            guard.sendIfAuthorized {
                                check(socket === targetSocket) { "Guarded request connection changed" }
                                sendable.commitGuardedSend(send)
                            }
                        }, onFailure = { failure -> failGuardedRequest(sendable, failure) })
                        sendable.attachGuardedCancellation(cancel)
                    } catch (failure: Exception) {
                        failGuardedRequest(sendable, failure)
                    }
                }
            }
        }
    }

    private fun failGuardedRequest(sendable: RespondableSendable, failure: Throwable) {
        val callbacks = synchronized(this) {
            guardedRequests.remove(sendable)
            updateStateLocked(Event.Cancel(sendable)).also { pending ->
                if (sendable.completeGuarded()) pending += { sendable.callback.onError(failure) }
            }
        }
        dispatchCallbacks(callbacks)
    }

    private fun scheduleReconnect(attempt: Int) {
        val identity = Any()
        reconnectIdentity = identity
        reconnector.scheduleConnect(attempt) {
            val callbacks = synchronized(this) {
                if (reconnectIdentity !== identity) return@scheduleConnect
                reconnectIdentity = null
                updateStateLocked(Event.ReadyToReconnect)
            }
            dispatchCallbacks(callbacks)
        }
    }

    private fun connectToSocket(url: String, callbacks: MutableList<() -> Unit>) {
        reconnectIdentity = null
        reconnector.reset()
        // Retire a failed prior attempt before construction or any synchronous callback can occur.
        socketIdentity = null
        socket?.clearListeners()
        socket?.disconnect()
        socket = null
        val identity = Any()
        socketIdentity = identity
        try {
            val created = RpcSocket(url, socketListener(identity), logger, webSocketFactory, jsonMapper, transportPolicy)
            socket = created
            created.connectAsync()
        } catch (failure: Exception) {
            if (socketIdentity === identity) callbacks += updateStateLocked(Event.ConnectionError(failure))
        }
    }

    private fun disconnect() {
        // Invalidate first: an already-entered listener can outlive clearListeners/disconnect.
        socketIdentity = null
        reconnectIdentity = null
        socket?.clearListeners()
        socket?.disconnect()
        socket = null
        requestExecutor.reset()
        reconnector.reset()
    }

    private fun unsubscribe(subscription: SocketStateMachine.Subscription) {
        require(subscription is RespondableSubscription)

        val unsubscribeRequest =
            RuntimeRequest(subscription.unsubscribeMethod, listOf(subscription.id))

        executeRequest(
            unsubscribeRequest,
            deliveryType = DeliveryType.AT_MOST_ONCE,
            FireAndForgetCallback()
        )
    }

    interface ResponseListener<R> {
        fun onNext(response: R)

        fun onError(throwable: Throwable)
    }

    interface Cancellable {
        fun cancel()
    }

    inner class SubscribedCallback(
        private val initiatorId: Int,
        private val unsubscribeMethod: String,
        private val subscriptionCallback: ResponseListener<SubscriptionChange>
    ) : ResponseListener<RpcResponse> {

        override fun onNext(response: RpcResponse) {
            val id = try {
                string().nonNull().map(response, jsonMapper)
            } catch (e: Exception) {
                subscriptionCallback.onError(e)

                return
            }

            val subscription =
                RespondableSubscription(id, initiatorId, unsubscribeMethod, subscriptionCallback)

            updateState(Event.Subscribed(subscription))
        }

        override fun onError(throwable: Throwable) {
            subscriptionCallback.onError(throwable)
        }
    }

    inner class RequestCancellable(
        private val sendable: SocketStateMachine.Sendable
    ) : Cancellable {

        override fun cancel() {
            val callbacks = synchronized(this@SocketService) {
                if (sendable is RespondableSendable && sendable.sendGuard != null) {
                    sendable.completeGuarded()
                    guardedRequests.remove(sendable)
                }
                updateStateLocked(Event.Cancel(sendable))
            }
            dispatchCallbacks(callbacks)
        }
    }

    private class FireAndForgetCallback : ResponseListener<RpcResponse> {

        override fun onNext(response: RpcResponse) {
            // do nothing
        }

        override fun onError(throwable: Throwable) {
            // do nothing
        }
    }
}
