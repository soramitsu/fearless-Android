package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues

import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.mixin.api.DemoMixin
import jp.co.soramitsu.common.mixin.api.DemoUi
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val demoMixin: DemoMixin
) : BaseViewModel(), UpdatesProviderUi by updatesMixin, NetworkStateUi by networkStateMixin, DemoUi by demoMixin {

    val demoIssues = demoMixin.enableDemoWarningsFlow.flatMapLatest {
        if (it) {
            walletInteractor.getChains()
        } else {
            flowOf(emptyList())
        }
    }.map { chains ->
        val listNodeError = chains.filter { it.nodes.size > 1 }
        val chainWithNodeError = if (listNodeError.size > 1) {
            listNodeError.random()
        } else {
            null
        }
        val listNetworkError = chains.filter { it.nodes.size <= 1 }

        val chainWithNetworkError = if (listNetworkError.size > 1) {
            listNetworkError.random()
        } else {
            null
        }

        listOf(chainWithNodeError, chainWithNetworkError).mapNotNull { it }.map { chain ->
            NetworkIssueItemState(
                iconUrl = chain.icon,
                title = chain.name,
                type = when {
                    chain.nodes.size > 1 -> NetworkIssueType.Node
                    else -> NetworkIssueType.Network
                },
                chainId = chain.id,
                chainName = chain.name,
                assetId = chain.utilityAsset.id,
                priceId = chain.utilityAsset.priceId
            )
        }
    }

    val state = combine(
        networkStateMixin.networkIssuesLiveData.asFlow(),
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
        },
        demoIssues
    ) { networkIssues, assetsWoAccount, demoIssues ->
        demoIssues.plus(networkIssues).plus(assetsWoAccount)
    }.map {
        NetworkIssuesState(it)
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
                walletRouter.openNetworkUnavailable(issue.chainName)
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
