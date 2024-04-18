package jp.co.soramitsu.wallet.impl.data.network.model

import android.util.Log
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalanceData
import jp.co.soramitsu.common.data.network.runtime.binding.EmptyBalance
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.wallet.api.data.cache.bindAssetsAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindOrmlTokensAccountDataOrDefault


fun constructBalanceKey(
    runtime: RuntimeSnapshot,
    asset: Asset,
    accountId: ByteArray
): String? {
    val keyConstructionResult = runCatching {
        val currency =
            asset.currency ?: return@runCatching runtime.metadata.system().storage("Account")
                .storageKey(runtime, accountId)
        when (asset.typeExtra) {
            null, ChainAssetType.Normal,
            ChainAssetType.Equilibrium,
            ChainAssetType.SoraUtilityAsset -> runtime.metadata.system().storage("Account")
                .storageKey(runtime, accountId)

            ChainAssetType.OrmlChain,
            ChainAssetType.OrmlAsset,
            ChainAssetType.VToken,
            ChainAssetType.VSToken,
            ChainAssetType.Stable,
            ChainAssetType.ForeignAsset,
            ChainAssetType.StableAssetPoolToken,
            ChainAssetType.SoraAsset,
            ChainAssetType.AssetId,
            ChainAssetType.Token2,
            ChainAssetType.Xcm,
            ChainAssetType.LiquidCrowdloan -> runtime.metadata.tokens().storage("Accounts")
                .storageKey(runtime, accountId, currency)

            ChainAssetType.Assets -> runtime.metadata.module(Modules.ASSETS).storage("Account")
                .storageKey(runtime, currency, accountId)

            ChainAssetType.Unknown -> error("Not supported type for token ${asset.symbol} in ${asset.chainName}")
        }
    }
    return keyConstructionResult
        .onFailure {
            Log.d(
                "BalancesUpdateSystem",
                "Failed to construct storage key for asset ${asset.symbol} (${asset.id}) $it "
            )
        }
        .getOrNull()
}

fun handleBalanceResponse(
    runtime: RuntimeSnapshot,
    asset: Asset,
    scale: String?
): Result<AssetBalanceData> {
    return runCatching {
        when (asset.typeExtra) {
            null,
            ChainAssetType.Normal,
            ChainAssetType.SoraUtilityAsset -> {
                bindAccountInfoOrDefault(scale, runtime)
            }

            ChainAssetType.OrmlChain,
            ChainAssetType.OrmlAsset,
            ChainAssetType.ForeignAsset,
            ChainAssetType.StableAssetPoolToken,
            ChainAssetType.LiquidCrowdloan,
            ChainAssetType.VToken,
            ChainAssetType.SoraAsset,
            ChainAssetType.VSToken,
            ChainAssetType.AssetId,
            ChainAssetType.Token2,
            ChainAssetType.Xcm,
            ChainAssetType.Stable -> {
                bindOrmlTokensAccountDataOrDefault(scale, runtime)
            }

            ChainAssetType.Equilibrium -> {
                bindEquilibriumAccountData(scale, runtime) ?: EmptyBalance
            }

            ChainAssetType.Assets -> {
                bindAssetsAccountData(scale, runtime) ?: EmptyBalance
            }

            ChainAssetType.Unknown -> EmptyBalance
        }
    }
}
