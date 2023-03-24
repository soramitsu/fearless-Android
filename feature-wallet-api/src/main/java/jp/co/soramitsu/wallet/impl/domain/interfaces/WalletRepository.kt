package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import kotlinx.coroutines.flow.Flow
import jp.co.soramitsu.core.models.Asset as CoreAsset
import java.math.BigDecimal
import java.math.BigInteger

interface WalletRepository {

    fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>>

    suspend fun getAssets(metaId: Long): List<Asset>

    suspend fun syncAssetsRates(currencyId: String)

    fun assetFlow(metaId: Long, accountId: AccountId, chainAsset: CoreAsset, minSupportedVersion: String?): Flow<Asset>

    suspend fun getAsset(metaId: Long, accountId: AccountId, chainAsset: CoreAsset, minSupportedVersion: String?): Asset?

    suspend fun updateAssetHidden(
        metaId: Long,
        accountId: AccountId,
        isHidden: Boolean,
        chainAsset: CoreAsset
    )

    suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    ): Fee

    suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal,
        tip: BigInteger?,
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

    suspend fun updateAssets(newItems: List<AssetUpdateItem>)

    suspend fun getRemoteConfig(): Result<AppConfigRemote>

    fun chainRegistrySyncUp()
}
