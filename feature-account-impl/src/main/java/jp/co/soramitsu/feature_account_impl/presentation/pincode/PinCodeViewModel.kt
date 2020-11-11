package jp.co.soramitsu.feature_account_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class PinCodeViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val deviceVibrator: DeviceVibrator,
    private val resourceManager: ResourceManager,
    private val pinCodeAction: PinCodeAction
) : BaseViewModel() {

    enum class ScreenState {
        CREATING,
        CONFIRMATION,
        CHECKING
    }

    private val _homeButtonVisibilityLiveData = MutableLiveData<Boolean>(false)
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

    private val _finishAppEvent = MutableLiveData<Event<Unit>>()
    val finisAppEvent: LiveData<Event<Unit>> = _finishAppEvent

    private var fingerPrintAvailable = false
    private var tempCode = ""
    private var currentScreenState: ScreenState? = null

    fun startAuth() {
        when (pinCodeAction) {
            PinCodeAction.CREATE -> {
                currentScreenState = ScreenState.CREATING
            }
            PinCodeAction.CHECK -> {
                currentScreenState = ScreenState.CHECKING
                _showFingerPrintEvent.value = Event(Unit)
            }
            PinCodeAction.CHANGE -> {
                currentScreenState = ScreenState.CHECKING
                _showFingerPrintEvent.value = Event(Unit)
                _homeButtonVisibilityLiveData.value = true
            }
        }
    }

    fun pinCodeEntered(pin: String) {
        when (currentScreenState) {
            ScreenState.CREATING -> tempCodeEntered(pin)
            ScreenState.CONFIRMATION -> matchPincodeWithTempCode(pin)
            ScreenState.CHECKING -> checkPinCode(pin)
        }
    }

    private fun tempCodeEntered(pin: String) {
        tempCode = pin
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_confirm_your_pin_code))
        _homeButtonVisibilityLiveData.value = true
        currentScreenState = ScreenState.CONFIRMATION
    }

    private fun matchPincodeWithTempCode(pinCode: String) {
        if (tempCode == pinCode) {
            registerPinCode(pinCode)
        } else {
            deviceVibrator.makeShortVibration()
            _matchingPincodeErrorEvent.value = Event(Unit)
        }
    }

    private fun registerPinCode(code: String) {
        disposables.add(
            interactor.savePin(code)
                .subscribe({
                    if (fingerPrintAvailable && PinCodeAction.CREATE == pinCodeAction) {
                        _biometricSwitchDialogLiveData.value = Event(Unit)
                    } else {
                        authSuccess()
                    }
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun checkPinCode(code: String) {
        disposables.add(
            interactor.isPinCorrect(code)
                .subscribe({ pinIsCorrect ->
                    if (pinIsCorrect) {
                        authSuccess()
                    } else {
                        deviceVibrator.makeShortVibration()
                        _matchingPincodeErrorEvent.value = Event(Unit)
                    }
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun backPressed() {
        when (currentScreenState) {
            ScreenState.CREATING -> authCancel()
            ScreenState.CONFIRMATION -> backToCreate()
            ScreenState.CHECKING -> authCancel()
        }
    }

    private fun backToCreate() {
        tempCode = ""
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_pin_code))
        if (PinCodeAction.CREATE == pinCodeAction) {
            _homeButtonVisibilityLiveData.value = false
        }
        currentScreenState = ScreenState.CREATING
    }

    fun onResume() {
        if (ScreenState.CHECKING == currentScreenState && interactor.isBiometricEnabled()) {
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
            PinCodeAction.CREATE -> router.openMain()
            PinCodeAction.CHECK -> router.openMain()
            PinCodeAction.CHANGE -> {
                when (currentScreenState) {
                    ScreenState.CHECKING -> {
                        currentScreenState = ScreenState.CREATING
                        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_new_pin_code))
                        _homeButtonVisibilityLiveData.value = true
                    }
                    ScreenState.CONFIRMATION -> {
                        router.back()
                        showMessage(resourceManager.getString(R.string.pincode_changed_message))
                    }
                }
            }
        }
    }

    private fun authCancel() {
        when (pinCodeAction) {
            PinCodeAction.CREATE -> _finishAppEvent.value = Event(Unit)
            PinCodeAction.CHECK -> _finishAppEvent.value = Event(Unit)
            PinCodeAction.CHANGE -> router.back()
        }
    }

    fun acceptAuthWithBiometry() {
        disposables.add(
            interactor.setBiometricOn()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    authSuccess()
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun declineAuthWithBiometry() {
        disposables.add(
            interactor.setBiometricOff()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    authSuccess()
                }, {
                    it.printStackTrace()
                })
        )
    }
}