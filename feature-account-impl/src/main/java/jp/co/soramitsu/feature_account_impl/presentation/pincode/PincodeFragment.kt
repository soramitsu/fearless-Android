package jp.co.soramitsu.feature_account_impl.presentation.pincode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentPincodeBinding
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintCallback
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintWrapper
import javax.inject.Inject

@AndroidEntryPoint
class PincodeFragment : BaseFragment<PinCodeViewModel>() {

    companion object {
        private const val KEY_PINCODE_ACTION = "pincode_action"

        fun getPinCodeBundle(pinCodeAction: PinCodeAction) = bundleOf(KEY_PINCODE_ACTION to pinCodeAction)
    }

    private val fingerprintWrapper: FingerprintWrapper by lazy {
        val biometricManager = BiometricManager.from(context?.applicationContext!!)
        val biometricPrompt =
            BiometricPrompt(this, MainThreadExecutor(), FingerprintCallback(viewModel))
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.pincode_biometry_dialog_title))
            .setNegativeButtonText(getString(R.string.common_cancel))
            .build()
        FingerprintWrapper(
            biometricManager,
            biometricPrompt,
            promptInfo
        )
    }

    private lateinit var binding: FragmentPincodeBinding

    @Inject
    lateinit var factory: PinCodeViewModel.PinCodeViewModelFactory

    private val vm: PinCodeViewModel by viewModels {
        PinCodeViewModel.provideFactory(
            factory,
            argument(KEY_PINCODE_ACTION)
        )
    }
    override val viewModel: PinCodeViewModel
        get() = vm

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.backPressed()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPincodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)

        binding.toolbar.setHomeButtonListener { viewModel.backPressed() }

        viewModel.fingerprintScannerAvailable(fingerprintWrapper.isAuthReady())

        with(binding.pinCodeView) {
            pinCodeEnteredListener = { viewModel.pinCodeEntered(it) }
            fingerprintClickListener = { fingerprintWrapper.toggleScanner() }
        }
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        viewModel.pinCodeAction.toolbarConfiguration.titleRes?.let {
            binding.toolbar.setTitle(getString(it))
        }

        viewModel.startFingerprintScannerEventLiveData.observeEvent {
            if (fingerprintWrapper.isAuthReady()) {
                fingerprintWrapper.startAuth()
            }
        }

        viewModel.biometricSwitchDialogLiveData.observeEvent {
            showAuthWithBiometryDialog()
        }

        viewModel.showFingerPrintEvent.observeEvent {
            binding.pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady())
        }

        viewModel.fingerPrintErrorEvent.observeEvent {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }

        viewModel.homeButtonVisibilityLiveData.observe(binding.toolbar::setHomeButtonVisibility)

        viewModel.matchingPincodeErrorEvent.observeEvent {
            binding.pinCodeView.pinCodeMatchingError()
        }

        viewModel.resetInputEvent.observeEvent {
            binding.pinCodeView.resetInput()
            binding.pinCodeView.setTitle(it)
        }

        viewModel.startAuth()
    }

    private fun showAuthWithBiometryDialog() {
        MaterialAlertDialogBuilder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(R.string.pincode_biometry_dialog_title)
            .setMessage(R.string.pincode_fingerprint_switch_dialog_title)
            .setCancelable(false)
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
}
