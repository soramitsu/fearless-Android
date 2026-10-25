package jp.co.soramitsu.fearless_utils.wsrpc.request

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class RespondableSendable(
    val request: RuntimeRequest,
    override val deliveryType: DeliveryType,
    val callback: SocketService.ResponseListener<RpcResponse>,
    val sendGuard: RequestSendGuard? = null
) : SocketStateMachine.Sendable {
    override val id: Int = request.id

    // Guarded request lifecycle: queued -> handed to transport -> terminal. A
    // cancellation after handoff cannot recall bytes already accepted by transport.
    private val disposition = AtomicInteger(0)
    private val transportCancellation = AtomicReference<(() -> Unit)?>(null)

    fun commitGuardedSend(send: () -> Unit) {
        check(sendGuard != null)
        if (disposition.compareAndSet(0, 1)) send()
    }

    fun attachGuardedCancellation(cancel: () -> Unit) {
        check(transportCancellation.compareAndSet(null, cancel))
        if (disposition.get() == 2) transportCancellation.getAndSet(null)?.invoke()
    }

    fun completeGuarded(): Boolean {
        val completed = disposition.getAndSet(2) != 2
        if (completed) transportCancellation.getAndSet(null)?.invoke()
        return completed
    }


    override fun toString(): String {
        return "Sendable($id)"
    }
}
