package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset.StakingType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.runtime.ext.ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isPolkadotOrKusama
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.wallet.impl.data.repository.HistoryRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.domain.model.toPhishingModel
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

private const val QR_PREFIX_SUBSTRATE = "substrate"
private const val PREFS_WALLET_SELECTED_CHAIN_ID = "wallet_selected_chain_id"
private const val PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT = "prefs_sora_card_hidden_sessions_count"
private const val SORA_CARD_HIDDEN_SESSIONS_LIMIT = 5
private const val HIDE_ZERO_BALANCES_PREFS_KEY = "hideZeroBalances"

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val addressBookRepository: AddressBookRepository,
    private val accountRepository: AccountRepository,
    private val historyRepository: HistoryRepository,
    private val chainRegistry: ChainRegistry,
    private val fileProvider: FileProvider,
    private val preferences: Preferences,
    private val selectedFiat: SelectedFiat,
    private val updatesMixin: UpdatesMixin,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher
) : WalletInteractor, UpdatesProviderUi by updatesMixin {

    override suspend fun getHideZeroBalancesForCurrentWallet(): Boolean {
        val walletId = accountRepository.getSelectedMetaAccount().id
        val key = getHideZeroBalancesKey(walletId)
        return preferences.getBoolean(key, false)
    }

    override suspend fun toggleHideZeroBalancesForCurrentWallet() {
        val walletId = accountRepository.getSelectedMetaAccount().id
        val key = getHideZeroBalancesKey(walletId)
        val value = preferences.getBoolean(key, false)
        val newValue = value.not()
        preferences.putBoolean(key, newValue)
    }

    override fun observeHideZeroBalanceEnabledForCurrentWallet(): Flow<Boolean> {
        return accountRepository.selectedMetaAccountFlow().flatMapMerge { wallet ->
            preferences.booleanFlow(getHideZeroBalancesKey(wallet.id), false)
        }.distinctUntilChanged()
    }

    private fun getHideZeroBalancesKey(walletId: Long): String {
        return "${HIDE_ZERO_BALANCES_PREFS_KEY}_$walletId"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun assetsFlow(): Flow<List<AssetWithStatus>> {
        return updatesMixin.tokenRatesUpdate.map {
            it.isNotEmpty()
        }
            .distinctUntilChanged()
            .flatMapLatest {
                accountRepository.selectedMetaAccountFlow()
            }.flatMapLatest {
                walletRepository.assetsFlow(it)
            }
            .filter { it.isNotEmpty() }
            .map { assets ->
                assets.sortedWith(defaultAssetListSort())
            }
    }

    override fun observeAssets(): Flow<List<AssetWithStatus>> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest {
                walletRepository.assetsFlow(it)
            }
            .filter { it.isNotEmpty() }
    }

    private fun defaultAssetListSort() =
        compareByDescending<AssetWithStatus> { it.asset.total.orZero() > BigDecimal.ZERO }
            .thenByDescending { it.asset.fiatAmount.orZero() }
            .thenBy { it.asset.token.configuration.isTestNet }
            .thenByDescending { it.asset.token.configuration.chainId.isPolkadotOrKusama() }
            .thenBy { it.asset.token.configuration.chainName }
            .thenBy { it.asset.token.configuration.symbol }
            .thenByDescending { it.asset.token.configuration.isUtility }
            .thenByDescending { it.asset.token.configuration.isNative == true }

    override suspend fun syncAssetsRates(): Result<Unit> {
        return runCatching {
            walletRepository.syncAssetsRates(selectedFiat.get())
        }
    }

    override fun assetFlow(chainId: ChainId, chainAssetId: String): Flow<Asset> {
        return accountRepository.selectedMetaAccountFlow().flatMapLatest { metaAccount ->

            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountId(chain)!!

            walletRepository.assetFlow(
                metaAccount.id,
                accountId,
                chainAsset,
                chain.minSupportedVersion
            )
        }
    }

    override suspend fun getCurrentAsset(chainId: ChainId, chainAssetId: String): Asset {
        return kotlin.runCatching { getCurrentAssetOrNull(chainId, chainAssetId)!! }.requireValue()
    }

    override suspend fun getCurrentAssetOrNull(chainId: ChainId, chainAssetId: String): Asset? {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)

        return walletRepository.getAsset(
            metaAccount.id,
            metaAccount.accountId(chain)!!,
            chainAsset,
            chain.minSupportedVersion
        )
    }

    override fun operationsFirstPageFlow(
        chainId: ChainId,
        chainAssetId: String
    ): Flow<OperationsPageChange> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { metaAccount ->
                val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
                val accountId = metaAccount.accountId(chain)!!

                historyRepository.operationsFirstPageFlow(accountId, chain, chainAsset).withIndex()
                    .map { (index, cursorPage) ->
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

            historyRepository.syncOperationsFirstPage(
                pageSize,
                filters,
                accountId,
                chain,
                chainAsset
            )
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

            historyRepository.getOperations(
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

    override suspend fun validateSendAddress(chainId: ChainId, address: String): Boolean =
        withContext(Dispatchers.Default) {
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

        return walletRepository.getTransferFee(chain = chain, transfer = transfer)
    }

    override suspend fun observeTransferFee(transfer: Transfer): Flow<Fee> {
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)

        return walletRepository.observeTransferFee(chain = chain, transfer = transfer)
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
            address
                ?: throw IllegalArgumentException("There is no address found to getQrCodeSharingSoraString")
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

    override suspend fun getMetaAccountSecrets(metaId: Long?) =
        accountRepository.getMetaAccountSecrets(metaId)

    override suspend fun getSelectedMetaAccount() = accountRepository.getSelectedMetaAccount()

    override suspend fun getChainAddressForSelectedMetaAccount(chainId: ChainId) =
        getSelectedMetaAccount().address(getChain(chainId))

    override suspend fun markAssetAsHidden(chainId: ChainId, chainAssetId: String) {
        manageAssetHidden(chainId, chainAssetId, true)
    }

    override suspend fun markAssetAsShown(chainId: ChainId, chainAssetId: String) {
        manageAssetHidden(chainId, chainAssetId, false)
    }

    private suspend fun manageAssetHidden(
        chainId: ChainId,
        chainAssetId: String,
        isHidden: Boolean
    ) {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(chainId)
        val accountId = metaAccount.accountId(chain)
        val chainAsset = chain.assetsById[chainAssetId] ?: return

        val chainsWithAsset = chainRegistry.currentChains.first().filter { chainItem ->
            val isChainItemFromSameEcosystem = if (chain.ecosystem() == ChainEcosystem.STANDALONE) {
                chainItem.id == chainId
            } else {
                chainItem.ecosystem() == chain.ecosystem()
            }
            isChainItemFromSameEcosystem && chainItem.assets.any {
                it.symbol == chainAsset.symbol
            }
        }

        val assetsToManage = chainsWithAsset.map {
            it.assets.filter { it.symbol == chainAsset.symbol }
        }.flatten()

        accountId?.let {
            assetsToManage.forEach {
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

    override fun getOperationAddressWithChainIdFlow(
        limit: Int?,
        chainId: ChainId
    ): Flow<Set<String>> =
        historyRepository.getOperationAddressWithChainIdFlow(limit, chainId)

    override suspend fun saveAddress(name: String, address: String, selectedChainId: String) {
        addressBookRepository.saveAddress(name, address, selectedChainId)
    }

    override fun observeAddressBook(chainId: ChainId) =
        addressBookRepository.observeAddressBook(chainId).mapList { it.copy(address = it.address.trim()) }

    override fun saveChainId(walletId: Long, chainId: ChainId?) {
        preferences.putString(PREFS_WALLET_SELECTED_CHAIN_ID + walletId, chainId)
    }

    override suspend fun getSavedChainId(walletId: Long): String? {
        val savedChainId = preferences.getString(PREFS_WALLET_SELECTED_CHAIN_ID + walletId)
        val existingChain = savedChainId?.let { runCatching { getChain(it) }.getOrNull() }
        return existingChain?.id
    }

    override fun isShowGetSoraCard(): Boolean =
        preferences.getInt(PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT, 0) <= 0

    override fun observeIsShowSoraCard(): Flow<Boolean> =
        preferences.intFlow(PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT, 0).map { it <= 0 }

    override fun hideSoraCard() {
        preferences.putInt(PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT, SORA_CARD_HIDDEN_SESSIONS_LIMIT)
    }

    override fun decreaseSoraCardHiddenSessions() {
        val newCount = preferences.getInt(PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT, 0) - 1
        if (newCount <= 0) {
            preferences.removeField(PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT)
        } else {
            preferences.putInt(PREFS_SORA_CARD_HIDDEN_SESSIONS_COUNT, newCount)
        }
    }

    override suspend fun getEquilibriumAccountInfo(
        asset: CoreAsset,
        accountId: AccountId
    ): EqAccountInfo? =
        walletRepository.getEquilibriumAccountInfo(asset, accountId)

    override suspend fun getEquilibriumAssetRates(chainAsset: CoreAsset): Map<BigInteger, EqOraclePricePoint?> =
        walletRepository.getEquilibriumAssetRates(chainAsset)

    override suspend fun checkControllerDeprecations(): List<ControllerDeprecationWarning> {
        val currentAccount = accountRepository.getSelectedMetaAccount()
        val chains = chainRegistry.chainsById.first()
        val allRelayChainStakingAssets = chains.values
            .map { it.assets }
            .flatten()
            .asSequence()
            .filter { it.staking == StakingType.RELAYCHAIN }
            .toList()

        val relayStakingChains = allRelayChainStakingAssets.map { it.chainId }

        val chainsWithDeprecatedControllerAccount = relayStakingChains.filter {
            chainRegistry.getRuntimeOrNull(it)?.metadata?.module(Modules.STAKING)?.calls?.get("set_controller")?.arguments?.isEmpty() == true
        }

        return chainsWithDeprecatedControllerAccount.mapNotNull { chainId ->
            val chain = chains[chainId] ?: return@mapNotNull null
            val accountId = currentAccount.accountId(chain) ?: return@mapNotNull null

            // checking current account is stash

            val controllerAccount = walletRepository.getControllerAccount(chainId, accountId)

            val currentAccountIsStashAndController =
                controllerAccount != null && controllerAccount.contentEquals(accountId) // the case is resolved
            if (currentAccountIsStashAndController) {
                return@mapNotNull null
            }

            val currentAccountHasAnotherController =
                controllerAccount != null && !controllerAccount.contentEquals(accountId) // user needs to fix it
            if (currentAccountHasAnotherController) {
                return@mapNotNull ControllerDeprecationWarning.ChangeController(chainId, chain.name)
            }

            // checking current account is controller
            val currentAccountIsNotStash = controllerAccount == null

            if (currentAccountIsNotStash) {
                val stash = walletRepository.getStashAccount(chainId, accountId)
                // we've found the stash
                if (stash != null) {
                    return@mapNotNull ControllerDeprecationWarning.ImportStash(
                        chainId,
                        stash.toAddress(chain.addressPrefix.toShort())
                    )
                }
            }

            return@mapNotNull null
        }
    }

    override suspend fun canUseAsset(chainId: String, chainAssetId: String): Boolean {
        val hasAsset = getCurrentAssetOrNull(chainId, chainAssetId) != null
        val hasRuntime = chainRegistry.getRuntimeOrNull(chainId) != null
        return hasAsset && hasRuntime
    }
}
