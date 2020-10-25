package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

class SubscribeStorageRequest(storageKey: String) : RuntimeRequest(
    "state_subscribeStorage",
    listOf(
        listOf(
            storageKey
        )
    )
)