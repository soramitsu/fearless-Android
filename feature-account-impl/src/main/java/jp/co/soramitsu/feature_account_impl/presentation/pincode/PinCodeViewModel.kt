package jp.co.soramitsu.feature_account_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
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
    private val deviceVibrator: DeviceVibrator,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    companion object {
        private const val COMPLETE_PIN_CODE_DELAY: Long = 12
    }

    private var fingerPrintAvailable = false
    private lateinit var action: PinCodeAction
    private var tempCode = ""

    private val inputCodeLiveData = MutableLiveData<String>()

    private val _titleLiveData = MutableLiveData<String>()
    val titleLiveData: LiveData<String> = _titleLiveData

    private val _showFingerPrintEvent = MutableLiveData<Event<Unit>>()
    val showFingerPrintEvent: LiveData<Event<Unit>> = _showFingerPrintEvent

    private val _startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData: LiveData<Event<Unit>> = _startFingerprintScannerEventLiveData

    private val _fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintDialogVisibilityLiveData: LiveData<Boolean> = _fingerPrintDialogVisibilityLiveData

    private val _fingerPrintErrorEvent = MutableLiveData<Event<String>>()
    val fingerPrintErrorEvent: LiveData<Event<String>> = _fingerPrintErrorEvent

    private val _pinCodeProgressLiveData = MediatorLiveData<Int>()
    val pinCodeProgressLiveData: LiveData<Int> = _pinCodeProgressLiveData

    private val _biometricSwitchDialogLiveData = MutableLiveData<Event<Unit>>()
    val biometricSwitchDialogLiveData: LiveData<Event<Unit>> = _biometricSwitchDialogLiveData

    private val _finishAppEvent = MutableLiveData<Event<Unit>>()
    val finisAppEvent: LiveData<Event<Unit>> = _finishAppEvent

    private val _homeButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val homeButtonVisibilityLiveData: LiveData<Boolean> = _homeButtonVisibilityLiveData

    private val _matchingPincodeErrorAnimationEvent = MutableLiveData<Event<Unit>>()
    val matchingPincodeErrorAnimationEvent: LiveData<Event<Unit>> = _matchingPincodeErrorAnimationEvent

    init {
        _pinCodeProgressLiveData.addSource(inputCodeLiveData) {
            _pinCodeProgressLiveData.value = it.length
        }

        _homeButtonVisibilityLiveData.value = false

        inputCodeLiveData.value = ""
    }

    fun startAuth() {
        _titleLiveData.value = resourceManager.getString(R.string.pincode_enter_pin_code)

        disposables.add(
            interactor.isCodeSet()
                .subscribe({
                    _titleLiveData.value = resourceManager.getString(R.string.pincode_enter_pin_code)
                    if (it) {
                        action = PinCodeAction.TIMEOUT_CHECK
                        _showFingerPrintEvent.value = Event(Unit)
                    } else {
                        action = PinCodeAction.CREATE_PIN_CODE
                    }
                }, {
                    it.printStackTrace()
                    action = PinCodeAction.CREATE_PIN_CODE
                })
        )
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
                            _titleLiveData.value = resourceManager.getString(R.string.pincode_confirm_your_pin_code)
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
                        processAuthSuccess()
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
                        processAuthSuccess()
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
                _titleLiveData.value = resourceManager.getString(R.string.pincode_enter_pin_code)
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
            _startFingerprintScannerEventLiveData.value = Event(Unit)
        }
    }

    fun onAuthenticationError(errString: String) {
        _fingerPrintErrorEvent.value = Event(errString)
    }

    fun onAuthenticationSucceeded() {
        processAuthSuccess()
    }

    fun onAuthenticationFailed() {
        _fingerPrintErrorEvent.value = Event(resourceManager.getString(R.string.pincode_fingerprint_error))
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
                    processAuthSuccess()
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun processAuthSuccess() {
        router.openMain()
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