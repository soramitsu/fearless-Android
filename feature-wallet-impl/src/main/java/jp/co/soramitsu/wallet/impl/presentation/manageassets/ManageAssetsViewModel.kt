package jp.co.soramitsu.wallet.impl.presentation.manageassets

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class ManageAssetsViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val accountInteractor: AccountInteractor,
    private val chainInteractor: ChainInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ManageAssetsContentInterface {

    private val initialAssetStates = MutableStateFlow<List<AssetBooleanState>>(emptyList())
    private val currentAssetStates = MutableStateFlow<List<AssetBooleanState>>(emptyList())

    private val selectedChainIdFlow = MutableStateFlow<ChainId?>(null)

    private val savedChainFlow = selectedChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val assetModelsFlow: Flow<List<AssetModel>> =
        combine(
            walletInteractor.assetsFlow(),
            chainInteractor.getChainsFlow(),
            walletInteractor.selectedMetaAccountFlow(),
            walletInteractor.observeSelectedAccountChainSelectFilter(),
            selectedChainIdFlow
        ) { assets, chains, currentMetaAccount, appliedFilterAsString, selectedChainId ->
            val filter = ChainSelectorViewStateWithFilters.Filter.entries.find {
                it.name == appliedFilterAsString
            } ?: ChainSelectorViewStateWithFilters.Filter.All

            val selectedAccountFavoriteChains = currentMetaAccount.favoriteChains
            val chainsWithFavoriteInfo = chains.map { chain ->
                chain to (selectedAccountFavoriteChains[chain.id]?.isFavorite == true)
            }
            val filteredChains = when {
                selectedChainId != null -> chains.filter { it.id == selectedChainId }
                filter == ChainSelectorViewStateWithFilters.Filter.All -> chainsWithFavoriteInfo.map { it.first }

                filter == ChainSelectorViewStateWithFilters.Filter.Favorite ->
                    chainsWithFavoriteInfo.filter { (_, isFavorite) -> isFavorite }.map { it.first }

                filter == ChainSelectorViewStateWithFilters.Filter.Popular ->
                    chainsWithFavoriteInfo.filter { (chain, _) ->
                        chain.rank != null
                    }.sortedBy { (chain, _) ->
                        chain.rank
                    }.map { it.first }

                else -> emptyList()
            }

            assets.filter {
                selectedChainId == null ||
                        it.asset.token.configuration.chainId == selectedChainId ||
                        it.asset.token.configuration.chainId in filteredChains.map { it.id }
            }
        }
            .mapList {
                when {
                    it.hasAccount -> it.asset
                    else -> null
                }
            }
            .map { it.filterNotNull() }
            .mapList { mapAssetToAssetModel(it) }


    private val enteredTokenQueryFlow = MutableStateFlow("")

    val state = MutableStateFlow(ManageAssetsScreenViewState.default)

    private fun subscribeScreenState() {
        combine(
            savedChainFlow,
            walletInteractor.observeSelectedAccountChainSelectFilter()
        ) { chain, filterAsText ->
            val filterApplied = ChainSelectorViewStateWithFilters.Filter.entries.find {
                it.name == filterAsText
            } ?: ChainSelectorViewStateWithFilters.Filter.All

            val selectedChainTitle = chain?.name ?: when(filterApplied) {
                ChainSelectorViewStateWithFilters.Filter.All ->
                    resourceManager.getString(R.string.chain_selection_all_networks)

                ChainSelectorViewStateWithFilters.Filter.Popular ->
                    resourceManager.getString(R.string.network_management_popular)

                ChainSelectorViewStateWithFilters.Filter.Favorite ->
                    resourceManager.getString(R.string.network_managment_favourite)
            }
            state.value = state.value.copy(selectedChainTitle = selectedChainTitle)
        }.launchIn(this)

        combine(assetModelsFlow, enteredTokenQueryFlow, currentAssetStates) { assetModels, searchQuery, currentStates ->
            val sortedAssets = assetModels
                .filter {
                    searchQuery.isEmpty() ||
                            it.token.configuration.symbol.contains(searchQuery, true) ||
                            it.token.configuration.name.orEmpty().contains(searchQuery, true)
                }
                .sortedWith(compareBy<AssetModel> {
                    it.isHidden == true
                }.thenByDescending {
                    it.fiatAmount.orZero()
                }.thenByDescending {
                    it.available.orZero()
                }.thenBy {
                    it.token.configuration.chainName
                })
                .map { model ->
                    model.copy(isHidden = currentStates.firstOrNull {
                        it.assetId == model.token.configuration.id && it.chainId == model.token.configuration.chainId
                    }?.value == false)
                }

            val assets = sortedAssets.map {
                it.toManageAssetItemState()
            }

            val groupedAssets: Map<String, List<ManageAssetItemState>> = assets.groupBy {
                it.symbol
            }

            groupedAssets to searchQuery
        }.onEach { (assets, searchQuery) ->
            state.value = state.value.copy(assets = assets, searchQuery = searchQuery)
        }.launchIn(this)
    }

    init {
        subscribeScreenState()

        accountInteractor.selectedMetaAccountFlow().map { it.id }.distinctUntilChanged().map {
            selectedChainIdFlow.value = walletInteractor.getSavedChainId(it)

            walletInteractor.assetsFlow().firstOrNull()?.let { assets ->
                val assetStates = assets.map {
                    AssetBooleanState(
                        chainId = it.asset.token.configuration.chainId,
                        assetId = it.asset.token.configuration.id,
                        value = it.asset.enabled != false
                    )
                }
                initialAssetStates.value = assetStates
                currentAssetStates.value = assetStates
            }
        }.launchIn(this)

        walletRouter.chainSelectorPayloadFlow.map { chainId ->
            val walletId = accountInteractor.selectedLightMetaAccount().id
            walletInteractor.saveChainId(walletId, chainId)
            selectedChainIdFlow.value = chainId
        }.launchIn(this)
    }

    private fun AssetModel.toManageAssetItemState() = ManageAssetItemState(
        id = token.configuration.id,
        imageUrl = token.configuration.iconUrl,
        chainName = token.configuration.chainName,
        assetName = token.configuration.name,
        symbol = token.configuration.symbol.uppercase(),
        amount = available.orZero().formatCrypto(),
        fiatAmount = getAsFiatWithCurrency(available) ?: "${token.fiatSymbol.orEmpty()}0".takeIf { token.configuration.priceId != null || token.configuration.priceProvider != null },
        chainId = token.configuration.chainId,
        isChecked = isHidden != true,
        isZeroAmount = available.orZero().isZero(),
        showEdit = false
    )

    override fun onSearchInput(input: String) {
        enteredTokenQueryFlow.value = input
    }

    override fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean) {
        currentAssetStates.value = currentAssetStates.value.map {
            if (it.assetId == assetItemState.id && it.chainId == assetItemState.chainId) {
                it.copy(value = checked)
            } else {
                it
            }
        }
    }

    override fun onItemClicked(assetItemState: ManageAssetItemState) {
    }

    override fun onEditClicked(assetItemState: ManageAssetItemState) {
    }

    override fun onDoneClicked() {
        walletRouter.back()
    }

    override fun onSelectedChainClicked() {
        launch {
            val selectedChainId = savedChainFlow.firstOrNull()?.id
            walletRouter.openSelectChain(selectedChainId, isFilteringEnabled = true)
        }
    }

    fun onDialogClose() {
        walletRouter.setChainSelectorPayload(selectedChainIdFlow.value)
        val initial = initialAssetStates.value
        val changes = currentAssetStates.value.filter {
            it !in initial
        }
        kotlinx.coroutines.MainScope().launch {
            walletInteractor.updateAssetsHiddenState(changes)
        }
    }
}

