package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
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

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.mockito.kotlin.doSuspendableAnswer

/** Actual pool API, service and SCALE builder; RPC and all key providers are local test doubles. */
class StakingPoolPublicFeeTest {
    @Test fun `ed25519 pool fee needs public data only`() = runBlocking<Unit> { checkFee(CryptoType.ED25519, "Ed25519", 64) }
    @Test fun `sr25519 pool fee needs public data only`() = runBlocking<Unit> { checkFee(CryptoType.SR25519, "Sr25519", 64) }
    @Test fun `ecdsa pool fee retains its signature width without keys`() = runBlocking<Unit> { checkFee(CryptoType.ECDSA, "Ecdsa", 65) }

    @Test fun `fee suspended at nonce never opens keys before or after resume`() = runBlocking<Unit> { checkHeld("nonce") }
    @Test fun `fee suspended at runtime never opens keys before or after resume`() = runBlocking<Unit> { checkHeld("runtime") }

    @Test fun `failed public fee query does not fall back to signing or submission`() = runBlocking<Unit> {
        val h = Harness()
        whenever(h.rpc.estimateExtrinsicFee(any(), any())).thenThrow(IllegalStateException("local fee unavailable"))
        val result = runCatching { h.estimate() }
        assertEquals("local fee unavailable", result.exceptionOrNull()?.message)
        h.assertNoKeysOrSubmission()
    }

    @Test fun `fee envelope uses the reviewed wallet crypto despite an earlier duplicate`() = runBlocking<Unit> {
        val h = Harness()
        whenever(h.wallets.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.SR25519)
        whenever(h.provider.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.ECDSA)
        assertEquals(FEE, h.estimate())
        val signature = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic()).signature!!.signature as DictEnum.Entry<*>
        assertEquals("Ecdsa", signature.name)
        assertArrayEquals(ByteArray(65), signature.value as ByteArray)
        h.assertNoKeysOrSubmission()
        verify(h.wallets).forWallet(42)
    }

    private suspend fun checkFee(crypto: CryptoType, name: String, length: Int) {
        val h = Harness()
        whenever(h.provider.getCryptoTypeFor(any(), any())).thenReturn(crypto)
        assertEquals(FEE, h.estimate())
        h.assertNoKeysOrSubmission()
        val decoded = Extrinsic.fromHex(h.runtime, h.estimatedExtrinsic())
        val signature = decoded.signature!!
        assertArrayEquals(h.account, signature.accountIdentifier as ByteArray)
        assertEquals(BigInteger.valueOf(3), signature.signedExtras[SignedExtras.NONCE])
        val wrapper = signature.signature as DictEnum.Entry<*>
        assertEquals(name, wrapper.name)
        assertArrayEquals(ByteArray(length), wrapper.value as ByteArray)
        assertEquals("NominationPools.join", "${decoded.call.module.name}.${decoded.call.function.name}")
        assertEquals(mapOf("amount" to BigInteger.TEN, "pool_id" to BigInteger.valueOf(7)), decoded.call.arguments)
    }

    private suspend fun checkHeld(boundary: String) = coroutineScope {
        val h = Harness()
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        if (boundary == "nonce") {
            whenever(h.rpc.getAccountNonce(any(), any())).doSuspendableAnswer {
                entered.complete(Unit); resume.await(); BigInteger.valueOf(3)
            }
        } else {
            whenever(h.registry.getRuntime(any())).doSuspendableAnswer {
                entered.complete(Unit); resume.await(); h.runtime
            }
        }
        val fee = async { runCatching { h.estimate() } }
        try {
            withTimeout(10_000) { while (!entered.isCompleted && !fee.isCompleted) yield() }
            if (fee.isCompleted) fee.await().getOrThrow()
            assertTrue("Actual preparation reached held public boundary", entered.isCompleted)
            h.assertNoKeysOrSubmission()
            resume.complete(Unit)
            assertEquals(FEE, fee.await().getOrThrow())
            h.assertNoKeysOrSubmission()
        } finally { resume.complete(Unit); fee.cancel() }
    }

    private class Harness {
        val chain = mock<Chain> {
            on { id } doReturn GENESIS
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val account = ByteArray(32) { 5 }
        val rpc = mock<RpcCalls>()
        val provider = mock<GuardedKeypairProvider>()
        val wallets = mock<WalletBoundKeypairProvider>()
        val runtime = runtime(POLKADOT_EXTENSIONS, listOf(POOL))
        val registry = mock<IChainRegistry>()
        private val mortality = mock<MortalityConstructor>()
        private val service = ExtrinsicService(rpc, wallets, ExtrinsicBuilderFactory(rpc, registry, mortality))
        private val selection = mock<StakingSharedState>()
        private val api = StakingPoolApi(service, selection, mock(), PoolJoinPreferencesFixture().journal(), mock())

        init {
            runBlocking {
                whenever(registry.getRuntime(GENESIS)).thenReturn(runtime)
                whenever(rpc.getRuntimeVersion(GENESIS)).thenReturn(RuntimeVersion(1_005_000, 26))
                whenever(rpc.getAccountNonce(any(), any())).thenReturn(BigInteger.valueOf(3))
                whenever(rpc.estimateExtrinsicFee(eq(GENESIS), any())).thenReturn(FEE)
                whenever(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64, 12), BLOCK_HASH))
                whenever(wallets.forWallet(42)).thenReturn(provider)
                whenever(wallets.getCryptoTypeFor(any(), any())).doSuspendableAnswer { provider.getCryptoTypeFor(chain, account) }
                whenever(provider.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.ED25519)
                whenever(provider.getKeypairFor(any(), any())).thenThrow(IllegalStateException("private key unavailable"))
            }
        }

        suspend fun estimate() = api.estimateJoinFee(chain, account.toAddress(42), BigInteger.TEN, 42, BigInteger.valueOf(7))
        suspend fun estimatedExtrinsic(): String = argumentCaptor<String>().also {
            verify(rpc).estimateExtrinsicFee(eq(GENESIS), it.capture())
        }.firstValue
        suspend fun assertNoKeysOrSubmission() {
            verify(wallets, never()).getCryptoTypeFor(any(), any())
            verify(wallets, never()).getKeypairFor(any(), any())
            verify(wallets, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
            verify(provider, never()).getKeypairFor(any(), any())
            verify(provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
            verify(rpc, never()).submitExtrinsic(any(), any())
            verify(rpc, never()).submitAuthorizedExtrinsic(any(), any(), any(), any())
            verify(selection, never()).chain()
        }
    }
    private companion object {
        val GENESIS = "91".repeat(32)
        val BLOCK_HASH = ByteArray(32) { 0x11 }
        val FEE: BigInteger = BigInteger.valueOf(15_000_000)

        // Order and names as Polkadot 2005000 and Avail list them; unmapped ones encode nothing.
        val POLKADOT_EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "PrevalidateAttests", "CheckMetadataHash"
        )
        val POOL = Module(
            "NominationPools", null,
            mapOf("join" to MetadataFunction("join", listOf(FunctionArgument("amount", u8), FunctionArgument("pool_id", u8)), emptyList(), 39 to 0)),
            emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(39)
        )

        fun runtime(signedExtensions: List<String>, modules: List<Module>): RuntimeSnapshot {
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
