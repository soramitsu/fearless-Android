package jp.co.soramitsu.account.impl.presentation.account.rename

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RenameAccountViewModel @Inject constructor(
    private val router: AccountRouter,
    private val interactor: AccountInteractor,
    resourceManager: ResourceManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), RenameAccountCallback {

    private val walletId = savedStateHandle.get<Long>(RenameAccountDialog.WALLET_ID_KEY) ?: error("Not specified walletId for rename")
    private val walletName = savedStateHandle.get<String>(RenameAccountDialog.WALLET_NAME_KEY).orEmpty()

    private val initialNameInput = TextInputViewState(
        text = walletName,
        hint = resourceManager.getString(R.string.wallet_name)
    )

    private val walletNickname = MutableStateFlow(walletName)

    private val isSaveEnabled = walletNickname.map {
        it.isNotBlank()
    }

    val state = combine(
        walletNickname,
        isSaveEnabled
    ) { walletNickname, isSaveEnabled ->
        RenameAccountState(
            walletNickname = TextInputViewState(
                text = walletNickname,
                hint = resourceManager.getString(R.string.wallet_name)
            ),
            isSaveEnabled = isSaveEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = RenameAccountState.Empty.copy(walletNickname = initialNameInput))

    override fun accountNameChanged(accountName: CharSequence) {
        walletNickname.value = accountName.toString()
    }

    override fun onSaveClicked() {
        walletNickname.value?.let {
            launch {
                interactor.updateAccountName(walletId, it)
                router.back()
            }
        }
    }

    override fun onBackClick() {
        router.back()
    }
}
