package co.jp.soramitsu.walletconnect.model

/**
 * What a dApp says about itself. All of it is self-reported: only [WalletConnectVerifyContext]
 * says where a dApp really runs.
 */
data class WalletConnectMetadata(
    val name: String,
    val description: String,
    val url: String,
    val icons: List<String>,
    val redirect: String? = null
)

/** The chains, methods and events a dApp asks for in one CAIP-2 namespace, for example `eip155`. */
data class WalletConnectProposalNamespace(
    val chains: List<String>?,
    val methods: List<String>,
    val events: List<String>
)

/** What a session approves in one namespace: CAIP-10 accounts on its chains, and the methods and events. */
data class WalletConnectSessionNamespace(
    val chains: List<String>?,
    val accounts: List<String>,
    val methods: List<String>,
    val events: List<String>
)

/** A dApp's request to open a session (`wc_sessionPropose`). */
data class WalletConnectProposal(
    val id: Long,
    val pairingTopic: String,
    val proposer: WalletConnectMetadata,
    val proposerPublicKey: String,
    val requiredNamespaces: Map<String, WalletConnectProposalNamespace>,
    val optionalNamespaces: Map<String, WalletConnectProposalNamespace>,
    val relayProtocol: String,
    /** Unix seconds after which the dApp no longer waits for an answer. */
    val expiryTimestamp: Long
) {
    val name: String get() = proposer.name
    val url: String get() = proposer.url
    val icons: List<String> get() = proposer.icons
}

/** One JSON-RPC call inside a session request; [params] is its raw JSON text. */
data class WalletConnectRpcRequest(
    val id: Long,
    val method: String,
    val params: String
)

/** A dApp's call on an open session (`wc_sessionRequest`) that waits for the user. */
data class WalletConnectRequest(
    val topic: String,
    val chainId: String?,
    val peerMetaData: WalletConnectMetadata?,
    val request: WalletConnectRpcRequest,
    /** Unix seconds after which the request can no longer be answered. */
    val expiryTimestamp: Long? = null
)

/** An open session with a dApp. */
data class WalletConnectSession(
    val topic: String,
    val pairingTopic: String,
    /** Unix seconds. */
    val expiry: Long,
    val namespaces: Map<String, WalletConnectSessionNamespace>,
    val requiredNamespaces: Map<String, WalletConnectProposalNamespace>,
    val optionalNamespaces: Map<String, WalletConnectProposalNamespace>,
    val metaData: WalletConnectMetadata?,
    /** Whether the dApp confirmed the session. */
    val acknowledged: Boolean,
    /** Where the dApp ran when it proposed the session, as WalletConnect Verify attested it. */
    val verifyContext: WalletConnectVerifyContext? = null
)

/**
 * Verify API result. VALID: the attested origin is the dApp's claimed URL. INVALID: the dApp runs
 * on another origin than it claims. UNKNOWN: no valid attestation came with the message.
 */
enum class WalletConnectValidation { VALID, INVALID, UNKNOWN }

data class WalletConnectVerifyContext(
    val validation: WalletConnectValidation,
    /** The origin the Verify server attested; null when [validation] is UNKNOWN. */
    val verifiedOrigin: String?,
    /** The URL the dApp claims in its metadata. */
    val claimedUrl: String?,
    /** The Verify server lists the origin as a known scam. */
    val isScam: Boolean
) {
    companion object {
        fun unknown(claimedUrl: String?) = WalletConnectVerifyContext(WalletConnectValidation.UNKNOWN, null, claimedUrl, isScam = false)
    }
}

/** What the WalletConnect client reports to the app. */
sealed interface WalletConnectEvent {
    data class SessionProposal(val proposal: WalletConnectProposal, val verifyContext: WalletConnectVerifyContext) : WalletConnectEvent
    data class SessionRequest(val request: WalletConnectRequest, val verifyContext: WalletConnectVerifyContext) : WalletConnectEvent
    data class SessionDeleted(val topic: String) : WalletConnectEvent
    data class SessionSettled(val topic: String) : WalletConnectEvent
    data class ConnectionChanged(val isConnected: Boolean) : WalletConnectEvent
}

/** A scanned or opened link is not a valid WalletConnect v2 pairing URI (v1 URIs included). */
class WalletConnectUriException(message: String) : IllegalArgumentException(message)

/** The pairing URI is valid but its `expiryTimestamp` has passed: the dApp must show a new one. */
class WalletConnectPairingExpiredException : IllegalStateException("The WalletConnect link has expired")
