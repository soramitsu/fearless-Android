package jp.co.soramitsu.wallet.impl.domain.implementations

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.calls.getExistentialDeposit
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase

class ExistentialDepositUseCaseImpl(
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls,
    private val remoteStorage: StorageDataSource
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

                ChainAssetType.Assets -> {
                    getAssetsExistentialDeposit(chainId, chainAsset).orZero()
                }

                ChainAssetType.OrmlAsset,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.LiquidCrowdloan,
                ChainAssetType.VToken,
                ChainAssetType.VSToken,
                ChainAssetType.Token2,
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

    private suspend fun getAssetsExistentialDeposit(chainId: ChainId, chainAsset: Asset): BigInteger? {
        return remoteStorage.query(
            chainId = chainId,
            keyBuilder = { it.metadata.module(Modules.ASSETS).storage("Account").storageKey(it, chainAsset.currency) },
            binding = { scale, runtime ->
                scale ?: return@query null
                val returnType = runtime.metadata.module(Modules.ASSETS).storage("Account").returnType()
                val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

                val minBalance = bindNumber(decoded.getTyped("minBalance"))
                minBalance
            }
        )

    }

    private fun getExistentialDepositRpcArgument(asset: Asset): Pair<String, Any>? {
        return when (asset.type) {
            ChainAssetType.Stable,
            ChainAssetType.VToken,
            ChainAssetType.VSToken,
            ChainAssetType.OrmlAsset -> "token" to asset.symbol.uppercase()
            ChainAssetType.Token2 -> "token2" to (asset.currencyId?.toBigInteger() ?: return null)
            ChainAssetType.ForeignAsset -> "foreignAsset" to (asset.currencyId?.toBigInteger() ?: return null)
            ChainAssetType.StableAssetPoolToken -> "stableAssetPoolToken" to (asset.currencyId?.toBigInteger() ?: return null)
            ChainAssetType.LiquidCrowdloan -> "liquidCrowdloan" to (asset.currencyId?.toBigInteger() ?: return null)
            else -> null
        }
    }
}
