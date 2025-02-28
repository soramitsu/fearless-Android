package jp.co.soramitsu.wallet.impl.domain

import android.net.Uri
import android.util.Log
import com.mastercard.mpqr.pushpayment.model.PushPaymentData
import com.mastercard.mpqr.pushpayment.parser.Parser
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset.StakingType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isPolkadotOrKusama
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.data.repository.HistoryRepository
import jp.co.soramitsu.wallet.impl.data.repository.isSupported
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.OperationsPageChange
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import jp.co.soramitsu.wallet.impl.domain.model.QrContentSora
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.domain.model.toPhishingModel
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URLDecoder
import kotlin.coroutines.CoroutineContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

private const val QR_PREFIX_SUBSTRATE = "substrate"
const val QR_PREFIX_WALLET_CONNECT = "wc"
const val QR_PREFIX_TON_CONNECT = "tc"
private const val PREFS_WALLET_SELECTED_CHAIN_ID = "wallet_selected_chain_id"
private const val CHAIN_SELECT_FILTER_APPLIED = "chain_select_filter_applied"
private const val ACCOUNT_ID_MIN_TAG = 26
private const val ACCOUNT_ID_MAX_TAG = 51
private const val ASSET_SORTING_KEY = "ASSET_SORTING_KEY"
private const val ASSET_MANAGEMENT_INTRO_PASSED_KEY = "ASSET_MANAGEMENT_INTRO_PASSED_KEY"

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val addressBookRepository: AddressBookRepository,
    private val accountRepository: AccountRepository,
    private val historyRepository: HistoryRepository,
    private val chainRegistry: ChainRegistry,
    private val fileProvider: FileProvider,
    private val preferences: Preferences,
    private val selectedFiat: SelectedFiat,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val chainsRepository: ChainsRepository,
    private val networkStateService: NetworkStateService,
    private val tokenRepository: TokenRepository,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) : WalletInteractor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun assetsFlow(): Flow<List<AssetWithStatus>> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest {
                walletRepository.assetsFlow(it)
            }
            .filter { it.isNotEmpty() }
            .map { assets ->
                assets.sortedWith(defaultAssetListSort())
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun assetsFlowAndAccount(): Flow<Pair<Long, List<AssetWithStatus>>> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { meta ->
                walletRepository.assetsFlow(meta).map {
                    meta.id to it
                }
            }
            .filter { it.second.isNotEmpty() }
            .map { (walletId, assets) ->
                walletId to assets.sortedWith(defaultAssetListSort())
            }
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
        return withContext(Dispatchers.Default) {
            runCatching {
                walletRepository.syncAssetsRates(selectedFiat.get())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun assetFlow(chainId: ChainId, chainAssetId: String): Flow<Asset> {
        return accountRepository.selectedMetaAccountFlow().flatMapLatest { metaAccount ->

            val (chain, chainAsset) = chainsRepository.chainWithAsset(chainId, chainAssetId)
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

    override suspend fun getCurrentAssetOrNull(chainId: ChainId, chainAssetId: String): Asset? =
        withContext(coroutineContext) {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val (chain, chainAsset) = chainsRepository.chainWithAsset(chainId, chainAssetId)

            return@withContext walletRepository.getAsset(
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
        return flow {
            val account = accountRepository.getSelectedMetaAccount()
            emit(account)
        }.flatMapLatest { metaAccount ->
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
            val (chain, chainAsset) = chainsRepository.chainWithAsset(chainId, chainAssetId)
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
                val chain = chainsRepository.getChain(chainId)

                mapAccountToWalletAccount(chain, metaAccount)
            }
    }

    override suspend fun validateSendAddress(chainId: ChainId, address: String): Boolean =
        withContext(Dispatchers.Default) {
            val chain = chainsRepository.getChain(chainId)

            chain.isValidAddress(address)
        }

    override suspend fun isAddressFromPhishingList(address: String): Boolean {
        return walletRepository.isAddressFromPhishingList(address)
    }

    override suspend fun getPhishingInfo(address: String): PhishingModel? = withContext(coroutineContext) {
        return@withContext walletRepository.getPhishingInfo(address)?.toPhishingModel()
    }

    override suspend fun observeTransferFee(
        transfer: Transfer
    ): Flow<BigDecimal> {
        val chain = chainsRepository.getChain(transfer.chainAsset.chainId)

        return walletRepository.observeTransferFee(
            chain = chain,
            transfer = transfer
        )
    }

    override suspend fun performTransfer(
        transfer: Transfer
    ): Result<String> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)
        val accountId = metaAccount.accountId(chain)!!

        return runCatching {
            walletRepository.performTransfer(accountId, chain, transfer)
        }
    }

    override suspend fun getQrCodeSharingSoraString(
        chainId: ChainId,
        assetId: String,
        amount: BigDecimal?
    ): String {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(chainId)
        val asset = chain.assets.firstOrNull { it.id == assetId }

        val address = metaAccount.address(chain)
        val pubKey = metaAccount.accountId(chain)?.toHexString(withPrefix = true)
        val name = metaAccount.name
        val currencyId = asset?.currencyId

        return if (address != null && pubKey != null && currencyId != null) {
            val optionalAmount = if (amount.orZero() > BigDecimal.ZERO) {
                ":$amount"
            } else {
                ""
            }
//            substrate:[user address]:[user public key]:[user name]:[token id]:<amount>
            "$QR_PREFIX_SUBSTRATE:$address:$pubKey:$name:$currencyId$optionalAmount"
        } else {
            address
                ?: throw IllegalArgumentException("There is no address found to getQrCodeSharingSoraString")
        }
    }

    override suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String) = runCatching {
        fileProvider.getFileInExternalCacheStorage(fileName)
    }

    override fun tryReadAddressFromSoraFormat(content: String): String? {
        return try {
            val list = content.split(":")
            list.getOrNull(1)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun tryReadCBDCAddressFormat(content: String): QrContentCBDC? {
        val qrParamValue =
            runCatching { Uri.parse(content).getQueryParameter("qr") }.getOrNull() ?: return null
        val mastercardPushPaymentString = URLDecoder.decode(qrParamValue, "UTF-8")

        val pushPaymentData = Parser.parseWithoutTagValidation(mastercardPushPaymentString)
        val transactionAmount =
            if (pushPaymentData.transactionAmount != null && pushPaymentData.transactionAmount > 0) {
                pushPaymentData.transactionAmount.toBigDecimal()
            } else {
                BigDecimal.ZERO
            }
        return QrContentCBDC(
            transactionAmount = transactionAmount,
            transactionCurrencyCode = pushPaymentData.transactionCurrencyCode,
            description = pushPaymentData.additionalData?.purpose,
            name = pushPaymentData.merchantName,
            billNumber = pushPaymentData.additionalData?.billNumber,
            recipientId = getAccountId(pushPaymentData)
        )
    }

    override fun extractTonAddress(input: String): String? {
        val regex = Regex("ton://transfer/([a-zA-Z0-9_\\-:.]+)")
        return regex.find(input)?.groupValues?.get(1)
    }

    private fun getAccountId(item: PushPaymentData): String {
        for (i in ACCOUNT_ID_MIN_TAG..ACCOUNT_ID_MAX_TAG) {
            item.getMAIData("$i")?.aid?.let {
                return it
            }
        }

        throw IllegalArgumentException("ACCOUNT_ID not found")
    }

    override fun tryReadSoraFormat(content: String): QrContentSora? {
//        substrate:[user address]:[user public key]:[user name]:[token id]:<amount>
        val list = content.split(":")
        return if (list[0] != QR_PREFIX_SUBSTRATE) {
            null
        } else {
            try {
                QrContentSora(
                    address = list[1],
                    publicKey = list[2],
                    userName = list[3],
                    tokenId = list[4],
                    amount = list.getOrNull(5)
                )
            } catch (e: Exception) {
                Log.e("WalletInteractorImpl", "Error read QR content", e)
                null
            }
        }
    }

    private fun mapAccountToWalletAccount(chain: Chain, account: MetaAccount) = with(account) {
        WalletAccount(account.address(chain)!!, name)
    }

    override suspend fun getChain(chainId: ChainId) = chainRegistry.getChain(chainId)

    override suspend fun getSelectedMetaAccount() = accountRepository.getSelectedMetaAccount()

    override suspend fun getChainAddressForSelectedMetaAccount(chainId: ChainId) =
        getSelectedMetaAccount().address(getChain(chainId))

    override suspend fun updateAssetsHiddenState(state: List<AssetBooleanState>) =
        withContext(coroutineContext) {
            val wallet = getSelectedMetaAccount()
            val updateItems = state.mapNotNull {
                val chain = getChain(it.chainId)
                val asset = chain.assetsById[it.assetId]
                val tokenPriceId =
                    asset?.priceProvider?.takeIf { provider -> selectedFiat.isUsd() && provider.isSupported }?.id
                        ?: asset?.priceId
                wallet.accountId(chain)?.let { accountId ->
                    AssetUpdateItem(
                        metaId = wallet.id,
                        chainId = it.chainId,
                        accountId = accountId,
                        id = it.assetId,
                        sortIndex = Int.MAX_VALUE, // Int.MAX_VALUE on sorting because we don't use it anymore - just random value
                        enabled = it.value,
                        tokenPriceId = tokenPriceId
                    )
                }
            }
            walletRepository.updateAssetsHidden(updateItems)
        }

    override suspend fun markAssetAsHidden(chainId: ChainId, chainAssetId: String) {
        updateAssetsHiddenState(listOf(AssetBooleanState(chainId, chainAssetId, false)))
    }

    override suspend fun markAssetAsShown(chainId: ChainId, chainAssetId: String) {
        updateAssetsHiddenState(listOf(AssetBooleanState(chainId, chainAssetId, true)))
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> {
        return accountRepository.selectedMetaAccountFlow()
    }

    override fun polkadotAddressForSelectedAccountFlow(): Flow<String> {
        return selectedMetaAccountFlow().map {
            val chain = chainsRepository.getChain(polkadotChainId)
            it.address(chain) ?: ""
        }
    }

    override fun getChains(): Flow<List<Chain>> = chainsRepository.chainsFlow()

    override fun getOperationAddressWithChainIdFlow(
        chainId: ChainId,
        limit: Int?
    ): Flow<Set<String>> =
        historyRepository.getOperationAddressWithChainIdFlow(chainId, limit)
            .flowOn(coroutineContext)

    override suspend fun getOperationAddressWithChainId(
        chainId: ChainId,
        limit: Int?
    ): Set<String> =
        withContext(coroutineContext) {
            historyRepository.getOperationAddressWithChainId(
                chainId,
                limit
            )
        }

    override suspend fun saveAddress(name: String, address: String, selectedChainId: String) =
        withContext(coroutineContext) {
            addressBookRepository.saveAddress(name, address, selectedChainId)
        }

    override fun observeAddressBook(chainId: ChainId) =
        addressBookRepository.observeAddressBook(chainId)
            .mapList { it.copy(address = it.address.trim()) }
            .flowOn(coroutineContext)

    override fun saveChainId(walletId: Long, chainId: ChainId?) {
        preferences.putString(PREFS_WALLET_SELECTED_CHAIN_ID + walletId, chainId)
    }

    override suspend fun getSavedChainId(walletId: Long): String? {
        val savedChainId = preferences.getString(PREFS_WALLET_SELECTED_CHAIN_ID + walletId)
        val existingChain = savedChainId?.let { runCatching { getChain(it) }.getOrNull() }
        return existingChain?.id
    }

    override suspend fun getEquilibriumAccountInfo(
        asset: CoreAsset,
        accountId: AccountId
    ): EqAccountInfo? =
        walletRepository.getEquilibriumAccountInfo(asset, accountId)

    override suspend fun getEquilibriumAssetRates(chainAsset: CoreAsset): Map<BigInteger, EqOraclePricePoint?> =
        walletRepository.getEquilibriumAssetRates(chainAsset)

    override suspend fun checkClaimSupport(chainId: ChainId): Boolean {
        val metadata = chainRegistry.getRuntimeOrNull(chainId)?.metadata

        return metadata?.moduleOrNull(Modules.VESTING)?.calls?.get("claim") != null
                || metadata?.moduleOrNull(Modules.VESTING)?.calls?.get("vest") != null
                || metadata?.moduleOrNull(Modules.VESTED_REWARDS)?.calls?.get("claim_rewards") != null
    }

    override suspend fun estimateClaimRewardsFee(chainId: ChainId): BigInteger {
        return walletRepository.estimateClaimRewardsFee(chainId)
    }

    override suspend fun getVestingLockedAmount(chainId: ChainId): BigInteger? {
        return walletRepository.getVestingLockedAmount(chainId)
    }

    override suspend fun claimRewards(chainId: ChainId): Result<String> {
        val currentAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainsRepository.getChain(chainId)
        val accountId: AccountId = currentAccount.accountId(chain)
            ?: throw IllegalArgumentException("Error retrieving accountId for chain ${chain.name}")
        return walletRepository.claimRewards(chain, accountId)
    }


    override suspend fun checkControllerDeprecations(): List<ControllerDeprecationWarning> {
        val currentAccount = accountRepository.getSelectedMetaAccount()
        val chains = chainsRepository.getChainsById()
        val allRelayChainStakingAssets = chains.values
            .map { it.assets }
            .flatten()
            .asSequence()
            .filter { it.staking == StakingType.RELAYCHAIN }
            .toList()

        val relayStakingChains = allRelayChainStakingAssets.map { it.chainId }

        val chainsWithDeprecatedControllerAccount = relayStakingChains.filter {
            chainRegistry.getRuntimeOrNull(it)?.metadata?.moduleOrNull(Modules.STAKING)?.calls?.get(
                "set_controller"
            )?.arguments?.isEmpty() == true
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

    override suspend fun saveChainSelectFilter(walletId: Long, filter: String) {
        val key = getChainSelectFilterAppliedKey(walletId)
        preferences.putString(key, filter)
    }

    override suspend fun saveAssetManagementIntroPassed() {
        preferences.putBoolean(ASSET_MANAGEMENT_INTRO_PASSED_KEY, true)
    }

    override fun getAssetManagementIntroPassed(): Boolean {
        return preferences.getBoolean(ASSET_MANAGEMENT_INTRO_PASSED_KEY, defaultValue = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSelectedAccountChainSelectFilter(): Flow<ChainSelectorViewStateWithFilters.Filter> {
        return accountRepository.selectedMetaAccountFlow().map {
            it.id
        }.distinctUntilChanged().flatMapLatest {
            val key = getChainSelectFilterAppliedKey(it)

            preferences.stringFlow(key) {
                // emit empty string on start as indication that no filter is used
                // as opposed to null which was thrown for random reasons
                return@stringFlow ""
            }.filterNotNull()
                .map { stringValue ->
                    ChainSelectorViewStateWithFilters.Filter.entries.find { entry ->
                        entry.name == stringValue
                    } ?: ChainSelectorViewStateWithFilters.Filter.All
                }
        }
    }

    private fun getChainSelectFilterAppliedKey(walletId: Long): String {
        return "${CHAIN_SELECT_FILTER_APPLIED}_$walletId"
    }

    override fun observeChainsPerAsset(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<Chain, Asset?>> {
        return walletRepository.observeChainsPerAsset(accountMetaId, assetId)
    }

    override fun observeCurrentAccountChainsPerAsset(
        assetId: String
    ): Flow<Map<Chain, Asset?>> {
        return accountRepository.selectedMetaAccountFlow().flatMapLatest {
            walletRepository.observeChainsPerAsset(it.id, assetId)
        }
    }

    override fun applyAssetSorting(sorting: AssetSorting) {
        preferences.putString(ASSET_SORTING_KEY, sorting.name)
    }

    override fun observeAssetSorting(): Flow<AssetSorting> {
        return preferences.stringFlow(ASSET_SORTING_KEY) {
            AssetSorting.FiatBalance.toString()
        }.map { sortingAsString ->
            sortingAsString?.let {
                AssetSorting.entries.find { sorting -> sorting.name == it }
            } ?: AssetSorting.FiatBalance
        }
    }

    override fun networkIssuesFlow(): Flow<Map<ChainId, NetworkIssueType>> {
        return networkStateService.networkIssuesFlow
    }

    override suspend fun retryChainSync(chainId: ChainId): Result<Unit> {
        return withContext(coroutineContext) {
            val chain = chainsRepository.getChain(chainId)
            chainRegistry.setupChain(chain)
            val runtime = withTimeoutOrNull(15_000L) {
                chainRegistry.awaitRuntimeProvider(chainId).get()
            }
            BalanceUpdateTrigger.invoke(chainId)
            if (runtime == null) {
                return@withContext Result.failure(Exception("Failed to sync chain"))
            } else {
                return@withContext Result.success(Unit)
            }
        }
    }

    override suspend fun getToken(chainAsset: jp.co.soramitsu.core.models.Asset) =
        withContext(coroutineContext) {
            tokenRepository.getToken(chainAsset)
        }

    override suspend fun getExportSourceTypes(chainId: ChainId, walletId: Long?): MutableSet<ExportSource> {
        val accountId = walletId ?: accountRepository.getSelectedLightMetaAccount().id
        val chainEcosystem = chainsRepository.getChain(chainId).ecosystem

        val options = mutableSetOf<ExportSource>()
        when (chainEcosystem) {
            Ecosystem.Substrate -> {
                accountRepository.getSubstrateSecrets(accountId)?.let {
                    it[SubstrateSecrets.Entropy]?.run {
                        options += ExportSource.Mnemonic
                        options += ExportSource.Seed
                        options += ExportSource.Json
                    }
                    it[SubstrateSecrets.Seed]?.run {
                        options += ExportSource.Seed
                        options += ExportSource.Json
                    }
                }
            }

            Ecosystem.EthereumBased,
            Ecosystem.Ethereum -> {
                accountRepository.getEthereumSecrets(accountId)?.let {
                    it[EthereumSecrets.Entropy]?.run {
                        options += ExportSource.Mnemonic
                        options += ExportSource.Seed
                        options += ExportSource.Json
                    }
                    it[EthereumSecrets.Seed]?.run {
                        options += ExportSource.Seed
                        options += ExportSource.Json
                    }
                }
            }

            Ecosystem.Ton -> {
                accountRepository.getTonSecrets(accountId)?.let {
                    options += ExportSource.Mnemonic
                }
            }
        }

        return options.toSortedSet(compareBy { it.sort })
    }

    override fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount> {
        return accountRepository.selectedMetaAccountFlow()
    }
}