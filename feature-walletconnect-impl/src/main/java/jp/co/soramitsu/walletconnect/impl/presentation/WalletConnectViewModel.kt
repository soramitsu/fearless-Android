package jp.co.soramitsu.walletconnect.impl.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.walletconnect.impl.presentation.state.WalletConnectMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WalletConnectViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    savedStateHandle: SavedStateHandle,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository
) : WalletConnectScreenInterface, BaseViewModel() {

    private val pairingTopic: String? = savedStateHandle[WalletConnectFragment.PAIRING_TOPIC_KEY]
    private val proposal: Wallet.Model.SessionProposal = WCDelegate.sessionProposalEvent?.first ?: error("No proposal provided")

    private val selectedOptionalNetworkIds = MutableStateFlow<Set<String>>(emptySet())
    private val selectedWalletIds = MutableStateFlow<Set<Long>>(setOf())

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow: SharedFlow<List<WalletItemViewState>> = combine(accountsFlow, selectedWalletIds) { accounts, selectedWalletIds ->
        val balanceModel = TotalBalance.Empty

        accounts.map {
            WalletItemViewState(
                id = it.id,
                title = it.name,
                isSelected = if (selectedWalletIds.isEmpty()) it.isSelected else it.id in selectedWalletIds,
                walletIcon = it.picture.value,
                balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
                )
            )
        }
    }
        .inBackground()
        .share()

    val state: StateFlow<WalletConnectViewState> = walletItemsFlow.map { walletItems ->
        val chains = walletConnectInteractor.getChains()

        val proposalRequiredChains = proposal.requiredNamespaces.flatMap { it.value.chains.orEmpty() }
        val requiredChains = chains.filter {
            it.caip2id in proposalRequiredChains
        }

        val proposalOptionalChains = proposal.optionalNamespaces.flatMap { it.value.chains.orEmpty() }
        val optionalChains = chains.filter {
            it.caip2id in proposalOptionalChains
        }

        val requiredChainNames: String = requiredChains.joinToString { it.name }
        val optionalChainNames: String = optionalChains.joinToString { it.name }

        val requiredNetworksSelectorState = SelectorState(
            title = resourceManager.getString(R.string.connection_required_networks),
            subTitle = requiredChainNames,
            iconUrl = null,
            actionIcon = null
        )
        val optionalNetworksSelectorState = SelectorState(
            title = resourceManager.getString(R.string.connection_optional_networks),
            subTitle = optionalChainNames,
            iconUrl = null,
        ).takeIf { optionalChains.isNotEmpty() }

        val requiredMethods = proposal.requiredNamespaces.flatMap { it.value.methods }
        val requiredEvents = proposal.requiredNamespaces.flatMap { it.value.events }

        val requiredInfoItems = listOf(
            InfoItemViewState(
                title = resourceManager.getString(R.string.connection_methods),
                subtitle = requiredMethods.joinToString { it }
            ),
            InfoItemViewState(
                title = resourceManager.getString(R.string.connection_events),
                subtitle = requiredEvents.joinToString { it }
            )
        )

        val requiredPermissions = InfoItemSetViewState(
            title = requiredChainNames,
            infoItems = requiredInfoItems
        )

        // optional
        val optionalMethods = proposal.optionalNamespaces.flatMap { it.value.methods }
        val optionalEvents = proposal.optionalNamespaces.flatMap { it.value.events }

        val optionalInfoItems = mutableListOf<InfoItemViewState>()

        if (optionalMethods.isNotEmpty()) {
            optionalInfoItems.add(
                InfoItemViewState(
                    title = resourceManager.getString(R.string.connection_methods),
                    subtitle = optionalMethods.joinToString { it }
                )
            )
        }
        if (optionalEvents.isNotEmpty()) {
            optionalInfoItems.add(
                InfoItemViewState(
                    title = resourceManager.getString(R.string.connection_events),
                    subtitle = optionalEvents.joinToString { it }
                )
            )
        }
        val optionalPermissions = InfoItemSetViewState(
            title = optionalChainNames,
            infoItems = optionalInfoItems
        ).takeIf { optionalInfoItems.isNotEmpty() }

        WalletConnectViewState(
            sessionProposal = proposal,
            requiredPermissions = requiredPermissions,
            optionalPermissions = optionalPermissions,
            requiredNetworksSelectorState = requiredNetworksSelectorState,
            optionalNetworksSelectorState = optionalNetworksSelectorState,
            wallets = walletItems
        )
    }.stateIn(this, SharingStarted.Eagerly, WalletConnectViewState.default)

    init {
        launch {
            checkChainsSupported()

            val initialSelectedWalletId = accountRepository.getSelectedLightMetaAccount().id
            selectedWalletIds.value = setOf(initialSelectedWalletId)
        }
    }

    private suspend fun checkChainsSupported() {
        val chains = walletConnectInteractor.getChains()

        val proposalRequiredChains = proposal.requiredNamespaces.flatMap { it.value.chains.orEmpty() }
        val supportedChains = chains.filter {
            it.caip2id in proposalRequiredChains
        }

        if (supportedChains.size < proposalRequiredChains.size) {
            showError(
                title = resourceManager.getString(R.string.common_error_general_title),
                message = resourceManager.getString(R.string.connection_chains_not_supported_error),
                positiveButtonText = resourceManager.getString(R.string.common_close),
                positiveClick = ::rejectSessionSilent,
                onBackClick = ::rejectSessionSilent
            )
        }
    }

    override fun onClose() {
        launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onApproveClick() {
        val requiredMethods = proposal.requiredNamespaces.flatMap { it.value.methods }
        val isAllMethodsSupported = WalletConnectMethod.values().map { it.method }.containsAll(requiredMethods)

        if (isAllMethodsSupported) {
            approveSession()
        } else {
            showError(
                message = resourceManager.getString(R.string.connection_methods_not_supported_warning),
                positiveButtonText = resourceManager.getString(R.string.connection_approve),
                negativeButtonText = resourceManager.getString(R.string.connection_reject),
                positiveClick = ::approveSession,
                negativeClick = ::rejectSession
            )
        }
    }

    private fun approveSession() {
        val selectedWalletIds = selectedWalletIds.value
        val selectedOptionalChainIds = selectedOptionalNetworkIds.value

        launch(Dispatchers.IO) {
            val chains = walletConnectInteractor.getChains()
            val allMetaAccounts = accountRepository.allMetaAccounts()

            val requiredSessionNamespaces = proposal.requiredNamespaces.mapValues { proposal ->
                val requiredNamespaceChains = chains.filter { chain ->
                    chain.caip2id in proposal.value.chains.orEmpty()
                }

                val requiredAccounts = selectedWalletIds.flatMap { walletId ->
                    requiredNamespaceChains.mapNotNull { chain ->
                        allMetaAccounts.firstOrNull { it.id == walletId }?.address(chain)?.let { address ->
                            chain.caip2id + ":" + address
                        }
                    }
                }

                Wallet.Model.Namespace.Session(
                    chains = proposal.value.chains,
                    accounts = requiredAccounts,
                    events = proposal.value.events,
                    methods = proposal.value.methods
                )
            }

            val optionalSessionNamespaces = if (selectedOptionalChainIds.isEmpty()) {
                mapOf()
            } else {
                proposal.optionalNamespaces.mapValuesNotNull { proposal ->
                    val optionalNamespaceSelectedChains = chains.filter { chain ->
                        chain.caip2id in proposal.value.chains.orEmpty() && chain.id in selectedOptionalChainIds
                    }

                    if (optionalNamespaceSelectedChains.isEmpty()) return@mapValuesNotNull null

                    val optionalAccounts = selectedWalletIds.flatMap { walletId ->
                        optionalNamespaceSelectedChains.mapNotNull { chain ->
                            allMetaAccounts.firstOrNull { it.id == walletId }?.address(chain)?.let { address ->
                                chain.caip2id + ":" + address
                            }
                        }
                    }

                    val sessionChains = optionalNamespaceSelectedChains.map { it.caip2id }

                    Wallet.Model.Namespace.Session(
                        chains = sessionChains,
                        accounts = optionalAccounts,
                        events = proposal.value.events,
                        methods = proposal.value.methods
                    )
                }
            }

            val sessionNamespaces = requiredSessionNamespaces.mapValues { required ->
                val optional = optionalSessionNamespaces[required.key]

                Wallet.Model.Namespace.Session(
                    chains = (required.value.chains.orEmpty() + optional?.chains.orEmpty()).distinct(),
                    accounts = (required.value.accounts + optional?.accounts.orEmpty()).distinct(),
                    events = (required.value.events + optional?.events.orEmpty()).distinct(),
                    methods = (required.value.methods + optional?.methods.orEmpty()).distinct()
                )

            } + optionalSessionNamespaces.filter { it.key !in requiredSessionNamespaces.keys }

            Web3Wallet.approveSession(
                params = Wallet.Params.SessionApprove(
                    proposerPublicKey = proposal.proposerPublicKey,
                    namespaces = sessionNamespaces,
                    relayProtocol = proposal.relayProtocol
                ),
                onSuccess = {
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                            null,
                            null,
                            resourceManager.getString(R.string.connection_approve_success_message)
                        )
                    }
                    WCDelegate.refreshConnections()
                },
                onError = {
                    showError(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = it.throwable.message.orEmpty(),
                        positiveButtonText = resourceManager.getString(R.string.common_close),
                        positiveClick = ::rejectSessionSilent,
                        onBackClick = ::rejectSessionSilent
                    )
                }
            )
        }
    }

    override fun onRejectClicked() {
        rejectSession()
    }

    private fun rejectSession() {
        WCDelegate.sessionProposalEvent?.let {
            Web3Wallet.rejectSession(
                params = Wallet.Params.SessionReject(
                    it.first.proposerPublicKey,
                    "User rejected"
                ),
                onSuccess = {
                    WCDelegate.refreshConnections()
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                            null,
                            null,
                            resourceManager.getString(R.string.common_rejected)
                        )
                    }
                },
                onError = {})
        }
    }

    private fun rejectSessionSilent() {
        WCDelegate.sessionProposalEvent?.let {
            Web3Wallet.rejectSession(
                params = Wallet.Params.SessionReject(
                    it.first.proposerPublicKey,
                    "Blockchain not supported by wallet"
                ),
                onSuccess = {
                    onClose()
                },
                onError = {
                    onClose()
                }
            )
        }
    }

    override fun onOptionalNetworksClicked() {
        val optionalChains = proposal.optionalNamespaces.flatMap { it.value.chains.orEmpty() }
        val selected = selectedOptionalNetworkIds.value
        if (optionalChains.isNotEmpty()) {
            walletConnectRouter.openSelectMultipleChainsForResult(optionalChains, selected.toList())
                .onEach(::handleSelectedChains)
                .launchIn(viewModelScope)
        }
    }

    private fun handleSelectedChains(state: ChainChooseResult) {
        selectedOptionalNetworkIds.value = state.selectedChainIds
    }

    override fun onRequiredNetworksClicked() {
        val requiredNetworks = proposal.requiredNamespaces.flatMap { it.value.chains.orEmpty() }

        launch {
            val chains = walletConnectInteractor.getChains()
            val requiredProposalChains = chains.filter { chain ->
                chain.caip2id in requiredNetworks
            }

            if (requiredNetworks.isNotEmpty()) {
                val selected = requiredProposalChains.map { it.id }
                walletConnectRouter.openSelectMultipleChains(requiredNetworks, selected, isViewMode = true)
            }
        }
    }

    override fun onWalletSelected(item: WalletItemViewState) {
        viewModelScope.launch {
            val currentIds = selectedWalletIds.value

            val newSelected: Set<Long> = if (item.id in currentIds) {
                currentIds.filter { it != item.id }.toSet()
            } else {
                currentIds.plus(item.id)
            }

            selectedWalletIds.value = newSelected
        }
    }
}
