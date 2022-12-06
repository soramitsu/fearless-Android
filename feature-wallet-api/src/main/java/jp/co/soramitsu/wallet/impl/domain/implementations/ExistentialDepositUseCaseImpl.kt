package jp.co.soramitsu.wallet.impl.domain.implementations

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase

@Deprecated("DON'T USE IT")
class ExistentialDepositUseCaseImpl(private val chainRegistry: ChainRegistry, private val rpcCalls: RpcCalls) : ExistentialDepositUseCase {
    override suspend fun invoke(chainAsset: Chain.Asset): BigInteger {
        val chainAssetExistentialDeposit = chainAsset.existentialDeposit?.toBigInteger()
        if (chainAssetExistentialDeposit != null) {
            return chainAssetExistentialDeposit
        }

        val chainId = chainAsset.chainId

        val existentialDepositResult = kotlin.runCatching {
            when (chainAsset.type) {
                null,
                ChainAssetType.Normal,
                ChainAssetType.OrmlChain,
                ChainAssetType.SoraAsset -> {
                    // from const
                    val runtime = chainRegistry.getRuntime(chainId)

                    runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime)
                }
                ChainAssetType.OrmlAsset,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.LiquidCrowdloan,
                ChainAssetType.VToken,
                ChainAssetType.VSToken,
                ChainAssetType.Stable,
                ChainAssetType.Equilibrium -> {
                    // from rpc call
                    // TODO FIX RPC CALL
                    rpcCalls.getExistentialDeposit(chainId, chainAsset.currency ?: return BigInteger.ZERO)
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
}
