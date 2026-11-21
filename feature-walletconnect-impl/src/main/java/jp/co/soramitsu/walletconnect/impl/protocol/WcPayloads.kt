package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Reads and checks what dApps send. Every reader throws [WcProtocolException] with the code to answer. */
internal object WcPayloads {

    fun metadata(element: JsonElement?): WcStoredMetadata {
        val metadata = element as? JsonObject ?: invalid("The dApp sent no metadata")
        val url = metadata.string("url")?.trim().orEmpty()
        if (url.length > WcLimits.MAX_URL_LENGTH) invalid("The dApp URL is too long")
        val icons = (metadata["icons"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { icon -> icon.isString }?.content?.trim() }
            .filter { it.length <= WcLimits.MAX_URL_LENGTH && (it.startsWith("https://") || it.startsWith("http://")) }
            .take(WcLimits.MAX_ICONS)
        return WcStoredMetadata(
            name = WcLimits.displayText(metadata.string("name").orEmpty(), WcLimits.MAX_NAME_DISPLAY_LENGTH),
            description = WcLimits.displayText(metadata.string("description").orEmpty(), WcLimits.MAX_DESCRIPTION_LENGTH),
            url = url,
            icons = icons,
            redirect = (metadata["redirect"] as? JsonObject)?.string("native")?.takeIf { it.length <= WcLimits.MAX_URL_LENGTH }
        )
    }

    /**
     * Proposal namespaces, normalized: a namespace keyed by a CAIP-2 chain (`eip155:1`) becomes
     * namespace `eip155` with that chain, merged with any other entry of the namespace.
     */
    fun proposalNamespaces(element: JsonElement?): Map<String, WcStoredProposalNamespace> {
        if (element == null || element is JsonNull) return emptyMap()
        val namespaces = element as? JsonObject ?: invalid("Malformed namespaces")
        if (namespaces.size > WcLimits.MAX_NAMESPACES) refuse(WcErrors.LIMIT_EXCEEDED, "Too many namespaces")
        val merged = LinkedHashMap<String, WcStoredProposalNamespace>()
        for ((key, value) in namespaces) {
            val (namespaceKey, entry) = proposalNamespace(key, value)
            val previous = merged[namespaceKey]
            merged[namespaceKey] = WcStoredProposalNamespace(
                chains = (previous?.chains.orEmpty() + entry.chains.orEmpty()).distinct(),
                methods = (previous?.methods.orEmpty() + entry.methods).distinct(),
                events = (previous?.events.orEmpty() + entry.events).distinct()
            )
        }
        if (merged.values.any { it.chains.orEmpty().size > WcLimits.MAX_CHAINS_PER_NAMESPACE }) {
            refuse(WcErrors.LIMIT_EXCEEDED, "Too many chains")
        }
        return merged
    }

    // One entry of the proposal's namespaces and the namespace it belongs to.
    private fun proposalNamespace(key: String, value: JsonElement): Pair<String, WcStoredProposalNamespace> {
        val namespace = value as? JsonObject ?: invalid("Malformed namespace")
        val isChainKey = key.contains(':')
        val isValidKey = if (isChainKey) WcLimits.isCaip2(key) else WcLimits.isNamespaceKey(key)
        if (!isValidKey) refuse(WcErrors.UNSUPPORTED_NAMESPACE_KEY, "Unsupported namespace key.")
        val namespaceKey = key.substringBefore(':')
        val chains = if (isChainKey) {
            listOf(key)
        } else {
            namespace["chains"].stringList(WcLimits.MAX_CHAINS_PER_NAMESPACE) ?: refuse(WcErrors.UNSUPPORTED_CHAINS, "Unsupported chains.")
        }
        if (chains.any { !WcLimits.isCaip2(it) || it.substringBefore(':') != namespaceKey }) {
            refuse(WcErrors.UNSUPPORTED_CHAINS, "Unsupported chains.")
        }
        val methods = names(namespace["methods"], WcLimits.MAX_METHODS_PER_NAMESPACE, WcErrors.UNSUPPORTED_METHODS)
        val events = names(namespace["events"], WcLimits.MAX_EVENTS_PER_NAMESPACE, WcErrors.INVALID_EVENT)
        return namespaceKey to WcStoredProposalNamespace(chains = chains, methods = methods, events = events)
    }

    private fun names(
        element: JsonElement?,
        maxSize: Int,
        code: Long
    ): List<String> {
        val names = element.stringList(maxSize) ?: refuse(code, "Malformed or too many names")
        if (names.any { !WcLimits.isName(it) }) refuse(code, "Malformed name")
        return names.distinct()
    }

    /** Session namespaces as `wc_sessionSettle` and `wc_sessionUpdate` send them. */
    fun sessionNamespacesJson(namespaces: Map<String, WcStoredSessionNamespace>): JsonObject = buildJsonObject {
        namespaces.forEach { (key, namespace) ->
            put(
                key,
                buildJsonObject {
                    namespace.chains?.let { chains -> put("chains", strings(chains)) }
                    put("accounts", strings(namespace.accounts))
                    put("methods", strings(namespace.methods))
                    put("events", strings(namespace.events))
                }
            )
        }
    }

    private fun strings(values: List<String>) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    /**
     * Checks the namespaces the wallet is about to approve: every namespace, chain, method and event
     * was proposed, and every account is a CAIP-10 account on an approved chain.
     */
    fun checkApproval(proposal: WcStoredProposal, namespaces: Map<String, WcStoredSessionNamespace>) {
        if (namespaces.isEmpty()) refuse(WcErrors.UNSUPPORTED_ACCOUNTS, "No accounts approved")
        val proposed = proposal.requiredNamespaces.keys + proposal.optionalNamespaces.keys
        for ((key, namespace) in namespaces) {
            if (key !in proposed) refuse(WcErrors.UNSUPPORTED_NAMESPACE_KEY, "Unsupported namespace key.")
            val asked = listOfNotNull(proposal.requiredNamespaces[key], proposal.optionalNamespaces[key])
            val chains = namespace.chains.orEmpty()
            if (chains.any { chain -> asked.none { chain in it.chains.orEmpty() } }) {
                refuse(WcErrors.UNSUPPORTED_CHAINS, "Unsupported chains.")
            }
            if (namespace.methods.any { method -> asked.none { method in it.methods } }) {
                refuse(WcErrors.UNSUPPORTED_METHODS, "Unsupported methods.")
            }
            if (namespace.events.any { event -> asked.none { event in it.events } }) {
                refuse(WcErrors.INVALID_EVENT, "Unsupported events.")
            }
            val accountsValid = namespace.accounts.isNotEmpty() &&
                namespace.accounts.all { WcLimits.isCaip10(it) && it.substringBeforeLast(':') in chains }
            if (!accountsValid) refuse(WcErrors.UNSUPPORTED_ACCOUNTS, "Unsupported accounts.")
        }
    }

    /** The chains a session approved in the namespace of [chainId]: its chains and its accounts' chains. */
    fun approvedChains(namespace: WcStoredSessionNamespace): Set<String> =
        namespace.chains.orEmpty().toSet() + namespace.accounts.map { it.substringBeforeLast(':') }

    private fun invalid(message: String): Nothing = refuse(WcErrors.INVALID_REQUEST, message)
}
