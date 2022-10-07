package jp.co.soramitsu.feature_account_impl.presentation.experimental

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.zxing.integration.android.IntentIntegrator
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.qrScanner.QrScannerActivity
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_experimental.experimentsBackButton
import kotlinx.android.synthetic.main.fragment_experimental.experimentsBeaconDapp
import kotlinx.android.synthetic.main.fragment_experimental.experimentsBeaconDappClickView

class ExperimentalFragment : BaseFragment<ExperimentalViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_experimental, container, false)
    }

    override fun initViews() {
        experimentsBeaconDappClickView.setOnClickListener { viewModel.onBeaconClicked() }
        experimentsBackButton.setOnClickListener { viewModel.backClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .experimentalComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ExperimentalViewModel) {
        viewModel.state.observeState<ExperimentalState> { state ->
            setBeaconVisibility(state.beaconDapp != null)

            state.beaconDapp?.let {
                experimentsBeaconDapp.text = it.name
            }
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

    private fun setBeaconVisibility(isVisible: Boolean) {
        experimentsBeaconDapp.isVisible = isVisible
//        experimentsBeaconDappStatus.isVisible = isVisible
        experimentsBeaconDappClickView.isVisible = isVisible
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        result?.contents?.let {
            viewModel.beaconQrScanned(it)
        }
    }
}
