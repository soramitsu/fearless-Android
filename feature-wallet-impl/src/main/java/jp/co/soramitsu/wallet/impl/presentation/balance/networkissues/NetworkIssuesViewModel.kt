package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NetworkIssuesViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val accountInteractor: AccountInteractor,
    private val updatesMixin: UpdatesMixin,
    private val networkStateMixin: NetworkStateMixin,
    private val resourceManager: ResourceManager
) : BaseViewModel(), UpdatesProviderUi by updatesMixin, NetworkStateUi by networkStateMixin {

    val state = combine(
        networkStateMixin.networkIssuesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet()),
        walletInteractor.assetsFlow().map {
            it.filter { !it.hasAccount && !it.asset.markedNotNeed }.map {
                NetworkIssueItemState(
                    iconUrl = it.asset.token.configuration.chainIcon ?: it.asset.token.configuration.iconUrl,
                    title = it.asset.token.configuration.chainName,
                    type = NetworkIssueType.Account,
                    chainId = it.asset.token.configuration.chainId,
                    chainName = it.asset.token.configuration.chainName,
                    assetId = it.asset.token.configuration.id
                )
            }
        }
    ) { networkIssues, assetsWoAccount ->
        networkIssues.plus(assetsWoAccount.toSet())
    }.map {
        NetworkIssuesState(it.toList())
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            NetworkIssuesState(emptyList())
        )

    fun onIssueClicked(issue: NetworkIssueItemState) {
        when (issue.type) {
            NetworkIssueType.Node -> {
                walletRouter.openNodes(issue.chainId)
            }
            NetworkIssueType.Network -> {
                val payload = AlertViewState(
                    title = resourceManager.getString(R.string.staking_main_network_title, issue.chainName),
                    message = resourceManager.getString(R.string.network_issue_unavailable),
                    buttonText = resourceManager.getString(R.string.top_up),
                    iconRes = R.drawable.ic_alert_16
                )
                walletRouter.openAlert(payload)
            }
            NetworkIssueType.Account -> launch {
                val meta = accountInteractor.selectedMetaAccountFlow().first()
                val payload = AddAccountBottomSheet.Payload(
                    metaId = meta.id,
                    chainId = issue.chainId,
                    chainName = issue.chainName,
                    assetId = issue.assetId,
                    priceId = issue.priceId,
                    markedAsNotNeed = false
                )
                walletRouter.openOptionsAddAccount(payload)
            }
        }
    }

    fun onBackClicked() {
        walletRouter.back()
    }
}
