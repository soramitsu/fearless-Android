package jp.co.soramitsu.nft.impl.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetDialog
import jp.co.soramitsu.common.compose.component.MainToolbarShimmer
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.models.retrieveString
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.askPermissionsSafely
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.common.utils.shareText
import jp.co.soramitsu.nft.impl.navigation.NavAction
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.ChooseNFTRecipientNavComposable
import jp.co.soramitsu.nft.impl.presentation.collection.CollectionNFTsNavComposable
import jp.co.soramitsu.nft.impl.presentation.confirmsend.ConfirmNFTSendNavComposable
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsNavComposable
import jp.co.soramitsu.nft.navigation.NestedNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
@Suppress("ClassOrdering")
class NFTFlowFragment : BaseComposeBottomSheetDialogFragment<NFTFlowViewModel>() {

    companion object {
        const val SELECTED_CHAIN_ID = "selectedChainId"
        const val CONTRACT_ADDRESS_KEY = "contractAddress"
        const val COLLECTION_NAME = "collectionName"

        fun getCollectionDetailsBundle(
            selectedChainId: ChainId,
            contractAddress: String,
            collectionName: String
        ) = bundleOf(
            SELECTED_CHAIN_ID to selectedChainId,
            CONTRACT_ADDRESS_KEY to contractAddress,
            COLLECTION_NAME to collectionName
        )
    }

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> =
        registerForActivityResult(
            ScanTextContract()
        ) { viewModel.onQRCodeScannerResult(it) }

    override val viewModel: NFTFlowViewModel by viewModels()

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

    private fun requestCameraPermission() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!askPermissionsSafely(Manifest.permission.CAMERA).isSuccess) {
                return@launch
            }

            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                .setPrompt("")
                .setBeepEnabled(false)
                .setCaptureActivity(ScannerActivity::class.java)

            barcodeLauncher.launch(options)
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
                        val loadingRoute = NestedNavGraphRoute.Loading.routeName

                        if (currentRoute == loadingRoute || !isBackNavigationSuccess) {
                            viewModel.exitFlow()
                        }
                    }

                    is NavAction.QRCodeScanner ->
                        requestCameraPermission()

                    is NavAction.ShowError ->
                        showErrorDialog(
                            title = it.errorTitle ?: resources.getString(R.string.common_error_general_title),
                            message = it.errorText,
                            positiveClick = viewModel::onNavigationClick,
                            negativeClick = viewModel::onNavigationClick,
                            onBackClick = viewModel::onNavigationClick
                        )

                    is NavAction.ShowToast ->
                        showMessage(it.toastMessage)

                    is NavAction.ShareText ->
                        requireActivity().shareText(it.text)
                }
            }.launchIn(this)
        }

        BottomSheetDialog(
            modifier = Modifier.fillMaxSize()
        ) {
            when (val loadingState = toolbarState.value) {
                is LoadingState.Loaded<Pair<TextModel, Int>> ->
                    ToolbarBottomSheet(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = loadingState.data.first.retrieveString(),
                        navigationIconResId = loadingState.data.second,
                        onNavigationClick = remember {
                            { viewModel.onNavigationClick() }
                        }
                    )

                is LoadingState.Loading<Pair<TextModel, Int>> ->
                    MainToolbarShimmer(
                        homeIconState = ToolbarHomeIconState()
                    )
            }

            MarginVertical(margin = 24.dp)
            NavHost(
                startDestination = NestedNavGraphRoute.Loading.routeName,
                contentAlignment = Alignment.TopCenter,
                navController = navController,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                CollectionNFTsNavComposable(
                    viewsListFlow = viewModel.collectionNFTsScreenState,
                    pageScrollingCallback = viewModel.pageScrollingCallback
                )

                NftDetailsNavComposable(
                    stateFlow = viewModel.nftDetailsScreenState,
                    screenInterface = viewModel
                )

                ChooseNFTRecipientNavComposable(
                    stateFlow = viewModel.recipientChooserScreenState,
                    callback = viewModel
                )

                ConfirmNFTSendNavComposable(
                    stateFlow = viewModel.confirmSendScreenState,
                    callback = viewModel
                )

                composable(NestedNavGraphRoute.Loading.routeName) {
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
}
