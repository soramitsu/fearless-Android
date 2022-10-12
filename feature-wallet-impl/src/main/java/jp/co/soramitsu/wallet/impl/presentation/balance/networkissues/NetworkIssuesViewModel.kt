package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues

import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NetworkIssuesViewModel @Inject constructor(
    private val router: WalletRouter,
    private val updatesMixin: UpdatesMixin,
    private val networkStateMixin: NetworkStateMixin
) : BaseViewModel(), UpdatesProviderUi by updatesMixin, NetworkStateUi by networkStateMixin {

//    val state = walletItemsFlow.map { walletItems ->
//        NetworkIssuesState(
//            issues = emptyList()
//        )
//    }.stateIn(
//        viewModelScope,
//        SharingStarted.Eagerly,
//        NetworkIssuesState(
//            emptyList()
//        )
//    )

    val state = networkStateMixin.networkIssuesLiveData.asFlow().map {
        NetworkIssuesState(issues = it)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            NetworkIssuesState(emptyList())
        )

    fun onIssueClicked(issue: NetworkIssueItemState) {

        println("!!! CLICKED ISSUE: ${issue.title}")
    }

    fun onBackClicked() {
        router.back()
    }
}
