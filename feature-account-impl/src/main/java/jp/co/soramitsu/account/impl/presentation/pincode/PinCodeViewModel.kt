package jp.co.soramitsu.account.impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/** PIN recovery never turns an unreadable stored PIN into first-run setup. */
@HiltViewModel
class PinCodeViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val deviceVibrator: DeviceVibrator,
    private val resourceManager: ResourceManager,
    savedStateHandle: SavedStateHandle,
    private val pinRecovery: WalletPinRecovery
) : BaseViewModel() {
    sealed class ScreenState {
        object Loading : ScreenState()
        object Recovering : ScreenState()
        object Creating : ScreenState()
        data class Confirmation(val codeToConfirm: String) : ScreenState() {
            override fun toString(): String = "Confirmation"
        }
        object Checking : ScreenState()
    }

    data class RecoveryState(
        val visible: Boolean = true,
        val loading: Boolean = true,
        val strongBiometric: Boolean = false,
        val deviceCredential: Boolean = false,
        val choices: List<WalletPinRecovery.Choice> = emptyList(),
        val error: Boolean = false
    )

    val biometricPromptGate = PinBiometricPromptGate()
    val pinCodeAction = savedStateHandle.get<PinCodeAction>(PincodeFragment.KEY_PINCODE_ACTION)!!
    private val _homeButtonVisibilityLiveData = MutableLiveData(pinCodeAction.toolbarConfiguration.backVisible)
    val homeButtonVisibilityLiveData: LiveData<Boolean> = _homeButtonVisibilityLiveData
    private val _resetInputEvent = MutableLiveData<Event<String>>()
    val resetInputEvent: LiveData<Event<String>> = _resetInputEvent
    private val _matchingPincodeErrorEvent = MutableLiveData<Event<Unit>>()
    val matchingPincodeErrorEvent: LiveData<Event<Unit>> = _matchingPincodeErrorEvent
    private val _showFingerPrintEvent = MutableLiveData<Event<Unit>>()
    val showFingerPrintEvent: LiveData<Event<Unit>> = _showFingerPrintEvent
    private val _startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData: LiveData<Event<Unit>> = _startFingerprintScannerEventLiveData
    private val _fingerPrintErrorEvent = MutableLiveData<Event<String>>()
    val fingerPrintErrorEvent: LiveData<Event<String>> = _fingerPrintErrorEvent
    private val _biometricSwitchDialogLiveData = MutableLiveData<Event<Unit>>()
    val biometricSwitchDialogLiveData: LiveData<Event<Unit>> = _biometricSwitchDialogLiveData
    val recoveryState = MutableLiveData(RecoveryState())
    private var fingerPrintAvailable = false
    private var strongAvailable = false
    private var credentialAvailable = false
    private var biometricPreviouslyEnabled = false
    private var currentState: ScreenState? = null
    // Deliberately absent from SavedStateHandle: restoring a screen cannot restore authorization.
    private var challenge: String? = null
    private var busy = false

    fun startAuth() {
        if (currentState != null) return
        currentState = ScreenState.Loading
        viewModelScope.launch {
            try {
                when (interactor.getPinHealth()) {
                    PinHealth.UNREADABLE -> startRecovery()
                    PinHealth.ABSENT -> {
                        // Only the genuine onboarding route may create the first PIN.
                        check(pinCodeAction is PinCodeAction.Create)
                        showPinCreation()
                    }
                    PinHealth.HEALTHY -> {
                        currentState = ScreenState.Checking
                        recoveryState.value = RecoveryState(visible = false)
                        _showFingerPrintEvent.value = Event(Unit)
                        onResume()
                    }
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
        }
    }

    private suspend fun startRecovery() {
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = ScreenState.Recovering
        recoveryState.value = RecoveryState()
        biometricPreviouslyEnabled = interactor.isBiometricEnabled()
        val id = pinRecovery.begin()
        challenge = id
        val choices = pinRecovery.choices(id)
        recoveryState.value = RecoveryState(loading = false, choices = choices,
            strongBiometric = strongAvailable && biometricPreviouslyEnabled, deviceCredential = credentialAvailable)
    }

    fun configureRecoveryAuthentication(strong: Boolean, deviceCredential: Boolean) {
        strongAvailable = strong
        credentialAvailable = deviceCredential
        recoveryState.value = recoveryState.value?.copy(
            strongBiometric = strong && biometricPreviouslyEnabled, deviceCredential = deviceCredential)
    }

    fun retryRecovery() {
        if (busy) return
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = null
        recoveryState.value = RecoveryState()
        startAuth()
    }

    fun verifyBackup(target: RecoveryTarget, mode: ImportMode) {
        val id = challenge ?: return
        if (!pinRecovery.isActive(id)) { recoveryFailed(); return }
        if (currentState == ScreenState.Recovering && !busy && recoveryState.value?.choices?.any { it.target == target } == true) {
            router.openPinRecoveryImport(target, mode, id)
        }
    }

    fun strongBiometricSucceeded() {
        val id = challenge ?: return
        if (currentState != ScreenState.Recovering || !strongAvailable || !biometricPreviouslyEnabled || busy) return
        busy = true
        viewModelScope.launch {
            try {
                pinRecovery.strongBiometricSucceeded(id)
                showPinCreation()
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
    }

    fun deviceCredentialSucceeded() {
        if (currentState != ScreenState.Recovering || !credentialAvailable || challenge == null || busy) return
        challenge?.let(pinRecovery::cancel)
        challenge = null
        WalletViewOnlyAccess.enter()
        // Never resume a pending send, export, dapp request, or other private action.
        router.openMain()
    }

    fun pinCodeEntered(pin: String) {
        if (busy || pin.length != 6 || pin.any { it !in '0'..'9' }) return
        when (val state = currentState) {
            ScreenState.Creating -> {
                _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_confirm_your_pin_code))
                _homeButtonVisibilityLiveData.value = true
                currentState = ScreenState.Confirmation(pin)
            }
            is ScreenState.Confirmation -> if (state.codeToConfirm == pin) registerPinCode(pin) else mismatch()
            ScreenState.Checking -> checkPinCode(pin)
            else -> Unit
        }
    }

    private fun registerPinCode(code: String) {
        busy = true
        viewModelScope.launch {
            try {
                val id = challenge
                if (id == null) interactor.savePin(code) else {
                    pinRecovery.saveNewPin(id, code)
                    challenge = null
                }
                if (fingerPrintAvailable && pinCodeAction is PinCodeAction.Create && id == null) {
                    _biometricSwitchDialogLiveData.value = Event(Unit)
                } else authSuccess()
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
    }

    private fun checkPinCode(code: String) {
        busy = true
        viewModelScope.launch {
            try {
                if (interactor.getPinHealth() == PinHealth.UNREADABLE) startRecovery()
                else if (interactor.isPinCorrect(code)) authSuccess() else mismatch()
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
    }

    private fun mismatch() {
        deviceVibrator.makeShortVibration()
        _matchingPincodeErrorEvent.value = Event(Unit)
    }

    private fun showPinCreation() {
        currentState = ScreenState.Creating
        recoveryState.value = RecoveryState(visible = false)
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_create_top_title))
        _homeButtonVisibilityLiveData.value = pinCodeAction.toolbarConfiguration.backVisible
    }

    private fun recoveryFailed() {
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = ScreenState.Recovering
        recoveryState.value = RecoveryState(loading = false, error = true)
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_pin_code))
    }

    fun backPressed() {
        if (busy) return
        if (currentState is ScreenState.Confirmation) showPinCreation()
        else if (pinCodeAction.toolbarConfiguration.backVisible) {
            challenge?.let(pinRecovery::cancel)
            challenge = null
            router.back()
        }
    }

    fun onResume() {
        viewModelScope.launch {
            try {
                if (challenge != null && !pinRecovery.isActive(checkNotNull(challenge))) recoveryFailed()
                else if (currentState == ScreenState.Recovering && challenge?.let(pinRecovery::isAuthorized) == true) showPinCreation()
                else if (currentState == ScreenState.Checking && interactor.getPinHealth() == PinHealth.HEALTHY && interactor.isBiometricEnabled()) {
                    _startFingerprintScannerEventLiveData.value = Event(Unit)
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
        }
    }

    /** Temporary PIN confirmation material never survives a stopped screen. */
    fun screenStopped() {
        if (currentState is ScreenState.Confirmation) {
            if (busy) currentState = ScreenState.Confirmation("") else showPinCreation()
        }
    }

    fun onAuthenticationError(errString: String) { _fingerPrintErrorEvent.value = Event(errString) }

    fun biometryAuthenticationSucceeded() {
        if (currentState != ScreenState.Checking || busy) return
        busy = true
        viewModelScope.launch {
            try {
                if (interactor.getPinHealth() == PinHealth.HEALTHY && interactor.isBiometricEnabled()) authSuccess()
                else if (interactor.getPinHealth() == PinHealth.UNREADABLE) startRecovery()
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
    }

    fun biometryAuthenticationFailed() {
        _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_fingerprint_error))
    }

    fun fingerprintScannerAvailable(authReady: Boolean) { fingerPrintAvailable = authReady }

    private fun authSuccess() {
        WalletViewOnlyAccess.pinResetCompleted()
        when (pinCodeAction) {
            is PinCodeAction.Create -> router.openAfterPinCode(pinCodeAction.delayedNavigation)
            is PinCodeAction.Check -> if (pinCodeAction.delayedNavigation == null) router.back()
                else router.openAfterPinCode(pinCodeAction.delayedNavigation)
            is PinCodeAction.Change -> when (currentState) {
                ScreenState.Checking -> showPinCreation()
                is ScreenState.Confirmation -> {
                    router.back()
                    showMessage(resourceManager.getString(R.string.pincode_changed_message))
                }
                else -> Unit
            }
        }
    }

    fun acceptAuthWithBiometry() {
        viewModelScope.launch { interactor.setBiometricOn(); authSuccess() }
    }

    fun declineAuthWithBiometry() {
        viewModelScope.launch { interactor.setBiometricOff(); authSuccess() }
    }

    override fun onCleared() {
        biometricPromptGate.cancel()
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = null
        super.onCleared()
    }
}
