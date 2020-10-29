package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class ConfirmMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val deviceVibrator: DeviceVibrator,
    private val payload: ConfirmMnemonicPayload
) : BaseViewModel() {

    private val originMnemonic = payload.mnemonic

    val shuffledMnemonic = originMnemonic.shuffled()

    private val confirmationMnemonicWords = MutableLiveData<List<String>>(emptyList())

    private val _resetConfirmationEvent = MutableLiveData<Event<Unit>>()
    val resetConfirmationEvent: LiveData<Event<Unit>> = _resetConfirmationEvent

    private val _removeLastWordFromConfirmationEvent = MutableLiveData<Event<Unit>>()
    val removeLastWordFromConfirmationEvent: LiveData<Event<Unit>> = _removeLastWordFromConfirmationEvent

    val nextButtonEnableLiveData: LiveData<Boolean> = confirmationMnemonicWords.map {
        originMnemonic.size == it.size
    }

    val skipVisible = payload.createExtras != null

    private val _matchingMnemonicErrorAnimationEvent = MutableLiveData<Event<Unit>>()
    val matchingMnemonicErrorAnimationEvent: LiveData<Event<Unit>> = _matchingMnemonicErrorAnimationEvent

    fun homeButtonClicked() {
        router.backToBackupMnemonicScreen()
    }

    fun resetConfirmationClicked() {
        reset()
    }

    private fun reset() {
        confirmationMnemonicWords.value = mutableListOf()
        _resetConfirmationEvent.sendEvent()
    }

    fun addWordToConfirmMnemonic(word: String) {
        confirmationMnemonicWords.value?.let {
            val wordList = mutableListOf<String>().apply {
                addAll(it)
                add(word)
            }
            confirmationMnemonicWords.value = wordList
        }
    }

    fun removeLastWordFromConfirmation() {
        confirmationMnemonicWords.value?.let {
            if (it.isEmpty()) {
                return
            }
            val wordList = mutableListOf<String>().apply {
                addAll(it.subList(0, it.size - 1))
            }
            confirmationMnemonicWords.value = wordList
        }

        _removeLastWordFromConfirmationEvent.sendEvent()
    }

    fun nextButtonClicked() {
        confirmationMnemonicWords.value?.let { enteredWords ->
            if (originMnemonic == enteredWords) {
                proceed()
            } else {
                deviceVibrator.makeShortVibration()
                _matchingMnemonicErrorAnimationEvent.sendEvent()
            }
        }
    }

    private fun proceed() {
        val createExtras = payload.createExtras

        if (createExtras != null) {
            createAccount(createExtras)
        } else {
            finishConfirmGame()
        }
    }

    private fun finishConfirmGame() {
        router.back()
    }

    private fun createAccount(extras: ConfirmMnemonicPayload.CreateExtras) {
        val mnemonicString = originMnemonic.joinToString(" ")

        with(extras) {
            disposables += interactor.createAccount(accountName, mnemonicString, cryptoType, derivationPath, networkType)
                .andThen(interactor.isCodeSet())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::continueBasedOnCodeStatus, Throwable::printStackTrace)
        }
    }

    fun matchingErrorAnimationCompleted() {
        reset()
    }

    private fun continueBasedOnCodeStatus(isCodeSet: Boolean) {
        if (isCodeSet) {
            router.openMain()
        } else {
            router.openCreatePincode()
        }
    }

    fun skipClicked() {
        proceed()
    }
}