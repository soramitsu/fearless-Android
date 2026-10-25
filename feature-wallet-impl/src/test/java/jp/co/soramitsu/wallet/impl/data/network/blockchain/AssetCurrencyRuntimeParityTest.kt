package jp.co.soramitsu.wallet.impl.data.network.blockchain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.SubstrateBalanceLoader
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Balance keys and transfer calls on live runtimes against Play 4.2.0 (AL-9).
 *
 * prod-parity/13-asset-currency-chains.json holds what 4.2.0 built (its Asset model, its SCALE library and
 * its SubstrateBalanceLoader/WssSubstrateSource dispatch) for every asset the bundled registry lists on SORA,
 * Acala, Bifrost Kusama/Polkadot, Interlay, HydraDX, Pendulum and Polkadot Asset Hub. The runtimes are
 * read-only snapshots of those chains (metadata at a finalized block) plus the app's chain types JSON.
 * Here the RC code builds the same things from the bundled registry and must produce the same bytes.
 */
class AssetCurrencyRuntimeParityTest {

    private val vectors = Json.parseToJsonElement(resource("/prod-parity/13-asset-currency-chains.json")).jsonObject
    private val chainVectors = vectors.getValue("chains").jsonArray.map { it.jsonObject }
    private val recipient = vectors.getValue("recipient").jsonPrimitive.content.unhex()
    private val signer = SubstrateKeypairFactory.generate(EncryptionType.ED25519, vectors.getValue("signerSeed").jsonPrimitive.content.unhex())

    @Test
    fun `the bundled registry still lists the assets the vectors were built from`() {
        chainVectors.forEach { vector ->
            val chain = registryChain(vector)
            vector.assets().forEach { assetVector ->
                val input = assetVector.getValue("input").jsonObject
                val asset = chain.assets.singleOrNull { it.id == input.string("assetId") }
                    ?: throw AssertionError("${chain.name}: ${input.string("symbol")} is gone from the registry; regenerate the AL-9 vectors")

                assertEquals(input.toString(), input.string("type"), asset.type?.name)
                assertEquals(input.toString(), input.string("currencyId"), asset.currencyId)
                assertEquals(input.toString(), input.getValue("isUtility").jsonPrimitive.booleanOrNull, asset.isUtility)
                assertEquals(input.toString(), assetVector.string("typeExtra"), asset.typeExtra?.name)
            }
            assertEquals(chain.name, vector.assets().size, chain.assets.size)
        }
    }

    @Test
    fun `SubstrateBalanceLoader builds the 4_2_0 balance key for every asset on every runtime`() = runBlocking {
        chainVectors.forEach { vector ->
            val live = liveChain(vector)
            val requested = loadBalances(live, storage = emptyMap()).requestedKeys

            assertEquals(live.chain.name, vector.assets().map { it.string("balanceKey") }, requested)
        }
    }

    @Test
    fun `SORA XOR loads from System Account and VAL and PSWAP from Tokens Accounts`() = runBlocking {
        val live = liveChain(chainVectors.single { it.string("chain") == "sora" })
        val xor = live.asset("xor")
        val value = live.asset("val")
        val pswap = live.asset("pswap")
        assertEquals("System.Account", xor.string("balanceStorage"))
        assertEquals("Tokens.Accounts", value.string("balanceStorage"))
        assertEquals("Tokens.Accounts", pswap.string("balanceStorage"))

        val result = loadBalances(
            live,
            storage = mapOf(
                xor.string("balanceKey")!! to systemAccount(free = XOR_FREE),
                value.string("balanceKey")!! to tokensAccount(free = VAL_FREE),
                pswap.string("balanceKey")!! to tokensAccount(free = PSWAP_FREE)
            )
        )

        val freeById = result.updates.associate { it.id to it.freeInPlanks }
        assertEquals(live.chain.assets.size, result.updates.size)
        assertEquals(XOR_FREE, freeById.getValue(xor.assetId()))
        assertEquals(VAL_FREE, freeById.getValue(value.assetId()))
        assertEquals(PSWAP_FREE, freeById.getValue(pswap.assetId()))
        assertNull(result.scanError)
    }

