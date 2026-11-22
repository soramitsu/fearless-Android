package jp.co.soramitsu.irohaconnect.impl.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.irohaconnect.api.IrohaConnectRouter
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionError
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionException
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectFailure
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectPhase
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IrohaConnectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val service: IrohaConnectService,
    private val router: IrohaConnectRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel(), IrohaConnectScreenCallbacks {

    private val selectedAccount = MutableStateFlow<String?>(null)
    private val linkFailure = MutableStateFlow<IrohaConnectFailure?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<IrohaConnectViewState> = combine(service.snapshot, selectedAccount, linkFailure, busy) { snapshot, selected, failure, isBusy ->
        val chosen = selected?.takeIf { address -> snapshot.accounts.any { it.address == address } }
        IrohaConnectViewStates.build(snapshot, chosen, failure, isBusy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IrohaConnectViewState())

    init {
        savedStateHandle.get<String>(IrohaConnectFragment.KEY_LINK)?.let { link ->
            // The link is consumed once; a recreated screen must never rejoin or replace a session by itself.
            savedStateHandle.remove<String>(IrohaConnectFragment.KEY_LINK)
            viewModelScope.launch {
                WalletSessionLock.awaitUnlocked()
                linkFailure.value = service.connect(link)
            }
        }
    }

    override fun onAccountSelected(address: String) {
        selectedAccount.value = address
    }

    override fun onApproveSession() = perform {
        val address = state.value.accounts.firstOrNull { it.selected }?.address
            ?: throw IrohaConnectActionException(IrohaConnectActionError.ACCOUNT_UNAVAILABLE)
        service.approveSession(address)
    }

    override fun onRejectSession() = perform { service.rejectSession() }

    override fun onApproveRequest() = perform {
        val requestId = service.snapshot.value.request?.requestId ?: throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
        service.approveRequest(requestId)
    }

    override fun onRejectRequest() = perform {
        val requestId = service.snapshot.value.request?.requestId ?: throw IrohaConnectActionException(IrohaConnectActionError.NOT_AVAILABLE)
        service.rejectRequest(requestId)
    }

    override fun onDisconnect() = perform { service.disconnect() }

    override fun onDismissMessage() {
        linkFailure.value = null
        viewModelScope.launch { runCatching { service.clearMessages() } }
    }

    override fun onClose() {
        // Closing the screen keeps an active session; it ends only on Disconnect or when the dApp ends it.
        val phase = service.snapshot.value.phase
        if (phase == IrohaConnectPhase.ERROR || phase == IrohaConnectPhase.IDLE) onDismissMessage()
        router.closeIrohaConnect()
    }

    private fun perform(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                if (WalletSessionLock.isLocked()) throw IrohaConnectActionException(IrohaConnectActionError.WALLET_LOCKED)
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IrohaConnectActionException) {
                showError(resourceManager.getString(IrohaConnectViewStates.actionMessage(error.error)))
            } catch (_: Exception) {
                showError(resourceManager.getString(IrohaConnectViewStates.actionMessage(IrohaConnectActionError.NOT_AVAILABLE)))
            } finally {
                busy.value = false
            }
        }
    }
}
