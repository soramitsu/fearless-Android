package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger

interface WalletRepository {

    fun assetsFlow(accountAddress: String): Flow<List<Asset>>

    suspend fun syncAssetsRates(account: WalletAccount)

    fun assetFlow(accountAddress: String, type: Token.Type): Flow<Asset>

    suspend fun getAsset(accountAddress: String, type: Token.Type): Asset?

    suspend fun syncAsset(account: WalletAccount, type: Token.Type)

    suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
        account: WalletAccount
    )

    suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        currentAccount: WalletAccount
    ): CursorPage<Operation>

    fun operationsFirstPageFlow(currentAccount: WalletAccount): Flow<CursorPage<Operation>>

    suspend fun getContacts(account: WalletAccount, query: String): Set<String>

    suspend fun getTransferFee(accountAddress: String, transfer: Transfer): Fee

    suspend fun performTransfer(accountAddress: String, transfer: Transfer, fee: BigDecimal)

    suspend fun checkTransferValidity(accountAddress: String, transfer: Transfer): TransferValidityStatus

    suspend fun updatePhishingAddresses()

    suspend fun isAddressFromPhishingList(address: String): Boolean

    suspend fun getAccountFreeBalance(accountAddress: String): BigInteger
}
