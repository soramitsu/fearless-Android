package jp.co.soramitsu.feature_account_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.setValueIfNew
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.model.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import java.util.concurrent.TimeUnit

class PinCodeViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val progress: WithProgress,
    private val maxPinCodeLength: Int
) : BaseViewModel(), WithProgress by progress {

    companion object {
        private const val COMPLETE_PIN_CODE_DELAY: Long = 12
    }

    private lateinit var action: PinCodeAction
    private var tempCode = ""

    private val inputCodeLiveData = MutableLiveData<String>()

    val toolbarTitleResLiveData = MutableLiveData<Int>()
    val wrongPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val showFingerPrintEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintAutFailedLiveData = MutableLiveData<Event<Unit>>()
    val fingerPrintErrorLiveData = MutableLiveData<Event<String>>()
    val pinCodeProgressLiveData = MediatorLiveData<Int>()
    val deleteButtonVisibilityLiveData = MediatorLiveData<Boolean>()

    private val _closeAppLiveData = MutableLiveData<Event<Unit>>()
    val closeAppLiveData: LiveData<Event<Unit>> = _closeAppLiveData

    init {
        pinCodeProgressLiveData.addSource(inputCodeLiveData) {
            pinCodeProgressLiveData.value = it.length
        }

        deleteButtonVisibilityLiveData.addSource(inputCodeLiveData) {
            deleteButtonVisibilityLiveData.setValueIfNew(it.isNotEmpty())
        }

        inputCodeLiveData.value = ""
    }

    fun startAuth(pinCodeAction: PinCodeAction) {
        action = pinCodeAction
        toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code

        when (action) {
            PinCodeAction.OPEN_PASSPHRASE -> {
                showFingerPrintEventLiveData.value = Event(Unit)
            }
            PinCodeAction.TIMEOUT_CHECK -> {
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
                            onError(it.localizedMessage)
                            action = PinCodeAction.CREATE_PIN_CODE
                        })
                )
            }
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
            tempCode = ""
            inputCodeLiveData.value = ""
            toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code
            onError(R.string.pincode_repeat_error)
        }
    }

    private fun registerPinCode(code: String) {
        disposables.add(
            interactor.savePin(code)
                .subscribe({
                    // TODO: 8/12/20 succesffully registered
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun checkPinCode(code: String) {
        disposables.add(
            interactor.checkPin(code)
                .subscribe({
                    // TODO: 8/12/20 Successfull check
                }, {
                    it.printStackTrace()
                    inputCodeLiveData.value = ""
                    wrongPinCodeEventLiveData.value = Event(Unit)
                })
        )
    }

    fun backPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                _closeAppLiveData.value = Event(Unit)
            } else {
                tempCode = ""
                inputCodeLiveData.value = ""
                toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code
            }
        } else {
            if (PinCodeAction.TIMEOUT_CHECK == action) {
                _closeAppLiveData.value = Event(Unit)
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
}