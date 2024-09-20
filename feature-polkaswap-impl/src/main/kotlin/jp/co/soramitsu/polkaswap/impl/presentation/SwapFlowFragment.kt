package jp.co.soramitsu.polkaswap.impl.presentation

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetDialog
import jp.co.soramitsu.common.compose.component.MainToolbarShimmer
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.models.retrieveString
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.impl.presentation.SwapDestinationsArgs.CHAIN_ID
import jp.co.soramitsu.polkaswap.impl.presentation.SwapDestinationsArgs.TOKEN_FROM_ID
import jp.co.soramitsu.polkaswap.impl.presentation.SwapDestinationsArgs.TOKEN_TO_ID
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensContent
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensViewModel

@AndroidEntryPoint
class SwapFlowFragment : BaseComposeBottomSheetDialogFragment<SwapFlowViewModel>() {

    companion object {

        const val KEY_SELECTED_CHAIN_ID = "KEY_SELECTED_CHAIN_ID"
        const val KEY_SELECTED_ASSET_FROM_ID = "KEY_SELECTED_ASSET_FROM_ID"
        const val KEY_SELECTED_ASSET_TO_ID = "KEY_SELECTED_ASSET_TO_ID"

        fun getBundle(selectedChainId: String?, assetIdFrom: String?, assetIdTo: String?) = bundleOf(
            KEY_SELECTED_CHAIN_ID to selectedChainId,
            KEY_SELECTED_ASSET_FROM_ID to assetIdFrom,
            KEY_SELECTED_ASSET_TO_ID to assetIdTo
        )
    }

    override val viewModel: SwapFlowViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val navController = rememberNavController()
        val toolbarState = viewModel.toolbarStateFlow.collectAsStateWithLifecycle()
        val navActions: SwapNavigationActions = remember(navController) {
            SwapNavigationActions(navController)
        }

        SetupNavDestinationChangedListener(
            navController = navController,
            onNavDestinationChanged = remember {
                viewModel::onDestinationChanged
            }
        )

        BottomSheetDialog {
            when (val loadingState = toolbarState.value) {
                is SwapFlowViewModel.ToolbarState.Hidden -> {}
                is SwapFlowViewModel.ToolbarState.Loading ->
                    MainToolbarShimmer(
                        homeIconState = ToolbarHomeIconState.Navigation(R.drawable.ic_arrow_back_24dp)
                    )
                is SwapFlowViewModel.ToolbarState.Loaded ->
                    ToolbarBottomSheet(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = loadingState.title?.retrieveString(),
                        onNavigationClick = remember {
                            {
                                viewModel.onNavigationClick()
                            }
                        }
                    )
            }

            NavHost(
                startDestination = SwapDestinations.SWAP_ROUTE,
                contentAlignment = Alignment.TopCenter,
                navController = navController,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                composable(
                    route = SwapDestinations.SWAP_ROUTE,
                    arguments = listOf(
                        navArgument(CHAIN_ID) {
                            defaultValue = arguments?.getString(KEY_SELECTED_CHAIN_ID)
                            nullable = true
                        },
                        navArgument(TOKEN_FROM_ID) {
                            defaultValue = arguments?.getString(KEY_SELECTED_ASSET_FROM_ID)
                            nullable = true
                        },
                        navArgument(TOKEN_TO_ID) {
                            defaultValue = arguments?.getString(KEY_SELECTED_ASSET_TO_ID)
                            nullable = true
                        }
                    )
                ) { backStackEntry ->
                    val chainId = backStackEntry.arguments?.getString(CHAIN_ID)
                    val tokenFromId = backStackEntry.arguments?.getString(TOKEN_FROM_ID)
                    val tokenToId = backStackEntry.arguments?.getString(TOKEN_TO_ID)

                    val viewModel = hiltViewModel(creationCallback = { factory: SwapTokensViewModel.SwapViewModelFactory ->
                        factory.create(chainId, tokenFromId, tokenToId)
                    })

                    val swapState by viewModel.state.collectAsStateWithLifecycle()

                    SwapTokensContent(
                        state = swapState,
                        callbacks = viewModel
                    )
                }
            }
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    @Composable
    private inline fun SetupNavDestinationChangedListener(
        navController: NavController,
        crossinline onNavDestinationChanged: (newRoute: String) -> Unit
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val onDestinationChangedListener =
                NavController.OnDestinationChangedListener { _, destination, _ ->
                    onNavDestinationChanged(destination.route!!)
                }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        navController.addOnDestinationChangedListener(onDestinationChangedListener)

                    Lifecycle.Event.ON_STOP ->
                        navController.removeOnDestinationChangedListener(
                            onDestinationChangedListener
                        )

                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
        }
    }
}
