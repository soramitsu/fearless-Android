package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class GetStorageRequest(storageKey: String) : RuntimeRequest(
    "state_getStorage",
    listOf(
        storageKey
    )
)