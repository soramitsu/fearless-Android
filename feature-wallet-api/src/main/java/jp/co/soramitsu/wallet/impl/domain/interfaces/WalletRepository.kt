package jp.co.soramitsu.wallet.impl.domain.interfaces

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import kotlinx.coroutines.flow.Flow

interface WalletRepository {

    fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>>

    suspend fun getAssets(metaId: Long): List<Asset>

    suspend fun syncAssetsRates(currencyId: String)

    fun assetFlow(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Flow<Asset>

    suspend fun getAsset(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Asset?

    suspend fun updateAssetHidden(metaId: Long, accountId: AccountId, chainId: ChainId, assetSymbol: String, isHidden: Boolean)

    suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    )

    suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ): CursorPage<Operation>

    fun operationsFirstPageFlow(
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ): Flow<CursorPage<Operation>>

    suspend fun getContacts(
        accountId: AccountId,
        chain: Chain,
        query: String
    ): Set<String>

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

    suspend fun getAccountFreeBalance(chainAsset: Chain.Asset, accountId: AccountId): BigInteger

    suspend fun updateAssets(newItems: List<AssetUpdateItem>)

    suspend fun getRemoteConfig(): Result<AppConfigRemote>

    fun chainRegistrySyncUp()
}
