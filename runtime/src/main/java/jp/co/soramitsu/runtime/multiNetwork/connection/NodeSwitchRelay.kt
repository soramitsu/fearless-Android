package jp.co.soramitsu.runtime.multiNetwork.connection

import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.utils.cycle

class NodesSwitchRelay(
    nodes: List<ChainNode>
) {
    private val availableNodesCycle = nodes.cycle().iterator()
    private val attempts: MutableMap<String, Int> = nodes.associate { it.url to 0 }.toMutableMap()

     operator fun invoke(connect: (ChainNode) -> Result<Any>): Result<ChainNode> {
//        if (attempts.values.all { it > 3 }) return Result.failure("All nodes failed")

        val node = availableNodesCycle.next()
        val attempt = attempts[node.url] ?: 0

        if (attempt > 3) return invoke(connect)
        attempts[node.url] = attempt + 1
        return connect(node).fold(
            onSuccess = { Result.success(node) },
            onFailure = { invoke(connect) })
    }
}