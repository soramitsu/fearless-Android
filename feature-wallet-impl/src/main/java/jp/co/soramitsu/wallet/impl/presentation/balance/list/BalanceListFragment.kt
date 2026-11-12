package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.MainToolbar
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.feedback.FeedbackCue
import jp.co.soramitsu.common.feedback.TransferFeedback
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.presentation.askPermissionsSafely
import jp.co.soramitsu.common.scan.ScanTextContract
import jp.co.soramitsu.common.scan.ScannerActivity
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.setupBuyIntegration
import kotlinx.coroutines.launch

private const val DEBUG_FEEDBACK_ARRIVAL = "FEEDBACK_ARRIVAL"
private const val DEBUG_FEEDBACK_SENT = "FEEDBACK_SENT"

@AndroidEntryPoint
class BalanceListFragment : BaseComposeFragment<BalanceListViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    override val viewModel: BalanceListViewModel by viewModels()

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> =
        registerForActivityResult(ScanTextContract()) { result ->
            result?.let {
                viewModel.qrCodeScanned(it)
            }
        }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        viewModel.onScreenVisibilityChanged(true)
        // Decode the transfer sounds ahead of their first use.
        TransferFeedback.get(requireContext()).prepare()
        if (BuildConfig.DEBUG) registerFeedbackSimulator()
    }

    override fun onPause() {
        viewModel.onScreenVisibilityChanged(false)
        unregisterFeedbackSimulator()
        super.onPause()
    }

    private var feedbackSimulator: BroadcastReceiver? = null

    /**
     * Debug builds only: lets a device walk show the arrival effect and the send success ignite
     * without real funds (`am broadcast -a <package>.debug.FEEDBACK_ARRIVAL` or `.FEEDBACK_SENT`).
     */
    private fun registerFeedbackSimulator() {
        if (feedbackSimulator != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.debugSimulateFeedback(sent = intent?.action?.endsWith(DEBUG_FEEDBACK_SENT) == true)
            }
        }
        val packageName = requireContext().packageName
        val filter = IntentFilter().apply {
            addAction("$packageName.debug.$DEBUG_FEEDBACK_ARRIVAL")
            addAction("$packageName.debug.$DEBUG_FEEDBACK_SENT")
        }
        ContextCompat.registerReceiver(requireContext(), receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        feedbackSimulator = receiver
    }

    private fun unregisterFeedbackSimulator() {
        feedbackSimulator?.let { runCatching { requireContext().unregisterReceiver(it) } }
        feedbackSimulator = null
    }

    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsState()
        val arrivalPulse by viewModel.arrivalPulse.collectAsState()

        WalletScreenWithRefresh(state, viewModel, arrivalPulse)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @ExperimentalMaterialApi
    @Composable
    override fun Toolbar(modalBottomSheetState: ModalBottomSheetState) {
        val toolbarState by viewModel.toolbarState.collectAsState()
        val toolbarModifier = if (BuildConfig.DEBUG) {
            Modifier.combinedClickable(onLongClick = viewModel::onServiceButtonClick, onClick = {})
        } else {
            Modifier
        }
        Column(modifier = toolbarModifier) {
            MainToolbar(
                state = toolbarState,
                menuItems = listOf(
                    MenuIconItem(
                        icon = R.drawable.ic_scan,
                        onClick = ::requestCameraPermission
                    ),
                    MenuIconItem(
                        icon = R.drawable.ic_search,
                        onClick = viewModel::openSearchAssets
                    )
                ),
                onChangeChainClick = viewModel::openSelectChain,
                onNavigationClick = viewModel::openWalletSelector,
                onScoreClick = viewModel::onScoreClick
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyboard()

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)
        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
        viewModel.showUpdateSecurityNotice.observeEvent { showUpdateSecurityNotice() }
        viewModel.showUniversalPasswordNotice.observeEvent(::showUniversalPasswordNotice)
        viewModel.universalPasswordSwitchResult.observeEvent { universalPasswordNotice?.showResult(it.rows) }
        viewModel.openPlayMarket.observeEvent { openPlayMarket() }
        viewModel.tokensArrived.observeEvent { symbols ->
            TransferFeedback.play(requireContext(), FeedbackCue.Received, view)
            Toast.makeText(requireContext(), getString(jp.co.soramitsu.common.R.string.fw_feedback_tokens_received, symbols.joinToString(", ")),
                Toast.LENGTH_SHORT).show()
        }
        // The hero's Buy uses the same provider chooser as asset details.
        setupBuyIntegration(viewModel)
    }

    private fun showFiatChooser(payload: DynamicListBottomSheet.Payload<FiatCurrency>) {
        FiatCurrenciesChooserBottomSheetDialog(
            requireContext(),
            imageLoader,
            payload,
            viewModel::onFiatSelected
        ).show()
    }

    private fun showUnsupportedChainAlert() {
        AlertBottomSheet.Builder(requireContext())
            .setTitle(R.string.update_needed_text)
            .setMessage(R.string.chain_unsupported_text)
            .setButtonText(R.string.common_update)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun showUpdateSecurityNotice() {
        AlertBottomSheet.Builder(requireContext())
            .setTitle(R.string.update_security_notice_title)
            .setMessage(R.string.update_security_notice_message)
            .setButtonText(R.string.common_ok)
            .setIcon(R.drawable.ic_security_shield_ok_24)
            .setCancelable(false)
            .callback { viewModel.onUpdateSecurityNoticeClosed() }
            .build()
            .show()
    }

    private var universalPasswordNotice: UniversalPasswordNoticeSheet? = null

    private fun showUniversalPasswordNotice(state: UniversalPasswordNoticeState) {
        if (universalPasswordNotice?.isShowing == true) return
        universalPasswordNotice = UniversalPasswordNoticeSheet(
            requireContext(),
            state.rows,
            onSwitch = { viewModel.onUniversalPasswordSwitch(state.metaId) },
            onClosed = {
                viewModel.onUniversalPasswordNoticeClosed(state.metaId)
                universalPasswordNotice = null
            }
        ).also { it.show() }
    }

    private fun openPlayMarket() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_APP_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_BROWSER_URI)))
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
