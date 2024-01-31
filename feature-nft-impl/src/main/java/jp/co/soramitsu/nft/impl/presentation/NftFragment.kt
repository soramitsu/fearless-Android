package jp.co.soramitsu.nft.impl.presentation

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.utils.shareText
import jp.co.soramitsu.nft.impl.presentation.NftNavigator.NFT_DETAILS_ROUTE
import jp.co.soramitsu.nft.impl.presentation.collection.NFTCollectionsNavComposable
import jp.co.soramitsu.nft.impl.presentation.collection.NftCollectionViewModel.Companion.COLLECTION_CONTRACT_ADDRESS_KEY
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsScreen
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsViewModel
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsViewModel.Companion.CHAIN_ID
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsViewModel.Companion.TOKEN_ID
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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

    override val viewModel: NftViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val navController = rememberNavController()
        val flowState: NFTFlowState by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            NftNavigator.sharedFlow.onEach {
                navController.navigate(it)
            }.launchIn(this)
        }

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

            composable(
                NFT_DETAILS_ROUTE,
                arguments = listOf(
                    navArgument(CHAIN_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(COLLECTION_CONTRACT_ADDRESS_KEY) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(TOKEN_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                val viewModel: NftDetailsViewModel by viewModels<NftDetailsViewModel>(extrasProducer = {
                    MutableCreationExtras(defaultViewModelCreationExtras).apply {
                        set(DEFAULT_ARGS_KEY, it.arguments ?: Bundle.EMPTY)
                    }
                })

                LaunchedEffect(Unit) {
                    viewModel.shareState.onEach {
                        if (it.isNotEmpty()) {
                            requireActivity().shareText(it)
                        }
                    }.launchIn(this)
                }

                NftDetailsScreen(viewModel = viewModel)
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
}
