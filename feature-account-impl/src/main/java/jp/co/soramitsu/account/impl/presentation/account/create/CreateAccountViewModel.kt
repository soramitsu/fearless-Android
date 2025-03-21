package jp.co.soramitsu.account.impl.presentation.account.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), CreateAccountCallback {

    private val accountMode = savedStateHandle.get<AccountType>(CreateAccountScreenKeys.ACCOUNT_TYPE_KEY) ?: throw IllegalStateException("ACCOUNT_TYPE_KEY can't be null")
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
            hint = resourceManager.getString(R.string.wallet_name)
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
        if (isFromGoogleBackup) {
            router.openMnemonicAgreementsDialogForGoogleBackup(
                accountName = walletNickname.value,
                accountTypes = listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
            )
        } else {
            _showScreenshotsWarningEvent.value = Event(Unit)
        }
    }

    fun screenshotWarningConfirmed() {
        val accountTypes = when (accountMode) {
            AccountType.SubstrateOrEvm -> listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
            AccountType.Ton -> listOf(WalletEcosystem.Ton)
        }
        router.openMnemonicScreen(walletNickname.value, accountTypes)
    }

    override fun onBackClick() {
        router.back()
    }
}
