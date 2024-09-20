package jp.co.soramitsu.polkaswap.impl.presentation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.navigation.SwapNavGraphRoute
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SwapFlowViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    open class ToolbarState(val title: TextModel? = null) {
        object Hidden: ToolbarState()
        object Loading: ToolbarState()
        data class Loaded(val text: TextModel): ToolbarState(text)
    }

    private val initFromAssetId =
        savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_FROM_ID)
    private val initToAssetId =
        savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID)
    private val initFromChainId =
        savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_CHAIN_ID)

    private val mutableToolbarStateFlow = MutableStateFlow<ToolbarState>(ToolbarState.Loading)
//    private val mutableToolbarStateFlowOld = MutableStateFlow<LoadingState<TextModel>>(LoadingState.Loading())
    val toolbarStateFlow: StateFlow<ToolbarState> = mutableToolbarStateFlow

    init {
        polkaswapInteractor.setChainId(initFromChainId)
    }

    fun onDestinationChanged(route: String) {
        println("!!! swapFlowVM onDestinationChanged(route = $route")
        val newToolbarState: ToolbarState = when (route) {
//            SwapDestinations.LOADING ->
//                LoadingState.Loading()

            SwapDestinations.SWAP_ROUTE ->
                ToolbarState.Hidden
//                LoadingState.Loaded(
//                    TextModel.SimpleString("Swap tokens")
//                )

            else -> ToolbarState.Loading
        }

        mutableToolbarStateFlow.value = newToolbarState
    }

    fun onNavigationClick() {
        polkaswapRouter.back()
//        internalPoolsRouter.back()
    }

}