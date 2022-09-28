package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SearchAssetsViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val router: WalletRouter
) : BaseViewModel() {

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val enteredAssetQueryFlow = MutableStateFlow("")
    private val hiddenAssetsState = MutableLiveData(HiddenItemState(isExpanded = false))

    private val assetStates = combine(
        interactor.assetsFlow(),
        chainInteractor.getChainsFlow()
    ) { assets: List<AssetWithStatus>, chains: List<JoinedChainInfo> ->
        val selectedChainId = savedStateHandle.get<String?>(SearchAssetsFragment.KEY_CHAIN_ID)
        val assetStates = mutableListOf<AssetListItemViewState>()

        assets
            .filter { it.hasAccount }
            .filter { selectedChainId == null || selectedChainId == it.asset.token.configuration.chainId }
            .sortedWith(defaultAssetListSort())
            .map { assetWithStatus ->
                val token = assetWithStatus.asset.token
                val chainAsset = token.configuration

                val chainLocal = chains.find { it.chain.id == token.configuration.nativeChainId }
                val chain = chainLocal?.let { mapChainLocalToChain(it) }

                val isSupported: Boolean = when (chain?.minSupportedVersion) {
                    null -> true
                    else -> AppVersion.isSupported(chain.minSupportedVersion)
                }

                val assetChainUrls = chains.filter { it.assets.any { it.symbolToShow == chainAsset.symbolToShow } }
                    .associate { it.chain.id to it.chain.icon }

                val stateItem = assetStates.find { it.displayName == chainAsset.symbolToShow }
                if (stateItem == null) {
                    val assetListItemViewState = AssetListItemViewState(
                        assetIconUrl = chainAsset.iconUrl,
                        assetChainName = chain?.name.orEmpty(),
                        assetSymbol = chainAsset.symbol,
                        displayName = chainAsset.symbolToShow,
                        assetTokenFiat = token.fiatRate?.formatAsCurrency(token.fiatSymbol),
                        assetTokenRate = token.recentRateChange?.formatAsChange(),
                        assetBalance = assetWithStatus.asset.total?.format().orEmpty(),
                        assetBalanceFiat = token.fiatRate?.multiply(assetWithStatus.asset.total)?.formatAsCurrency(token.fiatSymbol),
                        assetChainUrls = assetChainUrls,
                        chainId = chain?.id.orEmpty(),
                        chainAssetId = chainAsset.id,
                        isSupported = isSupported,
                        isHidden = !assetWithStatus.asset.enabled
                    )

                    assetStates.add(assetListItemViewState)
                }
            }
        assetStates
    }

    val state = combine(
        assetStates,
        enteredAssetQueryFlow,
        hiddenAssetsState.asFlow()
    ) { assetsListItemStates: List<AssetListItemViewState>,
        searchQuery,
        hiddenAssetsState: HiddenItemState ->

        if (assetsListItemStates.isEmpty()) {
            return@combine LoadingState.Loading()
        }

        val assets = assetsListItemStates
            .filter {
                searchQuery.isEmpty() || it.displayName.contains(searchQuery, true) || it.assetChainName.contains(searchQuery, true)
            }

        LoadingState.Loaded(
            SearchAssetState(
                assets = assets,
                searchQuery = searchQuery,
                hiddenState = hiddenAssetsState
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    private fun defaultAssetListSort() = compareByDescending<AssetWithStatus> { it.asset.total.orZero() > BigDecimal.ZERO }
        .thenByDescending { it.asset.fiatAmount.orZero() }
        .thenBy { it.asset.token.configuration.isTestNet }
        .thenBy { it.asset.token.configuration.chainId.defaultChainSort() }
        .thenBy { it.asset.token.configuration.chainName }

    private val itemsToFillTheMostScreens = 7
    val assetShimmerItems = assetModelsFlow().take(itemsToFillTheMostScreens)
        .mapList {
            AssetListItemShimmerViewState(
                assetIconUrl = it.token.configuration.iconUrl,
                assetChainUrls = listOf(it.token.configuration.iconUrl)
            )
        }
        .stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = defaultWalletShimmerItems())

    private fun defaultWalletShimmerItems(): List<AssetListItemShimmerViewState> = listOf(
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/SORA.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Bifrost.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
    ).map { iconUrl ->
        AssetListItemShimmerViewState(
            assetIconUrl = iconUrl,
            assetChainUrls = listOf(iconUrl)
        )
    }

    private fun assetModelsFlow(): Flow<List<AssetModel>> =
        interactor.assetsFlow()
            .mapList {
                when {
                    it.hasAccount -> it.asset
                    else -> null
                }
            }
            .map { it.filterNotNull() }
            .mapList { mapAssetToAssetModel(it) }

    @OptIn(ExperimentalMaterialApi::class)
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {
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
        }
    }

    suspend fun hideAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsHidden(chainId, chainAssetId)
    }

    suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openChooseRecipient(assetPayload)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    fun backClicked() {
        router.back()
    }

    fun assetClicked(asset: AssetListItemViewState) {
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

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    fun onAssetSearchEntered(query: String) {
        enteredAssetQueryFlow.value = query
    }

    fun onHiddenAssetClicked() {
        hiddenAssetsState.value = HiddenItemState(
            isExpanded = hiddenAssetsState.value?.isExpanded?.not() ?: false
        )
    }
}
