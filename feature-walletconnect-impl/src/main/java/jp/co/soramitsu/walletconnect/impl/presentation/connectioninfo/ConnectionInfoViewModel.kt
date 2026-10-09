package jp.co.soramitsu.walletconnect.impl.presentation.connectioninfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import com.reown.walletkit.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.dappUrl
import jp.co.soramitsu.walletconnect.impl.presentation.walletConnectValueOrBack
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
    private val session: Wallet.Model.Session? = walletConnectValueOrBack(
        value = walletConnectInteractor.getActiveSessionByTopic(topic),
        onUnavailable = walletConnectRouter::back
    )

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow: SharedFlow<List<WalletNameItemViewState>> = accountsFlow.mapList {
        WalletNameItemViewState(
            id = it.id,
            title = it.name,
            isSelected = it.isSelected,
            walletIcon = it.picture.value
        )
    }
        .inBackground()
        .share()

    val state = combine(
        walletItemsFlow,
        accountRepository.allMetaAccountsFlow()
    ) { walletItems, allMetaAccounts ->
        val activeSession = session ?: return@combine ConnectInfoViewState.default
        val chains = walletConnectInteractor.getChains()

        val sessionNamespaceChains = activeSession.namespaces.flatMap { it.value.chains.orEmpty() }
        val sessionChains = chains.filter {
            it.caip2id in sessionNamespaceChains
        }

        val sessionChainNames: String = sessionChains.joinToString { it.name }

        val sessionMethods = activeSession.namespaces.flatMap { it.value.methods }
        val sessionEvents = activeSession.namespaces.flatMap { it.value.events }

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

        val sessionAccounts = activeSession.namespaces.flatMap { it.value.accounts }

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
        val expireDate = resourceManager.formatDate(activeSession.expiry * 1000)

        val sessionState = InfoItemViewState(
            title = activeSession.metaData?.name,
            subtitle = activeSession.metaData?.dappUrl,
            imageUrl = activeSession.metaData?.icons?.firstOrNull(),
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
        val activeSession = session ?: run {
            walletConnectRouter.back()
            return
        }
        walletConnectInteractor.disconnectSession(
            topic = topic,
            onSuccess = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    val dappName = activeSession.metaData?.name ?: resourceManager.getString(R.string.common_dapp)
                    walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                        null,
                        null,
                        resourceManager.getString(R.string.connection_disconnect_success_message, dappName),
                        resourceManager.getString(R.string.all_done)
                    )
                }
            },
            onError = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    showError(text = resourceManager.getString(R.string.common_dapp))
                }
            }
        )
    }
}
