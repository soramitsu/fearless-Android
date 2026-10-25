package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime

import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import kotlin.random.Random

private fun nextId() = Random.nextInt(1, Int.MAX_VALUE)

open class RuntimeRequest(
    val method: String,
    val params: List<Any>,
    val id: Int = nextId()
) : RpcRequest()
