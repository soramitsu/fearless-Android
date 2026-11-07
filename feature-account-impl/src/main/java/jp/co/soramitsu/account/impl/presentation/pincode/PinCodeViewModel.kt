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
import jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.pin.PinAttemptLimiter
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.feature_account_impl.R
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** PIN recovery never turns an unreadable stored PIN into first-run setup. */
@HiltViewModel
class PinCodeViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val deviceVibrator: DeviceVibrator,
    private val resourceManager: ResourceManager,
    savedStateHandle: SavedStateHandle,
    private val pinRecovery: WalletPinRecovery,
    private val biometricVault: BiometricPinVault
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
        val error: Boolean = false,
        /** Started from "Forgot PIN?" for a readable PIN, not for an unreadable one. */
        val forgotten: Boolean = false
    )

    val biometricPromptGate = PinBiometricPromptGate()
    val pinCodeAction = savedStateHandle.get<PinCodeAction>(PincodeFragment.KEY_PINCODE_ACTION)!!
    private val _homeButtonVisibilityLiveData = MutableLiveData(pinCodeAction.toolbarConfiguration.backVisible)
    val homeButtonVisibilityLiveData: LiveData<Boolean> = _homeButtonVisibilityLiveData
    private val _resetInputEvent = MutableLiveData<Event<String>>()
    val resetInputEvent: LiveData<Event<String>> = _resetInputEvent
    private val _matchingPincodeErrorEvent = MutableLiveData<Event<Unit>>()
    val matchingPincodeErrorEvent: LiveData<Event<Unit>> = _matchingPincodeErrorEvent
    private val _pinRetryDelayEvent = MutableLiveData<Event<String>>()
    val pinRetryDelayEvent: LiveData<Event<String>> = _pinRetryDelayEvent
    /** Whether the PIN pad offers its biometric button. */
    private val _showFingerPrintEvent = MutableLiveData<Event<Boolean>>()
    val showFingerPrintEvent: LiveData<Event<Boolean>> = _showFingerPrintEvent
    private val _startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData: LiveData<Event<Unit>> = _startFingerprintScannerEventLiveData
    private val _fingerPrintErrorEvent = MutableLiveData<Event<String>>()
    val fingerPrintErrorEvent: LiveData<Event<String>> = _fingerPrintErrorEvent
    /** Offers biometric unlock; true when the offer follows a change to the phone's biometrics. */
    private val _biometricSwitchDialogLiveData = MutableLiveData<Event<Boolean>>()
    val biometricSwitchDialogLiveData: LiveData<Event<Boolean>> = _biometricSwitchDialogLiveData
    val recoveryState = MutableLiveData(RecoveryState())
    /** "Forgot PIN?" shows on the unlock screen while it asks for a readable PIN. */
    private val _forgotPinVisible = MutableLiveData(false)
    val forgotPinVisible: LiveData<Boolean> = _forgotPinVisible
    private var fingerPrintAvailable = false
    private var strongAvailable = false
    private var credentialAvailable = false
    private var biometricPreviouslyEnabled = false
    private var currentState: ScreenState? = null
    // Deliberately absent from SavedStateHandle: restoring a screen cannot restore authorization.
    private var challenge: String? = null
    private var busy = false
    // The PIN just typed, held only while the biometric offer is open so it can be sealed.
    private var pendingBiometricPin: String? = null

    /** Keystore work (generating the vault key can take a second) stays off the main thread. */
    @androidx.annotation.VisibleForTesting
    internal var vaultDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Exports, recovery screens and PIN changes take the PIN itself; only unlocking accepts biometrics. */
    private val biometricAllowed: Boolean =
        (pinCodeAction as? PinCodeAction.Check)?.allowBiometric == true

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
                        _forgotPinVisible.value = biometricAllowed
                        _showFingerPrintEvent.value = Event(canUseBiometricUnlock())
                        if (biometricAllowed && biometricVault.wasInvalidated()) {
                            _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_biometrics_changed))
                        }
                        onResume()
                    }
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
        }
    }

    private suspend fun startRecovery(forgotten: Boolean = false) {
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = ScreenState.Recovering
        _forgotPinVisible.value = false
        recoveryState.value = RecoveryState(forgotten = forgotten)
        biometricPreviouslyEnabled = interactor.isBiometricEnabled()
        val id = pinRecovery.begin(forgotten)
        challenge = id
        val choices = pinRecovery.choices(id)
        recoveryState.value = RecoveryState(loading = false, choices = choices,
            strongBiometric = strongAvailable && biometricPreviouslyEnabled, deviceCredential = credentialAvailable,
            forgotten = forgotten)
    }

    /**
     * "Forgot PIN?" on the unlock screen: the backup-proof recovery for a readable PIN. Proving one
     * wallet's backup opens the wallet key, and the new PIN then rewraps it.
     */
    fun forgotPin() {
        if (busy || currentState != ScreenState.Checking || !biometricAllowed) return
        busy = true
        viewModelScope.launch {
            try {
                startRecovery(forgotten = true)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
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

    /**
     * How the recovery screen's strong-biometric prompt must run: with the vault's key when one is
     * enrolled, so that only a biometric enrolled before the vault was sealed can authorize a new PIN.
     */
    sealed interface RecoveryPrompt {
        class Sealed(val cipher: Cipher) : RecoveryPrompt
        object Unsealed : RecoveryPrompt
        object Blocked : RecoveryPrompt
    }

    fun recoveryBiometricPrompt(): RecoveryPrompt {
        if (!biometricVault.isEnrolled()) {
            return if (biometricVault.wasInvalidated()) RecoveryPrompt.Blocked else RecoveryPrompt.Unsealed
        }
        return when (val prepared = biometricVault.prepareUnlock()) {
            is BiometricPinVault.UnlockCipher.Ready -> RecoveryPrompt.Sealed(prepared.cipher)
            BiometricPinVault.UnlockCipher.Invalidated -> {
                _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_biometrics_changed))
                RecoveryPrompt.Blocked
            }
            BiometricPinVault.UnlockCipher.Unavailable -> RecoveryPrompt.Blocked
        }
    }

    /** Only the BIOMETRIC_STRONG recovery prompt's callback may call this. */
    fun strongBiometricSucceeded(cipher: Cipher?) {
        val id = challenge ?: return
        if (currentState != ScreenState.Recovering || !strongAvailable || !biometricPreviouslyEnabled || busy) return
        // Once a vault exists, only its key proves the biometric; a later enrollment cannot. Its PIN
        // must then open the wallet key, which the new PIN rewraps.
        val vaultPin = if (biometricVault.isEnrolled()) (cipher?.let(biometricVault::decryptPin) ?: return) else null
        if (!biometricVault.isEnrolled() && biometricVault.wasInvalidated()) return
        busy = true
        viewModelScope.launch {
            try {
                pinRecovery.strongBiometricSucceeded(id, vaultPin)
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
                    pendingBiometricPin = code
                    _biometricSwitchDialogLiveData.value = Event(false)
                } else {
                    // A changed or recovered PIN is resealed, so biometric unlock keeps matching it.
                    resealOrClearVault(code)
                    authSuccess()
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
    }

    private fun checkPinCode(code: String) {
        busy = true
        viewModelScope.launch {
            try {
                val remainingDelay = interactor.getPinRetryDelayMillis()
                if (remainingDelay > 0L) {
                    // Checking PIN health decrypts it too, so reject delayed entries before that read.
                    mismatch()
                    showRetryDelay(remainingDelay)
                    return@launch
                }
                if (interactor.getPinHealth() == PinHealth.UNREADABLE) startRecovery()
                else if (interactor.isPinCorrect(code)) pinVerified(code) else {
                    mismatch()
                    showRetryDelay(interactor.getPinRetryDelayMillis())
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
            finally { busy = false }
        }
    }

    /**
     * After the right PIN on an unlock: asks again about biometrics if the phone's biometrics changed,
     * and seals the PIN for wallets upgraded from versions that kept biometric unlock unsealed.
     */
    private suspend fun pinVerified(code: String) {
        if (biometricAllowed && fingerPrintAvailable && biometricVault.wasInvalidated()) {
            pendingBiometricPin = code
            _biometricSwitchDialogLiveData.value = Event(true)
            return
        }
        if (biometricAllowed && fingerPrintAvailable && interactor.isBiometricEnabled() && !biometricVault.isEnrolled()) {
            sealQuietly(code)
        }
        authSuccess()
    }

    private suspend fun resealOrClearVault(code: String) {
        if (fingerPrintAvailable && interactor.isBiometricEnabled()) sealQuietly(code)
        else withContext(vaultDispatcher) { biometricVault.clear() }
    }

    /** A failed seal only leaves biometric unlock unavailable; the PIN keeps working. */
    private suspend fun sealQuietly(code: String): Boolean = withContext(vaultDispatcher) {
        try {
            biometricVault.enroll(code)
            true
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            biometricVault.clear()
            false
        }
    }

    private fun canUseBiometricUnlock(): Boolean = biometricAllowed && fingerPrintAvailable && biometricVault.isEnrolled()

    /** The cipher for the unlock prompt's CryptoObject, or null when biometric unlock cannot run. */
    fun biometricUnlockCipher(): Cipher? {
        if (!biometricAllowed || currentState != ScreenState.Checking || busy || !fingerPrintAvailable) return null
        return when (val prepared = biometricVault.prepareUnlock()) {
            is BiometricPinVault.UnlockCipher.Ready -> prepared.cipher
            BiometricPinVault.UnlockCipher.Invalidated -> {
                _showFingerPrintEvent.value = Event(false)
                _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_biometrics_changed))
                viewModelScope.launch { interactor.setBiometricOff() }
                null
            }
            BiometricPinVault.UnlockCipher.Unavailable -> null
        }
    }

    private fun mismatch() {
        deviceVibrator.makeShortVibration()
        _matchingPincodeErrorEvent.value = Event(Unit)
    }

    /** Says how long PIN entry is paused after repeated wrong PINs. Biometrics stay available meanwhile. */
    private fun showRetryDelay(delay: Long) {
        if (delay > 0L) {
            val seconds = PinAttemptLimiter.secondsLeft(delay)
            _pinRetryDelayEvent.value = Event(resourceManager.getString(R.string.wallet_emergency_locked, seconds))
        }
    }

    private fun showPinCreation() {
        currentState = ScreenState.Creating
        _forgotPinVisible.value = false
        recoveryState.value = RecoveryState(visible = false)
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_create_top_title))
        _homeButtonVisibilityLiveData.value = pinCodeAction.toolbarConfiguration.backVisible
    }

    private fun recoveryFailed() {
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = ScreenState.Recovering
        _forgotPinVisible.value = false
        recoveryState.value = RecoveryState(loading = false, error = true)
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_pin_code))
    }

    fun backPressed() {
        if (busy) return
        if (currentState is ScreenState.Confirmation) showPinCreation()
        // Back from "Forgot PIN?" returns to the PIN pad; the readable PIN still works.
        else if (currentState == ScreenState.Recovering && recoveryState.value?.forgotten == true) retryRecovery()
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
                else if (currentState == ScreenState.Checking && interactor.getPinHealth() == PinHealth.HEALTHY &&
                    interactor.isBiometricEnabled() && canUseBiometricUnlock()) {
                    _startFingerprintScannerEventLiveData.value = Event(Unit)
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { recoveryFailed() }
        }
    }

    /** Temporary PIN confirmation material never survives a stopped screen. */
    fun screenStopped() {
        pendingBiometricPin = null
        if (currentState is ScreenState.Confirmation) {
            if (busy) currentState = ScreenState.Confirmation("") else showPinCreation()
        }
    }

    fun onAuthenticationError(errString: String) { _fingerPrintErrorEvent.value = Event(errString) }

    /**
     * The unlock prompt succeeded with [cipher] as its CryptoObject. Unlocking needs the PIN that
     * cipher unseals to match the stored PIN; a biometric alone never unlocks.
     */
    fun biometryAuthenticationSucceeded(cipher: Cipher?) {
        if (currentState != ScreenState.Checking || busy || !biometricAllowed) return
        busy = true
        viewModelScope.launch {
            try {
                if (interactor.getPinHealth() == PinHealth.UNREADABLE) {
                    startRecovery()
                    return@launch
                }
                val pin = cipher?.let { withContext(vaultDispatcher) { biometricVault.decryptPin(it) } }
                when {
                    pin == null -> _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_fingerprint_error))
                    interactor.isBiometricEnabled() && interactor.verifyBiometricVaultPin(pin) -> authSuccess()
                    else -> {
                        // Sealed before a PIN change made elsewhere: it is resealed after the next PIN entry.
                        withContext(vaultDispatcher) { biometricVault.clear() }
                        _showFingerPrintEvent.value = Event(false)
                        _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_enter_pin_code))
                    }
                }
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
        pendingBiometricPin = null
        WalletViewOnlyAccess.pinResetCompleted()
        // Every route here proved the PIN (or its sealed biometric), so dApp requests may proceed.
        WalletSessionLock.unlock()
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
        val pin = pendingBiometricPin
        pendingBiometricPin = null
        viewModelScope.launch {
            biometricVault.acknowledgeInvalidation()
            // Without the PIN at hand (the screen stopped meanwhile) the next PIN entry seals it.
            if (pin == null || sealQuietly(pin)) interactor.setBiometricOn() else interactor.setBiometricOff()
            authSuccess()
        }
    }

    fun declineAuthWithBiometry() {
        pendingBiometricPin = null
        viewModelScope.launch {
            biometricVault.acknowledgeInvalidation()
            withContext(vaultDispatcher) { biometricVault.clear() }
            interactor.setBiometricOff()
            authSuccess()
        }
    }

    override fun onCleared() {
        pendingBiometricPin = null
        biometricPromptGate.cancel()
        challenge?.let(pinRecovery::cancel)
        challenge = null
        currentState = null
        super.onCleared()
    }
}
