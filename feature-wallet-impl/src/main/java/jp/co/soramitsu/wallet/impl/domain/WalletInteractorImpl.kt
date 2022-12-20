package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.runtime.ext.isValidAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isPolkadotOrKusama
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.domain.model.toPhishingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext

private const val CUSTOM_ASSET_SORTING_PREFS_KEY = "customAssetSorting-"
private const val QR_PREFIX_SUBSTRATE = "substrate"
private const val PREFS_WALLET_SELECTED_CHAIN_ID = "wallet_selected_chain_id"

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val addressBookRepository: AddressBookRepository,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val fileProvider: FileProvider,
    private val preferences: Preferences,
    private val selectedFiat: SelectedFiat,
    private val updatesMixin: UpdatesMixin
) : WalletInteractor, UpdatesProviderUi by updatesMixin {

    override fun assetsFlow(): Flow<List<AssetWithStatus>> {
        return updatesMixin.tokenRatesUpdate.map {
            it.isNotEmpty()
        }
            .distinctUntilChanged()
            .flatMapLatest { ratesUpdating ->
                accountRepository.selectedMetaAccountFlow()
                    .flatMapLatest {
                        walletRepository.assetsFlow(it)
                    }
                    .filter { it.isNotEmpty() }
                    .map { assets ->
                        when {
                            customAssetSortingEnabled() -> assets.sortedBy { it.asset.sortIndex }
                            else -> assets.sortedWith(defaultAssetListSort())
                        }
                    }
            }
    }

    override fun observeAssets(): Flow<List<AssetWithStatus>> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest {
                walletRepository.assetsFlow(it)
            }
            .filter { it.isNotEmpty() }
    }

    private fun defaultAssetListSort() = compareByDescending<AssetWithStatus> { it.asset.total.orZero() > BigDecimal.ZERO }
        .thenByDescending { it.asset.fiatAmount.orZero() }
        .thenBy { it.asset.token.configuration.isTestNet }
        .thenByDescending { it.asset.token.configuration.chainId.isPolkadotOrKusama() }
        .thenBy { it.asset.token.configuration.chainName }

    override suspend fun syncAssetsRates(): Result<Unit> {
        return runCatching {
            walletRepository.syncAssetsRates(selectedFiat.get())
        }
    }

    override fun assetFlow(chainId: ChainId, chainAssetId: String): Flow<Asset> {
        return accountRepository.selectedMetaAccountFlow().flatMapLatest { metaAccount ->

            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountId(chain)!!

            walletRepository.assetFlow(metaAccount.id, accountId, chainAsset, chain.minSupportedVersion)
        }
    }

    override suspend fun getCurrentAsset(chainId: ChainId, chainAssetId: String): Asset {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)

        return walletRepository.getAsset(metaAccount.id, metaAccount.accountId(chain)!!, chainAsset, chain.minSupportedVersion)!!
    }

    override fun operationsFirstPageFlow(chainId: ChainId, chainAssetId: String): Flow<OperationsPageChange> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { metaAccount ->
                val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
                val accountId = metaAccount.accountId(chain)!!

                walletRepository.operationsFirstPageFlow(accountId, chain, chainAsset).withIndex().map { (index, cursorPage) ->
                    OperationsPageChange(cursorPage, accountChanged = index == 0)
                }
            }
    }

    override suspend fun syncOperationsFirstPage(
        chainId: ChainId,
        chainAssetId: String,
        pageSize: Int,
        filters: Set<TransactionFilter>
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountId(chain)!!

            walletRepository.syncOperationsFirstPage(pageSize, filters, accountId, chain, chainAsset)
        }
    }

    override suspend fun getOperations(
        chainId: ChainId,
        chainAssetId: String,
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>
    ): Result<CursorPage<Operation>> {
        return runCatching {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountId(chain)!!

            walletRepository.getOperations(
                pageSize,
                cursor,
                filters,
                accountId,
                chain,
                chainAsset
            )
        }
    }

    override fun selectedAccountFlow(chainId: ChainId): Flow<WalletAccount> {
        return accountRepository.selectedMetaAccountFlow()
            .map { metaAccount ->
                val chain = chainRegistry.getChain(chainId)

                mapAccountToWalletAccount(chain, metaAccount)
            }
    }

    override suspend fun validateSendAddress(chainId: ChainId, address: String): Boolean = withContext(Dispatchers.Default) {
        val chain = chainRegistry.getChain(chainId)

        chain.isValidAddress(address)
    }

    override suspend fun isAddressFromPhishingList(address: String): Boolean {
        return walletRepository.isAddressFromPhishingList(address)
    }

    override suspend fun getPhishingInfo(address: String): PhishingModel? {
        return walletRepository.getPhishingInfo(address)?.toPhishingModel()
    }

    override suspend fun getTransferFee(transfer: Transfer): Fee {
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)

        return walletRepository.getTransferFee(chain, transfer)
    }

    override suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        tipInPlanks: BigInteger?
    ): Result<String> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)
        val accountId = metaAccount.accountId(chain)!!

        return runCatching {
            walletRepository.performTransfer(accountId, chain, transfer, fee, tipInPlanks)
        }
    }

    override suspend fun checkTransferValidityStatus(transfer: Transfer): Result<TransferValidityStatus> {
        return runCatching {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val chain = chainRegistry.getChain(transfer.chainAsset.chainId)
            val accountId = metaAccount.accountId(chain)!!

            walletRepository.checkTransferValidity(metaAccount.id, accountId, chain, transfer)
        }
    }

    override suspend fun getQrCodeSharingSoraString(chainId: ChainId, assetId: String): String {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(chainId)
        val asset = chain.assets.firstOrNull { it.id == assetId }

        val address = metaAccount.address(chain)
        val pubKey = metaAccount.accountId(chain)
        val name = metaAccount.name
        val currencyId = asset?.currencyId

        return if (address != null && pubKey != null && currencyId != null) {
            "$QR_PREFIX_SUBSTRATE:$address:$pubKey:$name:$currencyId"
        } else {
            address ?: throw IllegalArgumentException("There is no address found to getQrCodeSharingSoraString")
        }
    }

    override suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String) = runCatching {
        fileProvider.getFileInExternalCacheStorage(fileName)
    }

    override fun tryReadAddressFromSoraFormat(content: String): String? {
        val list = content.split(":")
        return list.getOrNull(1)
    }

    override fun tryReadTokenIdFromSoraFormat(content: String): String? {
        val list = content.split(":")
        return list.getOrNull(4)
    }

    override suspend fun updateAssets(newItems: List<AssetUpdateItem>) {
        walletRepository.updateAssets(newItems)
    }

    private fun mapAccountToWalletAccount(chain: Chain, account: MetaAccount) = with(account) {
        WalletAccount(account.address(chain)!!, name)
    }

    override suspend fun getChain(chainId: ChainId) = chainRegistry.getChain(chainId)

    override suspend fun getMetaAccountSecrets(metaId: Long?) = accountRepository.getMetaAccountSecrets(metaId)

    override suspend fun getSelectedMetaAccount() = accountRepository.getSelectedMetaAccount()

    override suspend fun getChainAddressForSelectedMetaAccount(chainId: ChainId) = getSelectedMetaAccount().address(getChain(chainId))

    override suspend fun customAssetSortingEnabled(): Boolean {
        val metaId = accountRepository.getSelectedMetaAccount().id
        return preferences.getBoolean("$CUSTOM_ASSET_SORTING_PREFS_KEY$metaId", false)
    }

    override suspend fun enableCustomAssetSorting() {
        val metaId = accountRepository.getSelectedMetaAccount().id
        preferences.putBoolean("$CUSTOM_ASSET_SORTING_PREFS_KEY$metaId", true)
    }

    override suspend fun markAssetAsHidden(chainId: ChainId, chainAssetId: String) {
        manageAssetHidden(chainId, chainAssetId, true)
    }

    override suspend fun markAssetAsShown(chainId: ChainId, chainAssetId: String) {
        manageAssetHidden(chainId, chainAssetId, false)
    }

    private suspend fun manageAssetHidden(chainId: ChainId, chainAssetId: String, isHidden: Boolean) {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(chainId)
        val accountId = metaAccount.accountId(chain)
        val chainAsset = chain.assetsById[chainAssetId] ?: return

        val tokenChains = chainRegistry.currentChains.first().filter {
            it.assets.any { it.symbolToShow == chainAsset.symbolToShow }
        }

        val tokenChainAssets = tokenChains.map {
            it.assets.filter { it.symbolToShow == chainAsset.symbolToShow }
        }.flatten()

        accountId?.let {
            tokenChainAssets.forEach {
                walletRepository.updateAssetHidden(
                    chainAsset = it,
                    metaId = metaAccount.id,
                    accountId = accountId,
                    isHidden = isHidden
                )
            }
        }
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> {
        return accountRepository.selectedMetaAccountFlow()
    }

    override fun polkadotAddressForSelectedAccountFlow(): Flow<String> {
        return selectedMetaAccountFlow().map {
            val chain = chainRegistry.getChain(polkadotChainId)
            it.address(chain) ?: ""
        }
    }

    override fun getChains(): Flow<List<Chain>> = chainRegistry.currentChains

    override fun getOperationAddressWithChainIdFlow(limit: Int?, chainId: ChainId): Flow<Set<String>> =
        walletRepository.getOperationAddressWithChainIdFlow(limit, chainId)

    override suspend fun saveAddress(name: String, address: String, selectedChainId: String) {
        addressBookRepository.saveAddress(name, address, selectedChainId)
    }

    override fun observeAddressBook(chainId: ChainId) = addressBookRepository.observeAddressBook(chainId)

    override fun saveChainId(chainId: ChainId?) = preferences.putString(PREFS_WALLET_SELECTED_CHAIN_ID, chainId)

    override fun getSavedChainId(): ChainId? = preferences.getString(PREFS_WALLET_SELECTED_CHAIN_ID)
}
