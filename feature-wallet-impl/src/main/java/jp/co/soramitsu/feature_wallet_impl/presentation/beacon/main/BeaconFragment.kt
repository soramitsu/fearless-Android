package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.load
import com.google.zxing.integration.android.IntentIntegrator
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.qrScanner.QrScannerActivity
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppIcon
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppName
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppUrl
import kotlinx.android.synthetic.main.fragment_beacon.beaconConnect
import kotlinx.android.synthetic.main.fragment_beacon.beaconContainer
import kotlinx.android.synthetic.main.fragment_beacon.beaconSelectedAccount
import kotlinx.android.synthetic.main.fragment_beacon.beaconToolbar
import kotlinx.android.synthetic.main.fragment_beacon.beaconWarningGroup

private const val QR_CONTENT_KEY = "QR_CONTENT_KEY"

class BeaconFragment : BaseFragment<BeaconViewModel>() {

    companion object {

        fun getBundle(qrContent: String) = Bundle().apply {
            putString(QR_CONTENT_KEY, qrContent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_beacon, container, false)

    override fun initViews() {
        beaconContainer.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        beaconToolbar.setHomeButtonListener { viewModel.back() }
        onBackPressed { viewModel.back() }
        beaconConnect.prepareForProgress(viewLifecycleOwner)
        beaconConnect.setOnClickListener { viewModel.connectClicked() }
    }

    private fun openExitDialog() {
        warningDialog(
            requireContext(),
            onConfirm = { viewModel.exit() }
        ) {
            setTitle(R.string.common_are_you_sure)
            setMessage(R.string.beacon_exit_message)
        }
    }

    override fun inject() {
        val qrContent = arguments?.getString(QR_CONTENT_KEY)
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .beaconComponentFactory()
            .create(this, qrContent)
            .inject(this)
    }

    override fun subscribe(viewModel: BeaconViewModel) {
        viewModel.state.observe {
            when (it) {
                is BeaconStateMachine.State.Connected -> {
                    val metadata = it.dAppMetadata

                    beaconAppName.text = metadata.name
                    metadata.icon?.let(beaconAppIcon::load)

                    beaconAppUrl.showValueOrHide(metadata.url)

                    beaconConnect.setState(ButtonState.NORMAL)
                    beaconConnect.setText(R.string.common_disconnect)
                    beaconWarningGroup.isVisible = false
                }
                is BeaconStateMachine.State.Initializing -> {
                    beaconConnect.setState(ButtonState.PROGRESS)
                    beaconConnect.setText(R.string.common_connect)
                    beaconAppUrl.showValueOrHide("")
                }
                is BeaconStateMachine.State.AwaitingPermissionsApproval -> {
                    beaconConnect.setState(ButtonState.NORMAL)
                    beaconConnect.setText(R.string.common_connect)
                    val metadata = it.dAppMetadata

                    beaconAppName.text = metadata.name
                    metadata.icon?.let(beaconAppIcon::load)

                    beaconAppUrl.showValueOrHide(metadata.url)
                    beaconWarningGroup.isVisible = true
                }
                is BeaconStateMachine.State.Reconnecting -> {
                    beaconConnect.setState(ButtonState.PROGRESS)
                    beaconConnect.setText(R.string.common_connect)
                    beaconAppUrl.showValueOrHide("")
                }
                is BeaconStateMachine.State.AwaitingInitialize -> {
                    beaconConnect.setState(ButtonState.PROGRESS)
                    beaconConnect.setText(R.string.common_connect)
                    val metadata = it.dAppMetadata

                    beaconAppName.text = metadata.name
                    metadata.icon?.let(beaconAppIcon::load)

                    beaconAppUrl.showValueOrHide(metadata.url)
                }
                else -> {
                    beaconAppUrl.showValueOrHide("")
                }
            }
        }

        viewModel.currentAccountAddressModel.observe {
            beaconSelectedAccount.setTitle(it.name ?: "")
            beaconSelectedAccount.setAccountIcon(it.image)
        }

        viewModel.totalBalanceLiveData.observe {
            beaconSelectedAccount.setText(it)
        }

        viewModel.scanBeaconQrEvent.observeEvent {
            val integrator = IntentIntegrator.forSupportFragment(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
                setPrompt("")
                setBeepEnabled(false)
                captureActivity = QrScannerActivity::class.java
            }

            integrator.initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        result?.contents?.let {
            viewModel.beaconQrScanned(it)
        }
    }
}
