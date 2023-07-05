package jp.co.soramitsu.account.impl.presentation.account.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.utils.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val router: AccountRouter,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), CreateAccountCallback {

    private val payload = savedStateHandle.getLiveData<ChainAccountCreatePayload>(CreateAccountScreenKeys.PAYLOAD_KEY)
    private val isFromGoogleBackup = savedStateHandle.get<Boolean>(CreateAccountScreenKeys.IS_FROM_GOOGLE_BACKUP_KEY) ?: false

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    private val _showScreenshotsWarningEvent = MutableLiveData<Event<Unit>>()
    val showScreenshotsWarningEvent: LiveData<Event<Unit>> = _showScreenshotsWarningEvent

    private val walletNickname = MutableStateFlow("")
    private val isContinueEnabled = walletNickname.map {
        it.isNotBlank()
    }

    private val walletNameInputViewState = walletNickname.map { walletNickname ->
        TextInputViewState(
            text = walletNickname,
            hint = "Wallet name"
        )
    }

    val state = combine(
        walletNameInputViewState,
        isContinueEnabled
    ) { walletNameInputViewState, isContinueEnabled ->
        CreateAccountState(
            walletNickname = walletNameInputViewState,
            isContinueEnabled = isContinueEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = CreateAccountState.Empty)

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    override fun accountNameChanged(accountName: CharSequence) {
        walletNickname.value = accountName.toString()
        _nextButtonEnabledLiveData.value = accountName.isNotEmpty()
    }

    override fun nextClicked() {
        if (isFromGoogleBackup && BuildConfig.DEBUG) {
            router.openMnemonicAgreementsDialog(
                isFromGoogleBackup = isFromGoogleBackup,
                accountName = walletNickname.value
            )
        } else {
            _showScreenshotsWarningEvent.value = Event(Unit)
        }
    }

    fun screenshotWarningConfirmed() {
        router.openMnemonicScreen(isFromGoogleBackup, walletNickname.value, payload.value)
    }

    override fun onBackClick() {
        router.back()
    }
}
