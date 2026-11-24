package jp.co.soramitsu.liquiditypools.impl.data

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.core.utils.soraAssetIdCode
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.CollectionEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.NumberType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliases
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * The SORA mainnet runtime snapshot (spec 133) used by the wallet parity tests. Storage keys and values in the
 * repository tests are built with this runtime, so key layout and value decoding run against real metadata.
 */
internal object SoraRuntime {
    const val SORA_PREFIX: Short = 69

    val runtime: RuntimeSnapshot by lazy {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle").isFile }
        val snapshot = File(root, "feature-wallet-impl/src/test/resources/prod-parity/runtime/sora.json.gz")
            .inputStream().let(::GZIPInputStream)
            .use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }
        RuntimeFactory(JsonFactory.create()).constructRuntime(
            metadataRaw = snapshot.getValue("metadata").jsonPrimitive.content,
            ownTypesRaw = snapshot.getValue("types").jsonPrimitive.content,
            runtimeVersion = snapshot.getValue("specVersion").jsonPrimitive.int
        ).runtime
    }

    fun storage(module: String, name: String): StorageEntry = runtime.metadata.module(module).storage(name)

    fun key(
        module: String,
        name: String,
        vararg keys: Any?
    ): String = storage(module, name).storageKey(runtime, *keys)

    fun prefix(module: String, name: String): String = storage(module, name).storageKey()

    fun valueType(module: String, name: String): Type<*> = requireNotNull(storage(module, name).type.value)

    fun encode(type: Type<*>, value: Any?): String = type.bytes(runtime, value).toHexString(withPrefix = true)

    fun encodeValue(
        module: String,
        name: String,
        value: Any?
    ): String = encode(valueType(module, name), value)

    fun code(assetId: String) = Struct.Instance(mapOf("code" to soraAssetIdCode(assetId.fromHex())))

    fun address(accountId: ByteArray): String = accountId.toAddress(SORA_PREFIX)

    /** The element type of a Vec storage value, for example PoolData in DemeterFarmingPlatform.Pools. */
    fun vecElement(module: String, name: String): Type<*> =
        requireNotNull((valueType(module, name).skipAliases() as Vec).typeReference.value)

    /** A struct of [type] with zero values except [overrides]; an unknown override name fails with the real field list. */
    fun struct(type: Type<*>, overrides: Map<String, Any?>): Struct.Instance {
        val struct = type.skipAliases() as Struct
        val unknown = overrides.keys - struct.mapping.keys
        require(unknown.isEmpty()) { "Unknown fields $unknown; ${struct.name} has ${struct.mapping.keys}" }
        return Struct.Instance(
            struct.mapping.mapValues { (name, reference) ->
                if (name in overrides) overrides[name] else defaultValue(reference.value)
            }
        )
    }

    private fun defaultValue(type: Type<*>?): Any? = when (val resolved = type?.skipAliases()) {
        is Struct -> struct(resolved, emptyMap())
        is Tuple -> resolved.typeReferences.map { defaultValue(it.value) }
        is FixedByteArray -> ByteArray(resolved.length)
        is FixedArray -> List(resolved.length) { defaultValue(resolved.typeReference.value) }
        is NumberType -> BigInteger.ZERO
        is BooleanType -> false
        is Vec -> emptyList<Any?>()
        is Option -> null
        is CollectionEnum -> resolved.elements.first()
        is DictEnum -> resolved.elements.values.first().let { DictEnum.Entry(it.name, defaultValue(it.value.value)) }
        else -> error("No default for ${resolved?.name} (${resolved?.javaClass?.simpleName})")
    }

    fun u128(value: BigInteger): String = littleEndian(value, 16)

    fun littleEndian(value: BigInteger, size: Int): String {
        val bigEndian = value.toByteArray().dropWhile { it == 0.toByte() }
        return (bigEndian.reversed() + List(size - bigEndian.size) { 0.toByte() }).joinToString("") { "%02x".format(it) }
    }
}

/** A SORA node double: storage values and key listings answered from maps, every request recorded. */
internal class FakeSoraNode(assets: List<CoreAsset>) {
    val storage = mutableMapOf<String, String?>()
    val keys = mutableMapOf<String, List<String>>()
    val requests = mutableListOf<RuntimeRequest>()
    var pathAvailable = true

    val socket: SocketService = mockk {
        every { jsonMapper } returns Gson()
        every { executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>()
            requests += request
            val result: Any? = when (request.method) {
                "state_getStorage" -> storage[request.params.single() as String]
                "state_getKeys" -> keys[request.params.singleOrNull()?.let { it as String }].orEmpty()
                "liquidityProxy_isPathAvailable" -> pathAvailable
                else -> error("Unexpected request ${request.method}")
            }
            thirdArg<SocketService.ResponseListener<RpcResponse>>().onNext(RpcResponse("2.0", result, request.id, null))
            mockk(relaxed = true)
        }
    }

    val chain: Chain = mockk(relaxed = true) {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { addressPrefix } returns SoraRuntime.SORA_PREFIX.toInt()
        every { isEthereumBased } returns false
        every { externalApi } returns null
        every { this@mockk.assets } returns assets
    }

    val chainRegistry: ChainRegistry = mockk {
        val connection = mockk<ChainConnection> { every { socketService } returns socket }
        val provider = mockk<RuntimeProvider> { coEvery { get() } returns SoraRuntime.runtime }
        coEvery { getRuntimeOrNull(soraMainChainId) } returns SoraRuntime.runtime
        coEvery { awaitConnection(soraMainChainId) } returns connection
        coEvery { awaitRuntimeProvider(soraMainChainId) } returns provider
        coEvery { getChain(soraMainChainId) } returns chain
    }

    fun requestsOf(method: String) = requests.filter { it.method == method }
}
