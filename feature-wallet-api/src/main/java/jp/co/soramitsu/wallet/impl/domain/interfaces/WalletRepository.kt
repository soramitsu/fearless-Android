package jp.co.soramitsu.wallet.impl.domain.interfaces

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.okx.OkxCrossChainResponse
import jp.co.soramitsu.common.data.network.okx.OkxResponse
import jp.co.soramitsu.common.data.network.okx.OkxSwapResponse
import jp.co.soramitsu.common.data.network.okx.OkxToken
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.OkxChainLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.OkxTokenModel
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import kotlinx.coroutines.flow.Flow
import jp.co.soramitsu.core.models.Asset as CoreAsset

interface WalletRepository {

    fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>>

    suspend fun getAssets(metaId: Long): List<Asset>

    suspend fun syncAssetsRates(currencyId: String)

    fun assetFlow(
        metaId: Long,
        accountId: AccountId,
        chainAsset: CoreAsset,
        minSupportedVersion: String?
    ): Flow<Asset>

    suspend fun getAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: CoreAsset,
        minSupportedVersion: String?
    ): Asset?

    suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    ): Fee

    suspend fun observeTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    ): Flow<Fee>

    suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal,
        tip: BigInteger?,
        appId: BigInteger?,
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    ): String

    suspend fun checkTransferValidity(
        metaId: Long,
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    ): TransferValidityStatus

    suspend fun updatePhishingAddresses()

    suspend fun isAddressFromPhishingList(address: String): Boolean

    suspend fun getPhishingInfo(address: String): PhishingLocal?

    suspend fun getAccountFreeBalance(chainAsset: CoreAsset, accountId: AccountId): BigInteger

    suspend fun getEquilibriumAssetRates(chainAsset: CoreAsset): Map<BigInteger, EqOraclePricePoint?>

    suspend fun getEquilibriumAccountInfo(asset: CoreAsset, accountId: AccountId): EqAccountInfo?

    suspend fun getRemoteConfig(): Result<AppConfigRemote>

//    suspend fun fetchOkxSupportedAssets()
    suspend fun getOkxAssets(chainId: ChainId? = null): List<CoreAsset>
    suspend fun getOkxTokens(chainId: ChainId? = null): List<OkxTokenModel>

    suspend fun getSingleAssetPriceCoingecko(priceId: String, currency: String): BigDecimal?
    suspend fun getControllerAccount(chainId: ChainId, accountId: AccountId): AccountId?
    suspend fun getStashAccount(chainId: ChainId, accountId: AccountId): AccountId?

    suspend fun getTotalBalance(
        chainAsset: CoreAsset,
        chain: Chain,
        accountId: ByteArray
    ): BigInteger

    fun observeChainsPerAsset(accountMetaId: Long, assetId: String): Flow<Map<Chain, Asset?>>

    suspend fun getVestingLockedAmount(chainId: ChainId): BigInteger?
    suspend fun estimateClaimRewardsFee(chainId: ChainId): BigInteger
    suspend fun claimRewards(chain: IChain, accountId: AccountId): Result<String>
    suspend fun updateAssetsHidden(state: List<AssetUpdateItem>)

    fun observeOkxChains(): Flow<List<Chain>>
    suspend fun getOkxChains(): List<Chain>
    suspend fun getOkxCrossChains(chainId: ChainId? = null): List<Chain>
    suspend fun crossChainBuildTx(
        fromChainId: String,
        toChainId: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        amount: String,
        sort: Int? = null, // 0 - default
        slippage: String,  // 0.002 - 0.5
        userWalletAddress: String,
    ): OkxResponse<OkxCrossChainResponse>

    suspend fun getOkxSwap(
        chainId: String,
        amount: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        slippage: String,
        userWalletAddress: String
    ): OkxResponse<OkxSwapResponse>
}
