package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues.unavailable

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.wallet.impl.presentation.balance.networkissues.unavailable.NetworkUnavailableFragment.Companion.KEY_PAYLOAD
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NetworkUnavailableViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountRouter: AccountRouter
) : BaseViewModel() {

    private val chainName: String? = savedStateHandle[KEY_PAYLOAD]

    val state: StateFlow<NetworkUnavailableViewState> = flowOf(
        NetworkUnavailableViewState(
            chainName = chainName.orEmpty()
        )
    ).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        NetworkUnavailableViewState(
            chainName = chainName.orEmpty()
        )
    )

    fun back() {
        accountRouter.back()
    }

    fun topUp() {
        back()
    }
}
