package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetWithStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger

interface WalletRepository {

    fun assetsFlow(meta: MetaAccount, chainAccounts: List<MetaAccount.ChainAccount> = emptyList()): Flow<List<AssetWithStatus>>

    suspend fun getAssets(metaId: Long): List<Asset>

    suspend fun syncAssetsRates(currencyId: String)

    fun assetFlow(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Flow<Asset>

    suspend fun getAsset(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Asset?

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
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    )

    suspend fun checkTransferValidity(
        metaId: Long,
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
        batchAll: Boolean = false
    ): TransferValidityStatus

    suspend fun updatePhishingAddresses()

    suspend fun isAccountIdFromPhishingList(accountId: AccountId): Boolean

    suspend fun getAccountFreeBalance(chainId: ChainId, accountId: AccountId): BigInteger

    suspend fun updateAssets(newItems: List<AssetUpdateItem>)

    suspend fun getRemoteConfig(): Result<AppConfigRemote>

    fun chainRegistrySyncUp()
}
