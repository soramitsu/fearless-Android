package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AssetBalanceUseCase
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.presentation.actions.AddAccountPayload
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.AssetDetailsFragment.Companion.KEY_ASSET_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetDetailsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val getAssetBalance: AssetBalanceUseCase,
    private val walletRouter: WalletRouter,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val assetNotNeedAccount: AssetNotNeedAccountUseCase,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel(), AssetDetailsCallback {

    companion object {
        private const val KEY_ALERT_RESULT = "notNeedAlertResult"
    }

    private var lastSelectedChainIdWithNetworkIssue: ChainId? = null

    private val tabSelectionFlow = MutableStateFlow(AssetDetailsState.Tab.AvailableChains)

    private val assetIdFlow = with(savedStateHandle) {
        MutableStateFlow(
            value = get<String>(KEY_ASSET_ID) ?: error("No asset specified")
        )
    }

    private val cachedSelectedMetaAccount = interactor.selectedMetaAccountFlow()
        .flowOn(Dispatchers.IO).share()

    private val cachedPerChainBalanceWithAssetFlow: SharedFlow<Map<Chain, AssetWithStatus?>> =
        combine(cachedSelectedMetaAccount, assetIdFlow) { selectedMetaAccount, assetId ->
            selectedMetaAccount.id to assetId
        }.flatMapLatest { (selectedMetaAccountId, assetId) ->
            interactor.observeChainsPerAsset(selectedMetaAccountId, assetId)
                .also { chainsPerAssetFlow ->
                    val chainSelection =
                        interactor.getSavedChainId(walletId = selectedMetaAccountId)
                    val chainsWithAsset = chainsPerAssetFlow.first().toList()

                    openBalanceDetailsForSelectedOrSingleChain(
                        chainSelection,
                        chainsWithAsset,
                        assetId
                    )
                }.combine(interactor.assetsFlow()) { resultMap, assetsWithStatus ->
                    val resultWithStatuses = resultMap.mapValues { resultEntry ->
                        resultEntry.value?.let { resultAsset ->
                            assetsWithStatus.firstOrNull {
                                resultEntry.key.id == it.asset.token.configuration.chainId &&
                                        resultAsset.token.configuration.id == it.asset.token.configuration.id
                            }
                        }
                    }
                    resultWithStatuses
                }
        }.flowOn(Dispatchers.IO).share()

    private fun openBalanceDetailsForSelectedOrSingleChain(
        selectedChainId: ChainId?,
        chainsWithAsset: List<Pair<Chain, Asset?>>,
        assetId: String
    ) {
        launch {
            val singleChainId = when {
                selectedChainId != null && selectedChainId in chainsWithAsset.map { it.first.id } -> selectedChainId
                chainsWithAsset.size == 1 -> chainsWithAsset.first().first.id
                else -> null
            }

            singleChainId?.let {
                val payload = AssetPayload(
                    chainId = singleChainId,
                    chainAssetId = assetId
                )
                walletRouter.openAssetDetailsAndPopUpToBalancesList(payload)
            }
        }
    }

    val toolbarState: StateFlow<LoadingState<MainToolbarViewState>> = createToolbarState()

    private fun createToolbarState(): StateFlow<LoadingState<MainToolbarViewState>> {
        return cachedSelectedMetaAccount.map { selectedMetaAccount ->
            LoadingState.Loaded(
                MainToolbarViewState(
                    title = selectedMetaAccount.name,
                    homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_arrow_back_24dp),
                    selectorViewState = ChainSelectorViewState()
                )
            )
        }.stateIn(
            this,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            LoadingState.Loading()
        )
    }

    val contentState = MutableStateFlow(AssetDetailsState.empty)

    private fun subscribeBalance() {
        combine(cachedSelectedMetaAccount, assetIdFlow) { selectedMetaAccount, assetId ->
            selectedMetaAccount.id to assetId
        }.flatMapLatest { (selectedAccountMetaId, assetId) ->
            getAssetBalance.observe(selectedAccountMetaId, assetId)
        }.onEach { assetBalance ->
            val assetBalanceState =
                AssetBalanceViewState(
                    transferableBalance = assetBalance.assetBalance.formatCrypto(assetBalance.assetSymbol),
                    changeViewState = ChangeBalanceViewState(
                        percentChange = assetBalance.rateChange?.formatAsChange().orEmpty(),
                        fiatChange = assetBalance.fiatBalance.formatFiat(assetBalance.fiatSymbol)
                    ),
                    address = "" // implies invisible address state
                )

            contentState.update { prevState ->
                prevState.copy(balanceState = LoadingState.Loaded(assetBalanceState))
            }
        }
            .launchIn(viewModelScope)
    }

    private fun subscribeTabSelection() {
        tabSelectionFlow.onEach { selectedTab ->
            contentState.update { prevState ->
                prevState.copy(
                    tabState = MultiToggleButtonState(
                        currentSelection = selectedTab,
                        toggleStates = AssetDetailsState.Tab.entries
                    )
                )
            }
        }.launchIn(viewModelScope)
    }

    private val networkIssuesFlow = interactor.networkIssuesFlow().map { issuesMap ->
        issuesMap.mapValues {
            when(it.value) {
                jp.co.soramitsu.common.domain.model.NetworkIssueType.Node -> NetworkIssueType.Node
                jp.co.soramitsu.common.domain.model.NetworkIssueType.Network -> NetworkIssueType.Network
                jp.co.soramitsu.common.domain.model.NetworkIssueType.Account -> NetworkIssueType.Account
            }
        }
    }

    private fun subscribeAssets() {
        val assetSortingFlow = interactor.observeAssetSorting()
        cachedPerChainBalanceWithAssetFlow
            .combine(tabSelectionFlow) { values, tabSelection ->
                when (tabSelection) {
                    AssetDetailsState.Tab.AvailableChains -> values

                    AssetDetailsState.Tab.MyChains -> values.filter {
                        val totalBalance = it.value?.asset?.total ?: return@filter false
                        return@filter totalBalance > BigDecimal.ZERO
                    }
                }
            }.combine(assetSortingFlow) { values, sorting ->
                val valuesAsList = values.toList()

                when (sorting) {
                    AssetSorting.FiatBalance ->
                        valuesAsList.sortedByDescending { (_, asset) -> asset?.asset?.transferable }

                    AssetSorting.Name ->
                        valuesAsList.sortedBy { (chain, _) -> chain.name }

                    AssetSorting.Popularity ->
                        valuesAsList.sortedByDescending { (chain, _) -> chain.rank }
                } to sorting
            }.combine(networkIssuesFlow) { (chainsPerAssetToAsset, sorting), networkIssues ->
                chainsPerAssetToAsset.filter {
                    it.second?.asset?.markedNotNeed != true
                }.map { (chain, asset) ->
                    val transferableBalance = asset?.asset?.transferable
                    val transferableFiatBalance =
                        transferableBalance?.applyFiatRate(asset.asset.token.fiatRate)

                    val networkIssueType = if (asset?.hasAccount == false) {
                        NetworkIssueType.Account
                    } else {
                        networkIssues[chain.id]
                    }

                    AssetDetailsItemViewState(
                        assetId = asset?.asset?.token?.configuration?.id,
                        chainId = chain.id,
                        iconUrl = chain.icon,
                        chainName = chain.name,
                        assetRepresentation = transferableBalance?.formatCrypto(asset.asset.token.configuration.symbol),
                        fiatRepresentation = transferableFiatBalance?.formatFiat(asset.asset.token.fiatSymbol),
                        networkIssueType = networkIssueType
                    )
                } to sorting
            }
            .onEach { (chainsStates, sorting) ->
                contentState.update { prevState ->
                    prevState.copy(
                        assetSorting = sorting,
                        items = chainsStates
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    init {
        subscribeBalance()
        subscribeTabSelection()
        subscribeAssets()

        walletRouter.chainSelectorPayloadFlow.flatMapLatest { chainId ->
            walletRouter.trackReturnToAssetDetailsFromChainSelector()?.onEach {
                if (chainId == null)
                    return@onEach

                val assetId =
                    cachedPerChainBalanceWithAssetFlow.replayCache.firstOrNull()?.mapKeys {
                        it.key.id
                    }?.get(chainId)?.asset?.token?.configuration?.id ?: return@onEach

                walletRouter.openAssetDetails(
                    AssetPayload(
                        chainId = chainId,
                        chainAssetId = assetId
                    )
                )
            } ?: flow { /* DO NOTHING */ }
        }.flowOn(Dispatchers.Main.immediate).share()

        walletRouter.listenAlertResultFlowFromNetworkIssuesScreen(KEY_ALERT_RESULT)
            .onEach { onAlertResult(it) }
            .launchIn(viewModelScope)
    }

    override fun onNavigationBack() {
        walletRouter.back()
    }

    override fun onSelectChainClick() {
        walletRouter.openSelectChain(
            assetId = assetIdFlow.value,
            showAllChains = true,
        )
    }

    override fun onChainTabClick(tab: AssetDetailsState.Tab) {
        tabSelectionFlow.value = tab
    }

    override fun onSortChainsClick() {
        walletRouter.openAssetIntermediateDetailsSort()
    }

    override fun onChainClick(itemState: AssetDetailsState.ItemState) {
        when (itemState.networkIssueType) {
            NetworkIssueType.Node -> {
                launch {
                    val meta = accountInteractor.selectedMetaAccountFlow().first()
                    walletRouter.openOptionsSwitchNode(
                        metaId = meta.id,
                        chainId = itemState.chainId,
                        chainName = itemState.chainName.orEmpty()
                    )
                }
            }

            NetworkIssueType.Network -> {
                val payload = AlertViewState(
                    title = resourceManager.getString(
                        R.string.staking_main_network_title,
                        itemState.chainName.orEmpty()
                    ),
                    message = resourceManager.getString(R.string.network_issue_unavailable),
                    buttonText = resourceManager.getString(R.string.top_up),
                    iconRes = R.drawable.ic_alert_16
                )
                walletRouter.openAlert(payload, KEY_ALERT_RESULT)
            }

            NetworkIssueType.Account -> {
                launch {
                    val meta = accountInteractor.selectedMetaAccountFlow().first()
                    itemState.assetId?.let {
                        val payload = AddAccountPayload(
                            metaId = meta.id,
                            chainId = itemState.chainId,
                            chainName = itemState.chainName.orEmpty(),
                            assetId = it,
                            markedAsNotNeed = false
                        )
                        walletRouter.openOptionsAddAccount(payload)
                    }
                }
            }

            null -> {
                val assetId = itemState.assetId ?: return

                walletRouter.openAssetDetails(
                    AssetPayload(
                        chainId = itemState.chainId,
                        chainAssetId = assetId
                    )
                )
            }
        }
    }

    private fun onAlertResult(result: Result<Unit>) {
        if (result.isSuccess) {
            val chainId = lastSelectedChainIdWithNetworkIssue ?: return
            launch {
                val meta = accountInteractor.selectedLightMetaAccount()
                assetNotNeedAccount.markChainAssetsNotNeed(
                    chainId = chainId,
                    metaId = meta.id
                )
                walletRouter.back()
            }
        }
    }
}