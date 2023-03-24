package jp.co.soramitsu.wallet.impl.domain.implementations

import android.util.Log
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.calls.getExistentialDeposit
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import java.math.BigInteger

class ExistentialDepositUseCaseImpl(
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls
) : ExistentialDepositUseCase {
    override suspend fun invoke(chainAsset: Asset): BigInteger {
        val chainAssetExistentialDeposit = chainAsset.existentialDeposit?.toBigInteger()
        if (chainAssetExistentialDeposit != null && chainAsset.type != ChainAssetType.Equilibrium) {
            return chainAssetExistentialDeposit
        }

        val chainId = chainAsset.chainId

        val existentialDepositResult = kotlin.runCatching {
            val runtime = chainRegistry.getRuntime(chainId)
            when (chainAsset.typeExtra) {
                null,
                ChainAssetType.Normal,
                ChainAssetType.OrmlChain,
                ChainAssetType.SoraUtilityAsset,
                ChainAssetType.SoraAsset -> {
                    runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime)
                }
                ChainAssetType.Equilibrium -> {
                    runtime.metadata.module(Modules.EQBALANCES).numberConstant("ExistentialDeposit", runtime)
                }
                ChainAssetType.OrmlAsset,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.LiquidCrowdloan,
                ChainAssetType.VToken,
                ChainAssetType.VSToken,
                ChainAssetType.Stable -> {
                    val assetIdentifier = getExistentialDepositRpcArgument(chainAsset) ?: return BigInteger.ZERO
                    rpcCalls.getExistentialDeposit(chainId, assetIdentifier)
                }
                else -> BigInteger.ZERO
            }
        }

        return existentialDepositResult.fold({
            it
        }, {
            Log.e("ExistentialDepositUseCaseImpl", "ExistentialDepositUseCaseImpl error: ${it.localizedMessage ?: it.message ?: it.toString()}")
            BigInteger.ZERO
        })
    }

    private fun getExistentialDepositRpcArgument(asset: Asset): Pair<String, Any>? {
        return when (asset.type) {
            ChainAssetType.Stable,
            ChainAssetType.VToken,
            ChainAssetType.VSToken,
            ChainAssetType.OrmlAsset -> "token" to asset.symbol.uppercase()
            ChainAssetType.ForeignAsset -> "foreignAsset" to (asset.currencyId?.toBigInteger() ?: return null)
            ChainAssetType.StableAssetPoolToken -> "stableAssetPoolToken" to (asset.currencyId?.toBigInteger() ?: return null)
            ChainAssetType.LiquidCrowdloan -> "liquidCrowdloan" to (asset.currencyId?.toBigInteger() ?: return null)
            else -> null
        }
    }
}
