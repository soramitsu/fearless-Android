package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account

import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest

class AccountInfoRequest(publicKey: ByteArray) : GetStorageRequest(
    listOf(
        Module.System.Account.storageKey(publicKey)
    )
)
