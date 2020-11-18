package jp.co.soramitsu.feature_account_impl.presentation.pincode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.interfaces.BackButtonListener
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintWrapper
import kotlinx.android.synthetic.main.fragment_import_account.toolbar
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeView
import javax.inject.Inject

class PincodeFragment : BaseFragment<PinCodeViewModel>(), BackButtonListener {

    companion object {
        private const val KEY_PINCODE_ACTION = "pincode_action"

        fun getPinCodeBundle(pinCodeAction: PinCodeAction): Bundle {
            return Bundle().apply {
                putParcelable(KEY_PINCODE_ACTION, pinCodeAction)
            }
        }
    }

    @Inject lateinit var fingerprintWrapper: FingerprintWrapper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pincode, container, false)
    }

    override fun inject() {
        val navigationFlow = argument<PinCodeAction>(KEY_PINCODE_ACTION)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .pincodeComponentFactory()
            .create(this, navigationFlow)
            .inject(this)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.backPressed() }

        viewModel.fingerprintScannerAvailable(fingerprintWrapper.isAuthReady())

        with(pinCodeView) {
            pinCodeEnteredListener = { viewModel.pinCodeEntered(it) }
            fingerprintClickListener = { fingerprintWrapper.toggleScanner() }
        }
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        viewModel.startFingerprintScannerEventLiveData.observeEvent {
            if (fingerprintWrapper.isAuthReady()) {
                fingerprintWrapper.startAuth()
            }
        }

        viewModel.biometricSwitchDialogLiveData.observeEvent {
            showAuthWithBiometryDialog()
        }

        viewModel.showFingerPrintEvent.observeEvent {
            pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady())
        }

        viewModel.fingerPrintErrorEvent.observeEvent {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }

        viewModel.finisAppEvent.observeEvent {
            requireActivity().finish()
        }

        viewModel.homeButtonVisibilityLiveData.observe(toolbar::setHomeButtonVisibility)

        viewModel.matchingPincodeErrorEvent.observeEvent {
            pinCodeView.pinCodeMatchingError()
        }

        viewModel.resetInputEvent.observeEvent {
            pinCodeView.resetInput()
            pinCodeView.setTitle(it)
        }

        viewModel.startAuth()
    }

    private fun showAuthWithBiometryDialog() {
        MaterialAlertDialogBuilder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(R.string.pincode_biometry_dialog_title)
            .setMessage(R.string.pincode_fingerprint_switch_dialog_title)
            .setPositiveButton(R.string.common_use) { _, _ ->
                viewModel.acceptAuthWithBiometry()
            }
            .setNegativeButton(R.string.common_skip) { _, _ ->
                viewModel.declineAuthWithBiometry()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        fingerprintWrapper.cancel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onBackButtonPressed() {
        viewModel.backPressed()
    }
}