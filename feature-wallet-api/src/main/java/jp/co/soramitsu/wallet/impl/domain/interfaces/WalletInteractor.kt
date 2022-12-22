package jp.co.soramitsu.wallet.impl.domain.interfaces

import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow

class NotValidTransferStatus(val status: TransferValidityStatus) : Exception()

interface WalletInteractor {

    fun assetsFlow(): Flow<List<AssetWithStatus>>

    suspend fun syncAssetsRates(): Result<Unit>

    fun assetFlow(chainId: ChainId, chainAssetId: String): Flow<Asset>

    suspend fun getCurrentAsset(chainId: ChainId, chainAssetId: String): Asset

    fun operationsFirstPageFlow(chainId: ChainId, chainAssetId: String): Flow<OperationsPageChange>

    suspend fun syncOperationsFirstPage(
        chainId: ChainId,
        chainAssetId: String,
        pageSize: Int,
        filters: Set<TransactionFilter>
    ): Result<*>

    suspend fun getOperations(
        chainId: ChainId,
        chainAssetId: String,
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>
    ): Result<CursorPage<Operation>>

    fun selectedAccountFlow(chainId: ChainId): Flow<WalletAccount>

    suspend fun validateSendAddress(chainId: ChainId, address: String): Boolean

    suspend fun isAddressFromPhishingList(address: String): Boolean

    suspend fun getPhishingInfo(address: String): PhishingModel?

    suspend fun getTransferFee(transfer: Transfer): Fee

    suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        tipInPlanks: BigInteger?
    ): Result<String>

    suspend fun checkTransferValidityStatus(transfer: Transfer): Result<TransferValidityStatus>

    suspend fun getQrCodeSharingSoraString(chainId: ChainId, assetId: String): String

    suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<File>

    fun tryReadAddressFromSoraFormat(content: String): String?

    fun tryReadTokenIdFromSoraFormat(content: String): String?

    suspend fun getChain(chainId: ChainId): Chain

    suspend fun getMetaAccountSecrets(metaId: Long? = null): EncodableStruct<MetaAccountSecrets>?

    suspend fun getSelectedMetaAccount(): MetaAccount

    suspend fun getChainAddressForSelectedMetaAccount(chainId: ChainId): String?

    suspend fun updateAssets(newItems: List<AssetUpdateItem>)

    suspend fun markAssetAsHidden(chainId: ChainId, chainAssetId: String)

    suspend fun markAssetAsShown(chainId: ChainId, chainAssetId: String)

    fun selectedMetaAccountFlow(): Flow<MetaAccount>

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>

    fun getChains(): Flow<List<Chain>>

    fun getOperationAddressWithChainIdFlow(limit: Int?, chainId: ChainId): Flow<Set<String>>

    suspend fun saveAddress(name: String, address: String, selectedChainId: String)

    fun observeAddressBook(chainId: ChainId): Flow<List<AddressBookContact>>

    fun observeAssets(): Flow<List<AssetWithStatus>>

    fun saveChainId(chainId: ChainId?)

    fun getSavedChainId(): ChainId?
}
