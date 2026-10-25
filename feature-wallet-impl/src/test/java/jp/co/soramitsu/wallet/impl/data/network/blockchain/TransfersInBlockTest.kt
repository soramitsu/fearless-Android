package jp.co.soramitsu.wallet.impl.data.network.blockchain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.callOrNull
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * AL-10: SubstrateBalanceLoader reconciles a balance change by reading the block and its events. The RC's
 * getBlock always threw, so this path failed on every chain. The block here carries transfers Play 4.2.0 built
 * on Polkadot Asset Hub (prod-parity/13-asset-currency-chains.json), decoded with the recorded runtime 2005000.
 */
class TransfersInBlockTest {

    private val vectors = Json.parseToJsonElement(resource("/prod-parity/13-asset-currency-chains.json")).jsonObject
    private val assetHub = vectors.getValue("chains").jsonArray.map { it.jsonObject }.single { it.string("chain") == "asset-hub-polkadot" }
    private val runtime = runtime(assetHub)
    private val chain = registry.single { it.id == assetHub.string("genesis")!!.removePrefix("0x") }
    private val sender = SubstrateKeypairFactory.generate(EncryptionType.ED25519, vectors.string("signerSeed")!!.unhex()).publicKey
    private val recipient = vectors.string("recipient")!!.unhex()

    // Balances.transfer_allow_death and Assets.transfer of 1 planck, signed by the oracle key.
    private val dotTransfer = assetHub.extrinsic("dot")
    private val usdtTransfer = assetHub.extrinsic("usdt")

    @Test
    fun `transfers of the block are read with chain_getBlock and its events`() = runBlocking {
        val socket = FakeSocket(block(usdtTransfer, dotTransfer))
        val storage = FakeEvents(runtime, events(extrinsicSuccess(0), extrinsicFailed(1)))

        val transfers = source(socket, storage).fetchAccountTransfersInBlock(chain.id, BLOCK_HASH, recipient).getOrThrow()

        assertEquals("chain_getBlock", socket.requests.single().method)
        assertEquals(listOf(BLOCK_HASH), socket.requests.single().params)
        assertEquals(listOf(BLOCK_HASH), storage.blocks)

        // The Assets transfer is not a native transfer. The DOT transfer is Balances.transfer_allow_death: this
        // runtime has no Balances.transfer, and looking for it threw, which skipped every extrinsic.
        val transfer = transfers.single().extrinsic
        assertArrayEquals(sender, transfer.senderId)
        assertArrayEquals(recipient, transfer.recipientId)
        assertEquals(BigInteger.ONE, transfer.amountInPlanks)
        assertEquals(runtime.metadata.balances().call("transfer_allow_death").index, transfer.index)
        assertNull(runtime.metadata.balances().callOrNull("transfer"))
    }

    @Test
    fun `transfers of other accounts are left out`() = runBlocking {
        val transfers = source(FakeSocket(block(dotTransfer)), FakeEvents(runtime, events(extrinsicSuccess(0))))
            .fetchAccountTransfersInBlock(chain.id, BLOCK_HASH, ByteArray(32) { 7 }).getOrThrow()

        assertTrue(transfers.isEmpty())
    }

    @Test
    fun `a block the node does not return is a failure`() = runBlocking {
        val result = source(FakeSocket(block = null), FakeEvents(runtime, events()))
            .fetchAccountTransfersInBlock(chain.id, BLOCK_HASH, recipient)

        assertTrue(result.isFailure)
    }

    private fun source(socket: FakeSocket, storage: StorageDataSource) =
        WssSubstrateSource(socket.rpcCalls(chain.id), storage, mockk())

    private fun block(vararg extrinsics: String) = mapOf(
        "block" to mapOf(
            "header" to mapOf("number" to "0x14ad4d1", "parentHash" to "0x" + "cd".repeat(32)),
            "extrinsics" to extrinsics.toList()
        ),
        "justifications" to null
    )

    private class FakeSocket(private val block: Any?) {
        val requests = mutableListOf<RuntimeRequest>()

        private val socket = mockk<SocketService> {
            every { jsonMapper } returns Gson()
            every { executeRequest(any(), any(), any()) } answers {
                val request = firstArg<RuntimeRequest>()
                requests += request
                thirdArg<SocketService.ResponseListener<RpcResponse>>().onNext(RpcResponse("2.0", block, request.id, null))
                mockk(relaxed = true)
            }
        }

