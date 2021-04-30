package jp.co.soramitsu.feature_wallet_api.data.cache

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

suspend fun AssetCache.updateAsset(
    accountAddress: String,
    accountInfo: AccountInfo,
) = updateAsset(accountAddress) {
    val data = accountInfo.data

    it.copy(
        freeInPlanks = data.free,
        reservedInPlanks = data.reserved,
        miscFrozenInPlanks = data.miscFrozen,
        feeFrozenInPlanks = data.feeFrozen
    )
}

fun bindAccountInfoOrDefault(hex: String?, runtime: RuntimeSnapshot): AccountInfo {
    return hex?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty()
}
