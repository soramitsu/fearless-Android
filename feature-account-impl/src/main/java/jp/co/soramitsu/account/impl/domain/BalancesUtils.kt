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
import kotlinx.coroutines.withTimeout
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric



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




suspend fun Ethereum.fetchEthBalance(asset: Asset, address: String): BigInteger {
    return withTimeout(3000L) {
        if (asset.isUtility) {
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
}
