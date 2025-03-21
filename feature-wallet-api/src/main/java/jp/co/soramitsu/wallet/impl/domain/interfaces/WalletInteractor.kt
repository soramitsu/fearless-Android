package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.SoraCardProgress
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import jp.co.soramitsu.wallet.impl.domain.model.QrContentSora
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class NotValidTransferStatus(val status: TransferValidityStatus) : Exception()

enum class AssetSorting {
    FiatBalance, Popularity, Name
}

interface WalletInteractor {

    fun assetsFlow(): Flow<List<AssetWithStatus>>
    fun assetsFlowAndAccount(): Flow<Pair<Long, List<AssetWithStatus>>>

    suspend fun syncAssetsRates(): Result<Unit>

    fun assetFlow(chainId: ChainId, chainAssetId: String): Flow<Asset>

    suspend fun getCurrentAsset(chainId: ChainId, chainAssetId: String): Asset

    suspend fun getCurrentAssetOrNull(chainId: ChainId, chainAssetId: String): Asset?

    fun operationsFirstPageFlow(chainId: ChainId, chainAssetId: String): Flow<OperationsPageChange>

    suspend fun syncOperationsFirstPage(
        chainId: ChainId,
        chainAssetId: String,
        pageSize: Int,
        filters: Set<TransactionFilter>
    ): Result<CursorPage<Operation>>

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

    suspend fun observeTransferFee(transfer: Transfer): Flow<BigDecimal>

    suspend fun performTransfer(
        transfer: Transfer
    ): Result<String>

    suspend fun getQrCodeSharingSoraString(chainId: ChainId, assetId: String, amount: BigDecimal?): String

    suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<File>

    fun tryReadAddressFromSoraFormat(content: String): String?

    fun tryReadSoraFormat(content: String): QrContentSora?

    suspend fun tryReadCBDCAddressFormat(content: String): QrContentCBDC?

    suspend fun getChain(chainId: ChainId): Chain

    suspend fun getSelectedMetaAccount(): MetaAccount

    suspend fun getChainAddressForSelectedMetaAccount(chainId: ChainId): String?

    suspend fun markAssetAsHidden(chainId: ChainId, chainAssetId: String)

    suspend fun updateAssetsHiddenState(state: List<AssetBooleanState>)

    suspend fun markAssetAsShown(chainId: ChainId, chainAssetId: String)

    fun selectedMetaAccountFlow(): Flow<MetaAccount>

    fun polkadotAddressForSelectedAccountFlow(): Flow<String>

    fun getChains(): Flow<List<Chain>>

    fun getOperationAddressWithChainIdFlow(chainId: ChainId, limit: Int?): Flow<Set<String>>

    suspend fun saveAddress(name: String, address: String, selectedChainId: String)

    fun observeAddressBook(chainId: ChainId): Flow<List<AddressBookContact>>


    fun saveChainId(walletId: Long, chainId: ChainId?)

    suspend fun getSavedChainId(walletId: Long): ChainId?

    suspend fun getEquilibriumAccountInfo(asset: CoreAsset, accountId: AccountId): EqAccountInfo?
    suspend fun getEquilibriumAssetRates(chainAsset: CoreAsset): Map<BigInteger, EqOraclePricePoint?>

    fun isShowGetSoraCard(): Boolean
    fun observeIsShowSoraCard(): Flow<Boolean>
    fun decreaseSoraCardHiddenSessions()
    fun hideSoraCard()

    suspend fun checkControllerDeprecations(): List<ControllerDeprecationWarning>
    suspend fun canUseAsset(chainId: String, chainAssetId: String): Boolean

    suspend fun saveChainSelectFilter(walletId: Long, filter: String)

    fun observeSelectedAccountChainSelectFilter(): Flow<ChainSelectorViewStateWithFilters.Filter>

    fun observeChainsPerAsset(accountMetaId: Long, assetId: String): Flow<Map<Chain, Asset?>>

    fun applyAssetSorting(sorting: AssetSorting)

    fun observeAssetSorting(): Flow<AssetSorting>
    suspend fun checkClaimSupport(chainId: ChainId): Boolean
    suspend fun estimateClaimRewardsFee(chainId: ChainId): BigInteger
    suspend fun getVestingLockedAmount(chainId: ChainId): BigInteger?
    suspend fun claimRewards(chainId: ChainId): Result<String>

    fun getAssetManagementIntroPassed(): Boolean
    suspend fun saveAssetManagementIntroPassed()
    fun networkIssuesFlow(): Flow<Map<ChainId, NetworkIssueType>>
    suspend fun retryChainSync(chainId: ChainId): Result<Unit>

    fun observeCurrentAccountChainsPerAsset(assetId: String): Flow<Map<Chain, Asset?>>
    suspend fun getOperationAddressWithChainId(chainId: ChainId, limit: Int?): Set<String>
    suspend fun getToken(chainAsset: jp.co.soramitsu.core.models.Asset): Token
    suspend fun getExportSourceTypes(chainId: ChainId, walletId: Long? = null): MutableSet<ExportSource>
    fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount>
    fun extractTonAddress(input: String): String?
    fun extractEthAddress(input: String): String?
    fun tryReadAmountFromQrContent(input: String): BigDecimal?
}
