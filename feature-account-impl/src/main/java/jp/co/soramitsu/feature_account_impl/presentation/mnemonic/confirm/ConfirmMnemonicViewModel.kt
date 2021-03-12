package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import kotlinx.coroutines.launch

class ConfirmMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
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
        viewModelScope.launch {
            val mnemonicString = originMnemonic.joinToString(" ")

            with(extras) {
                val result = interactor.createAccount(accountName, mnemonicString, cryptoType, derivationPath, networkType)

                if (result.isSuccess) {
                    continueBasedOnCodeStatus()
                } else {
                    showError(result.requireException())
                }
            }
        }
    }

    fun matchingErrorAnimationCompleted() {
        reset()
    }

    private suspend fun continueBasedOnCodeStatus() {
        if (interactor.isCodeSet()) {
            router.openMain()
        } else {
            router.openCreatePincode()
        }
    }

    fun skipClicked() {
        proceed()
    }
}
