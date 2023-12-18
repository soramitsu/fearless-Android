package jp.co.soramitsu.walletconnect.impl.presentation

import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import jp.co.soramitsu.common.utils.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object WCDelegate : Web3Wallet.WalletDelegate, CoreClient.CoreDelegate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _coreEvents: MutableSharedFlow<Core.Model> = MutableSharedFlow()
    val coreEvents: SharedFlow<Core.Model> = _coreEvents.asSharedFlow()

    private val _walletEvents: MutableSharedFlow<Wallet.Model> = MutableSharedFlow()
    val walletEvents: SharedFlow<Wallet.Model> = _walletEvents.asSharedFlow()
    var authRequestEvent: Pair<Wallet.Model.AuthRequest, Wallet.Model.VerifyContext>? = null
    var sessionProposalEvent: Pair<Wallet.Model.SessionProposal, Wallet.Model.VerifyContext>? = null
    var sessionRequestEvent: Pair<Wallet.Model.SessionRequest, Wallet.Model.VerifyContext>? = null

    init {
        CoreClient.setDelegate(this)
        Web3Wallet.setWalletDelegate(this)
    }

    private val updateSessions = MutableStateFlow(Event(Unit))
    val activeSessionFlow = updateSessions.map {
        println("!!! WCDelegate activeSessionFlow updateSessions: $it")
        Web3Wallet.getListOfActiveSessions()
    }

    fun refreshConnections() {
        println("!!! WCDelegate refreshConnections")
        updateSessions.value = Event(Unit)
    }

    override fun onAuthRequest(authRequest: Wallet.Model.AuthRequest, verifyContext: Wallet.Model.VerifyContext) {
        println("!!! WCDelegate onAuthRequest")
        authRequestEvent = Pair(authRequest, verifyContext)

        scope.launch {
            _walletEvents.emit(authRequest)
        }
    }

    override fun onConnectionStateChange(state: Wallet.Model.ConnectionState) {
        scope.launch {
            _walletEvents.emit(state)
        }
    }

    override fun onError(error: Wallet.Model.Error) {
        println("!!! error WC Delegate")
        error.throwable.printStackTrace()
        scope.launch {
            _walletEvents.emit(error)
        }
    }

    override fun onSessionDelete(sessionDelete: Wallet.Model.SessionDelete) {
        println("!!! WCDelegate onSessionDelete")
        refreshConnections()
        scope.launch {
            _walletEvents.emit(sessionDelete)
        }
    }

    override fun onSessionExtend(session: Wallet.Model.Session) {
        println("!!! WCDelegate onSessionExtend")
    }

    override fun onSessionProposal(
        sessionProposal: Wallet.Model.SessionProposal,
        verifyContext: Wallet.Model.VerifyContext
    ) {
        println("!!! WCDelegate onSessionProposal !!!")
        sessionProposalEvent = Pair(sessionProposal, verifyContext)

        scope.launch {
            _walletEvents.emit(sessionProposal)
        }
    }

    override fun onSessionRequest(
        sessionRequest: Wallet.Model.SessionRequest,
        verifyContext: Wallet.Model.VerifyContext
    ) {
        println("!!! WCDelegate onSessionRequest")
        // Once a Dapp sends the session request, it will be handled by the
        // onSessionRequest callback in the WalletDelegate
        sessionRequestEvent = Pair(sessionRequest, verifyContext)

        scope.launch {
            _walletEvents.emit(sessionRequest)
        }
    }

    override fun onSessionSettleResponse(settleSessionResponse: Wallet.Model.SettledSessionResponse) {
        println("!!! WCDelegate onSessionSettleResponse")
        refreshConnections()
        scope.launch {
            _walletEvents.emit(settleSessionResponse)
        }
    }

    override fun onSessionUpdateResponse(sessionUpdateResponse: Wallet.Model.SessionUpdateResponse) {
        println("!!! WCDelegate onSessionUpdateResponse")
        scope.launch {
            _walletEvents.emit(sessionUpdateResponse)
        }
    }

    override fun onPairingDelete(deletedPairing: Core.Model.DeletedPairing) {
        println("!!! WCDelegate onPairingDelete")
        scope.launch {
            _coreEvents.emit(deletedPairing)
        }
    }
}
