package jp.co.soramitsu.feature_account_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import java.util.concurrent.TimeUnit

class PinCodeViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val maxPinCodeLength: Int,
    private val deviceVibrator: DeviceVibrator
) : BaseViewModel() {

    companion object {
        private const val COMPLETE_PIN_CODE_DELAY: Long = 12
    }

    private var fingerPrintAvailable = false
    private lateinit var action: PinCodeAction
    private var tempCode = ""

    private val inputCodeLiveData = MutableLiveData<String>()

    val toolbarTitleResLiveData = MutableLiveData<Int>()
    val showFingerPrintEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintAutFailedLiveData = MutableLiveData<Event<Unit>>()
    val fingerPrintErrorLiveData = MutableLiveData<Event<String>>()
    val pinCodeProgressLiveData = MediatorLiveData<Int>()

    private val _biometricSwitchDialogLiveData = MutableLiveData<Event<Unit>>()
    val biometricSwitchDialogLiveData: LiveData<Event<Unit>> = _biometricSwitchDialogLiveData

    private val _finishAppEvent = MutableLiveData<Event<Unit>>()
    val finisAppEvent: LiveData<Event<Unit>> = _finishAppEvent

    private val _homeButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val homeButtonVisibilityLiveData: LiveData<Boolean> = _homeButtonVisibilityLiveData

    private val _matchingPincodeErrorAnimationEvent = MutableLiveData<Event<Unit>>()
    val matchingPincodeErrorAnimationEvent: LiveData<Event<Unit>> = _matchingPincodeErrorAnimationEvent

    init {
        pinCodeProgressLiveData.addSource(inputCodeLiveData) {
            pinCodeProgressLiveData.value = it.length
        }

        _homeButtonVisibilityLiveData.value = false

        inputCodeLiveData.value = ""
    }

    fun startAuth(pinCodeAction: PinCodeAction) {
        action = pinCodeAction
        toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code

        if (action == PinCodeAction.TIMEOUT_CHECK) {
            disposables.add(
                interactor.isCodeSet()
                    .subscribe({
                        toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code
                        if (it) {
                            showFingerPrintEventLiveData.value = Event(Unit)
                        } else {
                            action = PinCodeAction.CREATE_PIN_CODE
                        }
                    }, {
                        it.printStackTrace()
                        action = PinCodeAction.CREATE_PIN_CODE
                    })
            )
        }
    }

    fun pinCodeNumberClicked(pinCodeNumber: String) {
        inputCodeLiveData.value?.let { inputCode ->
            if (inputCode.length >= maxPinCodeLength) {
                return
            }
            val newCode = inputCode + pinCodeNumber
            inputCodeLiveData.value = newCode
            if (newCode.length == maxPinCodeLength) {
                pinCodeEntered(newCode)
            }
        }
    }

    fun pinCodeDeleteClicked() {
        inputCodeLiveData.value?.let { inputCode ->
            if (inputCode.isEmpty()) {
                return
            }
            inputCodeLiveData.value = inputCode.substring(0, inputCode.length - 1)
        }
    }

    private fun pinCodeEntered(pin: String) {
        disposables.add(
            Completable.complete()
                .delay(COMPLETE_PIN_CODE_DELAY, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (PinCodeAction.CREATE_PIN_CODE == action) {
                        if (tempCode.isEmpty()) {
                            tempCode = pin
                            inputCodeLiveData.value = ""
                            toolbarTitleResLiveData.value = R.string.pincode_confirm_your_pin_code
                            _homeButtonVisibilityLiveData.value = true
                        } else {
                            pinCodeEnterComplete(pin)
                        }
                    } else {
                        checkPinCode(pin)
                    }
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun pinCodeEnterComplete(pinCode: String) {
        if (tempCode == pinCode) {
            registerPinCode(pinCode)
        } else {
            inputCodeLiveData.value = ""
            deviceVibrator.makeShortVibration()
            _matchingPincodeErrorAnimationEvent.value = Event(Unit)
        }
    }

    private fun registerPinCode(code: String) {
        disposables.add(
            interactor.savePin(code)
                .subscribe({
                    if (fingerPrintAvailable) {
                        _biometricSwitchDialogLiveData.value = Event(Unit)
                    } else {
                        // TODO: 8/12/20 go to next registration screen
                    }
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun checkPinCode(code: String) {
        disposables.add(
            interactor.isPinCorrect(code)
                .subscribe({
                    if (it) {
                        // TODO: 8/12/20 successfull auth
                    } else {
                        inputCodeLiveData.value = ""
                        deviceVibrator.makeShortVibration()
                        _matchingPincodeErrorAnimationEvent.value = Event(Unit)
                    }
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun backPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                _finishAppEvent.value = Event(Unit)
            } else {
                tempCode = ""
                inputCodeLiveData.value = ""
                _homeButtonVisibilityLiveData.value = false
                toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code
            }
        } else {
            if (PinCodeAction.TIMEOUT_CHECK == action) {
                _finishAppEvent.value = Event(Unit)
            } else {
                router.backToBackupMnemonicScreen()
            }
        }
    }

    fun onResume() {
        if (action != PinCodeAction.CREATE_PIN_CODE) {
            startFingerprintScannerEventLiveData.value = Event(Unit)
        }
    }

    fun onAuthenticationError(errString: String) {
        fingerPrintErrorLiveData.value = Event(errString)
    }

    fun onAuthenticationSucceeded() {
        // TODO: 8/12/20 fingerprint successfull auth
    }

    fun onAuthenticationFailed() {
        fingerPrintAutFailedLiveData.value = Event(Unit)
    }

    fun fingerprintScannerAvailable(authReady: Boolean) {
        fingerPrintAvailable = authReady
    }

    fun fingerprintSwitchDialogYesClicked() {
        disposables.add(
            interactor.setBiometricOn()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    // TODO: 8/12/20 show next registration screen
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun fingerprintSwitchDialogNoClicked() {
        disposables.add(
            interactor.setBiometricOff()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    // TODO: 8/12/20 show next registration screen
                }, {
                    it.printStackTrace()
                })
        )
    }
}