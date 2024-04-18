package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getWithToken
//import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetListHelper
//import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import jp.co.soramitsu.wallet.impl.presentation.model.AssetPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SearchAssetsViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val router: WalletRouter,
    private val networkStateMixin: NetworkStateMixin
) : BaseViewModel(), NetworkStateUi by networkStateMixin, SearchAssetsScreenInterface {

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val enteredAssetQueryFlow = MutableStateFlow("")

    private val assetStates = combine(
        interactor.assetsFlow(),
        chainInteractor.getChainsFlow(),
        networkIssuesFlow
    ) { assets: List<AssetWithStatus>, chains: List<Chain>, networkIssues: Set<NetworkIssueItemState> ->

        val balanceListItems = AssetListHelper.processAssets(
            assets = assets,
            filteredChains = chains,
            networkIssues = networkIssues
        )

        val assetStates: List<AssetListItemViewState> = balanceListItems
            .sortedWith(defaultBalanceListItemSort())
            .map { it.toAssetState() }

        assetStates
    }

    val state = combine(
        assetStates,
        enteredAssetQueryFlow
    ) { assetsListItemStates: List<AssetListItemViewState>,
        searchQuery ->

        val assets = assetsListItemStates
            .filter {
                searchQuery.isEmpty() || it.assetSymbol.contains(searchQuery, true) || it.assetName.contains(searchQuery, true)
            }

        SearchAssetState(
            assets = assets,
            searchQuery = searchQuery
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = null)

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

    suspend fun hideAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsHidden(chainId, chainAssetId)
    }

    suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openSend(assetPayload)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    override fun backClicked() {
        router.back()
    }

    override fun assetClicked(state: AssetListItemViewState) {
        if (state.isSupported.not()) {
            _showUnsupportedChainAlert.value = Event(Unit)
            return
        }

        router.openAssetIntermediateDetails(state.chainAssetId)
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    override fun onAssetSearchEntered(query: String) {
        enteredAssetQueryFlow.value = query
    }


    private fun defaultBalanceListItemSort() = compareByDescending<BalanceListItemModel> { it.total > BigDecimal.ZERO }
        .thenByDescending { it.fiatAmount.orZero() }
        .thenBy { it.asset.isTestNet }
        .thenBy { it.asset.chainId.defaultChainSort() }
        .thenBy { it.asset.chainName }
}
