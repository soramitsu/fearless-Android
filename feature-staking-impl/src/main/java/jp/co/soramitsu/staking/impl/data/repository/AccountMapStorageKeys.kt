package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey

fun StorageEntry.accountMapStorageKeys(runtime: RuntimeSnapshot, accountIdsHex: List<String>): List<String> {
    return accountIdsHex.map { storageKey(runtime, it.fromHex()) }
}
