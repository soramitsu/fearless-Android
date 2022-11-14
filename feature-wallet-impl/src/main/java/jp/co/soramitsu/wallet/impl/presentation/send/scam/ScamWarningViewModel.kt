package jp.co.soramitsu.wallet.impl.presentation.send.scam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ScamWarningViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountRouter: AccountRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val symbol: String? = savedStateHandle[ScamWarningFragment.KEY_PAYLOAD]

    val state: StateFlow<ScamWarningViewState> = flowOf(
        ScamWarningViewState(
            title = resourceManager.getString(R.string.scam_alert_title),
            message = resourceManager.getString(R.string.scam_alert_message_format, symbol.orEmpty())
        )
    ).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ScamWarningViewState(
            title = "",
            message = null
        )
    )

    fun back() {
        accountRouter.back()
    }

    fun topUp() {
        back()
    }
}
