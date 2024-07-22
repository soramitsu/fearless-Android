package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsScreen
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListScreen
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class AllPoolsFragment : BaseComposeBottomSheetDialogFragment<AllPoolsViewModel>() {

    override val viewModel: AllPoolsViewModel by viewModels()

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
                }
            }.launchIn(this)
        }

        NavHost(
            startDestination = LiquidityPoolsNavGraphRoute.AllPoolsScreen.routeName,
            contentAlignment = Alignment.TopCenter,
            navController = navController,
            modifier = Modifier
                .fillMaxSize(),
        ) {

            composable(LiquidityPoolsNavGraphRoute.AllPoolsScreen.routeName) {
                val allPoolsScreenState by viewModel.state.collectAsState()
                BottomSheetScreen {
                    AllPoolsScreen(
                        state = allPoolsScreenState,
                        callback = viewModel
                    )
                }
            }

            composable(LiquidityPoolsNavGraphRoute.ListPoolsScreen.routeName) {
                val poolListState by viewModel.poolListState.collectAsState()
                BottomSheetScreen {
                    PoolListScreen(
                        state = poolListState,
                        callback = viewModel
                    )
                }
            }

            composable(LiquidityPoolsNavGraphRoute.PoolDetailsScreen.routeName) {
                val poolDetailState by viewModel.poolDetailState.collectAsState()
                BottomSheetScreen {
                    PoolDetailsScreen(
                        state = poolDetailState,
                        callbacks = viewModel
                    )
                }
            }

            composable(LiquidityPoolsNavGraphRoute.LiquidityAddScreen.routeName) {
                val liquidityAddState by viewModel.liquidityAddScreenState.collectAsState()
                BottomSheetScreen {
                    LiquidityAddScreen(liquidityAddState, viewModel)
                }
            }

            composable(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen.routeName) {
                val liquidityAddConfirmState by viewModel.liquidityAddConfirmState.collectAsStateWithLifecycle()
                BottomSheetScreen {
                    LiquidityAddConfirmScreen(liquidityAddConfirmState, viewModel)
                }
            }

            composable(LiquidityPoolsNavGraphRoute.Loading.routeName) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
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

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
