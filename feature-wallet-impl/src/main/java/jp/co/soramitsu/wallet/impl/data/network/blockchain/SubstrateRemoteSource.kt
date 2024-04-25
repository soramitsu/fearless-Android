package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.AssetsAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.domain.model.Transfer

class TransferExtrinsicWithStatus(
    val extrinsic: TransferExtrinsic,
    val statusEvent: ExtrinsicStatusEvent?
)

interface SubstrateRemoteSource {
    suspend fun getTotalBalance(chainAsset: Asset, accountId: AccountId): BigInteger
    suspend fun getAccountFreeBalance(chainAsset: Asset, accountId: AccountId): BigInteger

    suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): BigInteger

    suspend fun performTransfer(
        accountId: ByteArray,
        chain: Chain,
        transfer: Transfer,
        tip: BigInteger?,
        appId: BigInteger?,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): String

    suspend fun fetchAccountTransfersInBlock(
        chainId: ChainId,
        blockHash: String,
        accountId: ByteArray
    ): Result<List<TransferExtrinsicWithStatus>>

    suspend fun getEquilibriumAssetRates(asset: Asset): Map<BigInteger, EqOraclePricePoint?>
    suspend fun getEquilibriumAccountInfo(asset: Asset, accountId: AccountId): EqAccountInfo?

    suspend fun getAssetsAccountInfo(asset: Asset, accountId: AccountId): AssetsAccountInfo?

    suspend fun getControllerAccount(chainId: ChainId, currentAccountId: AccountId): AccountId?

    suspend fun getStashAccount(chainId: ChainId, currentAccountId: AccountId): AccountId?
}