        fun rpcCalls(chainId: String): RpcCalls {
            val connection = mockk<ChainConnection> { every { socketService } returns socket }
            val chainRegistry = mockk<IChainRegistry> { every { getConnection(chainId) } returns connection }
            return RpcCalls(chainRegistry)
        }
    }

    private class FakeEvents(private val runtime: RuntimeSnapshot, private val events: String) : StorageDataSource {
        val blocks = mutableListOf<String?>()

        override suspend fun <T> query(chainId: String, keyBuilder: (RuntimeSnapshot) -> String?, at: String?, binding: (String?, RuntimeSnapshot) -> T): T {
            assertEquals(SYSTEM_EVENTS_KEY, keyBuilder(runtime))
            blocks += at
            return binding(events, runtime)
        }

        override suspend fun <K, T> queryKeys(
            chainId: String,
            keysBuilder: (RuntimeSnapshot) -> Map<String, K>,
            at: String?,
            binding: (String?, RuntimeSnapshot) -> T
        ): Map<K, T> = error("not used")

        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> String?, binder: (String?, RuntimeSnapshot) -> T): Flow<T> =
            error("not used")

        override suspend fun <K, T> queryByPrefix(
            chainId: String,
            prefixKeyBuilder: (RuntimeSnapshot) -> String?,
            keyExtractor: (String) -> K,
            binding: (String?, RuntimeSnapshot, K) -> T
        ): Map<K, T> = error("not used")

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> String,
            childKeyBuilder: suspend java.io.OutputStream.(RuntimeSnapshot) -> Unit,
            binder: (String?, RuntimeSnapshot) -> T
        ): T = error("not used")
    }

    private companion object {
        const val BLOCK_HASH = "0xdd19dedebd1744036458abb0d129540e27e5da0b2bb7bc97637a06529143d345"

        // twox128("System") ++ twox128("Events")
        const val SYSTEM_EVENTS_KEY = "0x26aa394eea5630e07c48ae0c9558cef780d41e5e16056765bc8461851072c9d7"

        // EventRecord { phase: ApplyExtrinsic(u32), event: System (pallet 0) event, topics: [] } with an empty
        // DispatchEventInfo (weight 0/0 as compacts, class Normal, pays Yes).
        fun extrinsicSuccess(index: Int) = "00" + u32(index) + "00" + "00" + "0000" + "00" + "00" + "00"

        // ExtrinsicFailed { dispatch_error: CannotLookup, dispatch_info }.
        fun extrinsicFailed(index: Int) = "00" + u32(index) + "00" + "01" + "01" + "0000" + "00" + "00" + "00"

        fun events(vararg records: String) = "0x" + compact(records.size) + records.joinToString("")

        fun u32(value: Int) = (0 until 4).joinToString("") { "%02x".format((value shr (8 * it)) and 0xff) }
        fun compact(value: Int): String {
            require(value < 64)
            return "%02x".format(value shl 2)
        }

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }

        fun runtime(vector: JsonObject): RuntimeSnapshot {
            val runtimeRef = vector.getValue("runtime").jsonObject
            val body = GZIPInputStream(requireNotNull(TransfersInBlockTest::class.java.getResourceAsStream("/prod-parity/${runtimeRef.string("file")}"))).use { it.readBytes() }
            assertEquals("runtime snapshot", runtimeRef.string("sha256"), body.sha256())
            val snapshot = Json.parseToJsonElement(body.decodeToString()).jsonObject
            return RuntimeFactory(JsonFactory.create()).constructRuntime(
                metadataRaw = snapshot.getValue("metadata").jsonPrimitive.content,
                ownTypesRaw = snapshot.getValue("types").jsonPrimitive.content,
                runtimeVersion = snapshot.getValue("specVersion").jsonPrimitive.int
            ).runtime
        }

        fun JsonObject.extrinsic(symbol: String): String = getValue("assets").jsonArray.map { it.jsonObject }
            .single { it.getValue("input").jsonObject.string("symbol") == symbol }
            .string("extrinsic")!!

        fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.content

        fun resource(path: String): String = requireNotNull(TransfersInBlockTest::class.java.getResource(path)).readText()

        fun String.unhex(): ByteArray {
            val clean = removePrefix("0x")
            return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

        fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("", "0x") { "%02x".format(it) }
    }
}
