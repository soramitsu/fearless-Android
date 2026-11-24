package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectProposalNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the WalletConnect client keeps: the relay identity seed, pairings, sessions and their
 * symmetric keys, waiting proposals and requests, and which messages were already handled. It holds
 * key material, so it is only ever stored encrypted ([WalletConnectStateStorage]).
 */
@Serializable
internal data class WcStoredState(
    @SerialName("version") val version: Int = CURRENT_VERSION,
    @SerialName("clientSeed") val clientSeed: String? = null,
    @SerialName("pairings") val pairings: List<WcStoredPairing> = emptyList(),
    @SerialName("sessions") val sessions: List<WcStoredSession> = emptyList(),
    @SerialName("proposals") val proposals: List<WcStoredProposal> = emptyList(),
    @SerialName("requests") val requests: List<WcStoredRequest> = emptyList(),
    /** Hashes of handled messages and ids of handled requests, with when each may be forgotten (unix seconds). */
    @SerialName("handled") val handled: Map<String, Long> = emptyMap()
) {
    /** Whether anything but the identity is kept: only then does the client need the relay. */
    val hasTopics: Boolean get() = pairings.isNotEmpty() || sessions.isNotEmpty()

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
internal data class WcStoredPairing(
    @SerialName("topic") val topic: String,
    @SerialName("symKey") val symKey: String,
    @SerialName("relayProtocol") val relayProtocol: String,
    /** Unix seconds. */
    @SerialName("expiry") val expiry: Long,
    /** Whether a session was approved over it; an inactive pairing expires within minutes. */
    @SerialName("active") val active: Boolean,
    @SerialName("peer") val peer: WcStoredMetadata? = null
)

@Serializable
internal data class WcStoredSession(
    @SerialName("topic") val topic: String,
    @SerialName("symKey") val symKey: String,
    @SerialName("selfPublicKey") val selfPublicKey: String,
    @SerialName("peerPublicKey") val peerPublicKey: String,
    @SerialName("pairingTopic") val pairingTopic: String,
    @SerialName("relayProtocol") val relayProtocol: String,
    @SerialName("expiry") val expiry: Long,
    @SerialName("acknowledged") val acknowledged: Boolean,
    @SerialName("settleRequestId") val settleRequestId: Long? = null,
    @SerialName("namespaces") val namespaces: Map<String, WcStoredSessionNamespace>,
    @SerialName("requiredNamespaces") val requiredNamespaces: Map<String, WcStoredProposalNamespace> = emptyMap(),
    @SerialName("optionalNamespaces") val optionalNamespaces: Map<String, WcStoredProposalNamespace> = emptyMap(),
    @SerialName("peer") val peer: WcStoredMetadata,
    /** The Verify result of the proposal the session was approved from. */
    @SerialName("verify") val verify: WcStoredVerify? = null
)

@Serializable
internal data class WcStoredProposal(
    @SerialName("id") val id: Long,
    @SerialName("pairingTopic") val pairingTopic: String,
    @SerialName("proposerPublicKey") val proposerPublicKey: String,
    @SerialName("proposer") val proposer: WcStoredMetadata,
    @SerialName("requiredNamespaces") val requiredNamespaces: Map<String, WcStoredProposalNamespace>,
    @SerialName("optionalNamespaces") val optionalNamespaces: Map<String, WcStoredProposalNamespace>,
    @SerialName("relayProtocol") val relayProtocol: String,
    @SerialName("expiry") val expiry: Long,
    @SerialName("verify") val verify: WcStoredVerify
)

@Serializable
internal data class WcStoredRequest(
    @SerialName("topic") val topic: String,
    @SerialName("id") val id: Long,
    @SerialName("chainId") val chainId: String,
    @SerialName("method") val method: String,
    /** The request's params exactly as the dApp sent them, as JSON text. */
    @SerialName("params") val params: String,
    @SerialName("expiry") val expiry: Long,
    @SerialName("verify") val verify: WcStoredVerify
)

@Serializable
internal data class WcStoredMetadata(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("url") val url: String,
    @SerialName("icons") val icons: List<String>,
    @SerialName("redirect") val redirect: String? = null
) {
    fun toModel() = WalletConnectMetadata(name, description, url, icons, redirect)

    companion object {
        fun from(model: WalletConnectMetadata) =
            WcStoredMetadata(model.name, model.description, model.url, model.icons, model.redirect)
    }
}

@Serializable
internal data class WcStoredProposalNamespace(
    @SerialName("chains") val chains: List<String>? = null,
    @SerialName("methods") val methods: List<String>,
    @SerialName("events") val events: List<String>
) {
    fun toModel() = WalletConnectProposalNamespace(chains, methods, events)

    companion object {
        fun from(model: WalletConnectProposalNamespace) =
            WcStoredProposalNamespace(model.chains, model.methods, model.events)
    }
}

@Serializable
internal data class WcStoredSessionNamespace(
    @SerialName("chains") val chains: List<String>? = null,
    @SerialName("accounts") val accounts: List<String>,
    @SerialName("methods") val methods: List<String>,
    @SerialName("events") val events: List<String>
) {
    fun toModel() = WalletConnectSessionNamespace(chains, accounts, methods, events)

    companion object {
        fun from(model: WalletConnectSessionNamespace) =
            WcStoredSessionNamespace(model.chains, model.accounts, model.methods, model.events)
    }
}

@Serializable
internal data class WcStoredVerify(
    @SerialName("validation") val validation: String,
    @SerialName("verifiedOrigin") val verifiedOrigin: String? = null,
    @SerialName("claimedUrl") val claimedUrl: String? = null,
    @SerialName("isScam") val isScam: Boolean = false
) {
    fun toModel() = WalletConnectVerifyContext(
        validation = WalletConnectValidation.values().firstOrNull { it.name == validation } ?: WalletConnectValidation.UNKNOWN,
        verifiedOrigin = verifiedOrigin,
        claimedUrl = claimedUrl,
        isScam = isScam
    )

    companion object {
        fun from(model: WalletConnectVerifyContext) =
            WcStoredVerify(model.validation.name, model.verifiedOrigin, model.claimedUrl, model.isScam)
    }
}

/**
 * Where the client keeps [WcStoredState] as JSON. Implementations must encrypt it at rest; the
 * Android one uses the app's wallet encryption key in a preferences file of its own.
 */
interface WalletConnectStateStorage {
    /** The stored JSON, or null when nothing is stored. Throws when it exists but cannot be read now. */
    fun read(): String?

    /** Replaces the stored JSON durably; throws when it could not. */
    fun write(json: String)

    /** Removes the stored state. */
    fun clear()
}
