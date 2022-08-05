package jp.co.soramitsu.feature_account_impl.presentation.account.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class CreateAccountViewModel @AssistedInject constructor(
    @Assisted private val payload: ChainAccountCreatePayload?,
    private val interactor: AccountInteractor,
    private val router: AccountRouter
) : BaseViewModel() {

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    private val _showScreenshotsWarningEvent = MutableLiveData<Event<Unit>>()
    val showScreenshotsWarningEvent: LiveData<Event<Unit>> = _showScreenshotsWarningEvent

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun accountNameChanged(accountName: CharSequence) {
        _nextButtonEnabledLiveData.value = accountName.isNotEmpty()
    }

    fun nextClicked() {
        _showScreenshotsWarningEvent.value = Event(Unit)
    }

    fun screenshotWarningConfirmed(accountName: String) {
        router.openMnemonicScreen(accountName, payload)
    }

    @AssistedFactory
    interface CreateAccountViewModelFactory {
        fun create(payload: ChainAccountCreatePayload?): CreateAccountViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: CreateAccountViewModelFactory,
            payload: ChainAccountCreatePayload?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(payload) as T
            }
        }
    }
}
