package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AssetBalanceUseCase
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.AssetDetailsFragment.Companion.KEY_ASSET_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetDetailsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val getAssetBalance: AssetBalanceUseCase,
    private val walletRouter: WalletRouter,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel(), AssetDetailsCallback {

    private val tabSelectionFlow = MutableStateFlow(AssetDetailsState.Tab.AvailableChains)

    private val assetIdFlow = with(savedStateHandle) {
        MutableStateFlow(
            value = get<String>(KEY_ASSET_ID) ?: error("No asset specified")
        )
    }

    private val cachedSelectedMetaAccount = interactor.selectedLightMetaAccountFlow()
        .flowOn(Dispatchers.IO).share()

    private val cachedPerChainBalanceWithAssetFlow =
        combine(cachedSelectedMetaAccount, assetIdFlow) { selectedMetaAccount, assetId ->
            selectedMetaAccount.id to assetId
        }.flatMapLatest { (selectedMetaAccountId, assetId) ->
            interactor.observeChainsPerAsset(selectedMetaAccountId, assetId).also { valuesFlow ->
                launch {
                    val chainSelection = interactor.getSavedChainId(walletId = selectedMetaAccountId)
                    val valuesAsList = valuesFlow.first().toList()

                    val assetPayload = AssetPayload(
                        chainId = "",
                        chainAssetId = assetId
                    )

                    val resultingAssetPayload = when {
                        chainSelection != null ->
                            assetPayload.copy(chainId = chainSelection)

                        valuesAsList.size == 1 ->
                            assetPayload.copy(chainId = valuesAsList.first().first.id)

                        else -> null
                    }

                    resultingAssetPayload?.let {
                        walletRouter.openAssetDetailsAndPopUpToBalancesList(it)
                    }
                }
            }
        }.flowOn(Dispatchers.IO).share()

    val toolbarState: StateFlow<LoadingState<MainToolbarViewState>> = createToolbarState()

    private fun createToolbarState(): StateFlow<LoadingState<MainToolbarViewState>> {
        return cachedSelectedMetaAccount.map { selectedMetaAccount ->
            LoadingState.Loaded(
                MainToolbarViewState(
                    title = selectedMetaAccount.name,
                    homeIconState = ToolbarHomeIconState(navigationIcon = R.drawable.ic_arrow_back_24dp),
                    selectorViewState = ChainSelectorViewState()
                )
            )
        }.stateIn(
            this,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            LoadingState.Loading()
        )
    }

    val contentState: StateFlow<AssetDetailsState> = createContentStateFlow()

    private fun createContentStateFlow(): StateFlow<AssetDetailsState> {
        val assetBalanceFlow =
            combine(cachedSelectedMetaAccount, assetIdFlow) { selectedMetaAccount, assetId ->
                selectedMetaAccount.id to assetId
            }.flatMapLatest { (selectedAccountMetaId, assetId) ->
                getAssetBalance.observe(selectedAccountMetaId, assetId)
            }

        val assetSortingFlow = interactor.observeAssetSorting()

        val chainsPerAssetToAssetFlow = cachedPerChainBalanceWithAssetFlow
            .combine(tabSelectionFlow) { values, tabSelection ->
                when (tabSelection) {
                    AssetDetailsState.Tab.AvailableChains -> values

                    AssetDetailsState.Tab.MyChains -> values.filter {
                        val totalBalance = it.value?.total ?: return@filter false
                        return@filter totalBalance > BigDecimal.ZERO
                    }
                }
            }.combine(assetSortingFlow) { values, sorting ->
                val valuesAsList = values.toList()

                when(sorting) {
                    AssetSorting.FiatBalance ->
                        valuesAsList.sortedByDescending { (_, asset) -> asset?.total }

                    AssetSorting.Name ->
                        valuesAsList.sortedBy { (chain, _) -> chain.name }

                    AssetSorting.Popularity ->
                        valuesAsList.sortedByDescending { (chain, _) -> chain.rank }
                } to sorting
            }

        return combine(
            tabSelectionFlow,
            assetBalanceFlow,
            chainsPerAssetToAssetFlow
        ) { selectedTab, assetBalance, (chainsPerAssetToAsset, sorting) ->
            val assetBalanceState =
                AssetBalanceViewState(
                    transferableBalance = assetBalance.assetBalance.formatCrypto(assetBalance.assetSymbol),
                    changeViewState = ChangeBalanceViewState(
                        percentChange = assetBalance.rateChange?.formatAsChange().orEmpty(),
                        fiatChange = assetBalance.fiatBalance.formatFiat(assetBalance.fiatSymbol)
                    ),
                    address = "" // implies invisible address state
                )

            val chainItemViewStates = chainsPerAssetToAsset.map { (chain, asset) ->
                val totalBalance = asset?.total
                val totalFiatBalance = totalBalance?.applyFiatRate(asset.token.fiatRate)

                AssetDetailsItemViewState(
                    assetId = asset?.token?.configuration?.id,
                    chainId = chain.id,
                    iconUrl = chain.icon,
                    chainName = chain.name,
                    assetRepresentation = totalBalance?.formatCrypto(asset.token.configuration.symbol),
                    fiatRepresentation = totalFiatBalance?.formatFiat(asset.token.fiatSymbol)
                )
            }

            return@combine AssetDetailsViewState(
                assetSorting = sorting,
                balanceState = LoadingState.Loaded(assetBalanceState),
                tabState = MultiToggleButtonState(
                    currentSelection = selectedTab,
                    toggleStates = AssetDetailsState.Tab.values().toList()
                ),
                items = chainItemViewStates
            )
        }.stateIn(
            this,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            EmptyAssetDetailsViewState
        )
    }

    init {
        walletRouter.chainSelectorPayloadFlow.flatMapLatest { chainId ->
            walletRouter.trackReturnToAssetDetailsFromChainSelector()?.onEach {
                if (chainId == null)
                    return@onEach

                val assetId = cachedPerChainBalanceWithAssetFlow.replayCache.firstOrNull()?.mapKeys {
                    it.key.id
                }?.get(chainId)?.token?.configuration?.id ?: return@onEach

                walletRouter.openAssetDetails(
                    AssetPayload(
                        chainId = chainId,
                        chainAssetId = assetId
                    )
                )
            } ?: flow { /* DO NOTHING */ }
        }.flowOn(Dispatchers.Main.immediate).share()
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
        val assetId = itemState.assetId ?: return

        walletRouter.openAssetDetails(
            AssetPayload(
                chainId = itemState.chainId,
                chainAssetId = assetId
            )
        )
    }
}