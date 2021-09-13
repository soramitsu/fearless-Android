package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.OperationsPageChange
import jp.co.soramitsu.feature_wallet_api.domain.model.RecipientSearchResult
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.math.BigDecimal

class NotValidTransferStatus(val status: TransferValidityStatus) : Exception()
class PhishingAddress : Exception()

interface WalletInteractor {

    fun assetsFlow(): Flow<List<Asset>>

    suspend fun syncAssetsRates(): Result<Unit>

    suspend fun syncAssetRates(type: Token.Type): Result<Unit>

    fun assetFlow(type: Token.Type): Flow<Asset>

    suspend fun getCurrentAsset(): Asset

    fun currentAssetFlow(): Flow<Asset>

    fun operationsFirstPageFlow(): Flow<OperationsPageChange>

    suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
    ): Result<*>

    suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>
    ): Result<CursorPage<Operation>>

    fun selectedAccountFlow(): Flow<WalletAccount>

    suspend fun getRecipients(query: String): RecipientSearchResult

    suspend fun validateSendAddress(address: String): Boolean

    suspend fun isAddressFromPhishingList(address: String): Boolean

    suspend fun getTransferFee(transfer: Transfer): Fee

    suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        maxAllowedLevel: TransferValidityLevel = TransferValidityLevel.Ok
    ): Result<Unit>

    suspend fun checkTransferValidityStatus(transfer: Transfer): Result<TransferValidityStatus>

    suspend fun getAccountsInCurrentNetwork(): List<WalletAccount>

    suspend fun selectAccount(address: String)

    suspend fun getQrCodeSharingString(): String

    suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<Pair<File, Asset>>

    suspend fun getRecipientFromQrCodeContent(content: String): Result<String>

    suspend fun getSelectedAccount(): WalletAccount
}
