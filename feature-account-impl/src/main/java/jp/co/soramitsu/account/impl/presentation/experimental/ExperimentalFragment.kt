package jp.co.soramitsu.account.impl.presentation.experimental

import android.content.Intent
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.zxing.integration.android.IntentIntegrator
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.qrScanner.QrScannerActivity
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExperimentalBinding

@AndroidEntryPoint
class ExperimentalFragment : BaseFragment<ExperimentalViewModel>(R.layout.fragment_experimental) {

    private val binding by viewBinding(FragmentExperimentalBinding::bind)
    override val viewModel: ExperimentalViewModel by viewModels()

    override fun initViews() {
        binding.experimentsBeaconDappClickView.setOnClickListener { viewModel.onBeaconClicked() }
        binding.experimentsBackButton.setOnClickListener { viewModel.backClicked() }
    }

    override fun subscribe(viewModel: ExperimentalViewModel) {
        setBeaconVisibility(false)
        viewModel.state.observeState<ExperimentalState> { state ->
            setBeaconVisibility(state.beaconDapp != null)

            state.beaconDapp?.let {
                binding.experimentsBeaconDapp.text = it.name
            }
        }

        viewModel.scanBeaconQrEvent.observeEvent {
            val integrator = IntentIntegrator.forSupportFragment(this).apply {
                setPrompt("")
                setBeepEnabled(false)
                captureActivity = QrScannerActivity::class.java
            }

            integrator.initiateScan()
        }
    }

    private fun setBeaconVisibility(isVisible: Boolean) {
        binding.experimentsBeaconDapp.isVisible = isVisible
//        experimentsBeaconDappStatus.isVisible = isVisible
        binding.experimentsBeaconDappClickView.isVisible = isVisible
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        result?.contents?.let {
            viewModel.beaconQrScanned(it)
        }
    }
}
