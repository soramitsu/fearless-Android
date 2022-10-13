package jp.co.soramitsu.wallet.impl.presentation.beacon.main

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import coil.load
import com.google.zxing.integration.android.IntentIntegrator
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.qrScanner.QrScannerActivity
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentBeaconBinding

const val QR_CONTENT_KEY = "QR_CONTENT_KEY"

@AndroidEntryPoint
class BeaconFragment : BaseFragment<BeaconViewModel>(R.layout.fragment_beacon) {

    companion object {

        fun getBundle(qrContent: String) = Bundle().apply {
            putString(QR_CONTENT_KEY, qrContent)
        }
    }

    override val viewModel: BeaconViewModel by viewModels()
    private val binding by viewBinding(FragmentBeaconBinding::bind)

    override fun initViews() {
        binding.beaconContainer.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        binding.beaconToolbar.setHomeButtonListener { viewModel.back() }
        onBackPressed { viewModel.back() }
        binding.beaconConnect.prepareForProgress(viewLifecycleOwner)
        binding.beaconConnect.setOnClickListener { viewModel.connectClicked() }
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

    override fun subscribe(viewModel: BeaconViewModel) {
        viewModel.state.observe {
            when (it) {
                is BeaconStateMachine.State.Connected -> {
                    val metadata = it.dAppMetadata

                    binding.beaconAppName.text = metadata.name
                    metadata.icon?.let(binding.beaconAppIcon::load)

                    binding.beaconAppUrl.showValueOrHide(metadata.url)

                    binding.beaconConnect.setState(ButtonState.NORMAL)
                    binding.beaconConnect.setText(R.string.common_disconnect)
                    binding.beaconWarningGroup.isVisible = false
                }
                is BeaconStateMachine.State.Initializing -> {
                    binding.beaconConnect.setState(ButtonState.PROGRESS)
                    binding.beaconConnect.setText(R.string.common_connect)
                    binding.beaconAppUrl.showValueOrHide("")
                }
                is BeaconStateMachine.State.AwaitingPermissionsApproval -> {
                    binding.beaconConnect.setState(ButtonState.NORMAL)
                    binding.beaconConnect.setText(R.string.common_connect)
                    val metadata = it.dAppMetadata

                    binding.beaconAppName.text = metadata.name
                    metadata.icon?.let(binding.beaconAppIcon::load)

                    binding.beaconAppUrl.showValueOrHide(metadata.url)
                    binding.beaconWarningGroup.isVisible = true
                }
                is BeaconStateMachine.State.Reconnecting -> {
                    binding.beaconConnect.setState(ButtonState.PROGRESS)
                    binding.beaconConnect.setText(R.string.common_connect)
                    binding.beaconAppUrl.showValueOrHide("")
                }
                is BeaconStateMachine.State.AwaitingInitialize -> {
                    binding.beaconConnect.setState(ButtonState.PROGRESS)
                    binding.beaconConnect.setText(R.string.common_connect)
                    val metadata = it.dAppMetadata

                    binding.beaconAppName.text = metadata.name
                    metadata.icon?.let(binding.beaconAppIcon::load)

                    binding.beaconAppUrl.showValueOrHide(metadata.url)
                }
                else -> {
                    binding.beaconAppUrl.showValueOrHide("")
                }
            }
        }

        viewModel.currentAccountAddressModel.observe {
            binding.beaconSelectedAccount.setTitle(it.name ?: "")
            binding.beaconSelectedAccount.setAccountIcon(it.image)
        }

        viewModel.totalBalanceLiveData.observe {
            binding.beaconSelectedAccount.setText(it)
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
