package jp.co.soramitsu.common.data.network.config

import android.os.Build
import android.os.SystemClock
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.resources.ContextManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MutationAuthorizationStore internal constructor(private val state: MutationAuthorizationState) {
    @Inject
    constructor(preferences: Preferences, contextManager: ContextManager) : this(
        MutationAuthorizationState(
            context = kotlin.runCatching {
                val context = contextManager.getContext()
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val version = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                loadMutationAuthorizationContext(context.packageName, version) { name ->
                    context.assets.open(name).use { it.readBytes() }
                }
            }.getOrNull(),
            persistence = object : MutationAuthorizationPersistence {
                override fun read(): String? = preferences.getString(HIGH_WATER_KEY)
                override fun write(value: String): Boolean = preferences.replaceStringsDurably(
                    mapOf(HIGH_WATER_KEY to value), emptySet()
                )
            },
            clock = object : MutationAuthorizationClock {
                override fun wallSeconds() = System.currentTimeMillis() / 1000
                override fun elapsedMillis() = SystemClock.elapsedRealtime()
            }
        )
    )

    fun acceptFresh(token: String?): Boolean = state.acceptFresh(token)
    fun invalidate() = state.invalidate()
    fun permits(capability: MutationCapability): Boolean = state.permits(capability)
    fun acquire(capability: MutationCapability, intentSha256: String) = state.acquire(capability, intentSha256)

    private companion object {
        const val HIGH_WATER_KEY = "mutation.authorization.high-water.v1"
    }
}

/** All inputs are immutable APK assets; network config cannot replace trust, policy, or route data. */
internal fun loadMutationAuthorizationContext(
    actualPackage: String,
    actualVersion: Long,
    readAsset: (String) -> ByteArray
): MutationAuthorizationContext? = runCatching {
    val trustBytes = readAsset("mutation_authorization_trust.json")
    require(trustBytes.size <= 8192)
    val trust = JsonParser.parseString(trustBytes.toString(Charsets.UTF_8)).asJsonObject
    require(trust.keySet() == setOf("schema", "policySha256", "routeManifestSha256", "keys"))
    require(trust.get("schema").asString == "1")
    val keys = trust.getAsJsonObject("keys").entrySet().associate { (id, value) ->
        require(id.matches(Regex("[a-z0-9-]{1,64}")))
        val hex = value.asString
        require(hex.matches(Regex("[0-9a-f]{64}")))
        id to hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    require(keys.isNotEmpty()) { "Production mutation trust is not configured" }
    val policyBytes = readAsset("mutation_authorization_policy.json")
    val routeBytes = readAsset("mutation_route_manifest.json")
    require(policyBytes.size <= 8192 && routeBytes.size <= 8192)
    val policyHash = mutationSha256(policyBytes)
    val routeHash = mutationSha256(routeBytes)
    require(trust.get("policySha256").asString == policyHash && trust.get("routeManifestSha256").asString == routeHash)
    val policy = JsonParser.parseString(policyBytes.toString(Charsets.UTF_8)).asJsonObject
    require(policy.keySet() == setOf("schema", "audience", "environment", "appVersion", "capabilities"))
    require(policy.get("schema").asString == "1" && policy.get("environment").asString == "production")
    require(policy.get("audience").asString == actualPackage)
    require(canonicalDecimal(policy.get("appVersion").asString) == actualVersion && actualVersion > 0)
    val routes = JsonParser.parseString(routeBytes.toString(Charsets.UTF_8)).asJsonObject
    require(routes.keySet() == setOf("schema", "files") && routes.get("schema").asString == "1")
    val files = routes.getAsJsonObject("files")
    require(files.keySet() == setOf("approved_xcm_routes.tsv", "local_chains.json"))
    files.entrySet().forEach { (name, expected) ->
        val actual = readAsset(name)
        require(actual.size <= 20 * 1024 * 1024 && mutationSha256(actual) == expected.asString)
    }
    val capabilities = policy.getAsJsonObject("capabilities")
    require(capabilities.keySet() == MutationCapability.entries.map { it.wireName }.toSet())
    val approved = MutationCapability.entries.filter { capability ->
        val value = capabilities.get(capability.wireName)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        value.asBoolean
    }.toSet()
    MutationAuthorizationContext(actualPackage, actualVersion, policyHash, routeHash, keys, approved)
}.getOrNull()
