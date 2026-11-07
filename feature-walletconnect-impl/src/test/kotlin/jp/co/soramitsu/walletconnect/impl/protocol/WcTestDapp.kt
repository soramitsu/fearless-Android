package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/** A dApp on the far side of [WcTestRelay], speaking the protocol as the official SDK does. */
internal class WcTestDapp(
    private val relay: WcTestRelay,
    private val clock: WcClock,
    /** Attests each message the dApp sends, as Verify does for a dApp running in a browser. */
    var attest: ((message: String) -> String)? = null
) {
    val pairingSymKey: ByteArray = WcCrypto.randomBytes(WcCrypto.KEY_LENGTH)
    val pairingTopic: String = WcCrypto.topicOf(pairingSymKey)
    val keys: WcKeyPair = WcCrypto.generateKeyPair()
    private val ids = AtomicLong(clock.nowMillis() * 1000)

    var sessionSymKey: ByteArray? = null
        private set
    var sessionTopic: String? = null
        private set

    fun nextId(): Long = ids.incrementAndGet()

    fun uri(expiry: Long = clock.nowSeconds() + 300) =
        "wc:$pairingTopic@2?relay-protocol=irn&symKey=${WcEncoding.hex(pairingSymKey)}&expiryTimestamp=$expiry"

    /** Encrypts [rpc] for [topic] and hands it to the relay; returns the encoded message. */
    fun send(
        topic: String,
        symKey: ByteArray,
        rpc: WcJsonRpc,
        attestation: String? = null
    ): String {
        val message = WcCrypto.encodeType0(symKey, WcJsonRpcCodec.encode(rpc))
        relay.push(topic, message, attestation ?: attest?.invoke(message))
        return message
    }

    fun propose(
        id: Long = nextId(),
        optionalNamespaces: JsonObject = evmNamespaces(),
        requiredNamespaces: JsonObject = JsonObject(emptyMap()),
        url: String = "https://app.example.org",
        expiry: Long = clock.nowSeconds() + 300
    ): Long {
        val params = buildJsonObject {
            put("requiredNamespaces", requiredNamespaces)
            put("optionalNamespaces", optionalNamespaces)
            put("relays", buildJsonArray { add(buildJsonObject { put("protocol", "irn") }) })
            put(
                "proposer",
                buildJsonObject {
                    put("publicKey", WcEncoding.hex(keys.publicKey))
                    put(
                        "metadata",
                        buildJsonObject {
                            put("name", "Test dApp")
                            put("description", "A dApp in a test")
                            put("url", url)
                            put("icons", buildJsonArray { add(JsonPrimitive("https://app.example.org/icon.png")) })
                        }
                    )
                }
            )
            put("expiryTimestamp", expiry)
            put("pairingTopic", pairingTopic)
        }
        send(pairingTopic, pairingSymKey, WcJsonRpc.Request(id, WcMethods.SESSION_PROPOSE, params))
        return id
    }

    /** The wallet's latest message on [topic], decrypted. */
    fun latest(topic: String, symKey: ByteArray): WcJsonRpc {
        val message = relay.publishedOn(topic).last().message
        return requireNotNull(WcJsonRpcCodec.decode(WcCrypto.decodeType0(symKey, WcCrypto.parseEnvelope(message))))
    }

    fun all(topic: String, symKey: ByteArray): List<WcJsonRpc> = relay.publishedOn(topic).map {
        requireNotNull(WcJsonRpcCodec.decode(WcCrypto.decodeType0(symKey, WcCrypto.parseEnvelope(it.message))))
    }

    /** Reads the wallet's approval on the pairing topic and derives the session key, as a dApp does. */
    fun readApproval(): WcJsonRpc.Result {
        val approval = latest(pairingTopic, pairingSymKey) as WcJsonRpc.Result
        val responder = WcEncoding.unhex((approval.result as JsonObject).str("responderPublicKey"))
        val symKey = WcCrypto.deriveSymKey(keys.privateKey, responder)
        sessionSymKey = symKey
        sessionTopic = WcCrypto.topicOf(symKey)
        return approval
    }

    fun settleRequest(): WcJsonRpc.Request = all(requireNotNull(sessionTopic), requireNotNull(sessionSymKey))
        .filterIsInstance<WcJsonRpc.Request>()
        .first { it.method == WcMethods.SESSION_SETTLE }

    fun acknowledgeSettle(accepted: Boolean = true) {
        val settle = settleRequest()
        val answer = if (accepted) WcJsonRpc.Result(settle.id, JsonPrimitive(true)) else WcJsonRpc.Error(settle.id, 7000, "Session settlement failed.")
        send(requireNotNull(sessionTopic), requireNotNull(sessionSymKey), answer)
    }

    fun request(
        method: String,
        params: JsonElement,
        chainId: String = "eip155:1",
        id: Long = nextId(),
        expiryTimestamp: Long? = null,
        attestation: String? = null
    ): Long {
        val rpc = WcJsonRpc.Request(
            id, WcMethods.SESSION_REQUEST,
            buildJsonObject {
                put(
                    "request",
                    buildJsonObject {
                        put("method", method)
                        put("params", params)
                        expiryTimestamp?.let { put("expiryTimestamp", it) }
                    }
                )
                put("chainId", chainId)
            }
        )
        send(requireNotNull(sessionTopic), requireNotNull(sessionSymKey), rpc, attestation)
        return id
    }

    fun onSession(rpc: WcJsonRpc) = send(requireNotNull(sessionTopic), requireNotNull(sessionSymKey), rpc)

    fun sessionMessages(): List<WcJsonRpc> = all(requireNotNull(sessionTopic), requireNotNull(sessionSymKey))

    companion object {
        fun evmNamespaces(
            chains: List<String> = listOf("eip155:1"),
            methods: List<String> = listOf("personal_sign", "eth_sendTransaction", "wallet_switchEthereumChain"),
            events: List<String> = listOf("chainChanged", "accountsChanged")
        ) = buildJsonObject {
            put(
                "eip155",
                buildJsonObject {
                    put("chains", JsonArray(chains.map(::JsonPrimitive)))
                    put("methods", JsonArray(methods.map(::JsonPrimitive)))
                    put("events", JsonArray(events.map(::JsonPrimitive)))
                }
            )
        }

        fun paramsOf(json: String): JsonElement = WcJsonRpcCodec.json.parseToJsonElement(json)

        fun objectOf(json: String): JsonObject = WcJsonRpcCodec.json.parseToJsonElement(json).jsonObject
    }
}

/** A clock the test moves. */
internal class WcTestClock(@Volatile var millis: Long = 1_700_000_000_000) : WcClock {
    override fun nowMillis(): Long = millis

    fun advanceSeconds(seconds: Long) {
        millis += seconds * WcClock.MILLIS_PER_SECOND
    }
}

/** [WalletConnectStateStorage] in memory, for the engine's tests. */
internal class WcMemoryStorage(var json: String? = null) : WalletConnectStateStorage {
    var writes = 0
    var unreadable = false

    override fun read(): String? {
        check(!unreadable) { "key store locked" }
        return json
    }

    override fun write(json: String) {
        writes += 1
        this.json = json
    }

    override fun clear() {
        json = null
    }
}
