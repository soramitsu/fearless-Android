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

import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import org.mockito.kotlin.times
import org.mockito.kotlin.atLeastOnce
import kotlinx.coroutines.CancellationException
/** Actual API/service/SCALE; local fixture keys and mocked RPC, no network. A metadata update
 * between attempts makes both formerly retried envelopes encodable and exposes duplicate output. */
class StakingPoolMemberSubmissionTest {
    @Test fun `unbond does not resubmit an ambiguous failure`() = runBlocking<Unit> { ambiguous(true) }
    @Test fun `withdraw does not resubmit an ambiguous failure`() = runBlocking<Unit> { ambiguous(false) }
    @Test fun `unbond does not resubmit after cancellation`() = runBlocking<Unit> { cancelled(true) }
    @Test fun `withdraw does not resubmit after cancellation`() = runBlocking<Unit> { cancelled(false) }
    @Test fun `unbond acknowledgement needs only one submission`() = runBlocking<Unit> { success(true) }
    @Test fun `withdraw acknowledgement needs only one submission`() = runBlocking<Unit> { success(false) }
    @Test fun `unbond fee failure does not query another envelope`() = runBlocking<Unit> { feeFailure(true) }
    @Test fun `withdraw fee failure does not query another envelope`() = runBlocking<Unit> { feeFailure(false) }

    private suspend fun ambiguous(unbond: Boolean) {
        val failure = IllegalStateException("local fixture: output admitted but acknowledgement lost")
        val h = Harness(submitError = failure)
        val result = h.submit(unbond)
        assertEquals("A failed response does not prove non-submission", 1, h.submissions)
        assertEquals(failure.message, result.exceptionOrNull()?.message)
    }

    private suspend fun cancelled(unbond: Boolean) {
        val failure = CancellationException("local fixture: cancelled while waiting after output")
        val h = Harness(submitError = failure)
        val result = runCatching { h.submit(unbond) }
        assertEquals("Cancellation cannot authorize replacement output", 1, h.submissions)
        val error = result.exceptionOrNull() ?: result.getOrThrow().exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    private suspend fun success(unbond: Boolean) {
        val h = Harness()
        assertEquals("0xfixture", h.submit(unbond).getOrThrow())
        assertEquals(1, h.submissions)
        assertEquals(0, h.fees)
    }

    private suspend fun feeFailure(unbond: Boolean) {
        val failure = IllegalStateException("local fixture: fee RPC unavailable")
        val h = Harness(feeError = failure)
        assertEquals(failure.message, runCatching { h.estimate(unbond) }.exceptionOrNull()?.message)
        assertEquals("RPC failure is not argument incompatibility", 1, h.fees)
        assertEquals(0, h.submissions)
    }

    private class Harness(val submitError: Exception? = null, val feeError: Exception? = null) {
        val chain = mock<Chain> {
            on { id } doReturn GENESIS
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val account = ByteArray(32) { 5 }
        val rpc = mock<RpcCalls>()
        val provider = mock<GuardedKeypairProvider>()
        private fun memberRuntime(multi: Boolean): RuntimeSnapshot {
            val memberType = if (multi) DictEnum("MultiAddress", listOf(DictEnum.Entry("Id", TypeReference(FixedByteArray("AccountId", 32))))) else FixedByteArray("AccountId", 32)
            val pool = Module("NominationPools", null, listOf("unbond", "withdraw_unbonded").mapIndexed { index, name ->
                name to MetadataFunction(name, listOf(FunctionArgument("member_account", memberType), FunctionArgument(if (name == "unbond") "unbonding_points" else "num_slashing_spans", u8)), emptyList(), 39 to index)
            }.toMap(), emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(39))
            return runtime(POLKADOT_EXTENSIONS, listOf(pool))
        }
        val firstRuntime = memberRuntime(true)
        val laterRuntime = memberRuntime(false)
        var runtimeReads = 0
        var submissions = 0
        var fees = 0
        val registry = mock<IChainRegistry>()
        val mortality = mock<MortalityConstructor>()
        val service = ExtrinsicService(rpc, provider, ExtrinsicBuilderFactory(rpc, registry, mortality))
        val selection = mock<StakingSharedState>()
        val api = StakingPoolApi(service, selection, mock(), PoolJoinPreferencesFixture().journal(), mock())
        init {
            runBlocking {
                whenever(selection.chain()).thenReturn(chain)
                whenever(registry.getRuntime(GENESIS)).doSuspendableAnswer {
                    runtimeReads++
                    if (runtimeReads == 1) firstRuntime else laterRuntime
                }
                whenever(rpc.getRuntimeVersion(GENESIS)).thenReturn(RuntimeVersion(1_005_000, 26))
                whenever(rpc.getAccountNonce(any(), any())).thenReturn(BigInteger.valueOf(3))
                whenever(rpc.estimateExtrinsicFee(eq(GENESIS), any())).doSuspendableAnswer {
                    fees++
                    feeError?.let { throw it }
                    FEE
                }
                whenever(rpc.submitExtrinsic(eq(GENESIS), any())).doSuspendableAnswer {
                    submissions++
                    if (submissions == 1) submitError?.let { throw it }
                    "0xfixture"
                }
                whenever(mortality.construct(chain)).thenReturn(Mortality(Era.Mortal(64, 12), BLOCK_HASH))
                whenever(provider.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.ED25519)
                whenever(provider.getKeypairFor(any(), any())).thenReturn(BaseKeypair(ByteArray(32) { 7 }, account))
            }
        }
        suspend fun submit(unbond: Boolean) = if (unbond) api.unbond(account.toAddress(42), BigInteger.TEN) else api.withdrawUnbonded(account.toAddress(42))
        suspend fun estimate(unbond: Boolean) = if (unbond) api.estimateUnbondFee(account.toAddress(42), BigInteger.TEN) else api.estimateWithdrawUnbondedFee(account.toAddress(42))
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
