package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchAssetsViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val accountRepository: AccountRepository,
    private val router: WalletRouter,
    private val networkStateMixin: NetworkStateMixin,
    private val resourceManager: ResourceManager
) : BaseViewModel(), NetworkStateUi by networkStateMixin, SearchAssetsScreenInterface {

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val enteredAssetQueryFlow = MutableStateFlow("")

    private val connectingChainIdsFlow = networkStateMixin.chainConnectionsLiveData.map {
        it.filter { (_, isConnecting) -> isConnecting }.keys
    }.asFlow()

    private val assetStates = combine(
        interactor.assetsFlow(),
        chainInteractor.getChainsFlow(),
        connectingChainIdsFlow
    ) { assets: List<AssetWithStatus>, chains: List<Chain>, chainConnectings: Set<ChainId> ->
        assets.filter { it.hasAccount }
            .map { assetWithStatus ->
                val token = assetWithStatus.asset.token
                val tokenConfig = token.configuration
                val symbolToShow = tokenConfig.symbolToShow

                val chain = chains.firstOrNull { it.id == tokenConfig.chainId }
                val isSupported: Boolean = when (chain?.minSupportedVersion) {
                    null -> true
                    else -> AppVersion.isSupported(chain.minSupportedVersion)
                }

                val tokenChains = chains.filter { it.assets.any { it.symbolToShow == symbolToShow } }
                val hasNetworkIssue = tokenChains.any { it.id in chainConnectings }

                AssetListItemViewState(
                    assetIconUrl = tokenConfig.iconUrl,
                    assetChainName = chain?.name ?: tokenConfig.chainName,
                    assetSymbol = tokenConfig.symbol,
                    displayName = symbolToShow,
                    assetTokenFiat = token.fiatRate?.formatAsCurrency(token.fiatSymbol),
                    assetTokenRate = token.recentRateChange?.formatAsChange(),
                    assetBalance = assetWithStatus.asset.total?.format().orEmpty(),
                    assetBalanceFiat = token.fiatRate?.multiply(assetWithStatus.asset.total)?.formatAsCurrency(token.fiatSymbol),
                    assetChainUrls = emptyMap(),
                    chainId = tokenConfig.chainId,
                    chainAssetId = tokenConfig.id,
                    isSupported = isSupported,
                    isHidden = !assetWithStatus.asset.enabled,
                    hasAccount = assetWithStatus.hasAccount,
                    priceId = tokenConfig.priceId,
                    hasNetworkIssue = hasNetworkIssue
                )
            }
    }

    val state = combine(
        assetStates,
        enteredAssetQueryFlow
    ) { assetsListItemStates: List<AssetListItemViewState>,
        searchQuery ->

        val assets = when {
            searchQuery.isEmpty() -> emptyList()
            else -> assetsListItemStates.filter {
                it.displayName.contains(searchQuery, true) || it.assetChainName.contains(searchQuery, true)
            }
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

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    override fun onAssetSearchEntered(query: String) {
        enteredAssetQueryFlow.value = query
    }
}
