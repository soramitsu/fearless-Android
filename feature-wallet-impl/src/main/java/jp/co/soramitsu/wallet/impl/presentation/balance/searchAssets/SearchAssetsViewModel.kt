package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.ext.ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getWithToken
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
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

    private val connectingChainIdsFlow = networkStateMixin.chainConnectionsFlow.map {
        it.filter { (_, isConnecting) -> isConnecting }.keys
    }

    private val assetStates = combine(
        interactor.assetsFlow(),
        chainInteractor.getChainsFlow(),
        connectingChainIdsFlow,
        interactor.observeHideZeroBalanceEnabledForCurrentWallet()
    ) { assets: List<AssetWithStatus>, chains: List<Chain>, chainConnectings: Set<ChainId>, hideZeroBalancesEnabled ->
        val balanceListItems = mutableListOf<BalanceListItemModel>()

        chains.groupBy { if (it.isTestNet) ChainEcosystem.STANDALONE else it.ecosystem() }.forEach { (ecosystem, ecosystemChains) ->
            when (ecosystem) {
                ChainEcosystem.POLKADOT,
                ChainEcosystem.KUSAMA -> {
                    val ecosystemAssets = assets.filter {
                        it.asset.token.configuration.chainId in ecosystemChains.map { it.id }
                    }

                    val items = processAssets(ecosystemAssets, ecosystemChains, chainConnectings, hideZeroBalancesEnabled, ecosystem)
                    balanceListItems.addAll(items)
                }

                ChainEcosystem.STANDALONE -> {
                    ecosystemChains.forEach { chain ->
                        val chainAssets = assets.filter { it.asset.token.configuration.chainId == chain.id }
                        val items = processAssets(chainAssets, listOf(chain), chainConnectings, hideZeroBalancesEnabled, ecosystem)
                        balanceListItems.addAll(items)
                    }
                }
            }
        }

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

        val assets = when {
            searchQuery.isEmpty() -> emptyList()
            else -> assetsListItemStates.filter {
                it.displayName.contains(searchQuery, true) || it.assetChainName.contains(searchQuery, true) || it.assetName.contains(searchQuery, true)
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

    private fun processAssets(
        ecosystemAssets: List<AssetWithStatus>,
        ecosystemChains: List<Chain>,
        chainConnectings: Set<ChainId>,
        hideZeroBalancesEnabled: Boolean,
        ecosystem: ChainEcosystem
    ): List<BalanceListItemModel> {
        val result = mutableListOf<BalanceListItemModel>()
        ecosystemAssets.groupBy { it.asset.token.configuration.symbolToShow }.forEach { (symbol, symbolAssets) ->
            val tokenChains = ecosystemChains.getWithToken(symbol)
            if (tokenChains.isEmpty()) return@forEach

            val showChain = tokenChains.sortedWith(
                compareByDescending<Chain> {
                    it.assets.firstOrNull { it.symbolToShow == symbol }?.isUtility ?: false
                }.thenByDescending { it.parentId == null }
            ).firstOrNull()

            val showChainAsset = showChain?.assets?.firstOrNull { it.symbolToShow == symbol } ?: return@forEach

            val hasNetworkIssue = tokenChains.any { it.id in chainConnectings }
            val hasChainWithoutAccount = symbolAssets.any { it.hasAccount.not() }

            val assetIdsWithBalance = symbolAssets.filter {
                it.asset.total.orZero() > BigDecimal.ZERO
            }.groupBy(
                keySelector = { it.asset.token.configuration.chainId },
                valueTransform = { it.asset.token.configuration.id }
            )
            val assetChainUrls = ecosystemChains.getWithToken(symbol, assetIdsWithBalance).associate { it.id to it.icon }

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

    private fun defaultBalanceListItemSort() = compareByDescending<BalanceListItemModel> { it.total > BigDecimal.ZERO }
        .thenByDescending { it.fiatAmount.orZero() }
        .thenBy { it.asset.isTestNet }
        .thenBy { it.asset.chainId.defaultChainSort() }
        .thenBy { it.asset.chainName }
}
