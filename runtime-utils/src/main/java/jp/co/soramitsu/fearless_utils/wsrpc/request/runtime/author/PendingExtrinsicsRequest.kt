package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

private const val METHOD = "author_pendingExtrinsics"

class PendingExtrinsicsRequest : RuntimeRequest(METHOD, listOf())
