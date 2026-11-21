package jp.co.soramitsu.liquiditypools.impl.data

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.core.utils.soraAssetIdCode
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.coredb.model.BasicPoolLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.NumberType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliases
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.USDX_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.usdx
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.xor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * Bug 31: getBasicPools and updateBasicPools converted the target reserve with the base asset's precision
 * (updateAccountPools already used the target's), so a pool whose target does not have 18 decimals showed
 * and cached a target reserve off by 10^(18 - decimals). Storage keys and values are built with the SORA
 * mainnet runtime snapshot.
 */
class PoolsRepositoryReservePrecisionTest {

    private val storage = mutableMapOf<String, String>()
    private val keys = mutableMapOf<String, List<String>>()

    private val socket: SocketService = mockk {
        every { jsonMapper } returns Gson()
        every { executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>()
            val result: Any? = when (request.method) {
                "state_getStorage" -> storage[request.params.single() as String]
                "state_getKeys" -> keys[request.params.single() as String].orEmpty()
                else -> error("Unexpected request ${request.method}")
            }
            thirdArg<SocketService.ResponseListener<RpcResponse>>()
                .onNext(RpcResponse("2.0", result, request.id, null))
            mockk(relaxed = true)
        }
    }
    private val chain: Chain = mockk(relaxed = true) {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { addressPrefix } returns SORA_PREFIX
        every { isEthereumBased } returns false
        every { assets } returns listOf(xor, valToken, usdx)
    }
    private val chainRegistry: ChainRegistry = mockk {
        val connection = mockk<ChainConnection> { every { socketService } returns socket }
        val provider = mockk<RuntimeProvider> { coEvery { get() } returns runtime }
        coEvery { getRuntimeOrNull(soraMainChainId) } returns runtime
        coEvery { awaitConnection(soraMainChainId) } returns connection
        coEvery { awaitRuntimeProvider(soraMainChainId) } returns provider
        coEvery { getChain(soraMainChainId) } returns chain
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns mockk<MetaAccount>(relaxed = true) {
            every { substrateAccountId } returns ByteArray(32) { 7 }
        }
    }
    private val poolDao = mockk<PoolDao>(relaxed = true)

    private val repository = PoolsRepositoryImpl(
        extrinsicService = mockk(),
        chainRegistry = chainRegistry,
        accountRepository = accountRepository,
        poolDao = poolDao,
        db = mockk(),
        featureToggleStore = mockk(),
        mutationAuthorizer = mockk()
    )

    @Test
    fun shouldConvertEachReserveWithItsOwnAssetPrecision_whenReadingBasicPools() = runBlocking {
        poolsOfXor()

        val pools = repository.getBasicPools(soraMainChainId)

        val xorUsdx = pools.single { it.targetToken == usdx }
        assertAmount("1000", xorUsdx.baseReserves)
        assertAmount("2000", xorUsdx.targetReserves)
        assertAmount("300", xorUsdx.totalIssuance)
        val xorVal = pools.single { it.targetToken == valToken }
        assertAmount("1000", xorVal.baseReserves)
        assertAmount("4000", xorVal.targetReserves)
    }

    @Test
    fun shouldCacheEachReserveWithItsOwnAssetPrecision_whenRefreshingBasicPools() = runBlocking {
        poolsOfXor()
        dexInfos()
        coEvery { poolDao.getBasicPools() } returns emptyList()
        val inserted = slot<List<BasicPoolLocal>>()
        coEvery { poolDao.insertBasicPools(capture(inserted)) } returns Unit

        repository.updateBasicPools(soraMainChainId)

        val xorUsdx = inserted.captured.single { it.tokenIdTarget == USDX_ID }
        assertAmount("1000", xorUsdx.reserveBase)
        assertAmount("2000", xorUsdx.reserveTarget)
        assertAmount("300", xorUsdx.totalIssuance)
        val xorVal = inserted.captured.single { it.tokenIdTarget == VAL_ID }
        assertAmount("1000", xorVal.reserveBase)
        assertAmount("4000", xorVal.reserveTarget)
    }

    // XOR-USDX holds 1000 XOR and 2000 USDX (6 decimals); XOR-VAL holds 1000 XOR and 4000 VAL.
    private fun poolsOfXor() {
        keys[key("PoolXYK", "Reserves", code(XOR_ID))] = listOf(
            key("PoolXYK", "Reserves", code(XOR_ID), code(USDX_ID)),
            key("PoolXYK", "Reserves", code(XOR_ID), code(VAL_ID))
        )
        pool(USDX_ID, targetPlanks = BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(6)), reserveAccount = 9)
        pool(VAL_ID, targetPlanks = BigInteger.valueOf(4000).multiply(BigInteger.TEN.pow(18)), reserveAccount = 10)
    }

    private fun pool(
        target: String,
        targetPlanks: BigInteger,
        reserveAccount: Byte
    ) {
        val account = ByteArray(32) { reserveAccount }
        storage[key("PoolXYK", "Reserves", code(XOR_ID), code(target))] =
            encode(valueType("PoolXYK", "Reserves"), listOf(BASE_PLANKS, targetPlanks))
        storage[key("PoolXYK", "Properties", code(XOR_ID), code(target))] =
            account.toHexString(true) + ByteArray(32) { 11 }.toHexString()
        storage[key("PoolXYK", "TotalIssuances", account)] =
            encode(valueType("PoolXYK", "TotalIssuances"), ISSUANCE_PLANKS)
    }

    // The DEX registry lists XOR as the base asset of DEX 0.
    private fun dexInfos() {
        val infoType = valueType("DEXManager", "DEXInfos")
        val dex0 = key("DEXManager", "DEXInfos", BigInteger.ZERO)
        keys[runtime.metadata.module("DEXManager").storage("DEXInfos").storageKey()] = listOf(dex0)
        storage[dex0] = encode(infoType, struct(infoType, mapOf("baseAssetId" to code(XOR_ID))))
    }

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertTrue("expected $expected but was ${actual.toPlainString()}", BigDecimal(expected).compareTo(actual) == 0)
    }

    private companion object {
        const val SORA_PREFIX = 69
        val BASE_PLANKS: BigInteger = BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18))
        val ISSUANCE_PLANKS: BigInteger = BigInteger.valueOf(300).multiply(BigInteger.TEN.pow(18))

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

        fun entry(module: String, name: String): StorageEntry = runtime.metadata.module(module).storage(name)

        fun key(
            module: String,
            name: String,
            vararg arguments: Any?
        ): String = entry(module, name).storageKey(runtime, *arguments)

        fun valueType(module: String, name: String): Type<*> = requireNotNull(entry(module, name).type.value)

        fun encode(type: Type<*>, value: Any?): String = type.bytes(runtime, value).toHexString(withPrefix = true)

        fun code(assetId: String) = Struct.Instance(mapOf("code" to soraAssetIdCode(assetId.fromHex())))

        // A struct of [type] with zero values except [overrides].
        fun struct(type: Type<*>, overrides: Map<String, Any?>): Struct.Instance {
            val struct = type.skipAliases() as Struct
            return Struct.Instance(
                struct.mapping.mapValues { (name, reference) ->
                    if (name in overrides) overrides[name] else defaultValue(reference.value)
                }
            )
        }

        private fun defaultValue(type: Type<*>?): Any? = when (val resolved = type?.skipAliases()) {
            is Struct -> struct(resolved, emptyMap())
            is FixedByteArray -> ByteArray(resolved.length)
            is FixedArray -> List(resolved.length) { defaultValue(resolved.typeReference.value) }
            is NumberType -> BigInteger.ZERO
            is BooleanType -> false
            is Vec -> emptyList<Any?>()
            is Option -> null
            else -> error("No default for ${resolved?.name} (${resolved?.javaClass?.simpleName})")
        }
    }
}
