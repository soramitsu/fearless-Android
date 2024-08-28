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
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

suspend fun AssetCache.updateAsset(
    metaId: Long,
    accountId: AccountId,
    asset: Asset,
    balanceData: AssetBalanceData?
) {
    when (balanceData) {
        null, is EmptyBalance -> {
            updateAsset(metaId, accountId, asset) {
                it.copy(
                    accountId = accountId,
                    freeInPlanks = BigInteger.ZERO
                )
            }
        }

        is AccountInfo -> updateAsset(metaId, accountId, asset, accountInfoUpdater(balanceData))
        is OrmlTokensAccountData -> {
            updateAsset(metaId, accountId, asset) {
                it.copy(
                    accountId = accountId,
                    freeInPlanks = balanceData.free,
                    miscFrozenInPlanks = balanceData.frozen,
                    reservedInPlanks = balanceData.reserved
                )
            }
        }

        is EqAccountInfo -> {
            updateAsset(metaId, accountId, asset) {
                it.copy(
                    accountId = accountId,
                    freeInPlanks = balanceData.data.balances[asset.currency].orZero()
                )
            }
        }

        is AssetsAccountInfo -> {
            updateAsset(metaId, accountId, asset) {
                it.copy(
                    accountId = accountId,
                    freeInPlanks = balanceData.balance,
                    status = balanceData.status
                )
            }
        }

        is SimpleBalanceData -> {
            updateAsset(metaId, accountId, asset) {
                it.copy(
                    accountId = accountId,
                    freeInPlanks = balanceData.balance
                )
            }
        }

        else -> Unit
    }
}

//suspend fun AssetCache.updateAsset(
//    metaId: Long,
//    accountId: AccountId,
//    chainAsset: Asset,
//    accountInfo: AccountInfo
//) = updateAsset(metaId, accountId, chainAsset, accountInfoUpdater(accountInfo))

//suspend fun AssetCache.updateAsset(
//    accountId: AccountId,
//    chainAsset: Asset,
//    accountInfo: AccountInfo
//) = updateAsset(accountId, chainAsset, accountInfoUpdater(accountInfo))

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

fun bindEquilibriumAccountData(hex: String?, runtime: RuntimeSnapshot): EqAccountInfo? {
    return hex?.let { runCatching { bindEquilibriumAccountInfo(it, runtime) }.getOrNull() }
}

fun bindAssetsAccountData(hex: String?, runtime: RuntimeSnapshot): AssetsAccountInfo? {
    return hex?.let { bindAssetsAccountInfo(it, runtime) }
}
