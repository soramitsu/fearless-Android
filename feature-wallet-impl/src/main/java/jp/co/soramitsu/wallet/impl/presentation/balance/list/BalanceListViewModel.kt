package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.FiatCurrencies
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.moreThanZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.common.domain.KycRepository
import jp.co.soramitsu.runtime.ext.ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getWithToken
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.addressByteOrNull
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.AssetUpdateState
import jp.co.soramitsu.wallet.impl.presentation.model.AssetWithStateModel
import jp.co.soramitsu.wallet.impl.presentation.model.ControllerDeprecationWarningModel
import jp.co.soramitsu.wallet.impl.presentation.model.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import jp.co.soramitsu.oauth.R as SoraCardR

private const val CURRENT_ICON_SIZE = 40

@HiltViewModel
class BalanceListViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val soraCardInteractor: SoraCardInteractor,
    private val chainInteractor: ChainInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val accountRepository: AccountRepository,
    private val updatesMixin: UpdatesMixin,
    private val networkStateMixin: NetworkStateMixin,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val kycRepository: KycRepository
) : BaseViewModel(), UpdatesProviderUi by updatesMixin, NetworkStateUi by networkStateMixin, WalletScreenInterface {

    private val accountAddressToChainIdMap = mutableMapOf<String, ChainId?>()

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val _launchSoraCardSignIn = MutableLiveData<Event<SoraCardContractData>>()
    val launchSoraCardSignIn: LiveData<Event<SoraCardContractData>> = _launchSoraCardSignIn

    private val rawAssets = interactor.assetsFlow()

    private val assetModelsFlow: Flow<List<AssetModel>> = rawAssets
        .mapList {
            when {
                it.hasAccount -> it.asset
                else -> null
            }
        }
        .map { it.filterNotNull() }
        .mapList { mapAssetToAssetModel(it) }
        .inBackground()

    private val fiatSymbolFlow =
        kotlinx.coroutines.flow.combine(selectedFiat.flow(), getAvailableFiatCurrencies.flow()) { selectedFiat: String, fiatCurrencies: FiatCurrencies ->
            fiatCurrencies.associateBy { it.id }[selectedFiat]?.symbol
        }.onEach {
            sync()
        }

    private val chainsFlow = chainInteractor.getChainsFlow().mapList {
        it.toChainItemState()
    }.inBackground()
    private val selectedChainId = MutableStateFlow<ChainId?>(null)
    private val selectedChainItemFlow = kotlinx.coroutines.flow.combine(selectedChainId, chainsFlow) { selectedChainId, chains ->
        selectedChainId?.let {
            chains.firstOrNull { it.id == selectedChainId }
        }
    }

    private val currentMetaAccountFlow = accountRepository.selectedMetaAccountFlow()

    private val balanceFlow = combine(
        assetModelsFlow,
        fiatSymbolFlow,
        tokenRatesUpdate,
        assetsUpdate,
        chainsUpdate,
        currentMetaAccountFlow
    ) { assetModels: List<AssetModel>?,
        fiatSymbol: String?,
        tokenRatesUpdate: Set<String>?,
        assetsUpdate: Set<AssetKey>?,
        chainsUpdate: Set<String>?,
        currentMetaAccount: MetaAccount ->
        val assetsWithState = assetModels?.map { asset ->
            val rateUpdate = tokenRatesUpdate?.let { asset.token.configuration.id in it }
            val balanceUpdate = assetsUpdate?.let { asset.primaryKey in it }
            val chainUpdate = chainsUpdate?.let { asset.token.configuration.chainId in it }
            val isTokenFiatChanged = when {
                fiatSymbol == null -> false
                asset.token.fiatSymbol == null -> false
                else -> fiatSymbol != asset.token.fiatSymbol
            }

            AssetWithStateModel(
                asset = asset,
                state = AssetUpdateState(rateUpdate, balanceUpdate, chainUpdate, isTokenFiatChanged)
            )
        }.orEmpty()

        BalanceModel(assetsWithState, fiatSymbol.orEmpty())
    }.onStart { emit(BalanceModel(emptyList(), "")) }.inBackground().share()

    private val assetTypeSelectorState = MutableStateFlow(
        MultiToggleButtonState(
            currentSelection = AssetType.Currencies,
            toggleStates = AssetType.values().toList()
        )
    )

    @OptIn(FlowPreview::class)
    private val assetStates = kotlinx.coroutines.flow.combine(
        interactor.assetsFlow().debounce(100L),
        chainInteractor.getChainsFlow(),
        selectedChainId,
        networkIssuesFlow,
        interactor.observeHideZeroBalanceEnabledForCurrentWallet()
    ) { assets: List<AssetWithStatus>, chains: List<Chain>, selectedChainId: ChainId?, networkIssues: Set<NetworkIssueItemState>, hideZeroBalancesEnabled ->
        val balanceListItems = mutableListOf<BalanceListItemModel>()

        chains.groupBy { if (it.isTestNet) ChainEcosystem.STANDALONE else it.ecosystem() }.forEach { (ecosystem, ecosystemChains) ->
            when (ecosystem) {
                ChainEcosystem.POLKADOT,
                ChainEcosystem.KUSAMA -> {
                    val ecosystemAssets = assets.filter {
                        it.asset.token.configuration.chainId in ecosystemChains.map { it.id }
                    }

                    val filtered = ecosystemAssets
                        .filter { selectedChainId == null || selectedChainId == it.asset.token.configuration.chainId }

                    val items = processAssets(filtered, ecosystemChains, selectedChainId, networkIssues, hideZeroBalancesEnabled, ecosystem)
                    balanceListItems.addAll(items)
                }

                ChainEcosystem.STANDALONE -> {
                    ecosystemChains.forEach { chain ->
                        if (selectedChainId == null || selectedChainId == chain.id) {
                            val chainAssets = assets.filter { it.asset.token.configuration.chainId == chain.id }
                            val items = processAssets(chainAssets, listOf(chain), selectedChainId, networkIssues, hideZeroBalancesEnabled, ecosystem)
                            balanceListItems.addAll(items)
                        }
                    }
                }
            }
        }

        val assetStates: List<AssetListItemViewState> = balanceListItems
            .sortedWith(defaultBalanceListItemSort())
            .map { it.toAssetState() }

        assetStates
    }.onStart { emit(buildInitialAssetsList().toMutableList()) }.inBackground().share()

    init {
        observeNetworkState()
    }

    private fun observeNetworkState() {
        networkStateMixin.showConnectingBarFlow
            .onEach { hasConnectionProblems ->
                if (!hasConnectionProblems) {
                    refresh()
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun processAssets(
        ecosystemAssets: List<AssetWithStatus>,
        ecosystemChains: List<Chain>,
        selectedChainId: ChainId?,
        networkIssues: Set<NetworkIssueItemState>,
        hideZeroBalancesEnabled: Boolean,
        ecosystem: ChainEcosystem
    ): List<BalanceListItemModel> {
        val result = mutableListOf<BalanceListItemModel>()
        val metaAccount = accountRepository.getSelectedMetaAccount()
        ecosystemAssets.groupBy { it.asset.token.configuration.symbol }.forEach { (symbol, symbolAssets) ->
            val tokenChains = ecosystemChains.getWithToken(symbol)
            if (tokenChains.isEmpty()) return@forEach

            val mainChain = tokenChains.sortedWith(
                compareByDescending<Chain> {
                    it.assets.firstOrNull { it.symbol == symbol }?.isUtility ?: false
                }.thenByDescending { it.parentId == null }
            ).firstOrNull()

            val showChain = tokenChains.firstOrNull { it.id == selectedChainId } ?: mainChain
            val showChainAsset = showChain?.assets?.firstOrNull { it.symbol == symbol } ?: return@forEach

            val hasNetworkIssue = networkIssues.any { issueItemState ->
                val chainAssets = symbolAssets
                    .filter {
                        it.asset.token.configuration.chainId == issueItemState.chainId &&
                            it.asset.token.configuration.id == issueItemState.assetId &&
                            it.asset.metaId == metaAccount.id
                    }

                (issueItemState.chainId in tokenChains.map { it.id } || chainAssets.any { !it.hasAccount }) &&
                    chainAssets.any { !it.asset.markedNotNeed && it.asset.total.moreThanZero() }
            }

            val hasChainWithoutAccount = symbolAssets.any { it.hasAccount.not() }

            val assetIdsWithBalance = symbolAssets.filter {
                it.asset.total.orZero() > BigDecimal.ZERO
            }.groupBy(
                keySelector = { it.asset.token.configuration.chainId },
                valueTransform = { it.asset.token.configuration.id }
            )

            val assetChainUrls = if (selectedChainId == null) {
                ecosystemChains.getWithToken(symbol, assetIdsWithBalance)
                    .ifEmpty { listOf(showChain) }
                    .associate { it.id to it.icon }
            } else {
                emptyMap()
            }

            val assetTransferable = symbolAssets.sumByBigDecimal { it.asset.transferable }
            val assetTotal = symbolAssets.sumByBigDecimal { it.asset.total.orZero() }
            val assetTotalFiat = symbolAssets.sumByBigDecimal { it.asset.fiatAmount.orZero() }

            val isZeroBalance = assetTotal.isZero()

            val assetDisabledByUser = symbolAssets.any { it.asset.enabled == false }
            val assetManagedByUser = symbolAssets.any { it.asset.enabled != null }

            val isHidden = assetDisabledByUser || (!assetManagedByUser && isZeroBalance && hideZeroBalancesEnabled)

            val token = symbolAssets.first().asset.token

            val model = BalanceListItemModel(
                asset = showChainAsset,
                chain = showChain,
                token = token,
                total = assetTotal,
                fiatAmount = assetTotalFiat,
                transferable = assetTransferable,
                chainUrls = assetChainUrls,
                isHidden = isHidden,
                hasChainWithoutAccount = hasChainWithoutAccount,
                hasNetworkIssue = hasNetworkIssue,
                ecosystem = ecosystem
            )
            result.add(model)
        }
        return result
    }

    // we open screen - no assets in the list
    private suspend fun buildInitialAssetsList(): List<AssetListItemViewState> {
        return withContext(Dispatchers.Default) {
            val assets = chainInteractor.getRawChainAssets()

            assets.sortedWith(defaultChainAssetListSort()).map { chainAsset ->
                AssetListItemViewState(
                    assetIconUrl = chainAsset.iconUrl,
                    assetChainName = chainAsset.chainName,
                    assetName = chainAsset.name.orEmpty(),
                    assetSymbol = chainAsset.symbol,
                    assetTokenFiat = null,
                    assetTokenRate = null,
                    assetTransferableBalance = null,
                    assetTransferableBalanceFiat = null,
                    assetChainUrls = emptyMap(),
                    chainId = chainAsset.chainId,
                    chainAssetId = chainAsset.id,
                    isSupported = true,
                    isHidden = false,
                    hasAccount = true,
                    priceId = chainAsset.priceId,
                    hasNetworkIssue = false,
                    ecosystem = ChainEcosystem.POLKADOT.name,
                    isTestnet = chainAsset.isTestNet ?: false
                )
            }.filter { selectedChainId.value == null || selectedChainId.value == it.chainId }
        }
    }

    private fun defaultBalanceListItemSort() = compareByDescending<BalanceListItemModel> { it.total > BigDecimal.ZERO }
        .thenByDescending { it.fiatAmount.orZero() }
        .thenBy { it.asset.isTestNet }
        .thenBy { it.asset.chainId.defaultChainSort() }
        .thenBy { it.asset.chainName }

    private fun defaultChainAssetListSort() = compareBy<Asset> { it.isTestNet }
        .thenBy { it.chainId.defaultChainSort() }
        .thenBy { it.chainName }

//    private val soraCardState = combine(
//        interactor.observeIsShowSoraCard(),
//        soraCardInteractor.subscribeSoraCardInfo()
//    ) { isShow, soraCardInfo ->
//        val kycStatus = soraCardInfo?.kycStatus?.let(::mapKycStatus)
//        SoraCardItemViewState(kycStatus, soraCardInfo, null, isShow)
//    }
    private val soraCardState = flowOf(SoraCardItemViewState())

    val state = combine(
        assetStates,
        assetTypeSelectorState,
        balanceFlow,
        selectedChainId,
        soraCardState,
        currentMetaAccountFlow
    ) { assetsListItemStates: List<AssetListItemViewState>,
        multiToggleButtonState: MultiToggleButtonState<AssetType>,
        balanceModel: BalanceModel,
        selectedChainId: ChainId?,
        soraCardState: SoraCardItemViewState,
        currentMetaAccount: MetaAccount ->

        val selectedChainAddress = selectedChainId?.let {
            currentAccountAddress(chainId = it)
        }.orEmpty()

        val balanceState = AssetBalanceViewState(
            transferableBalance = balanceModel.totalTransferableBalance?.formatFiat(balanceModel.fiatSymbol).orEmpty(),
            address = selectedChainAddress,
            changeViewState = ChangeBalanceViewState(
                percentChange = balanceModel.transferableRate?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.totalTransferableBalanceChange.abs().formatFiat(balanceModel.fiatSymbol)
            )
        )

        val hasNetworkIssues = assetsListItemStates.any { it.hasNetworkIssue }
        println("!!! state balance list: wallet: ${currentMetaAccount.id}: ${currentMetaAccount.name}, isBackedUp = ${currentMetaAccount.isBackedUp}")
        WalletState(
            assets = assetsListItemStates,
            multiToggleButtonState = multiToggleButtonState,
            balance = balanceState,
            hasNetworkIssues = hasNetworkIssues,
            soraCardState = soraCardState,
            isBackedUp = currentMetaAccount.isBackedUp
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = WalletState.default)

    val toolbarState = kotlinx.coroutines.flow.combine(currentAddressModelFlow(), selectedChainItemFlow) { addressModel, chain ->
        LoadingState.Loaded(
            MainToolbarViewState(
                title = addressModel.nameOrAddress,
                homeIconState = ToolbarHomeIconState(walletIcon = addressModel.image),
                selectorViewState = ChainSelectorViewState(chain?.title, chain?.id)
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    init {
        updateSoraCardStatus()

        router.chainSelectorPayloadFlow.map { chainId ->
            val walletId = interactor.getSelectedMetaAccount().id
            interactor.saveChainId(walletId, chainId)
            selectedChainId.value = chainId
        }.launchIn(this)

        interactor.selectedMetaAccountFlow().map { wallet ->
            selectedChainId.value = interactor.getSavedChainId(wallet.id)
        }.launchIn(this)

        if (!interactor.isShowGetSoraCard()) {
            interactor.decreaseSoraCardHiddenSessions()
        }
    }

    override fun onRefresh() {
        refresh()
    }

    private fun refresh() {
        updateSoraCardStatus()
        sync()
    }

    fun onResume() {
        updateSoraCardStatus()
        viewModelScope.launch {
            interactor.selectedMetaAccountFlow().onEach {
                checkControllerDeprecations()
            }
            checkControllerDeprecations()
        }
    }

    private suspend fun checkControllerDeprecations() {
        val warnings = withContext(Dispatchers.Default) { interactor.checkControllerDeprecations() }
        warnings.firstOrNull()?.let { warning ->
            val model = warning.toModel(resourceManager)
            showError(
                title = model.title,
                message = model.message,
                positiveButtonText = model.buttonText,
                negativeButtonText = null,
                positiveClick = {
                    when (model.action) {
                        ControllerDeprecationWarningModel.Action.ChangeController -> {
                            router.openManageControllerAccount(model.chainId)
                        }

                        ControllerDeprecationWarningModel.Action.ImportStash -> {
                            router.openImportAccountScreenFromWallet(0)
                        }
                    }
                }
            )
        }
    }

    private fun updateSoraCardStatus() {
        viewModelScope.launch {
            val soraCardInfo = soraCardInteractor.getSoraCardInfo() ?: return@launch
            val accessTokenExpirationTime = soraCardInfo.accessTokenExpirationTime
            val accessTokenExpired = accessTokenExpirationTime < TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

            if (!accessTokenExpired) {
                kycRepository.getKycLastFinalStatus(soraCardInfo.accessToken).onSuccess { kycStatus ->
                    soraCardInteractor.updateSoraCardKycStatus(kycStatus = kycStatus?.toString().orEmpty())
                }
            }
        }
    }

    private fun sync() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                getAvailableFiatCurrencies.sync()
                interactor.syncAssetsRates()
            }

            result.exceptionOrNull()?.let(::showError)
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {
        val payload = AssetPayload(chainId, chainAssetId)
        launch {
            swipeableState.snapTo(SwipeState.INITIAL)
        }
        when (actionType) {
            ActionItemType.SEND -> {
                sendClicked(payload)
            }

            ActionItemType.RECEIVE -> {
                receiveClicked(payload)
            }

            ActionItemType.TELEPORT -> {
                showMessage("YOU NEED THE BLUE KEY")
            }

            ActionItemType.HIDE -> {
                launch { hideAsset(chainId, chainAssetId) }
            }

            ActionItemType.SHOW -> {
                launch { showAsset(chainId, chainAssetId) }
            }

            else -> {}
        }
    }

    private suspend fun hideAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsHidden(chainId, chainAssetId)
    }

    private suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openSend(assetPayload)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    override fun assetClicked(asset: AssetListItemViewState) {
        if (asset.hasNetworkIssue) {
            launch {
                val chain = interactor.getChain(asset.chainId)
                if (chain.nodes.size > 1) {
                    router.openNodes(asset.chainId)
                } else {
                    val payload = AlertViewState(
                        title = resourceManager.getString(R.string.staking_main_network_title, chain.name),
                        message = resourceManager.getString(R.string.network_issue_unavailable),
                        buttonText = resourceManager.getString(R.string.top_up),
                        iconRes = R.drawable.ic_alert_16
                    )
                    router.openAlert(payload)
                }
            }
            return
        }
        if (!asset.hasAccount) {
            launch {
                val meta = accountRepository.getSelectedMetaAccount()
                val payload = AddAccountBottomSheet.Payload(
                    metaId = meta.id,
                    chainId = asset.chainId,
                    chainName = asset.assetChainName,
                    assetId = asset.chainAssetId,
                    priceId = asset.priceId,
                    markedAsNotNeed = false
                )
                router.openOptionsAddAccount(payload)
            }
            return
        }
        if (asset.isSupported.not()) {
            _showUnsupportedChainAlert.value = Event(Unit)
            return
        }

        val payload = AssetPayload(
            chainId = asset.chainId,
            chainAssetId = asset.chainAssetId
        )

        router.openAssetDetails(payload)
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow(polkadotChainId)
            .catch { emit(WalletAccount("", "")) }
            .onEach {
                if (accountAddressToChainIdMap.containsKey(it.address).not()) {
                    selectedChainId.value = null
                    accountAddressToChainIdMap[it.address] = null
                } else {
                    selectedChainId.value = accountAddressToChainIdMap.getOrDefault(it.address, null)
                }
            }
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    override fun onBalanceClicked() {
        viewModelScope.launch {
            val currencies = getAvailableFiatCurrencies()
            if (currencies.isEmpty()) return@launch
            val selected = selectedFiat.get()
            val selectedItem = currencies.first { it.id == selected }
            _showFiatChooser.value = FiatChooserEvent(DynamicListBottomSheet.Payload(currencies, selectedItem))
        }
    }

    override fun onNetworkIssuesClicked() {
        router.openNetworkIssues()
    }

    override fun onBackupClicked() {
        launch {
            val selectedMetaAccount = accountRepository.getSelectedMetaAccount()
            router.openBackupWalletScreen(selectedMetaAccount.id)
        }
    }

    override fun onAddressClick() {
        launch {
            selectedChainId.value?.let {
                currentAccountAddress(chainId = it)
            }?.let { address ->
                copyToClipboard(address)
            }
        }
    }

    override fun soraCardClicked() {
        if (state.value.soraCardState?.kycStatus == null) {
            router.openGetSoraCard()
        } else {
            onSoraCardStatusClicked()
        }
    }

    override fun soraCardClose() {
        interactor.hideSoraCard()
    }

    fun onFiatSelected(item: FiatCurrency) {
        viewModelScope.launch {
            selectedFiat.set(item.id)
        }
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    override fun assetTypeChanged(type: AssetType) {
        assetTypeSelectorState.value = assetTypeSelectorState.value.copy(currentSelection = type)
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            val result = interactor.tryReadAddressFromSoraFormat(content) ?: content
            val qrTokenId = interactor.tryReadTokenIdFromSoraFormat(content)
            val payloadFromQr = qrTokenId?.let {
                val addressChains = interactor.getChains().first()
                    .filter { it.addressPrefix.toShort() == result.addressByteOrNull() }
                    .filter { it.assets.any { it.currencyId == qrTokenId } }
                if (addressChains.size == 1) {
                    val chain = addressChains[0]
                    val soraAsset = chain.assets.firstOrNull {
                        it.currencyId == qrTokenId
                    }

                    soraAsset?.let {
                        AssetPayload(it.chainId, it.id)
                    }
                } else {
                    null
                }
            }
            router.openSend(assetPayload = payloadFromQr, initialSendToAddress = result, currencyId = qrTokenId)
        }
    }

    fun openWalletSelector() {
        router.openSelectWallet()
    }

    fun openSearchAssets() {
        router.openSearchAssets()
    }

    fun openSelectChain() {
        router.openSelectChain(selectedChainId.value)
    }

    private fun copyToClipboard(text: String) {
        clipboardManager.addToClipboard(text)

        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    private fun mapKycStatus(kycStatus: String): String? {
        return when (runCatching { SoraCardCommonVerification.valueOf(kycStatus) }.getOrNull()) {
            SoraCardCommonVerification.Pending -> {
                resourceManager.getString(SoraCardR.string.kyc_result_verification_in_progress)
            }

            SoraCardCommonVerification.Successful -> {
                resourceManager.getString(R.string.sora_card_verification_successful)
            }

            SoraCardCommonVerification.Rejected -> {
                resourceManager.getString(SoraCardR.string.verification_rejected_title)
            }

            SoraCardCommonVerification.Failed -> {
                resourceManager.getString(SoraCardR.string.verification_failed_title)
            }

            else -> {
                null
            }
        }
    }

    private fun onSoraCardStatusClicked() {
        _launchSoraCardSignIn.value = Event(
            SoraCardContractData(
                locale = Locale.ENGLISH,
                apiKey = BuildConfig.SORA_CARD_API_KEY,
                domain = BuildConfig.SORA_CARD_DOMAIN,
                environment = when {
                    BuildConfig.DEBUG -> SoraCardEnvironmentType.TEST
                    else -> SoraCardEnvironmentType.PRODUCTION
                },
                kycCredentials = SoraCardKycCredentials(
                    endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
                    username = BuildConfig.SORA_CARD_KYC_USERNAME,
                    password = BuildConfig.SORA_CARD_KYC_PASSWORD
                ),
                client = OptionsProvider.header,
                userAvailableXorAmount = 0.0, // userAvailableXorAmount,
                areAttemptsPaidSuccessfully = false, // will be available in Phase 2
                isEnoughXorAvailable = false, // isEnoughXorAvailable,
                isIssuancePaid = false // will be available in Phase 2
            )
        )
    }

    fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        launch {
            soraCardInteractor.updateSoraCardInfo(
                accessToken,
                refreshToken,
                accessTokenExpirationTime,
                kycStatus
            )
        }
    }
}
