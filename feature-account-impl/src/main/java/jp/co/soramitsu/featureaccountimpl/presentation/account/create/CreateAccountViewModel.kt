package jp.co.soramitsu.featureaccountimpl.presentation.account.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountInteractor
import jp.co.soramitsu.featureaccountapi.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.featureaccountimpl.presentation.AccountRouter
import javax.inject.Inject

@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val payload = savedStateHandle.getLiveData<ChainAccountCreatePayload>(CreateAccountFragment.PAYLOAD_KEY)

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
        router.openMnemonicScreen(accountName, payload.value)
    }
}
