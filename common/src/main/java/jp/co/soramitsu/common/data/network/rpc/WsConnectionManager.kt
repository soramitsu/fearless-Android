package jp.co.soramitsu.common.data.network.rpc

import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService

class WsConnectionManager(
    val socketService: SocketService
) : ConnectionManager {
    private val lifecycleConditionSubject = BehaviorSubject.createDefault(LifecycleCondition.FORBIDDEN)

    override fun setLifecycleCondition(condition: LifecycleCondition) {
        lifecycleConditionSubject.onNext(condition)
    }

    override fun observeLifecycleCondition() = lifecycleConditionSubject

    override fun getLifecycleCondition() = lifecycleConditionSubject.value!!

    override fun start(url: String) {
        socketService.start(url)
    }

    override fun started() = socketService.started()

    override fun switchUrl(url: String) {
        socketService.switchUrl(url)
    }

    override fun stop() {
        socketService.stop()
    }

    override fun observeNetworkState() = socketService.observeNetworkState()
}