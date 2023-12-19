package jp.co.soramitsu.walletconnect.impl.presentation.connectioninfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionInfoViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    savedStateHandle: SavedStateHandle,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository
) : ConnectionInfoScreenInterface, BaseViewModel() {
    private val topic: String = savedStateHandle[ConnectionInfoFragment.CONNECTION_TOPIC_KEY] ?: error("No connection info provided")
    private val session: Wallet.Model.Session = Web3Wallet.getActiveSessionByTopic(topic) ?: error("No proposal provided")

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow: SharedFlow<List<WalletItemViewState>> = accountsFlow.mapList {
        val balanceModel = TotalBalance.Empty

        WalletItemViewState(
            id = it.id,
            title = it.name,
            isSelected = it.isSelected,
            walletIcon = it.picture.value,
            balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
            changeBalanceViewState = ChangeBalanceViewState(
                percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
            )
        )
    }
        .inBackground()
        .share()

    val state = combine(
        walletItemsFlow,
        accountRepository.allMetaAccountsFlow()
    ) { walletItems, allMetaAccounts ->
        val chains = walletConnectInteractor.getChains()

        val sessionNamespaceChains = session.namespaces.flatMap { it.value.chains.orEmpty() }
        val sessionChains = chains.filter {
            it.caip2id in sessionNamespaceChains
        }

        val sessionChainNames: String = sessionChains.joinToString { it.name }

        val sessionMethods = session.namespaces.flatMap { it.value.methods }
        val sessionEvents = session.namespaces.flatMap { it.value.events }

        val requiredInfoItems = listOf(
            InfoItemViewState(
                title = resourceManager.getString(R.string.connection_methods),
                subtitle = sessionMethods.joinToString { it }
            ),
            InfoItemViewState(
                title = resourceManager.getString(R.string.connection_events),
                subtitle = sessionEvents.joinToString { it }
            )
        )

        val sessionPermissions = InfoItemSetViewState(
            title = sessionChainNames,
            infoItems = requiredInfoItems
        )

        val sessionAccounts = session.namespaces.flatMap { it.value.accounts }

        val sessionWalletsIds = allMetaAccounts.filter { wallet ->
            val walletAddresses = sessionChains.mapNotNull { chain ->
                wallet.address(chain)
            }
            sessionAccounts.any { sessionAccount ->
                walletAddresses.any {
                    sessionAccount.endsWith(it)
                }
            }
        }.map {
            it.id
        }
        val sessionWalletItems = walletItems.filter { it.id in sessionWalletsIds }

        @Suppress("MagicNumber")
        val expireDate = resourceManager.formatDate(session.expiry * 1000)

        val sessionState = InfoItemViewState(
            title = session.metaData?.name,
            subtitle = session.metaData?.dappUrl,
            imageUrl = session.metaData?.icons?.firstOrNull(),
            placeholderIcon = R.drawable.ic_dapp_connection
        )

        ConnectInfoViewState(
            session = sessionState,
            permissions = sessionPermissions,
            wallets = sessionWalletItems,
            expireDate = expireDate
        )
    }
        .stateIn(this, SharingStarted.Eagerly, ConnectInfoViewState.default)

    override fun onClose() {
        launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onDisconnectClick() {
        Web3Wallet.disconnectSession(
            params = Wallet.Params.SessionDisconnect(topic),
            onSuccess = {
                WCDelegate.refreshConnections()
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                        null,
                        null,
                        resourceManager.getString(R.string.connection_disconnect_success_message)
                    )
                }
            },
            onError = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    showError(text = resourceManager.getString(R.string.common_try_again))
                }
            }
        )
    }
}
