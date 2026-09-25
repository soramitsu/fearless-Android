package jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal

import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import com.reown.walletkit.client.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectMethod
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.walletConnectValueOrBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionProposalViewModel @Inject constructor(
    accountListingMixin: AccountListingMixin,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val walletConnectRouter: WalletConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository
) : SessionProposalScreenInterface, BaseViewModel() {

//    private val pairingTopic: String? = savedStateHandle[SessionProposalFragment.PAIRING_TOPIC_KEY]
    private val proposal: Wallet.Model.SessionProposal? = walletConnectValueOrBack(
        value = WCDelegate.sessionProposalEvent?.first,
        onUnavailable = walletConnectRouter::back
    )

    private val selectedOptionalNetworkIds = MutableStateFlow(
        proposal?.optionalNamespaces.orEmpty().flatMap {
            it.value.chains.orEmpty()
        }.toSet()
    )
    private val selectedWalletIds = MutableStateFlow<Set<Long>>(setOf())
    private val isApproving = MutableStateFlow(false)
    private val isRejecting = MutableStateFlow(false)

    private val accountsFlow = accountListingMixin.accountsFlow(AddressIconGenerator.SIZE_BIG)

    private val walletItemsFlow: SharedFlow<List<WalletNameItemViewState>> = combine(accountsFlow, selectedWalletIds) { accounts, selectedWalletIds ->
        accounts.map {
            WalletNameItemViewState(
                id = it.id,
                title = it.name,
                isSelected = if (selectedWalletIds.isEmpty()) it.isSelected else it.id in selectedWalletIds,
                walletIcon = it.picture.value
            )
        }
    }
        .inBackground()
        .share()

    val state: StateFlow<SessionProposalViewState> = combine(
        walletItemsFlow,
        isApproving,
        isRejecting
    ) { walletItems, isApproving, isRejecting ->
        val activeProposal = proposal ?: return@combine SessionProposalViewState.default
        val chains = walletConnectInteractor.getChains()

        val proposalRequiredChains = activeProposal.requiredNamespaces.flatMap { it.value.chains.orEmpty() }
        val requiredChains = chains.filter {
            it.caip2id in proposalRequiredChains
        }

        val proposalOptionalChains = activeProposal.optionalNamespaces.flatMap { it.value.chains.orEmpty() }
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
        ).takeIf { requiredChains.isNotEmpty() }
        val optionalNetworksSelectorState = SelectorState(
            title = resourceManager.getString(R.string.connection_optional_networks),
            subTitle = optionalChainNames,
            iconUrl = null,
        ).takeIf { optionalChains.isNotEmpty() }

        val requiredMethods = activeProposal.requiredNamespaces.flatMap { it.value.methods }
        val requiredEvents = activeProposal.requiredNamespaces.flatMap { it.value.events }

        val requiredInfoItems = mutableListOf<InfoItemViewState>()
        if (requiredMethods.isNotEmpty()) {
            requiredInfoItems.add(
                InfoItemViewState(
                    title = resourceManager.getString(R.string.connection_methods),
                    subtitle = requiredMethods.joinToString { it }
                )
            )
        }
        if (requiredEvents.isNotEmpty()) {
            requiredInfoItems.add(
                InfoItemViewState(
                    title = resourceManager.getString(R.string.connection_events),
                    subtitle = requiredEvents.joinToString { it }
                )
            )
        }

        val requiredPermissions = InfoItemSetViewState(
            title = requiredChainNames,
            infoItems = requiredInfoItems
        ).takeIf { requiredInfoItems.isNotEmpty() }

        // optional
        val optionalMethods = activeProposal.optionalNamespaces.flatMap { it.value.methods }
        val optionalEvents = activeProposal.optionalNamespaces.flatMap { it.value.events }

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

        SessionProposalViewState(
            sessionProposal = activeProposal,
            requiredPermissions = requiredPermissions,
            optionalPermissions = optionalPermissions,
            requiredNetworksSelectorState = requiredNetworksSelectorState,
            optionalNetworksSelectorState = optionalNetworksSelectorState,
            wallets = walletItems,
            approving = isApproving,
            rejecting = isRejecting
        )
    }.stateIn(this, SharingStarted.Eagerly, SessionProposalViewState.default)

    init {
        launch {
            val activeProposal = proposal ?: return@launch
            walletConnectInteractor.checkChainsSupported(activeProposal).getOrNull()?.let { isSupported ->
                if (isSupported.not()) {
                    showError(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = resourceManager.getString(R.string.connection_chains_not_supported_error),
                        positiveButtonText = resourceManager.getString(R.string.common_close),
                        positiveClick = ::callSilentRejectSession,
                        onBackClick = ::callSilentRejectSession
                    )
                }
            }

            val initialSelectedWalletId = accountRepository.getSelectedLightMetaAccount().id
            selectedWalletIds.value = setOf(initialSelectedWalletId)
        }
    }

    override fun onClose() {
        viewModelScope.launch(Dispatchers.Main) {
            walletConnectRouter.back()
        }
    }

    override fun onApproveClick() {
        val activeProposal = proposal ?: run {
            walletConnectRouter.back()
            return
        }
        val requiredMethods = activeProposal.requiredNamespaces.flatMap { it.value.methods }
        val isAllMethodsSupported = WalletConnectMethod.values().map { it.method }.containsAll(requiredMethods)

        if (isAllMethodsSupported) {
            callSessionApprove()
        } else {
            showError(
                message = resourceManager.getString(R.string.connection_methods_not_supported_warning),
                positiveButtonText = resourceManager.getString(R.string.connection_approve),
                negativeButtonText = resourceManager.getString(R.string.connection_reject),
                positiveClick = ::callSessionApprove,
                negativeClick = ::callRejectSession
            )
        }
    }

    private fun callSessionApprove() {
        if (isApproving.value) return
        val activeProposal = proposal ?: run {
            walletConnectRouter.back()
            return
        }
        isApproving.value = true

        launch {
            val selectedWalletIds = selectedWalletIds.value
            val selectedOptionalChainIds = selectedOptionalNetworkIds.value
            walletConnectInteractor.approveSession(
                proposal = activeProposal,
                selectedWalletIds = selectedWalletIds,
                selectedOptionalChainIds = selectedOptionalChainIds,
                onSuccess = onApproveSessionSuccess(activeProposal),
                onError = ::onApproveSessionError
            )
        }
    }

    private fun onApproveSessionSuccess(
        activeProposal: Wallet.Model.SessionProposal
    ): (Wallet.Params.SessionApprove) -> Unit = {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                null,
                null,
                resourceManager.getString(R.string.connection_approve_success_message, activeProposal.name),
                resourceManager.getString(R.string.all_done)
            )
        }
        isApproving.value = false
        WCDelegate.refreshConnections()
    }

    private fun onApproveSessionError(error: Wallet.Model.Error) {
        isApproving.value = false
        viewModelScope.launch(Dispatchers.Main.immediate) {
            showError(
                title = resourceManager.getString(R.string.common_error_general_title),
                message = error.throwable.message.orEmpty(),
                positiveButtonText = resourceManager.getString(R.string.common_close),
                positiveClick = ::callSilentRejectSession,
                onBackClick = ::callSilentRejectSession
            )
        }
    }

    override fun onRejectClicked() {
        callRejectSession()
    }

    private fun callRejectSession() {
        if (isRejecting.value) return
        val activeProposal = proposal ?: run {
            walletConnectRouter.back()
            return
        }
        isRejecting.value = true

        walletConnectInteractor.rejectSession(
            proposal = activeProposal,
            onSuccess = onRejectSessionSuccess(),
            onError = {
                isRejecting.value = false
            }
        )
    }

    private fun onRejectSessionSuccess(): (Wallet.Params.SessionReject) -> Unit = {
        WCDelegate.refreshConnections()
        isRejecting.value = false
        viewModelScope.launch(Dispatchers.Main.immediate) {
            walletConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
                null,
                null,
                resourceManager.getString(R.string.common_rejected),
                resourceManager.getString(R.string.all_done)
            )
        }
    }

    private fun callSilentRejectSession() {
        val activeProposal = proposal ?: run {
            onClose()
            return
        }
        walletConnectInteractor.silentRejectSession(
            proposal = activeProposal,
            onSuccess = { onClose() },
            onError = { onClose() }
        )
    }

    override fun onOptionalNetworksClicked() {
        val activeProposal = proposal ?: run {
            walletConnectRouter.back()
            return
        }
        val optionalChains = activeProposal.optionalNamespaces.flatMap { it.value.chains.orEmpty() }
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
        val activeProposal = proposal ?: run {
            walletConnectRouter.back()
            return
        }
        val requiredNetworks = activeProposal.requiredNamespaces.flatMap { it.value.chains.orEmpty() }

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

    override fun onWalletSelected(item: WalletNameItemViewState) {
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