    @Test
    fun `WssSubstrateSource encodes the 4_2_0 transfer call for every asset on every runtime`() = runBlocking {
        val acceptanceVectors = mutableListOf<JsonObject>()
        chainVectors.forEach { vector ->
            val live = liveChain(vector)
            val metadataHash = "CheckMetadataHash" in vector.getValue("signedExtensions").jsonArray.map { it.jsonPrimitive.content }

            vector.assets().filter { it.string("transferCall") != null }.forEach { assetVector ->
                val label = "${live.chain.name} ${assetVector.getValue("input").jsonObject.string("symbol")}"
                val built = runCatching { buildTransfer(live, assetVector.assetId()) }
                val expectedError = assetVector.string("transferError")

                if (expectedError != null) {
                    // 4.2.0 could not build this call on today's runtime either; the RC must refuse too.
                    assertTrue("$label: 4.2.0 failed with $expectedError, RC built ${built.getOrNull()}", built.isFailure)
                    return@forEach
                }
                val extrinsic = built.getOrElse { throw AssertionError("$label: ${assetVector.string("transferCall")}", it) }
                // The call is the last field of a signed extrinsic.
                assertTrue("$label: call ${assetVector.string("callBytes")} not in $extrinsic", extrinsic.endsWith(assetVector.string("callBytes")!!.removePrefix("0x")))
                if (!metadataHash) {
                    assertEquals(label, assetVector.string("extrinsic"), extrinsic)
                }
                if (live.key == "sora" || assetVector.string("transferCall") == "Assets.transfer") {
                    acceptanceVectors += acceptanceVector(live, assetVector, extrinsic)
                }
            }
        }
        writeAcceptanceVectors(acceptanceVectors)
    }

    @Test
    fun `SORA XOR spendable and total balances come from System Account`() = runBlocking {
        val live = liveChain(chainVectors.single { it.string("chain") == "sora" })
        val xor = live.asset("xor")
        val value = live.asset("val")
        val storage = FakeStorage(
            live.runtime,
            mapOf(
                xor.string("balanceKey")!! to systemAccount(free = XOR_FREE, frozen = XOR_FROZEN),
                value.string("balanceKey")!! to tokensAccount(free = VAL_FREE, frozen = VAL_FROZEN)
            )
        )
        val source = WssSubstrateSource(mockk<RpcCalls>(), storage, mockk<ExtrinsicService>())
        val xorAsset = live.chain.assets.single { it.id == xor.assetId() }
        val valAsset = live.chain.assets.single { it.id == value.assetId() }

        assertEquals(XOR_FREE, source.getTotalBalance(xorAsset, recipient))
        assertEquals(XOR_FREE - XOR_FROZEN, source.getAccountSpendableBalance(xorAsset, recipient))
        assertEquals(VAL_FREE, source.getTotalBalance(valAsset, recipient))
        assertEquals(VAL_FREE - VAL_FROZEN, source.getAccountSpendableBalance(valAsset, recipient))
        assertEquals(listOf(xor.string("balanceKey"), xor.string("balanceKey"), value.string("balanceKey"), value.string("balanceKey")), storage.requested)
    }

    private class LiveChain(val key: String, val vector: JsonObject, val chain: Chain, val runtime: RuntimeSnapshot) {
        val genesis: String = vector.getValue("genesis").jsonPrimitive.content
        val specVersion = vector.getValue("specVersion").jsonPrimitive.int
        val transactionVersion = vector.getValue("transactionVersion").jsonPrimitive.int

        fun asset(symbol: String): JsonObject = vector.assets().single { it.getValue("input").jsonObject.string("symbol") == symbol }
    }

    private class BalanceResult(val requestedKeys: List<String>, val updates: List<jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem>, val scanError: String?)

