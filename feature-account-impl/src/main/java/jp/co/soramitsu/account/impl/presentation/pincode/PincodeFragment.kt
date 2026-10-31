package jp.co.soramitsu.account.impl.presentation.pincode

import jp.co.soramitsu.account.impl.presentation.pincode.PinBiometricPromptGate.Kind as Prompt
import android.os.Bundle
import android.os.Build
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.account.impl.presentation.common.SecureScreenGuard
import jp.co.soramitsu.account.impl.presentation.recovery.excludeRecoveryInputFromPersistence
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.io.MainThreadExecutor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentPincodeBinding
import jp.co.soramitsu.account.impl.presentation.pincode.fingerprint.FingerprintWrapper
import jp.co.soramitsu.common.presentation.ErrorDialog

@AndroidEntryPoint
class PincodeFragment : BaseFragment<PinCodeViewModel>() {

    companion object {
        const val KEY_PINCODE_ACTION = "pincode_action"

        fun getPinCodeBundle(pinCodeAction: PinCodeAction) = bundleOf(KEY_PINCODE_ACTION to pinCodeAction)
    }

    private val promptGate get() = viewModel.biometricPromptGate
    private var credentialIntentPending = false
    private val recoveryContent = mutableStateOf(PinCodeViewModel.RecoveryState())
    private val credentialResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = credentialIntentPending
        credentialIntentPending = false
        if (pending && result.resultCode == Activity.RESULT_OK) viewModel.deviceCredentialSucceeded()
    }
    private val biometricPrompt by lazy {
        BiometricPrompt(this, MainThreadExecutor(), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val prompt = promptGate.finish()
                when (prompt) {
                    Prompt.NORMAL -> viewModel.biometryAuthenticationSucceeded()
                    Prompt.STRONG_RECOVERY -> if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                        viewModel.strongBiometricSucceeded()
                    }
                    Prompt.DEVICE_CREDENTIAL -> if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                        viewModel.deviceCredentialSucceeded()
                    }
                    null -> Unit
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptGate.finish()
                if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) viewModel.onAuthenticationError(errString.toString())
            }
            override fun onAuthenticationFailed() { viewModel.biometryAuthenticationFailed() }
        })
    }

    private val fingerprintWrapper: FingerprintWrapper by lazy {
        val biometricManager = BiometricManager.from(context?.applicationContext!!)
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

    // The PIN pad and recovery prompts stay out of screen captures; the guard hands the flag to
    // the next secure screen instead of clearing it underneath that screen.
    private val secureScreen = SecureScreenGuard(this)
    private lateinit var binding: FragmentPincodeBinding

    override val viewModel: PinCodeViewModel by viewModels()

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.backPressed()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPincodeBinding.inflate(inflater, container, false)
        binding.root.excludeRecoveryInputFromPersistence()
        binding.pinRecoveryContent.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.pinRecoveryContent.setContent {
            FearlessTheme {
                PinRecoveryScreen(recoveryContent.value, { startRecoveryPrompt(Prompt.STRONG_RECOVERY) },
                    { startRecoveryPrompt(Prompt.DEVICE_CREDENTIAL) }, viewModel::verifyBackup, viewModel::retryRecovery)
            }
        }
        return binding.root
    }

    override fun initViews() {
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)

        binding.toolbar.setHomeButtonListener { viewModel.backPressed() }

        viewModel.fingerprintScannerAvailable(fingerprintWrapper.isAuthReady())
        val manager = BiometricManager.from(requireContext())
        val keyguard = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        viewModel.configureRecoveryAuthentication(
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS,
            keyguard.isDeviceSecure
        )

        with(binding.pinCodeView) {
            pinCodeEnteredListener = { viewModel.pinCodeEntered(it) }
            fingerprintClickListener = { startNormalPrompt() }
        }
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        viewModel.pinCodeAction.toolbarConfiguration.titleRes?.let {
            binding.toolbar.setTitle(getString(it))
        }

        viewModel.startFingerprintScannerEventLiveData.observeEvent {
            startNormalPrompt()
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

        viewModel.pinRetryDelayEvent.observeEvent {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }

        viewModel.resetInputEvent.observeEvent {
            binding.pinCodeView.resetInput()
            binding.pinCodeView.setTitle(it)
            binding.pinCodeView.changeFingerPrintButtonVisibility(false)
        }

        viewModel.recoveryState.observe { state ->
            recoveryContent.value = state
            binding.pinRecoveryContent.isVisible = state.visible
            binding.pinCodeView.isVisible = !state.visible
            if (state.visible) binding.pinCodeView.resetInput()
        }
        viewModel.startAuth()
    }

    private fun startNormalPrompt() {
        if (recoveryContent.value.visible || !fingerprintWrapper.isAuthReady() || !promptGate.begin(Prompt.NORMAL)) return
        fingerprintWrapper.startAuth()
    }

    private fun startRecoveryPrompt(prompt: Prompt) {
        val state = recoveryContent.value
        if (promptGate.inFlight() != null || !state.visible || state.loading || state.error) return
        if (prompt == Prompt.STRONG_RECOVERY && !state.strongBiometric) return
        if (prompt == Prompt.DEVICE_CREDENTIAL && !state.deviceCredential) return
        if (prompt == Prompt.DEVICE_CREDENTIAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val manager = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION")
            val intent = manager.createConfirmDeviceCredentialIntent(getString(R.string.pin_recovery_view_only), null) ?: return
            credentialIntentPending = true
            credentialResult.launch(intent)
            return
        }
        if (!promptGate.begin(prompt)) return
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(if (prompt == Prompt.STRONG_RECOVERY) R.string.pin_recovery_biometric else R.string.pin_recovery_view_only))
            .setAllowedAuthenticators(if (prompt == Prompt.STRONG_RECOVERY) BiometricManager.Authenticators.BIOMETRIC_STRONG
                else BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (prompt == Prompt.STRONG_RECOVERY) builder.setNegativeButtonText(getString(R.string.common_cancel))
        biometricPrompt.authenticate(builder.build())
    }

    private fun showAuthWithBiometryDialog() {
        val res = requireContext().resources
        ErrorDialog(
            isHideable = false,
            title = res.getString(R.string.pincode_biometry_dialog_title),
            message = res.getString(R.string.pincode_fingerprint_switch_dialog_title),
            positiveButtonText = res.getString(R.string.common_use),
            negativeButtonText = res.getString(R.string.common_skip),
            positiveClick = viewModel::acceptAuthWithBiometry,
            negativeClick = viewModel::declineAuthWithBiometry
        ).show(childFragmentManager)
    }

    override fun onPause() {
        super.onPause()
        // Normal/biometric prompts are canceled when the app loses focus. The
        // platform device-credential activity itself pauses this fragment.
        if (promptGate.inFlight() != Prompt.DEVICE_CREDENTIAL) {
            promptGate.cancel()
            fingerprintWrapper.cancel()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        secureScreen.onViewCreated()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        secureScreen.onStart()
    }

    override fun onDestroyView() {
        promptGate.cancel()
        biometricPrompt.cancelAuthentication()
        secureScreen.onDestroyView()
        super.onDestroyView()
    }

    override fun onStop() {
        viewModel.screenStopped()
        binding.pinCodeView.resetInput()
        secureScreen.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
