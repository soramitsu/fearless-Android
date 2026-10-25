package jp.co.soramitsu.wallet.impl.domain.implementations

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.calls.getExistentialDeposit
import jp.co.soramitsu.core.runtime.storage.incompatible
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUnavailableException
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExistentialDepositUseCaseImpl(
    private val chainRegistry: ChainRegistry,
    private val rpcCalls: RpcCalls,
    private val remoteStorage: StorageDataSource
) : ExistentialDepositUseCase {
    override suspend fun invoke(chainAsset: Asset): Result<BigInteger> = withContext(Dispatchers.Default) {
        val chainAssetExistentialDeposit = chainAsset.existentialDeposit?.toBigIntegerOrNull()
        if (chainAssetExistentialDeposit != null && chainAsset.type != ChainAssetType.Equilibrium) {
            return@withContext Result.success(chainAssetExistentialDeposit)
        }

        // 4.2.0 answered zero whenever this lookup failed, and send validation then warned about nothing.
        runCatching { queryExistentialDeposit(chainAsset) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .recoverCatching { throw ExistentialDepositUnavailableException(chainAsset, it) }
    }

    private suspend fun queryExistentialDeposit(chainAsset: Asset): BigInteger {
        val chainId = chainAsset.chainId

        val runtime = chainRegistry.getRuntimeOrNull(chainId)
            ?: return if (hasNoExistentialDeposit(chainAsset)) BigInteger.ZERO else error("Runtime of $chainId is not loaded")

        return when (chainAsset.typeExtra) {
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
                getAssetsExistentialDeposit(chainId, chainAsset)
            }

            ChainAssetType.OrmlAsset,
            ChainAssetType.ForeignAsset,
            ChainAssetType.StableAssetPoolToken,
            ChainAssetType.LiquidCrowdloan,
            ChainAssetType.VToken,
            ChainAssetType.VSToken,
            ChainAssetType.Token2,
            ChainAssetType.Stable -> {
                val assetIdentifier = requireNotNull(getExistentialDepositRpcArgument(chainAsset)) {
                    "${chainAsset.symbol} on ${chainAsset.chainName} has no currency id"
                }
                rpcCalls.getExistentialDeposit(chainId, assetIdentifier)
            }

            ChainAssetType.ERC20,
            ChainAssetType.BEP20,
            ChainAssetType.Jetton -> BigInteger.ZERO

            ChainAssetType.AssetId,
            ChainAssetType.Xcm,
            ChainAssetType.Unknown -> error("No existential deposit source for ${chainAsset.typeExtra} assets")
        }
    }

    // Without a Substrate runtime only EVM and TON assets have no minimum: there is no such rule on those networks.
    private suspend fun hasNoExistentialDeposit(chainAsset: Asset): Boolean {
        if (chainAsset.typeExtra in NO_EXISTENTIAL_DEPOSIT_TYPES) return true

        val chain = chainRegistry.getChain(chainAsset.chainId)
        return chain.isEthereumChain || chain.ecosystem == Ecosystem.Ton
    }

    // The minimum lives in the asset details (Assets.Asset). 4.2.0 read Assets.Account with the asset id
    // alone, a prefix key that has no value, so every lookup came back empty and was taken as zero.
    private suspend fun getAssetsExistentialDeposit(chainId: ChainId, chainAsset: Asset): BigInteger {
        val assetId = requireNotNull(chainAsset.currency) { "${chainAsset.symbol} on ${chainAsset.chainName} has no asset id" }

        return remoteStorage.query(
            chainId = chainId,
            keyBuilder = { assetDetailsStorageKey(it, assetId) },
            binding = { scale, runtime -> bindAssetMinBalance(scale, runtime) }
        )
    }

    private fun getExistentialDepositRpcArgument(asset: Asset): Pair<String, Any>? {
        return when (asset.type) {
            ChainAssetType.Stable,
            ChainAssetType.VToken,
            ChainAssetType.VSToken,
            ChainAssetType.OrmlAsset -> "token" to asset.symbol.uppercase()
            ChainAssetType.Token2 -> "token2" to (asset.currencyId?.toBigIntegerOrNull() ?: return null)
            ChainAssetType.ForeignAsset -> "foreignAsset" to (asset.currencyId?.toBigIntegerOrNull() ?: return null)
            ChainAssetType.StableAssetPoolToken -> "stableAssetPoolToken" to (asset.currencyId?.toBigIntegerOrNull() ?: return null)
            ChainAssetType.LiquidCrowdloan -> "liquidCrowdloan" to (asset.currencyId?.toBigIntegerOrNull() ?: return null)
            else -> null
        }
    }

    private companion object {
        val NO_EXISTENTIAL_DEPOSIT_TYPES = setOf(ChainAssetType.ERC20, ChainAssetType.BEP20, ChainAssetType.Jetton)
    }
}

private fun assetDetailsStorageKey(runtime: RuntimeSnapshot, assetId: Any): String =
    runtime.metadata.module(Modules.ASSETS).storage("Asset").storageKey(runtime, assetId)

// An absent entry means the asset does not exist on chain: that is a failed lookup, not a zero minimum.
private fun bindAssetMinBalance(scale: String?, runtime: RuntimeSnapshot): BigInteger {
    scale ?: error("Asset details are missing")

    val returnType = runtime.metadata.module(Modules.ASSETS).storage("Asset").returnType()
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    return bindNumber(decoded.getTyped("minBalance"))
}
