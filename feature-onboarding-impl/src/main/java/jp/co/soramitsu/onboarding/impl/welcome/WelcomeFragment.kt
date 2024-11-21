package jp.co.soramitsu.onboarding.impl.welcome

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.askPermissionsSafely
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.common.utils.isGooglePlayServicesAvailable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WelcomeFragment : BaseComposeFragment<WelcomeViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "key_payload"

        fun getBundle(
            displayBack: Boolean,
            chainAccountData: ChainAccountCreatePayload? = null,
            route: String? = null
        ): Bundle {
            return bundleOf(
                KEY_PAYLOAD to WelcomeFragmentPayload(displayBack, chainAccountData, route)
            )
        }
    }
//    private val startRoute = arguments?.let { it.getParcelable(KEY_PAYLOAD, WelcomeFragmentPayload::class.java)?.route }
    override val viewModel: WelcomeViewModel by viewModels()
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> viewModel.openAddWalletThroughGoogleScreen()
            Activity.RESULT_CANCELED -> { /* no action */ }
            else -> {
                val googleSignInStatus = result.data?.extras?.get("googleSignInStatus")
                viewModel.onGoogleLoginError(googleSignInStatus.toString())
            }
        }
    }

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(
        ScanTextContract()
    ) { result ->
        viewModel.onQrScanResult(result)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeBrowserEvents(viewModel)
    }

    private fun handleAuthorizeGoogleEvent() {
        viewModel.authorizeGoogle(launcher = launcher)
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val isGoogleAvailable = context?.isGooglePlayServicesAvailable() == true
        val navController = rememberNavController()

//        val sr = arguments?.let { it.getParcelable(KEY_PAYLOAD, WelcomeFragmentPayload::class.java)?.route }

        LaunchedEffect(Unit) {
            viewModel.events
                .onEach { event ->
                    when (event) {
                        WelcomeEvent.AuthorizeGoogle ->
                            handleAuthorizeGoogleEvent()

                        WelcomeEvent.ScanQR ->
                            requestCameraPermission()

                        is WelcomeEvent.Onboarding ->
                            when (event) {
                                is WelcomeEvent.Onboarding.WelcomeScreen -> {
                                    navController.navigate(route = event.route.replace("{accountType}", event.accountType.name))
                                }
                                else -> navController.navigate(event.route)
                            }
                    }
                }.launchIn(this)
        }

        FearlessAppTheme {
            NavHost(
                startDestination = viewModel.startDestination,
                contentAlignment = Alignment.TopCenter,
                navController = navController,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {

                OnboardingSplashScreen()

                SelectEcosystemScreen(listener = viewModel)

                OnboardingScreen(
                    backgroundImageFlow = viewModel.onboardingBackground,
                    onboardingStateFlow = viewModel.onboardingFlowState,
                    callback = viewModel
                )

                WelcomeScreen(
                    welcomeStateFlow = viewModel.state,
                    isGoogleAvailable = isGoogleAvailable,
                    callbacks = viewModel
                )
            }
        }
    }

    private fun requestCameraPermission() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = askPermissionsSafely(Manifest.permission.CAMERA)

            if (result.isSuccess) {
                initiateCameraScanner()
            }
        }
    }

    private fun initiateCameraScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            .setPrompt("")
            .setBeepEnabled(false)
            .setCaptureActivity(ScannerActivity::class.java)
        barcodeLauncher.launch(options)
    }
}
