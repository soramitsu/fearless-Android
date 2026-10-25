package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class StateRuntimeVersionRequest : RuntimeRequest("state_getRuntimeVersion", listOf())

object SubscribeStateRuntimeVersionRequest : RuntimeRequest("state_subscribeRuntimeVersion", listOf())
