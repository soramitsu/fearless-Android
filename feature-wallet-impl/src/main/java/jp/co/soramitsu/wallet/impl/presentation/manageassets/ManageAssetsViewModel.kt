package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.repository.isSupported
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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

    private val enteredTokenQueryFlow = MutableStateFlow("")

    val state = MutableStateFlow(ManageAssetsScreenViewState.default)

    private fun subscribeScreenState() {
        combine(
            savedChainFlow,
            walletInteractor.observeSelectedAccountChainSelectFilter()
        ) { chain, filterApplied ->

            val selectedChainTitle = chain?.name ?: when (filterApplied) {
                ChainSelectorViewStateWithFilters.Filter.All ->
                    resourceManager.getString(R.string.chain_selection_all_networks)

                ChainSelectorViewStateWithFilters.Filter.Popular ->
                    resourceManager.getString(R.string.network_management_popular)

                ChainSelectorViewStateWithFilters.Filter.Favorite ->
                    resourceManager.getString(R.string.network_managment_favourite)
            }
            state.value = state.value.copy(selectedChainTitle = selectedChainTitle)
        }.launchIn(this)

        jp.co.soramitsu.common.utils.combine(
            walletInteractor.assetsFlow(),
            chainInteractor.getChainsFlow(),
            walletInteractor.selectedMetaAccountFlow(),
            walletInteractor.observeSelectedAccountChainSelectFilter(),
            selectedChainIdFlow,
            enteredTokenQueryFlow,
            currentAssetStates
        ) { assets, chains, currentMetaAccount, filter, selectedChainId, searchQuery, currentStates ->
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
            }.filter {
                val accountId = currentMetaAccount.accountId(it)
                accountId != null && accountId.contentEquals(emptyAccountIdValue).not() // has account
            }

            val allChainAssets = filteredChains.map { it.assets }.flatten()

            val filteredChainAssets = allChainAssets.asSequence().filter { chainAsset ->
                (selectedChainId == null && filter == ChainSelectorViewStateWithFilters.Filter.All) ||
                        chainAsset.chainId == selectedChainId ||
                        chainAsset.chainId in filteredChains.map { it.id }
            }.filter {
                searchQuery.isEmpty() ||
                        it.symbol.contains(searchQuery, true) ||
                        it.name.orEmpty().contains(searchQuery, true)
            }.toList()

            filteredChainAssets.map { chainAsset ->
                val asset = assets.find { chainAsset.id == it.asset.token.configuration.id && chainAsset.chainId == it.asset.token.configuration.chainId }
                chainAsset to asset
            }.sortedWith(compareBy<Pair<Asset, AssetWithStatus?>> {
                it.second == null
            }.thenBy {
                it.second?.asset?.enabled == false
            }.thenByDescending {
                it.second?.asset?.fiatAmount.orZero()
            }.thenByDescending {
                it.second?.asset?.transferable.orZero()
            }.thenBy {
                it.second?.asset?.token?.configuration?.chainName
            }).map {
                val (chainAsset, assetWithStatus) = it
                val available = assetWithStatus?.asset?.transferable ?: BigDecimal.ZERO
                val fiatAmount = assetWithStatus?.asset?.token?.fiatRate?.let { rate -> available.applyFiatRate(rate).orZero().formatFiat(assetWithStatus.asset.token.fiatSymbol) }
                val isHidden = currentStates.find { assetBooleanState -> assetBooleanState.assetId == chainAsset.id && assetBooleanState.chainId == chainAsset.chainId }?.value == false

                ManageAssetItemState(
                    id = chainAsset.id,
                    imageUrl = chainAsset.iconUrl,
                    chainName = chainAsset.chainName,
                    assetName = chainAsset.name,
                    symbol = chainAsset.symbol.uppercase(),
                    amount = available.formatCrypto(),
                    fiatAmount = fiatAmount
                        ?: "${assetWithStatus?.asset?.token?.fiatSymbol.orEmpty()}0".takeIf { chainAsset.priceId != null || chainAsset.priceProvider?.isSupported == true},
                    chainId = chainAsset.chainId,
                    isChecked = !isHidden,
                    isZeroAmount = available.orZero().isZero(),
                    showEdit = false
                )
            }.groupBy {
                it.symbol
            } to searchQuery
        }.onEach { (groupedStates, searchQuery)  ->
            state.value = state.value.copy(assets = groupedStates, searchQuery = searchQuery)
        }.launchIn(this)
    }

    init {
        subscribeScreenState()

        viewModelScope.launch {
            val metaId = accountInteractor.selectedLightMetaAccount().id
            selectedChainIdFlow.value = walletInteractor.getSavedChainId(metaId)
        }
        viewModelScope.launch  {
            val assets = walletInteractor.assetsFlow().firstOrNull()
            val chainAssets = chainInteractor.getChainAssets()
            val assetsStates = chainAssets.map { chainAsset ->
                val asset = assets?.find {  it.asset.token.configuration.id == chainAsset.id && it.asset.token.configuration.chainId == chainAsset.chainId }
                val value = asset?.asset?.enabled ?: false
                AssetBooleanState(
                    chainId = chainAsset.chainId,
                    assetId = chainAsset.id,
                    value = value
                )
            }
            initialAssetStates.value = assetsStates
            currentAssetStates.value = assetsStates
        }

        walletRouter.chainSelectorPayloadFlow.map { chainId ->
            val walletId = accountInteractor.selectedLightMetaAccount().id
            walletInteractor.saveChainId(walletId, chainId)
            selectedChainIdFlow.value = chainId
        }.launchIn(this)
    }

    override fun onSearchInput(input: String) {
        enteredTokenQueryFlow.value = input
    }

    override fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean) {
        currentAssetStates.update {  prevState ->
            prevState.map {
                if (it.assetId == assetItemState.id && it.chainId == assetItemState.chainId) {
                    it.copy(value = checked)
                } else {
                    it
                }
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
