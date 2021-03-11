package jp.co.soramitsu.common.data.network.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow

enum class LifecycleCondition {
    ALLOWED, FORBIDDEN, STOPPED
}

interface ConnectionManager {
    fun setLifecycleCondition(condition: LifecycleCondition)

    fun lifecycleConditionFlow(): Flow<LifecycleCondition>

    fun getLifecycleCondition(): LifecycleCondition

    fun start(url: String)

    fun started(): Boolean

    fun switchUrl(url: String)

    fun stop()

    fun networkStateFlow(): Flow<SocketStateMachine.State>
}
