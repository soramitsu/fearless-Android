package jp.co.soramitsu.account.impl.presentation.account.rename

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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

    private val walletNickname = MutableStateFlow<String?>(null)
    private val isSaveEnabled = walletNickname.map {
        it.isNullOrBlank().not()
    }
    private val heightDiffDpFlow = MutableStateFlow(0.dp)

    private val walletNameInputViewState = walletNickname.mapNotNull { walletNickname ->
        walletNickname?.let {
            TextInputViewState(
                text = walletNickname,
                hint = resourceManager.getString(R.string.wallet_name)
            )
        }
    }

    val state = combine(
        walletNameInputViewState,
        isSaveEnabled,
        heightDiffDpFlow
    ) { walletNameInputViewState, isSaveEnabled, heightDiffDp ->
        RenameAccountState(
            walletNickname = walletNameInputViewState,
            isSaveEnabled = isSaveEnabled,
            heightDiffDp = heightDiffDp
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = RenameAccountState.Empty)

    init {
        launch {
            val account = interactor.getMetaAccount(walletId)
            walletNickname.value = account.name
        }
    }

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

    fun setHeightDiffDp(value: Dp) {
        heightDiffDpFlow.value = value
    }
}
