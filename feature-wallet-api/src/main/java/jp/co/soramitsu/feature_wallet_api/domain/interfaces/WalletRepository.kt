package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletRepository {

    fun assetsFlow(): Flow<List<Asset>>

    suspend fun syncAssetsRates()

    fun assetFlow(type: Token.Type): Flow<Asset>

    suspend fun getAsset(type: Token.Type): Asset?

    suspend fun syncAsset(type: Token.Type)

    fun transactionsFirstPageFlow(currentAccount: WalletAccount, pageSize: Int, accounts: List<WalletAccount>): Flow<List<Transaction>>

    suspend fun syncTransactionsFirstPage(pageSize: Int, account: WalletAccount, accounts: List<WalletAccount>)

    suspend fun getTransactionPage(pageSize: Int, page: Int, currentAccount: WalletAccount, accounts: List<WalletAccount>): List<Transaction>

    suspend fun getContacts(query: String): Set<String>

    suspend fun getTransferFee(transfer: Transfer): Fee

    suspend fun performTransfer(transfer: Transfer, fee: BigDecimal)

    suspend fun checkTransferValidity(transfer: Transfer): TransferValidityStatus

    suspend fun updatePhishingAddresses()

    suspend fun isAddressFromPhishingList(address: String): Boolean
}