    private suspend fun loadBalances(live: LiveChain, storage: Map<String, String>): BalanceResult {
        val chainRegistry = mockk<ChainRegistry>()
        val runtimeProvider = mockk<RuntimeProvider>()
        val remoteStorage = mockk<RemoteStorageSource>()
        val keys = slot<List<String>>()
        coEvery { chainRegistry.checkChainSyncedUp(live.chain) } returns true
        coEvery { chainRegistry.awaitRuntimeProvider(live.chain.id) } returns runtimeProvider
        coEvery { runtimeProvider.get() } returns live.runtime
        coEvery { remoteStorage.queryKeys(capture(keys), live.chain.id, null) } answers { keys.captured.associateWith { storage[it] } }
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        val account = substrateAccount()

        val updates = SubstrateBalanceLoader(
            chain = live.chain,
            chainRegistry = chainRegistry,
            remoteStorageSource = remoteStorage,
            substrateSource = mockk(),
            operationDao = mockk<OperationDao>(),
            scanStateStore = scanState
        ).loadBalance(setOf(account))

        updates.forEach { assertTrue(it.accountId.contentEquals(recipient)) }
        val state = scanState.states.value[NetworkScanKey(account.id, live.chain.id)]
        assertNotNull(state)
        return BalanceResult(keys.captured, updates, state?.errorMessage)
    }

    private suspend fun buildTransfer(live: LiveChain, assetId: String): String {
        val extrinsicService = mockk<ExtrinsicService>()
        val formExtrinsic = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery {
            extrinsicService.estimateFee(
                chain = live.chain,
                accountId = any(),
                useBatchAll = any(),
                tip = any(),
                appId = any(),
                formExtrinsic = capture(formExtrinsic)
            )
        } returns BigInteger.ONE
        val asset = live.chain.assets.single { it.id == assetId }
        val transfer = Transfer(
            sender = live.chain.addressOf(signer.publicKey),
            recipient = live.chain.addressOf(recipient),
            amount = BigDecimal.ONE.movePointLeft(asset.precision),
            chainAsset = asset,
            additionalParams = SubstrateTransferParams(tipInPlanks = null, appId = null)
        )
        assertEquals(BigInteger.ONE, transfer.amountInPlanks)

        WssSubstrateSource(mockk<RpcCalls>(), mockk<StorageDataSource>(), extrinsicService)
            .getTransferFee(live.chain, transfer, additional = null, batchAll = false)

        val builder = ExtrinsicBuilder(
            runtime = live.runtime,
            keypair = signer,
            nonce = BigInteger.ZERO,
            runtimeVersion = RuntimeVersion(live.specVersion, live.transactionVersion),
            genesisHash = live.genesis.unhex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = AddressInstanceConstructor.constructInstance(live.runtime.typeRegistry, signer.publicKey),
            era = Era.Immortal
        )
        formExtrinsic.captured.invoke(builder)
        return builder.build()
    }

    private fun acceptanceVector(live: LiveChain, assetVector: JsonObject, extrinsic: String) = JsonObject(
        mapOf(
            "label" to JsonPrimitive("android-rc-wss-substrate-source:${live.key}:${assetVector.getValue("input").jsonObject.string("symbol")}"),
            "chain" to JsonPrimitive(live.key),
            "builder" to JsonPrimitive("android-rc-wss-substrate-source"),
            "extrinsic" to JsonPrimitive(extrinsic.lowercase()),
            "expect" to JsonPrimitive("payment"),
            "builtFor" to JsonObject(
                mapOf(
                    "specVersion" to JsonPrimitive(live.specVersion),
                    "transactionVersion" to JsonPrimitive(live.transactionVersion)
                )
            )
        )
    )

    // For the root T-2 check: node scripts/verify-transaction-acceptance.mjs validate --vectors <this file>.
    private fun writeAcceptanceVectors(vectors: List<JsonObject>) {
        val file = File("build/reports/asset-currency-parity/tx-acceptance-vectors.json")
        file.parentFile.mkdirs()
        file.writeText(
            JsonObject(
                mapOf(
                    "schema" to JsonPrimitive("fearless-tx-acceptance-vectors/v1"),
                    "note" to JsonPrimitive("Android RC WssSubstrateSource transfers of 1 planck from the fixed unfunded AL-4 ED25519 key, nonce 0, immortal. Built by AssetCurrencyRuntimeParityTest."),
                    "vectors" to JsonArray(vectors)
                )
            ).toString()
        )
    }

