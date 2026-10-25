package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.system

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

private const val METHOD = "system_chain"

class NodeNetworkTypeRequest : RuntimeRequest(METHOD, listOf())
