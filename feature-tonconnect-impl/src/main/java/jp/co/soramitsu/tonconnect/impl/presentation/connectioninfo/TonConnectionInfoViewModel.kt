package jp.co.soramitsu.tonconnect.impl.presentation.connectioninfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.DappModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TonConnectionInfoViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val tonConnectInteractor: TonConnectInteractor,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    savedStateHandle: SavedStateHandle
) : TonConnectionInfoScreenInterface, BaseViewModel() {

    private val dapp: DappModel = savedStateHandle[TonConnectionInfoFragment.TON_CONNECTION_INFO_KEY] ?: error("No connection info provided")
    private val walletId = dapp.metaId ?: error("Wallet for the connection not specified")

    private val account = flowOf {
        accountListingMixin.getAccount(walletId, AddressIconGenerator.SIZE_BIG)
    }

    private val walletItem: SharedFlow<WalletNameItemViewState?> = account.map {
        WalletNameItemViewState(
            id = it.id,
            title = it.name,
            isSelected = true,
            walletIcon = it.picture.value
        )
    }
        .inBackground()
        .share()

    val state: StateFlow<TonConnectionInfoViewState> = walletItem.map { walletItem ->
        val chain = tonConnectInteractor.getChain()

        val requiredNetworksSelectorState = SelectorState(
            title = resourceManager.getString(R.string.connection_required_networks),
            subTitle = chain.name,
            iconUrl = chain.icon,
            actionIcon = null
        )

        TonConnectionInfoViewState(
            appInfo = dapp,
            requiredNetworksSelectorState = requiredNetworksSelectorState,
            wallet = walletItem
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TonConnectionInfoViewState.default)

    override fun onClose() {
        tonConnectRouter.back()
    }

    override fun onDisconnectClick() {
        viewModelScope.launch {
            tonConnectInteractor.disconnect(dapp.identifier)
            tonConnectRouter.back()

            val dappName = dapp.name ?: resourceManager.getString(R.string.common_dapp)
            tonConnectRouter.openOperationSuccess(
                null,
                null,
                resourceManager.getString(R.string.connection_disconnect_success_message, dappName),
                resourceManager.getString(R.string.all_done)
            )
        }
    }
}
