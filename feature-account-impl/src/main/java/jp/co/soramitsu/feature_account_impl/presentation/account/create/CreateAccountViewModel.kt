package jp.co.soramitsu.feature_account_impl.presentation.account.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin

class CreateAccountViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val networkChooserMixin: NetworkChooserMixin
) : BaseViewModel(),
    NetworkChooserMixin by networkChooserMixin {

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
        val selectedNetwork = selectedNetworkLiveData.value ?: return
        router.openMnemonicScreen(accountName, selectedNetwork.networkTypeUI.networkType)
    }
}