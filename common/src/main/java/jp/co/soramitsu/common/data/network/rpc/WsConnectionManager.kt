package jp.co.soramitsu.common.data.network.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class WsConnectionManager(
    val socketService: SocketService
) : ConnectionManager {
    private val lifecycleConditionSubject = MutableStateFlow(LifecycleCondition.FORBIDDEN)

    override fun setLifecycleCondition(condition: LifecycleCondition) {
        lifecycleConditionSubject.value = condition
    }

    override fun lifecycleConditionFlow() = lifecycleConditionSubject

    override fun getLifecycleCondition() = lifecycleConditionSubject.value

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

    override fun networkStateFlow() = socketService.networkStateFlow()
}
