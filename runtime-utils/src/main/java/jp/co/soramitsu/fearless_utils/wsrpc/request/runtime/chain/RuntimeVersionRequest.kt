package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

private const val METHOD = "chain_getRuntimeVersion"

class RuntimeVersionRequest : RuntimeRequest(METHOD, listOf())

class RuntimeVersion(val specVersion: Int, val transactionVersion: Int)
