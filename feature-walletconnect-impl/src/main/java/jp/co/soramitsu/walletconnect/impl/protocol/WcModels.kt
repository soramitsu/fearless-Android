package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectRpcRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Maps the stored state to the app's models, and the wallet's metadata to JSON. */
internal object WcModels {

    fun proposal(stored: WcStoredProposal) = WalletConnectProposal(
        id = stored.id,
        pairingTopic = stored.pairingTopic,
        proposer = stored.proposer.toModel(),
        proposerPublicKey = stored.proposerPublicKey,
        requiredNamespaces = stored.requiredNamespaces.mapValues { it.value.toModel() },
        optionalNamespaces = stored.optionalNamespaces.mapValues { it.value.toModel() },
        relayProtocol = stored.relayProtocol,
        expiryTimestamp = stored.expiry
    )

    fun session(stored: WcStoredSession) = WalletConnectSession(
        topic = stored.topic,
        pairingTopic = stored.pairingTopic,
        expiry = stored.expiry,
        namespaces = stored.namespaces.mapValues { it.value.toModel() },
        requiredNamespaces = stored.requiredNamespaces.mapValues { it.value.toModel() },
        optionalNamespaces = stored.optionalNamespaces.mapValues { it.value.toModel() },
        metaData = stored.peer.toModel(),
        acknowledged = stored.acknowledged,
        verifyContext = stored.verify?.toModel()
    )

    fun request(stored: WcStoredRequest, session: WcStoredSession?) = WalletConnectRequest(
        topic = stored.topic,
        chainId = stored.chainId,
        peerMetaData = session?.peer?.toModel(),
        request = WalletConnectRpcRequest(stored.id, stored.method, stored.params),
        expiryTimestamp = stored.expiry
    )

    fun metadataJson(metadata: WcStoredMetadata) = buildJsonObject {
        put("name", metadata.name)
        put("description", metadata.description)
        put("url", metadata.url)
        put("icons", buildJsonArray { metadata.icons.forEach { add(JsonPrimitive(it)) } })
        metadata.redirect?.let { native -> put("redirect", buildJsonObject { put("native", native) }) }
    }
}
