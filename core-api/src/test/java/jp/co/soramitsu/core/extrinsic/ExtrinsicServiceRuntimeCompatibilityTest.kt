package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger

/**
 * The app signs with the pinned fearless-utils builder. These cases fail if the pin falls back to a
 * Utils commit without the 4.2.0 signed-extension fixes or without the Asset Hub call refusal.
 */
class ExtrinsicServiceRuntimeCompatibilityTest {

    @Test
    fun `relay extrinsics carry CheckMetadataHash disabled in the extras and the signed payload`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES)

        assertEquals(FEE, h.service.estimateFee(h.chain) { transfer() })

        val extras = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic()).signature!!.signedExtras
        assertEquals(BigInteger.ZERO, extras[SignedExtras.CHECK_METADATA_HASH])

        val builder = h.factory.createForFee(h.chain).apply { transfer() }
        var payload = ByteArray(0)
        builder.prepareSigning().sign(BaseKeypair(ByteArray(32), ByteArray(32))) { _, message, _ ->
            payload = message
            SignatureWrapper.Ed25519(ByteArray(64))
        }
        // Additional data ends with CheckMortality's block hash and CheckMetadataHash's Option::None.
        assertArrayEquals(BLOCK_HASH + byteArrayOf(0), payload.copyOfRange(payload.size - 33, payload.size))
    }

    @Test
    fun `app id chains encode the app id in CheckAppId`() = runBlocking<Unit> {
        val h = Harness(AVAIL_EXTENSIONS, BALANCES)

        h.service.estimateFee(h.chain, h.account, appId = BigInteger.ZERO) { transfer() }

        val extras = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic()).signature!!.signedExtras
        assertEquals(BigInteger.ZERO, extras[SignedExtras.APP_ID])
        assertTrue(SignedExtras.ASSET_TX_PAYMENT !in extras)
    }

    @Test
    fun `relay staking that moved to Asset Hub is refused before it is signed or sent`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES, STAKING, module("RcMigrator", 255))

        val feeQuery = runCatching { h.service.estimateFee(h.chain) { bond() } }
        val submitted = h.service.submitExtrinsic(h.chain, h.account) { bond() }
        val authorized = h.service.submitAuthorizedExtrinsic(h.chain, h.account, { error("must not authorize") }) { bond() }

        assertTrue(feeQuery.exceptionOrNull() is CallMovedToAssetHubException)
        val refusal = submitted.exceptionOrNull() as CallMovedToAssetHubException
        assertEquals("Staking.bond", "${refusal.moduleName}.${refusal.callName}")
        assertTrue(authorized.exceptionOrNull() is CallMovedToAssetHubException)
        verify(h.provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
        verify(h.rpc, never()).estimateExtrinsicFee(any(), any())
        verify(h.rpc, never()).submitExtrinsic(any(), any())
        verify(h.rpc, never()).submitAuthorizedExtrinsic(any(), any(), any(), any())
    }

    @Test
    fun `an authorized mutation is never signed with an immortal era`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES)
        // MortalityConstructor falls back to an immortal era when the chain state cannot be read.
        whenever(h.mortality.construct(h.chain)).thenReturn(Mortality(Era.Immortal, GENESIS.fromHexBytes()))

        val authorized = h.service.submitAuthorizedExtrinsic(h.chain, h.account, { error("must not authorize") }) { transfer() }

        assertTrue(authorized.exceptionOrNull() is IllegalStateException)
        verify(h.provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
        verify(h.rpc, never()).submitAuthorizedExtrinsic(any(), any(), any(), any())
    }

    @Test
    fun `an authorized builder reports the blocks its transaction can be included in`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES)
        whenever(h.mortality.construct(h.chain)).thenReturn(
            Mortality(Era.Mortal(64, 12), BLOCK_HASH, birthBlock = BigInteger.valueOf(1_036))
        )

        val authorized = h.factory.createForAuthorizedSubmit(h.chain, h.account, h.provider, null, null)

        assertEquals(ExtrinsicValidity(BigInteger.valueOf(1_036), 64), authorized.validity)
        assertEquals(BigInteger.valueOf(1_100), authorized.validity?.deathBlock)
    }

    @Test
    fun `Asset Hub keeps building staking calls`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES, STAKING, module("StakingRcClient", 84))

        assertEquals(FEE, h.service.estimateFee(h.chain) { bond() })

        val call = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic()).call
        assertEquals("Staking.bond", "${call.module.name}.${call.function.name}")
    }

    @Test
    fun `ed25519 account fees do not read a private key`() = runBlocking<Unit> {
        assertPublicFee(CryptoType.ED25519, "Ed25519", 64)
    }

    @Test
    fun `sr25519 account fees do not read a private key`() = runBlocking<Unit> {
        assertPublicFee(CryptoType.SR25519, "Sr25519", 64)
    }

    @Test
    fun `ecdsa account fees keep the 65 byte signature envelope without private keys`() = runBlocking<Unit> {
        assertPublicFee(CryptoType.ECDSA, "Ecdsa", 65)
    }

    @Test
    fun `generic fees encode an inert placeholder instead of signing`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES)
        assertEquals(FEE, h.service.estimateFee(h.chain) { transfer() })
        val signature = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic()).signature!!
        assertArrayEquals(ByteArray(32), signature.accountIdentifier as ByteArray)
        val wrapper = signature.signature as DictEnum.Entry<*>
        assertEquals("Ed25519", wrapper.name)
        assertArrayEquals(ByteArray(64), wrapper.value as ByteArray)
        verify(h.provider, never()).getKeypairFor(any(), any())
        verify(h.provider, never()).getCryptoTypeFor(any(), any())
        verify(h.rpc, never()).submitExtrinsic(any(), any())
    }

    @Test
    fun `ethereum account fees preserve a 20 byte address and structured ecdsa signature`() = runBlocking<Unit> {
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES, ethereum = true)
        whenever(h.provider.getCryptoTypeFor(h.chain, h.account)).thenReturn(CryptoType.ECDSA)
        whenever(h.provider.getKeypairFor(any(), any())).thenThrow(IllegalStateException("private key unavailable"))
        assertEquals(FEE, h.service.estimateFee(h.chain, h.account) { transfer() })
        val signature = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic()).signature!!
        assertArrayEquals(h.account, signature.accountIdentifier as ByteArray)
        val fields = (signature.signature as Struct.Instance).mapping
        assertArrayEquals(ByteArray(32), fields["r"] as ByteArray)
        assertArrayEquals(ByteArray(32), fields["s"] as ByteArray)
        assertEquals(BigInteger.ZERO, fields["v"])
        verify(h.provider, never()).getKeypairFor(any(), any())
        verify(h.rpc, never()).submitExtrinsic(any(), any())
    }

    @Test
    fun `public account fee preserves batch call order`() = runBlocking<Unit> { assertBatchFee(false) }

    @Test
    fun `public account fee preserves atomic batch call order`() = runBlocking<Unit> { assertBatchFee(true) }

    @Test
    fun `placeholder and legacy signed account extrinsics have equal length and non signature fields`() = runBlocking<Unit> {
        for (crypto in listOf(CryptoType.ED25519, CryptoType.ECDSA)) {
            val h = Harness(POLKADOT_EXTENSIONS, BALANCES)
            whenever(h.provider.getCryptoTypeFor(h.chain, h.account)).thenReturn(crypto)
            val legacy = h.factory.createForSubmit(h.chain, h.account, h.provider, BigInteger.valueOf(7), null)
                .apply { transfer() }.build()
            h.service.estimateFee(h.chain, h.account, tip = BigInteger.valueOf(7)) { transfer() }
            val placeholder = h.estimatedExtrinsic()
            assertEquals(legacy.length, placeholder.length)
            val old = Extrinsic.fromHex(h.runtime, legacy)
            val new = Extrinsic.fromHex(h.runtime, placeholder)
            assertEquals(old.call.arguments, new.call.arguments)
            assertEquals(old.call.function.name, new.call.function.name)
            val normalized = Extrinsic.Instance(Extrinsic.Signature(old.signature!!.accountIdentifier, new.signature!!.signature, old.signature!!.signedExtras), old.call)
            assertEquals(placeholder, Extrinsic.toHex(h.runtime, normalized))
            assertArrayEquals(old.signature!!.accountIdentifier as ByteArray, new.signature!!.accountIdentifier as ByteArray)
            assertEquals((old.signature!!.signature as DictEnum.Entry<*>).name, (new.signature!!.signature as DictEnum.Entry<*>).name)
        }
    }

    private suspend fun assertBatchFee(all: Boolean) {
        val utility = Module("Utility", null, listOf("batch", "batch_all").mapIndexed { index, name ->
            name to MetadataFunction(name, listOf(FunctionArgument("calls", Vec("Calls", TypeReference(GenericCall)))), emptyList(), 26 to index)
        }.toMap(), emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(26))
        val h = Harness(POLKADOT_EXTENSIONS, BALANCES, utility)
        whenever(h.provider.getKeypairFor(any(), any())).thenThrow(IllegalStateException("private key unavailable"))
        h.service.estimateFee(h.chain, h.account, useBatchAll = all) {
            call("Balances", "transfer_keep_alive", mapOf("value" to BigInteger.ONE))
            call("Balances", "transfer_keep_alive", mapOf("value" to BigInteger.TEN))
        }
        val decoded = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic())
        assertEquals(if (all) "batch_all" else "batch", decoded.call.function.name)
        val nested = decoded.call.arguments["calls"] as List<*>
        assertEquals(listOf(BigInteger.ONE, BigInteger.TEN), nested.map { (it as GenericCall.Instance).arguments["value"] })
        verify(h.provider, never()).getKeypairFor(any(), any())
        verify(h.rpc, never()).submitExtrinsic(any(), any())
    }

    private suspend fun assertPublicFee(crypto: CryptoType, name: String, size: Int) {
        val h = Harness(AVAIL_EXTENSIONS, BALANCES)
        whenever(h.provider.getCryptoTypeFor(h.chain, h.account)).thenReturn(crypto)
        whenever(h.provider.getKeypairFor(any(), any())).thenThrow(IllegalStateException("private key unavailable"))
        assertEquals(FEE, h.service.estimateFee(h.chain, h.account, tip = BigInteger.valueOf(7), appId = BigInteger.valueOf(9)) { transfer() })
        val decoded = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic())
        val signature = decoded.signature!!
        assertArrayEquals(h.account, signature.accountIdentifier as ByteArray)
        assertEquals(BigInteger.valueOf(3), signature.signedExtras[SignedExtras.NONCE])
        assertEquals(BigInteger.valueOf(7), signature.signedExtras[SignedExtras.TIP])
        assertEquals(BigInteger.valueOf(9), signature.signedExtras[SignedExtras.APP_ID])
        val wrapper = signature.signature as DictEnum.Entry<*>
        assertEquals(name, wrapper.name)
        assertArrayEquals(ByteArray(size), wrapper.value as ByteArray)
        assertEquals("Balances.transfer_keep_alive", "${decoded.call.module.name}.${decoded.call.function.name}")
        verify(h.provider, never()).getKeypairFor(any(), any())
        verify(h.provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
        verify(h.rpc, never()).submitExtrinsic(any(), any())
        verify(h.rpc, never()).submitAuthorizedExtrinsic(any(), any(), any(), any())
    }

    private fun ExtrinsicBuilder.transfer() = call("Balances", "transfer_keep_alive", mapOf("value" to BigInteger.TEN))

    private fun ExtrinsicBuilder.bond() = call("Staking", "bond", mapOf("value" to BigInteger.TEN))

    private class Harness(signedExtensions: List<String>, vararg modules: Module, ethereum: Boolean = false) {
        val chain = mock<IChain> { on { id } doReturn GENESIS }
        val account = ByteArray(if (ethereum) 20 else 32) { 5 }
        val rpc = mock<RpcCalls>()
        val provider = mock<GuardedKeypairProvider>()
        val runtime = runtime(signedExtensions, modules.toList(), ethereum)
        private val registry = mock<IChainRegistry>()
        val mortality = mock<MortalityConstructor>()
        val factory = ExtrinsicBuilderFactory(rpc, registry, mortality)
        val service = ExtrinsicService(rpc, provider, factory)

        init {
            runBlocking {
                whenever(registry.getRuntime(GENESIS)).thenReturn(runtime)
                whenever(rpc.getRuntimeVersion(GENESIS)).thenReturn(RuntimeVersion(1_005_000, 26))
                whenever(rpc.getAccountNonce(chain, account)).thenReturn(BigInteger.valueOf(3))
                whenever(rpc.estimateExtrinsicFee(eq(GENESIS), any())).thenReturn(FEE)
                whenever(rpc.submitExtrinsic(eq(GENESIS), any())).thenReturn("0xhash")
                whenever(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64, 12), BLOCK_HASH))
                whenever(provider.getCryptoTypeFor(chain, account)).thenReturn(CryptoType.ED25519)
                whenever(provider.getKeypairFor(chain, account)).thenReturn(BaseKeypair(ByteArray(32) { 7 }, account))
            }
        }

        suspend fun estimatedExtrinsic(): String = argumentCaptor<String>().also {
            verify(rpc).estimateExtrinsicFee(eq(GENESIS), it.capture())
        }.firstValue
    }

    private companion object {
        fun String.fromHexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val GENESIS = "91".repeat(32)
        val BLOCK_HASH = ByteArray(32) { 0x11 }
        val FEE: BigInteger = BigInteger.valueOf(15_000_000)

        // Order and names as Polkadot 2005000 and Avail list them; unmapped ones encode nothing.
        val POLKADOT_EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "PrevalidateAttests", "CheckMetadataHash"
        )
        val AVAIL_EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "CheckAppId"
        )

        val BALANCES = module("Balances", 5, "transfer_keep_alive" to 3)
        val STAKING = module("Staking", 7, "bond" to 0)

        fun module(name: String, index: Int, vararg calls: Pair<String, Int>) = Module(
            name,
            null,
            calls.associate { (call, callIndex) ->
                call to MetadataFunction(call, listOf(FunctionArgument("value", u8)), emptyList(), index to callIndex)
            },
            emptyMap(),
            emptyMap(),
            emptyMap(),
            index.toBigInteger()
        )

        fun runtime(signedExtensions: List<String>, modules: List<Module>, ethereum: Boolean = false): RuntimeSnapshot {
            val signature = DictEnum(
                "ExtrinsicSignature",
                listOf(
                    DictEnum.Entry("Ed25519", TypeReference(FixedByteArray("Ed25519", 64))),
                    DictEnum.Entry("Sr25519", TypeReference(FixedByteArray("Sr25519", 64))),
                    DictEnum.Entry("Ecdsa", TypeReference(FixedByteArray("Ecdsa", 65)))
                )
            )
            val registry = TypeRegistry(
                v13Preset() + mapOf(
                    "Address" to TypeReference(FixedByteArray("Address", if (ethereum) 20 else 32)),
                    "ExtrinsicSignature" to TypeReference(if (ethereum) Struct("EthereumSignature", linkedMapOf(
                        "r" to TypeReference(FixedByteArray("r", 32)),
                        "s" to TypeReference(FixedByteArray("s", 32)),
                        "v" to TypeReference(u8)
                    )) else signature)
                ),
                DynamicTypeResolver.defaultCompoundResolver()
            )
            return RuntimeSnapshot(
                registry,
                RuntimeMetadata(
                    BigInteger.valueOf(14),
                    modules.associateBy { it.name },
                    ExtrinsicMetadata(BigInteger.valueOf(4), signedExtensions)
                )
            )
        }
    }
}
