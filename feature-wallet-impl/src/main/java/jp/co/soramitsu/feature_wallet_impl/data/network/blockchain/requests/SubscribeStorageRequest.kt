package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.StorageUtils

class SubscribeStorageRequest(publicKey: ByteArray) : RuntimeRequest(
    "state_subscribeStorage",
    listOf(
        listOf(
            StorageUtils.createStorageKey(Module.System.id, "Account", publicKey)

        )
    )
)