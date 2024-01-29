package jp.co.soramitsu.nft.impl.presentation

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.nft.impl.presentation.collection.NFTCollectionsNavComposable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@Stable
interface NftFlowNavigationCallback {

    fun onNavigationChanged(navController: NavController, destination: String)

    fun onBackPressed(navController: NavController)

    fun onFirstScreenClick(navController: NavController)

    fun onSecondScreenClick(navController: NavController)

}

data class NFTFlowState(
    val startDestination: String,
    val currentDestination: String
)

@AndroidEntryPoint
class NftFragment : BaseComposeBottomSheetDialogFragment<NftViewModel>() {

    companion object {

        const val START_DESTINATION_KEY = "startDestinationKey"
        const val CONTRACT_ADDRESS_KEY = "contractAddress"
        const val SELECTED_CHAIN_ID = "selectedChainId"

        fun getCollectionDetailsBundle(selectedChainId: ChainId, contractAddress: String) = bundleOf(
            START_DESTINATION_KEY to "collectionDetails/{$SELECTED_CHAIN_ID}/{$CONTRACT_ADDRESS_KEY}",
            SELECTED_CHAIN_ID to selectedChainId,
            CONTRACT_ADDRESS_KEY to contractAddress
        )

        fun buildCollectionDetailsDestination(contractAddress: String): String {
            return "collectionDetails/$contractAddress"
        }
    }

    override val viewModel: NftViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val navController = rememberNavController()
        val flowState: NFTFlowState by viewModel.state.collectAsStateWithLifecycle()


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
            startDestination = flowState.startDestination,
            contentAlignment = Alignment.TopCenter,
            navController = navController,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            NFTCollectionsNavComposable(arguments)

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

