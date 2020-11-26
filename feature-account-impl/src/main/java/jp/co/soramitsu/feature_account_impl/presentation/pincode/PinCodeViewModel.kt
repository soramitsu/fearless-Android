package jp.co.soramitsu.feature_account_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class PinCodeViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val deviceVibrator: DeviceVibrator,
    private val resourceManager: ResourceManager,
    val pinCodeAction: PinCodeAction
) : BaseViewModel() {

    sealed class ScreenState {
        object Creating : ScreenState()
        data class Confirmation(val codeToConfirm: String) : ScreenState()
        object Checking : ScreenState()
    }

    private val _homeButtonVisibilityLiveData = MutableLiveData<Boolean>(pinCodeAction.toolbarConfiguration.backVisible)
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

    private var fingerPrintAvailable = false
    private var currentState: ScreenState? = null

    fun startAuth() {
        when (pinCodeAction) {
            is PinCodeAction.Create -> {
                currentState = ScreenState.Creating
            }
            is PinCodeAction.Check -> {
                currentState = ScreenState.Checking
                _showFingerPrintEvent.value = Event(Unit)
            }
            is PinCodeAction.Change -> {
                currentState = ScreenState.Checking
                _showFingerPrintEvent.value = Event(Unit)
            }
        }
    }

    fun pinCodeEntered(pin: String) {
        when (currentState) {
            is ScreenState.Creating -> tempCodeEntered(pin)
            is ScreenState.Confirmation -> matchPinCodeWithCodeToConfirm(pin, (currentState as ScreenState.Confirmation).codeToConfirm)
            is ScreenState.Checking -> checkPinCode(pin)
        }
    }

    private fun tempCodeEntered(pin: String) {
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_confirm_your_pin_code_v1_0_1))
        _homeButtonVisibilityLiveData.value = true
        currentState = ScreenState.Confirmation(pin)
    }

    private fun matchPinCodeWithCodeToConfirm(pinCode: String, codeToConfirm: String) {
        if (codeToConfirm == pinCode) {
            registerPinCode(pinCode)
        } else {
            deviceVibrator.makeShortVibration()
            _matchingPincodeErrorEvent.value = Event(Unit)
        }
    }

    private fun registerPinCode(code: String) {
        disposables += interactor.savePin(code)
            .subscribe({
                if (fingerPrintAvailable && pinCodeAction is PinCodeAction.Create) {
                    _biometricSwitchDialogLiveData.value = Event(Unit)
                } else {
                    authSuccess()
                }
            }, DEFAULT_ERROR_HANDLER)
    }

    private fun checkPinCode(code: String) {
        disposables += interactor.isPinCorrect(code)
            .subscribe({ pinIsCorrect ->
                if (pinIsCorrect) {
                    authSuccess()
                } else {
                    deviceVibrator.makeShortVibration()
                    _matchingPincodeErrorEvent.value = Event(Unit)
                }
            }, DEFAULT_ERROR_HANDLER)
    }

    fun backPressed() {
        when (currentState) {
            is ScreenState.Creating -> authCancel()
            is ScreenState.Confirmation -> backToCreateFromConfirmation()
            is ScreenState.Checking -> authCancel()
        }
    }

    private fun backToCreateFromConfirmation() {
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_pin_code))
        if (pinCodeAction is PinCodeAction.Create) {
            _homeButtonVisibilityLiveData.value = pinCodeAction.toolbarConfiguration.backVisible
        }
        currentState = ScreenState.Creating
    }

    fun onResume() {
        if (ScreenState.Checking == currentState && interactor.isBiometricEnabled()) {
            _startFingerprintScannerEventLiveData.value = Event(Unit)
        }
    }

    fun onAuthenticationError(errString: String) {
        _fingerPrintErrorEvent.value = Event(errString)
    }

    fun biometryAuthenticationSucceeded() {
        authSuccess()
    }

    fun biometryAuthenticationFailed() {
        _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_fingerprint_error))
    }

    fun fingerprintScannerAvailable(authReady: Boolean) {
        fingerPrintAvailable = authReady
    }

    private fun authSuccess() {
        when (pinCodeAction) {
            is PinCodeAction.Create -> router.openAfterPinCode(pinCodeAction.delayedNavigation)
            is PinCodeAction.Check -> router.openAfterPinCode(pinCodeAction.delayedNavigation)
            is PinCodeAction.Change -> {
                when (currentState) {
                    is ScreenState.Checking -> {
                        currentState = ScreenState.Creating
                        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_new_pin_code))
                        _homeButtonVisibilityLiveData.value = true
                    }
                    is ScreenState.Confirmation -> {
                        router.back()
                        showMessage(resourceManager.getString(R.string.pincode_changed_message))
                    }
                }
            }
        }
    }

    private fun authCancel() {
        router.back()
    }

    fun acceptAuthWithBiometry() {
        disposables += interactor.setBiometricOn()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                authSuccess()
            }, DEFAULT_ERROR_HANDLER)
    }

    fun declineAuthWithBiometry() {
        disposables += interactor.setBiometricOff()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                authSuccess()
            }, DEFAULT_ERROR_HANDLER)
    }
}