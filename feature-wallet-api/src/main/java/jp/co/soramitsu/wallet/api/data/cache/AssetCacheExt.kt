package jp.co.soramitsu.wallet.api.data.cache

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalanceData
import jp.co.soramitsu.common.data.network.runtime.binding.AssetsAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EmptyBalance
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.OrmlTokensAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindAssetsAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindEquilibriumAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindOrmlTokensAccountData
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.cache.FreshAssetCacheUpdate
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

suspend fun AssetCache.updateAsset(
    metaId: Long,
    accountId: AccountId,
    asset: Asset,
    balanceData: AssetBalanceData?
) {
    val fresh = when (balanceData) {
        null, is EmptyBalance -> FreshAssetCacheUpdate.Free(BigInteger.ZERO)
        is AccountInfo -> FreshAssetCacheUpdate.AccountInfo(
            balanceData.data.free, balanceData.data.reserved, balanceData.data.miscFrozen, balanceData.data.feeFrozen
        )
        is OrmlTokensAccountData -> FreshAssetCacheUpdate.Orml(balanceData.free, balanceData.reserved, balanceData.frozen)
        is EqAccountInfo -> FreshAssetCacheUpdate.Free(balanceData.data.balances[asset.currency].orZero())
        is AssetsAccountInfo -> FreshAssetCacheUpdate.FreeAndStatus(balanceData.balance, balanceData.status)
        is SimpleBalanceData -> FreshAssetCacheUpdate.Free(balanceData.balance)
        else -> return
    }
    updateAsset(metaId, accountId, asset, fresh)
}

fun bindAccountInfoOrDefault(hex: String?, runtime: RuntimeSnapshot): AccountInfo {
    return hex?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty()
}

fun bindOrmlTokensAccountDataOrDefault(hex: String?, runtime: RuntimeSnapshot): OrmlTokensAccountData {
    return hex?.let { bindOrmlTokensAccountData(it, runtime) } ?: OrmlTokensAccountData.empty()
}

fun bindEquilibriumAccountData(hex: String?, runtime: RuntimeSnapshot): EqAccountInfo? {
    return hex?.let { runCatching { bindEquilibriumAccountInfo(it, runtime) }.getOrNull() }
}

fun bindAssetsAccountData(hex: String?, runtime: RuntimeSnapshot): AssetsAccountInfo? {
    return hex?.let { bindAssetsAccountInfo(it, runtime) }
}
