package jp.co.soramitsu.walletconnect.impl.presentation

sealed interface WalletConnectEvent

object NoAction : WalletConnectEvent // , NotifyEvent

interface CoreConnectEvent : WalletConnectEvent {
    object Disconnect : CoreConnectEvent
}

interface SignConnectEvent : WalletConnectEvent {
    object SessionProposal : SignConnectEvent
    data class SessionRequest(val arrayOfArgs: ArrayList<String?>, val numOfArgs: Int) : SignConnectEvent
    object Disconnect : SignConnectEvent

    data class ConnectionState(val isAvailable: Boolean) : SignConnectEvent
}

interface AuthConnectEvent : WalletConnectEvent {
    data class OnRequest(val id: Long, val message: String) : AuthConnectEvent
}
