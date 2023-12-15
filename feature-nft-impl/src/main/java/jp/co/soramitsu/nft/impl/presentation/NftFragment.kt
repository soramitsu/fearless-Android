package jp.co.soramitsu.nft.impl.presentation

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.theme.FearlessTheme

@Stable
interface NftFlowNavigationCallback {

    fun onNavigationChanged(navController: NavController, destination: String)

    fun onBackPressed(navController: NavController)

    fun onFirstScreenClick(navController: NavController)

    fun onSecondScreenClick(navController: NavController)

}

@AndroidEntryPoint
class NftFragment: BaseComposeBottomSheetDialogFragment<NftViewModel>() {
    override val viewModel: NftViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val navController = rememberNavController()

        SetupNavDestinationChangedListener(
            navController = navController,
            flowNavigationCallback = viewModel
        )

        BackHandler(
            onBack = remember {
                { viewModel.onBackPressed(navController) }
            }
        )

        NavHost(
            startDestination = "TODO add start screen",
            contentAlignment = Alignment.TopCenter,
            navController = navController,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            composable("TODO add start screen") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewModel.onFirstScreenClick(navController) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Screen One")
                }
            }

            composable("TODO another screen") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewModel.onSecondScreenClick(navController) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Screen Two")
                }
            }
        }
    }

    @Composable
    @Suppress("NOTHING_TO_INLINE") // Disposable effect should depend on parent Composable
    private inline fun SetupNavDestinationChangedListener(
        navController: NavController,
        flowNavigationCallback: NftFlowNavigationCallback
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val onDestinationChangedListener =
                NavController.OnDestinationChangedListener { navController, destination, _ ->
                    flowNavigationCallback.onNavigationChanged(navController, destination.route!!)
                }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when(event) {
                    Lifecycle.Event.ON_START ->
                        navController.addOnDestinationChangedListener(onDestinationChangedListener)

                    Lifecycle.Event.ON_STOP ->
                        navController.removeOnDestinationChangedListener(onDestinationChangedListener)

                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
        }
    }
}

