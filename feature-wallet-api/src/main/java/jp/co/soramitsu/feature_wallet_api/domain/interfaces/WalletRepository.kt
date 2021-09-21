package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
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

    fun assetsFlow(accountId: AccountId): Flow<List<Asset>>

    suspend fun syncAssetsRates()

    fun assetFlow(
        accountId: AccountId,
        chainAsset: Chain.Asset
    ): Flow<Asset>

    suspend fun getAsset(
        accountId: AccountId,
        chainAsset: Chain.Asset
    ): Asset?

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
        transfer: Transfer
    ): Fee

    suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal
    )

    suspend fun checkTransferValidity(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer
    ): TransferValidityStatus

    suspend fun updatePhishingAddresses()

    suspend fun isAccountIdFromPhishingList(accountId: AccountId): Boolean

    suspend fun getAccountFreeBalance(chainId: ChainId, accountId: AccountId): BigInteger
}
