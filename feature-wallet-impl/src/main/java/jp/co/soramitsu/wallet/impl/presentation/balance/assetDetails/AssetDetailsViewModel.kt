package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.AssetDetailsFragment.Companion.KEY_ASSET_PAYLOAD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A restored obsolete destination forwards its exact network/asset without waiting for balance data. */
@HiltViewModel
class AssetDetailsViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), AssetDetailsCallback {
    private val assetPayload = savedStateHandle.get<AssetPayload>(KEY_ASSET_PAYLOAD)
    private var navigationHandled = false

    // The transitional frame has no wallet identity, money or actionable stale asset rows.
    val toolbarState: StateFlow<LoadingState<MainToolbarViewState>> = MutableStateFlow(
        LoadingState.Loaded(MainToolbarViewState(
            title = "",
            homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_arrow_back_24dp),
            selectorViewState = ChainSelectorViewState()
        ))
    )
    val contentState: StateFlow<AssetDetailsState> = MutableStateFlow(AssetDetailsState.empty)

    /** Called only after the fragment resumes, when its authenticated navigator is attached. */
    fun onLegacyDestinationResumed() {
        if (navigationHandled) return
        navigationHandled = true
        try {
            val payload = assetPayload?.takeIf { it.chainId.isNotBlank() && it.chainAssetId.isNotBlank() }
            if (payload == null) walletRouter.back()
            else walletRouter.openAssetDetailsAndPopUpToBalancesList(payload)
        } catch (failure: Exception) {
            navigationHandled = false
            throw failure
        }
    }

    override fun onNavigationBack() {
        if (navigationHandled) return
        navigationHandled = true
        walletRouter.back()
    }

    override fun onSelectChainClick() = onLegacyDestinationResumed()
    override fun onChainTabClick(tab: AssetDetailsState.Tab) = onLegacyDestinationResumed()
    override fun onSortChainsClick() = onLegacyDestinationResumed()
    override fun onChainClick(itemState: AssetDetailsState.ItemState) = onLegacyDestinationResumed()
}
