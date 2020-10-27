package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class ConfirmMnemonicViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val deviceVibrator: DeviceVibrator,
    private val mnemonic: List<String>,
    private val accountName: String,
    private val cryptoType: CryptoType,
    private val node: Node,
    private val derivationPath: String
) : BaseViewModel() {

    private val _mnemonicLiveData = MediatorLiveData<List<String>>()
    val mnemonicLiveData: LiveData<List<String>> = _mnemonicLiveData

    private val _resetConfirmationEvent = MutableLiveData<Event<Unit>>()
    val resetConfirmationEvent: LiveData<Event<Unit>> = _resetConfirmationEvent

    private val _removeLastWordFromConfirmationEvent = MutableLiveData<Event<Unit>>()
    val removeLastWordFromConfirmationEvent: LiveData<Event<Unit>> = _removeLastWordFromConfirmationEvent

    private val _nextButtonEnableLiveData = MediatorLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    private val _matchingMnemonicErrorAnimationEvent = MutableLiveData<Event<Unit>>()
    val matchingMnemonicErrorAnimationEvent: LiveData<Event<Unit>> = _matchingMnemonicErrorAnimationEvent

    private val confirmationMnemonicWords = MutableLiveData<List<String>>()
    private val originMnemonic = MutableLiveData<List<String>>()

    init {
        _nextButtonEnableLiveData.addSource(confirmationMnemonicWords) { enteredWords ->
            mnemonicLiveData.value?.let { mnemonic ->
                _nextButtonEnableLiveData.value = mnemonic.size == enteredWords.size
            }
        }

        _mnemonicLiveData.addSource(originMnemonic) {
            _mnemonicLiveData.value = it.shuffled()
        }

        confirmationMnemonicWords.value = mutableListOf()

        originMnemonic.value = mnemonic
    }

    fun homeButtonClicked() {
        router.backToBackupMnemonicScreen()
    }

    fun resetConfirmationClicked() {
        reset()
    }

    private fun reset() {
        confirmationMnemonicWords.value = mutableListOf()
        _resetConfirmationEvent.value = Event(Unit)
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
        _removeLastWordFromConfirmationEvent.value = Event(Unit)
    }

    fun nextButtonClicked() {
        confirmationMnemonicWords.value?.let { enteredWords ->
            originMnemonic.value?.let { mnemonic ->
                if (mnemonic == enteredWords) {
                    createAccount()
                } else {
                    deviceVibrator.makeShortVibration()
                    _matchingMnemonicErrorAnimationEvent.value = Event(Unit)
                }
            }
        }
    }

    private fun createAccount() {
        val mnemonicString = mnemonic.joinToString(" ")
        disposables.add(
            interactor.createAccount(accountName, mnemonicString, cryptoType, derivationPath, node)
                .andThen(interactor.isCodeSet())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::continueBasedOnCodeStatus, Throwable::printStackTrace)
        )
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
        createAccount()
    }
}