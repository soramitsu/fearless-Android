package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.api.presentation.AssetSelectPurpose
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

/**
 * The asset picker of Send, Receive, Swap and cross-chain transfers. What it lists follows the
 * portfolio's own rule for its [AssetSelectPurpose] ([PortfolioVisibility.isPickableToSpend] and
 * [PortfolioVisibility.isPickableToReceive]), never the stored Shown flag alone: most assets are
 * Auto, and a picker that needed an explicit Shown was empty for every new wallet.
 *
 * An asset is one (network, asset id) everywhere here: registry ids repeat across networks (DOT's
 * id is the same on Polkadot and Polkadot AssetHub).
 */
@HiltViewModel
class AssetSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    walletInteractor: WalletInteractor,
    xcmInteractor: XcmInteractor,
    chainInteractor: ChainInteractor,
    savedStateHandle: SavedStateHandle,
    private val sharedSendState: SendSharedState,
    private val portfolioVisibility: PortfolioVisibility,
    private val sendPausePolicy: SendPausePolicy,
    portfolioPreferences: PortfolioPresentationPreferences
) : BaseViewModel(), AssetSelectContentInterface {

    private var choiceDone = false

    private val filterChainId: String? = savedStateHandle[AssetSelectFragment.KEY_FILTER_CHAIN_ID]
    private val isFilterXcmAssets: Boolean = savedStateHandle[AssetSelectFragment.KEY_IS_FILTER_XCM_ASSETS] ?: false
    private val purpose: AssetSelectPurpose = if (isFilterXcmAssets) {
        AssetSelectPurpose.XCM
    } else {
        AssetSelectPurpose.fromName(savedStateHandle[AssetSelectFragment.KEY_PURPOSE])
    }

    private val excludeAssetId: String? = savedStateHandle[AssetSelectFragment.KEY_EXCLUDE_ASSET_ID]
    private val selectedAssetFlow = MutableStateFlow(
        savedStateHandle.get<String>(AssetSelectFragment.KEY_SELECTED_ASSET_ID)?.let { assetId ->
            SelectedAsset(
                chainId = savedStateHandle[AssetSelectFragment.KEY_SELECTED_CHAIN_ID] ?: filterChainId,
                assetId = assetId
            )
        }
    )

    private val isHiddenExpandedFlow = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val showTestnetsFlow = walletInteractor.selectedMetaAccountFlow()
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest(portfolioPreferences::observeShowTestnets)

    private val sourceAssetsFlow: Flow<List<AssetWithStatus>> = if (isFilterXcmAssets) {
        xcmInteractor.getAvailableAssetsFlow(originChainId = filterChainId)
    } else {
        walletInteractor.assetsFlow()
    }

    private val pickerAssetsFlow: Flow<List<PickerAsset>> = combine(
        sourceAssetsFlow,
        chainInteractor.getChainsFlow(),
        walletInteractor.selectedMetaAccountFlow(),
        showTestnetsFlow
    ) { assets, chains, metaAccount, showTestnets ->
        val chainsById = chains.associateBy(Chain::id)

        assets.mapNotNull { (asset, hasAccount, _) ->
            val configuration = asset.token.configuration
            if (!hasAccount) return@mapNotNull null
            if (filterChainId != null && configuration.chainId != filterChainId) return@mapNotNull null
            if (configuration.id == excludeAssetId && (filterChainId == null || configuration.chainId == filterChainId)) {
                return@mapNotNull null
            }
            val chain = chainsById[configuration.chainId] ?: return@mapNotNull null
            val isPickerNetwork = configuration.chainId == filterChainId

            val isPickable = if (purpose.isSpending) {
                portfolioVisibility.isPickableToSpend(asset.sendAvailable, chain, isPickerNetwork, showTestnets)
            } else {
                portfolioVisibility.isPickableToReceive(chain, configuration, isPickerNetwork, showTestnets)
            }
            if (!isPickable) return@mapNotNull null

            PickerAsset(
                model = mapAssetToAssetModel(asset),
                isHidden = asset.enabled == false,
                isTestnet = portfolioVisibility.isTestnet(chain),
                isInPortfolio = portfolioVisibility.isVisible(
                    enabled = asset.enabled,
                    total = asset.total,
                    chain = chain,
                    asset = configuration,
                    isFavourite = metaAccount.favoriteChains[chain.id]?.isFavorite == true,
                    isOnlySelectedNetwork = isPickerNetwork,
                    isSendVetoed = sendPausePolicy.isPaused(chain),
                    showTestnets = showTestnets
                )
            )
        }
    }

    private val enteredTokenQueryFlow = MutableStateFlow("")

    val state = combine(
        pickerAssetsFlow,
        selectedAssetFlow,
        enteredTokenQueryFlow,
        isHiddenExpandedFlow
    ) { pickerAssets, selectedAsset, searchQuery, isHiddenExpanded ->
        val query = searchQuery.trim()
        val matching = pickerAssets.filter { pickerAsset ->
            val configuration = pickerAsset.model.token.configuration
            query.isEmpty() ||
                configuration.symbol.contains(query, true) ||
                configuration.name.orEmpty().contains(query, true) ||
                configuration.chainName.contains(query, true)
        }

        // Spending: hiding never blocks spending, so hidden funded assets follow in their own
        // collapsed section. Receiving: everything in one list, what the portfolio shows first.
        val (listed, hidden) = if (purpose.isSpending) {
            matching.partition { !it.isHidden }
        } else {
            matching to emptyList<PickerAsset>()
        }

        AssetSelectScreenViewState(
            assets = listed.sortedWith(pickerOrder()).map { it.toAssetItemState(selectedAsset) },
            hiddenAssets = hidden.sortedWith(pickerOrder()).map { it.toAssetItemState(selectedAsset) },
            // A search reveals matching hidden assets without another tap.
            isHiddenExpanded = isHiddenExpanded || query.isNotEmpty(),
            searchQuery = searchQuery,
            showAllChains = false,
            emptyMessage = if (purpose.isSpending) AssetSelectEmptyMessage.NothingToSend else AssetSelectEmptyMessage.NothingFound
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetSelectScreenViewState.default)

    private fun pickerOrder() = compareByDescending<PickerAsset> { it.isInPortfolio }
        .thenByDescending { it.model.fiatAmount.orZero() }
        .thenByDescending { it.model.total.orZero() > BigDecimal.ZERO }
        .thenBy { it.model.token.configuration.chainName }
        .thenBy { it.model.token.configuration.symbol.lowercase() }

    private fun PickerAsset.toAssetItemState(selectedAsset: SelectedAsset?) = AssetItemState(
        id = model.token.configuration.id,
        imageUrl = model.token.configuration.iconUrl,
        // The network is part of every entry ("SYMBOL · Network"), except in the cross-chain
        // picker, which is limited to one origin network.
        chainName = model.token.configuration.chainName.takeIf { !isFilterXcmAssets },
        symbol = model.token.configuration.symbol.uppercase(),
        amount = model.available?.formatCrypto(),
        // No fiat line without a trusted price (untrusted list, testnet), rather than a "0" value.
        fiatAmount = model.getAsFiatWithCurrency(model.available).orEmpty(),
        isSelected = selectedAsset?.matches(model.token.configuration.chainId, model.token.configuration.id) == true,
        chainId = model.token.configuration.chainId,
        isTestnet = isTestnet
    )

    override fun onAssetSelected(assetItemState: AssetItemState) {
        choiceDone = true
        val selectedAsset = SelectedAsset(chainId = assetItemState.chainId, assetId = assetItemState.id)
        if (selectedAssetFlow.value != selectedAsset) {
            selectedAssetFlow.value = selectedAsset

            sharedSendState.update(chainId = assetItemState.chainId, assetId = assetItemState.id)
        }

        walletRouter.backWithResult(
            WalletRouterApi.KEY_CHAIN_ID to assetItemState.chainId,
            WalletRouterApi.KEY_ASSET_ID to assetItemState.id
        )
    }

    override fun onSearchInput(input: String) {
        enteredTokenQueryFlow.value = input
    }

    override fun onHiddenSectionToggle() {
        isHiddenExpandedFlow.value = !isHiddenExpandedFlow.value
    }

    fun onDialogClose() {
        if (!choiceDone && sharedSendState.assetId == null) {
            walletRouter.popOutOfSend()
        }
    }

    private class PickerAsset(
        val model: AssetModel,
        val isHidden: Boolean,
        val isTestnet: Boolean,
        val isInPortfolio: Boolean
    )

    private data class SelectedAsset(val chainId: ChainId?, val assetId: String) {
        // Without a network (a bundle from an older build) the id alone has to do.
        fun matches(chainId: ChainId, assetId: String): Boolean =
            this.assetId == assetId && (this.chainId == null || this.chainId == chainId)
    }
}
