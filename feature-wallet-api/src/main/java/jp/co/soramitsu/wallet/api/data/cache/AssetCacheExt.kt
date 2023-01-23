package jp.co.soramitsu.wallet.api.data.cache

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.OrmlTokensAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindEquilibriumAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindOrmlTokensAccountData
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

suspend fun AssetCache.updateAsset(
    metaId: Long,
    accountId: AccountId,
    chainAsset: Chain.Asset,
    accountInfo: AccountInfo
) = updateAsset(metaId, accountId, chainAsset, accountInfoUpdater(accountInfo))

suspend fun AssetCache.updateAsset(
    accountId: AccountId,
    chainAsset: Chain.Asset,
    accountInfo: AccountInfo
) = updateAsset(accountId, chainAsset, accountInfoUpdater(accountInfo))

private fun accountInfoUpdater(accountInfo: AccountInfo) = { asset: AssetLocal ->
    val data = accountInfo.data

    asset.copy(
        freeInPlanks = data.free,
        reservedInPlanks = data.reserved,
        miscFrozenInPlanks = data.miscFrozen,
        feeFrozenInPlanks = data.feeFrozen
    )
}

fun bindAccountInfoOrDefault(hex: String?, runtime: RuntimeSnapshot): AccountInfo {
    return hex?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty()
}

fun bindOrmlTokensAccountDataOrDefault(hex: String?, runtime: RuntimeSnapshot): OrmlTokensAccountData {
    return hex?.let { bindOrmlTokensAccountData(it, runtime) } ?: OrmlTokensAccountData.empty()
}
fun bindEquilibriumAccountDataOrDefault(hex: String?, runtime: RuntimeSnapshot, currency: BigInteger?): AccountInfo {
    return hex?.let { bindEquilibriumAccountInfo(it, runtime, currency) } ?: AccountInfo.empty()
}