    private fun liveChain(vector: JsonObject): LiveChain {
        val runtimeRef = vector.getValue("runtime").jsonObject
        val body = GZIPInputStream(requireNotNull(javaClass.getResourceAsStream("/prod-parity/${runtimeRef.string("file")}"))).use { it.readBytes() }
        assertEquals("runtime snapshot", runtimeRef.string("sha256"), body.sha256())
        val snapshot = Json.parseToJsonElement(body.decodeToString()).jsonObject
        val runtime = RuntimeFactory(JsonFactory.create()).constructRuntime(
            metadataRaw = snapshot.getValue("metadata").jsonPrimitive.content,
            ownTypesRaw = snapshot.getValue("types").jsonPrimitive.content,
            runtimeVersion = snapshot.getValue("specVersion").jsonPrimitive.int
        ).runtime
        return LiveChain(vector.string("chain")!!, vector, registryChain(vector), runtime)
    }

    private fun registryChain(vector: JsonObject): Chain {
        val chainId = vector.getValue("genesis").jsonPrimitive.content.removePrefix("0x")
        return registry.single { it.id == chainId }
    }

    private fun substrateAccount() = MetaAccount(
        id = 1,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = recipient,
        substrateCryptoType = CryptoType.ED25519,
        substrateAccountId = recipient,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Substrate wallet",
        initialized = true
    )

    /** System.Account on SORA: nonce, consumers, providers, sufficients (u32), then free, reserved, misc_frozen, fee_frozen (u128). */
    private fun systemAccount(free: BigInteger, frozen: BigInteger = BigInteger.ZERO) =
        "0x" + List(4) { u32(0) }.joinToString("") + u128(free) + u128(BigInteger.ZERO) + u128(frozen) + u128(BigInteger.ZERO)

    /** orml_tokens::AccountData: free, reserved, frozen (u128). */
    private fun tokensAccount(free: BigInteger, frozen: BigInteger = BigInteger.ZERO) =
        "0x" + u128(free) + u128(BigInteger.ZERO) + u128(frozen)

    private fun u32(value: Int) = littleEndian(value.toBigInteger(), 4)
    private fun u128(value: BigInteger) = littleEndian(value, 16)
    private fun littleEndian(value: BigInteger, size: Int): String {
        val bigEndian = value.toByteArray().dropWhile { it == 0.toByte() }
        return (bigEndian.reversed() + List(size - bigEndian.size) { 0.toByte() }).joinToString("") { "%02x".format(it) }
    }

    private class FakeStorage(private val runtime: RuntimeSnapshot, private val values: Map<String, String>) : StorageDataSource {
        val requested = mutableListOf<String?>()

        override suspend fun <T> query(chainId: String, keyBuilder: (RuntimeSnapshot) -> String?, at: String?, binding: (String?, RuntimeSnapshot) -> T): T {
            val key = keyBuilder(runtime)
            requested += key
            return binding(values[key], runtime)
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
        val XOR_FREE: BigInteger = BigInteger("1234567890000000000000")
        val XOR_FROZEN: BigInteger = BigInteger("230000000000000000000")
        val VAL_FREE: BigInteger = BigInteger("42000000000000000000")
        val VAL_FROZEN: BigInteger = BigInteger("2000000000000000000")
        val PSWAP_FREE: BigInteger = BigInteger("7000000000000000000")

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }

        fun resource(path: String): String = requireNotNull(AssetCurrencyRuntimeParityTest::class.java.getResource(path)).readText()

        fun JsonObject.assets(): List<JsonObject> = getValue("assets").jsonArray.map { it.jsonObject }
        fun JsonObject.assetId(): String = getValue("input").jsonObject.string("assetId")!!
        fun JsonObject.string(key: String): String? = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

        fun String.unhex(): ByteArray {
            val clean = removePrefix("0x")
            return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

        fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("", "0x") { "%02x".format(it) }
    }
}
