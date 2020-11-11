package jp.co.soramitsu.feature_account_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
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
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    enum class State {
        CREATE,
        CONFIRM,
        CHECK
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
    private var currentState: State? = null

    fun startAuth() {
        if (interactor.isCodeSet()) {
            currentState = State.CHECK
            _showFingerPrintEvent.value = Event(Unit)
        } else {
            currentState = State.CREATE
        }
    }

    fun pinCodeEntered(pin: String) {
        when (currentState) {
            State.CREATE -> tempCodeEntered(pin)
            State.CONFIRM -> pinCodeEnterComplete(pin)
            State.CHECK -> checkPinCode(pin)
        }
    }

    private fun tempCodeEntered(pin: String) {
        tempCode = pin
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_confirm_your_pin_code))
        _homeButtonVisibilityLiveData.value = true
        currentState = State.CONFIRM
    }

    private fun pinCodeEnterComplete(pinCode: String) {
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
                    if (fingerPrintAvailable) {
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
        when (currentState) {
            State.CREATE -> _finishAppEvent.value = Event(Unit)
            State.CONFIRM -> backToCreate()
            State.CHECK -> _finishAppEvent.value = Event(Unit)
        }
    }

    private fun backToCreate() {
        tempCode = ""
        _resetInputEvent.value = Event(resourceManager.getString(R.string.pincode_enter_pin_code))
        _homeButtonVisibilityLiveData.value = false
    }

    fun onResume() {
        if (State.CHECK == currentState) {
            disposables += interactor.isBiometricEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it) {
                        _startFingerprintScannerEventLiveData.value = Event(Unit)
                    }
                }, {
                    it.message?.let(this::showError)
                })
        }
    }

    fun onAuthenticationError(errString: String) {
        _fingerPrintErrorEvent.value = Event(errString)
    }

    fun onAuthenticationSucceeded() {
        authSuccess()
    }

    fun onAuthenticationFailed() {
        _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_fingerprint_error))
    }

    fun fingerprintScannerAvailable(authReady: Boolean) {
        fingerPrintAvailable = authReady
    }

    private fun authSuccess() {
        router.openMain()
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
                    router.openMain()
                }, {
                    it.printStackTrace()
                })
        )
    }
}