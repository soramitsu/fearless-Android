package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger

/** Signing builders take their runtime and metadata hash from the SigningRuntimeSource and never sign Disabled by default. */
class ExtrinsicBuilderFactoryMetadataHashTest {

    private val hash = ByteArray(32) { 0x5a }

    private class Harness(private val signing: suspend (RuntimeSnapshot) -> SigningRuntime, extensions: List<String> = EXTENSIONS) {
        val chain = mock<IChain> { on { id } doReturn GENESIS }
        val account = ByteArray(32) { 5 }
        val rpc = mock<RpcCalls>()
        val runtime = runtime(extensions)
        var sourceCalls = 0
        var keyReads = 0
        private val registry = mock<IChainRegistry>()
        private val mortality = mock<MortalityConstructor>()
        val factory = ExtrinsicBuilderFactory(rpc, registry, mortality, SigningRuntimeSource { _, version ->
            sourceCalls++
            assertEquals(SPEC_VERSION, version.specVersion)
            signing(runtime)
        })
        val provider = object : KeypairProvider {
            override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray) = CryptoType.ED25519
            override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair {
                keyReads++
                return BaseKeypair(ByteArray(32) { 7 }, account)
            }
        }

        init {
            runBlocking {
                whenever(registry.getRuntime(GENESIS)).thenReturn(runtime)
                whenever(rpc.getRuntimeVersion(GENESIS)).thenReturn(RuntimeVersion(SPEC_VERSION, 26))
                whenever(rpc.getAccountNonce(chain, account)).thenReturn(BigInteger.valueOf(3))
                whenever(rpc.estimateExtrinsicFee(eq(GENESIS), any())).thenReturn(BigInteger.ONE)
                whenever(rpc.submitExtrinsic(eq(GENESIS), any())).thenReturn("0xhash")
                whenever(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64, 12), ByteArray(32) { 0x11 }))
            }
        }

        fun submit(): String = runBlocking {
            ExtrinsicService(rpc, provider, factory).submitExtrinsic(chain, account) { call("Balances", "transfer_keep_alive", mapOf("value" to BigInteger.ONE)) }
                .getOrThrow()
            argumentCaptor<String>().also { verify(rpc).submitExtrinsic(eq(GENESIS), it.capture()) }.firstValue
        }
    }

    @Test
    fun `a network that checks the hash is signed with Mode Enabled and the source's hash`() = runBlocking {
        val harness = Harness({ SigningRuntime.WithMetadataHash(it, hash) })
        val extrinsic = Extrinsic.fromHex(harness.runtime, harness.submit())
        assertEquals(BigInteger.ONE, extrinsic.signature!!.signedExtras[SignedExtras.CHECK_METADATA_HASH])

        val authorized = harness.factory.createForAuthorizedSubmit(harness.chain, harness.account, harness.provider, null, null).builder
        authorized.call("Balances", "transfer_keep_alive", mapOf("value" to BigInteger.ONE))
        val intent = authorized.prepareSigning().intentBytes()
        // CheckMetadataHash is the last extension: the signed payload ends with Some(hash).
        assertArrayEquals(byteArrayOf(1) + hash, intent.copyOfRange(intent.size - 33, intent.size))
    }

    @Test
    fun `no key is read and nothing is signed when the hash cannot be computed`() {
        val harness = Harness({ throw MetadataHashUnavailableException("offline") })
        assertThrows(MetadataHashUnavailableException::class.java) { harness.submit() }
        assertEquals(0, harness.keyReads)
        runBlocking { verify(harness.rpc, org.mockito.kotlin.never()).submitExtrinsic(any(), any()) }
    }

    @Test
    fun `a runtime that checks the hash is never signed without one`() {
        val harness = Harness({ SigningRuntime.WithoutMetadataCheck(it) })
        assertThrows(MetadataHashUnavailableException::class.java) { harness.submit() }
        assertEquals(0, harness.keyReads)
    }

    @Test
    fun `a hash for a runtime without the extension is refused`() {
        val harness = Harness({ SigningRuntime.WithMetadataHash(it, hash) }, EXTENSIONS - CHECK_METADATA_HASH)
        assertThrows(IllegalArgumentException::class.java) { harness.submit() }
    }

    @Test
    fun `runtimes without the extension and reviewed exemptions sign as before`() {
        val without = Harness({ SigningRuntime.WithoutMetadataCheck(it) }, EXTENSIONS - CHECK_METADATA_HASH)
        val extrinsic = Extrinsic.fromHex(without.runtime, without.submit())
        assertEquals(null, extrinsic.signature!!.signedExtras[SignedExtras.CHECK_METADATA_HASH])

        val exempt = Harness({ SigningRuntime.MetadataHashExempt(it, "reviewed") })
        val exemptExtrinsic = Extrinsic.fromHex(exempt.runtime, exempt.submit())
        assertEquals(BigInteger.ZERO, exemptExtrinsic.signature!!.signedExtras[SignedExtras.CHECK_METADATA_HASH])
    }

    @Test
    fun `fee estimates do not consult the signing source`() = runBlocking {
        val harness = Harness({ error("fees sign nothing") })
        ExtrinsicService(harness.rpc, harness.provider, harness.factory)
            .estimateFee(harness.chain, harness.account) { call("Balances", "transfer_keep_alive", mapOf("value" to BigInteger.ONE)) }
        assertEquals(0, harness.sourceCalls)
        assertEquals(0, harness.keyReads)
    }

    @Test
    fun `a metadata hash is 32 bytes and is copied`() {
        val runtime = runtime(EXTENSIONS)
        assertThrows(IllegalArgumentException::class.java) { SigningRuntime.WithMetadataHash(runtime, ByteArray(31)) }
        val source = hash.copyOf()
        val signing = SigningRuntime.WithMetadataHash(runtime, source)
        source[0] = 0
        assertArrayEquals(hash, signing.metadataHash)
    }

    private companion object {
        const val SPEC_VERSION = 1_005_000
        val GENESIS = "91".repeat(32)
        val EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "PrevalidateAttests", "CheckMetadataHash"
        )

        fun runtime(extensions: List<String>): RuntimeSnapshot {
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
                    "Address" to TypeReference(FixedByteArray("Address", 32)),
                    "ExtrinsicSignature" to TypeReference(signature)
                ),
                DynamicTypeResolver.defaultCompoundResolver()
            )
            val balances = Module(
                "Balances", null,
                mapOf("transfer_keep_alive" to MetadataFunction("transfer_keep_alive", listOf(FunctionArgument("value", u8)), emptyList(), 5 to 3)),
                emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(5)
            )
            return RuntimeSnapshot(
                registry,
                RuntimeMetadata(BigInteger.valueOf(14), mapOf("Balances" to balances), ExtrinsicMetadata(BigInteger.valueOf(4), extensions))
            )
        }
    }
}
