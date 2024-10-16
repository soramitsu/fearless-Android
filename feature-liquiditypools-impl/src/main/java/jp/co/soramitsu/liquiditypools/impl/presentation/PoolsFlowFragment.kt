package jp.co.soramitsu.liquiditypools.impl.presentation

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetDialog
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MainToolbarShimmer
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.models.retrieveString
import jp.co.soramitsu.common.presentation.InfoDialog
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsScreenWithRefresh
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemoveScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListScreen
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class PoolsFlowFragment : BaseComposeBottomSheetDialogFragment<PoolsFlowViewModel>() {

    override val viewModel: PoolsFlowViewModel by viewModels()

    // Compose BackHandler does not work in DialogFragments, nor does BackPressedDispatcher
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            super.onCreateDialog(savedInstanceState).apply {
                setOnKeyListener { _, keyCode, event ->
                    val isBackPressDetected =
                        keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP

                    if (isBackPressDetected) {
                        viewModel.onNavigationClick()
                    }

                    return@setOnKeyListener isBackPressDetected
                }
            }
        } else {
            // Call to super.onBackPressed() will cancel dialog as default behavior
            object : BottomSheetDialog(requireContext(), theme) {
                @SuppressLint("MissingSuperCall")
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onBackPressed() {
                    viewModel.onNavigationClick()
                }
            }
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val navController = rememberNavController()
        val toolbarState = viewModel.toolbarStateFlow.collectAsStateWithLifecycle()

        SetupNavDestinationChangedListener(
            navController = navController,
            onNavDestinationChanged = remember {
                viewModel::onDestinationChanged
            }
        )

        LaunchedEffect(Unit) {
            viewModel.navGraphRoutesFlow.onEach {
                navController.navigate(it.routeName)
            }.launchIn(this)

            viewModel.navGraphActionsFlow.onEach {
                when (it) {
                    is NavAction.BackPressed -> {
                        val isBackNavigationSuccess = navController.popBackStack()

                        val currentRoute = navController.currentDestination?.route
                        val loadingRoute = LiquidityPoolsNavGraphRoute.Loading.routeName

                        if (currentRoute == loadingRoute || !isBackNavigationSuccess) {
                            viewModel.exitFlow()
                        }
                    }

                    is NavAction.ShowError ->
                        showErrorDialog(
                            title = it.errorTitle ?: resources.getString(jp.co.soramitsu.common.R.string.common_error_general_title),
                            message = it.errorText
                        )

                    is NavAction.ShowInfo ->
                        InfoDialog(
                            title = it.title,
                            message = it.message
                        ).show(childFragmentManager)

                    NavAction.SupplyLiquidityCompleted -> {
                        viewModel.supplyLiquidityCompleted()
                    }
                }
            }.launchIn(this)
        }

        BottomSheetDialog(
            modifier = Modifier.fillMaxSize()
        ) {
            when (val loadingState = toolbarState.value) {
                is LoadingState.Loaded<TextModel> ->
                    if (loadingState.data.retrieveString().isEmpty()) {
                        PolkaswapImageToolbar()
                    } else {
                        ToolbarBottomSheet(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            title = loadingState.data.retrieveString(),
                            onNavigationClick = remember {
                                {
                                    viewModel.onNavigationClick()
                                }
                            }
                        )
                    }

                is LoadingState.Loading<TextModel> ->
                    MainToolbarShimmer(
                        homeIconState = ToolbarHomeIconState.Navigation(jp.co.soramitsu.feature_wallet_impl.R.drawable.ic_arrow_back_24dp)
                    )
            }

            NavHost(
                startDestination = LiquidityPoolsNavGraphRoute.Loading.routeName,
                contentAlignment = Alignment.TopCenter,
                navController = navController,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                composable(LiquidityPoolsNavGraphRoute.AllPoolsScreen.routeName) {
                    val allPoolsScreenState by viewModel.allPoolsScreenState.collectAsStateWithLifecycle()
                    AllPoolsScreenWithRefresh(
                        state = allPoolsScreenState,
                        callback = viewModel
                    )
                }

                composable(LiquidityPoolsNavGraphRoute.ListPoolsScreen.routeName) {
                    val poolListState by viewModel.poolListScreenState.collectAsStateWithLifecycle()
                    PoolListScreen(
                        state = poolListState,
                        callback = viewModel
                    )
                }

                composable(LiquidityPoolsNavGraphRoute.PoolDetailsScreen.routeName) {
                    val poolDetailState by viewModel.poolDetailsScreenState.collectAsStateWithLifecycle()
                    PoolDetailsScreen(
                        state = poolDetailState,
                        callbacks = viewModel
                    )
                }

                composable(LiquidityPoolsNavGraphRoute.LiquidityAddScreen.routeName) {
                    val liquidityAddState by viewModel.liquidityAddScreenState.collectAsStateWithLifecycle()
                    LiquidityAddScreen(liquidityAddState, viewModel)
                }

                composable(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen.routeName) {
                    val liquidityAddConfirmState by viewModel.liquidityAddConfirmState.collectAsStateWithLifecycle()
                    LiquidityAddConfirmScreen(liquidityAddConfirmState, viewModel)
                }

                composable(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen.routeName) {
                    val liquidityRemoveState by viewModel.liquidityRemoveScreenState.collectAsStateWithLifecycle()
                    LiquidityRemoveScreen(liquidityRemoveState, viewModel)
                }

                composable(LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen.routeName) {
                    val liquidityRemoveState by viewModel.liquidityRemoveConfirmState.collectAsStateWithLifecycle()
                    LiquidityRemoveConfirmScreen(liquidityRemoveState, viewModel)
                }

                composable(LiquidityPoolsNavGraphRoute.Loading.routeName) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
        }
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

    @Composable
    private fun PolkaswapImageToolbar() {
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .padding(bottom = 12.dp)
        ) {
            NavigationIconButton(
                modifier = Modifier.padding(start = 16.dp),
                navigationIconResId = R.drawable.ic_cross_32,
                onNavigationClick = viewModel::onNavigationClick
            )
            Image(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .align(Alignment.Top)
                    .size(
                        width = 100.dp,
                        height = 28.dp
                    ),
                res = R.drawable.logo_polkaswap_big,
                contentDescription = null
            )
       }
    }

//    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
//        behavior.state = BottomSheetBehavior.STATE_EXPANDED
//        behavior.isHideable = false
//        behavior.skipCollapsed = true
//    }
}
