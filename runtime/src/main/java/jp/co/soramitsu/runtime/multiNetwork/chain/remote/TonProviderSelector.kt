package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.network.ton.TonNetwork
import jp.co.soramitsu.common.data.network.ton.TonProviderUnavailableException
import jp.co.soramitsu.common.data.network.ton.problemFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A TON endpoint that has proven which network it serves. */
sealed interface TonProvider {
    val baseUrl: String
    val network: TonNetwork

    /** TI, proven by its service-info. It accepts transactions only when its service-info says it is not read-only. */
    data class Indexer(
        override val baseUrl: String,
        override val network: TonNetwork,
        val acceptsTransactions: Boolean
    ) : TonProvider

    /** The registry's TON node, a TonAPI server, proven by the global id of the masterchain head it serves. */
    data class RegistryNode(
        override val baseUrl: String,
        override val network: TonNetwork
    ) : TonProvider
}

/** Where TON data for one chain may come from. */
data class TonEndpoints(
    val network: TonNetwork,
    /** TI's base URL, or null when no indexer is configured for this network. */
    val indexerBaseUrl: String?,
    val registryNodeBaseUrl: String
)

/**
 * Picks the TON endpoint a request goes to. TI answers only after its service-info has proven that it serves the
 * requested network. Otherwise the registry's TON node answers, once its masterchain head has proven the same; that
 * fallback is logged and can be switched off ([registryNodeFallback]). With neither, requests fail with
 * [TonProviderUnavailableException], so callers show an error and never data from an unproven endpoint.
 *
 * Results are kept for [VERIFIED_TTL_MS] (so the wallet moves to TI within minutes of TI proving mainnet), failed
 * checks for [FAILED_TTL_MS], and a TI that failed or answered for another network is passed over for
 * [INDEXER_COOLDOWN_MS].
 */
class TonProviderSelector(
    private val tonApi: TonApi,
    private val registryNodeFallback: Boolean,
    private val now: () -> Long,
    private val log: (String) -> Unit
) {
    private class Check<T : TonProvider>(val provider: T?, val problem: String?, val expiresAt: Long)

    private data class Key(val baseUrl: String, val network: TonNetwork)

    private val mutex = Mutex()
    private val indexerChecks = mutableMapOf<Key, Check<TonProvider.Indexer>>()
    private val registryNodeChecks = mutableMapOf<Key, Check<TonProvider.RegistryNode>>()
    private val indexerCooldowns = mutableMapOf<Key, Pair<Long, String>>()
    private val fallbackLogs = mutableMapOf<Key, Pair<Long, String>>()

    /** The verified endpoint for reads right now. */
    suspend fun provider(endpoints: TonEndpoints): TonProvider = mutex.withLock {
        val indexer = indexerCheck(endpoints)
        indexer.provider ?: verifiedRegistryNode(endpoints, indexer.problem.orEmpty())
    }

    /** The verified registry node, used when TI is not usable for [reason]. */
    suspend fun registryNode(endpoints: TonEndpoints, reason: String): TonProvider.RegistryNode = mutex.withLock {
        verifiedRegistryNode(endpoints, reason)
    }

    /** TI proved the network but then failed a request or answered for another network: pass it over for a while. */
    suspend fun passOverIndexer(endpoints: TonEndpoints, error: Throwable) {
        val baseUrl = endpoints.indexerBaseUrl ?: return
        val key = Key(baseUrl, endpoints.network)
        mutex.withLock {
            indexerCooldowns[key] = now() + INDEXER_COOLDOWN_MS to error.describe()
            indexerChecks.remove(key)
        }
    }

    private suspend fun indexerCheck(endpoints: TonEndpoints): Check<TonProvider.Indexer> {
        val baseUrl = endpoints.indexerBaseUrl
            ?: return Check(provider = null, problem = "no TON indexer is configured", expiresAt = 0L)
        val key = Key(baseUrl, endpoints.network)
        val time = now()

        indexerCooldowns[key]?.let { (until, reason) ->
            if (until > time) return Check(provider = null, problem = "TI failed a request: $reason", expiresAt = 0L)
            indexerCooldowns.remove(key)
        }

        return indexerChecks[key]?.takeIf { it.expiresAt > time }
            ?: checkIndexer(key).also { indexerChecks[key] = it }
    }

    private suspend fun checkIndexer(key: Key): Check<TonProvider.Indexer> {
        val problem = try {
            val info = tonApi.getIndexerServiceInfo("${key.baseUrl}/api/indexer/v1/service-info")
            info.problemFor(key.network, key.baseUrl)
                ?: return Check(
                    provider = TonProvider.Indexer(key.baseUrl, key.network, acceptsTransactions = info.readOnly == false),
                    problem = null,
                    expiresAt = now() + VERIFIED_TTL_MS
                ).also { log("TON ${key.network.id}: using ${key.baseUrl} (service-info proves ${key.network.chainId})") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            "service-info: ${error.describe()}"
        }

        return Check(provider = null, problem = "TI is not verified: $problem", expiresAt = now() + FAILED_TTL_MS)
    }

    private suspend fun verifiedRegistryNode(endpoints: TonEndpoints, reason: String): TonProvider.RegistryNode {
        if (!registryNodeFallback) {
            throw TonProviderUnavailableException(
                "TON ${endpoints.network.id}: $reason; the registry node fallback is off (TON_REGISTRY_NODE_FALLBACK)"
            )
        }

        val key = Key(endpoints.registryNodeBaseUrl, endpoints.network)
        val time = now()
        val check = registryNodeChecks[key]?.takeIf { it.expiresAt > time }
            ?: checkRegistryNode(key).also { registryNodeChecks[key] = it }

        val provider = check.provider ?: throw TonProviderUnavailableException(
            "TON ${endpoints.network.id}: $reason; registry node ${key.baseUrl}: ${check.problem}"
        )

        logFallback(key, reason)
        return provider
    }

    private suspend fun checkRegistryNode(key: Key): Check<TonProvider.RegistryNode> {
        val problem = try {
            val globalId = tonApi.getMasterchainHead("${key.baseUrl}/v2/blockchain/masterchain-head").globalId
            if (globalId == key.network.globalId) {
                return Check(
                    provider = TonProvider.RegistryNode(key.baseUrl, key.network),
                    problem = null,
                    expiresAt = now() + VERIFIED_TTL_MS
                )
            }
            "it serves global id $globalId, not ${key.network.globalId}"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            "masterchain head: ${error.describe()}"
        }

        return Check(provider = null, problem = problem, expiresAt = now() + FAILED_TTL_MS)
    }

    // Explicit and visible: TON is not on TI for this network, and why. Repeated at most every VERIFIED_TTL_MS
    // per node and reason so a balance refresh does not flood the log.
    private fun logFallback(key: Key, reason: String) {
        val time = now()
        val last = fallbackLogs[key]
        if (last != null && last.second == reason && time - last.first < VERIFIED_TTL_MS) return

        fallbackLogs[key] = time to reason
        log(
            "TON ${key.network.id}: using the registry node ${key.baseUrl} instead of TI ($reason). " +
                "Switch off with FL_ANDROID_TON_REGISTRY_NODE_FALLBACK=false once TI serves ${key.network.chainId}."
        )
    }

    private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

    companion object {
        const val VERIFIED_TTL_MS = 5 * 60_000L
        const val FAILED_TTL_MS = 30_000L
        const val INDEXER_COOLDOWN_MS = 2 * 60_000L
    }
}
