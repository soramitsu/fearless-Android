package jp.co.soramitsu.account.impl.domain

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalanceData
import jp.co.soramitsu.common.data.network.runtime.binding.EmptyBalance
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.wallet.api.data.cache.bindAssetsAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindOrmlTokensAccountDataOrDefault
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric

fun buildStorageKeys(
    chain: Chain,
    metaAccount: MetaAccount,
    runtime: RuntimeSnapshot
): Result<List<StorageKeyWithMetadata>> {
    val accountId = metaAccount.accountId(chain)
        ?: return Result.failure(RuntimeException("Can't get account id for meta account ${metaAccount.name}, chain: ${chain.name}"))

    return Result.success(buildStorageKeys(chain, runtime, metaAccount.id, accountId))
}

fun buildStorageKeys(
    chain: Chain,
    runtime: RuntimeSnapshot,
    metaAccountId: Long,
    accountId: ByteArray
): List<StorageKeyWithMetadata> {
    if (chain.utilityAsset != null && chain.utilityAsset?.typeExtra == ChainAssetType.Equilibrium) {
        val equilibriumStorageKeys = listOf(
            constructBalanceKey(
                runtime,
                requireNotNull(chain.utilityAsset),
                accountId
            ).let {
                StorageKeyWithMetadata(
                    requireNotNull(chain.utilityAsset),
                    metaAccountId,
                    accountId,
                    it
                )
            })
        return equilibriumStorageKeys
    }

    val storageKeys = chain.assets.map { asset ->
        StorageKeyWithMetadata(asset, metaAccountId, accountId,
            constructBalanceKey(runtime, asset, accountId)
        )
    }
    return storageKeys
}

data class StorageKeyWithMetadata(
    val asset: Asset,
    val metaAccountId: Long,
    val accountId: AccountId,
    val key: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StorageKeyWithMetadata

        if (asset != other.asset) return false
        if (metaAccountId != other.metaAccountId) return false
        if (!accountId.contentEquals(other.accountId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = asset.hashCode()
        result = 31 * result + metaAccountId.hashCode()
        result = 31 * result + accountId.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "StorageKeyWithMetadata(asset=${asset.name}, metaAccountId=$metaAccountId, key='$key')"
    }
}


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

suspend fun Ethereum.fetchEthBalance(asset: Asset, address: String): BigInteger {
    return if (asset.isUtility) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            ethGetBalance(
                address,
                DefaultBlockParameterName.LATEST
            ).send().balance
        }
    } else {
        val erc20GetBalanceFunction = Function(
            "balanceOf",
            listOf(Address(address)),
            emptyList()
        )

        val erc20BalanceWei = withContext(kotlinx.coroutines.Dispatchers.IO) {
            ethCall(
                Transaction.createEthCallTransaction(
                    null,
                    asset.id,
                    FunctionEncoder.encode(erc20GetBalanceFunction)
                ),
                DefaultBlockParameterName.LATEST
            ).send().value
        }

        Numeric.decodeQuantity(erc20BalanceWei)
    }
}

