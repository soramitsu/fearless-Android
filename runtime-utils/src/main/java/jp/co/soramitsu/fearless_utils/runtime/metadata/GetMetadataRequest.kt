package jp.co.soramitsu.fearless_utils.runtime.metadata

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

object GetMetadataRequest : RuntimeRequest("state_getMetadata", listOf())
