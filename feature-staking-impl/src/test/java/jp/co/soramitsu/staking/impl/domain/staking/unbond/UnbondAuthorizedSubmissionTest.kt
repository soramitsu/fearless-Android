package jp.co.soramitsu.staking.impl.domain.staking.unbond

import java.math.BigInteger
import jp.co.soramitsu.core.extrinsic.*
import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.PreparedExtrinsic
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.StakingState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

/** Actual interactor and ExtrinsicService; keys, encoder and transport remain controlled boundaries. */
class UnbondAuthorizedSubmissionTest {
    private class ReadCancelled(val marker: Any) : CancellationException("fixture cancellation")
    private class Harness {
        @Volatile var current = true
        val chain = mock<Chain> {
            on { id } doReturn "fixture"
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
            on { externalApi } doReturn null
        }
        val account = ByteArray(32) { 1 }
        val state = StakingState.Parachain.None(chain, account)
        val rpc = mock<RpcCalls>()
        val factory = mock<ExtrinsicBuilderFactory>()
        val builder = mock<ExtrinsicBuilder>()
        val prepared = mock<PreparedExtrinsic>()
        val provider = mock<GuardedKeypairProvider>()
        val wallets = mock<WalletBoundKeypairProvider>()
        val key = BaseKeypair(ByteArray(32), ByteArray(32))
        val interactor = UnbondInteractor(ExtrinsicService(rpc, wallets, factory))
        var intent: String? = null
        val guard = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
                check(intentSha256 == intent)
                if (!current) throw CancellationException("fixture review revoked")
                return operation()
            }
        }
        init {
            whenever(wallets.forWallet(42L)).thenReturn(provider)
            runBlocking {
                whenever(factory.createForAuthorizedSubmit(any(), any(), any(), anyOrNull(), anyOrNull()))
                    .thenReturn(AuthorizedExtrinsicBuilder(builder, null))
                whenever(factory.createForFee(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(builder)
                whenever(provider.getAuthorizedKeypairFor(any(), any(), any(), any())).thenReturn(key)
                whenever(rpc.submitAuthorizedExtrinsic(any(), any(), any(), any())).thenReturn("fixture-hash")
                whenever(rpc.estimateExtrinsicFee(any(), any())).thenReturn(BigInteger.ONE)
            }
            whenever(builder.prepareSigning(any())).thenReturn(prepared)
            whenever(prepared.intentBytes()).thenReturn(byteArrayOf(9))
            whenever(prepared.sign(any(), any())).thenReturn("0x0102")
        }
        fun authorize(value: String): MutationExecutionGuard { intent = value; return guard }
        suspend fun submit(form: suspend ExtrinsicBuilder.() -> Unit = {}) =
            interactor.unbond(state, 42L, { current }, ::authorize, form)
        suspend fun fee(form: suspend ExtrinsicBuilder.() -> Unit = {}) =
            interactor.estimateFee(state, 42L, { current }, form)
    }

    @Test fun knownReviewUsesCapturedWalletAndOnlyAuthorizedSigningRoute() = runBlocking<Unit> {
        val h = Harness()
        assertEquals("fixture-hash", h.submit().getOrThrow())
        verify(h.wallets).forWallet(42L)
        verify(h.factory).createForAuthorizedSubmit(eq(h.chain), argThat { contentEquals(h.account) }, eq(h.provider), anyOrNull(), anyOrNull())
        verify(h.provider).getAuthorizedKeypairFor(eq(h.chain), argThat { contentEquals(h.account) }, eq(h.guard), eq(h.intent!!))
        verify(h.prepared).sign(eq(h.key), any())
        verify(h.rpc).submitAuthorizedExtrinsic("fixture", "0x0102", h.guard, h.intent!!)
        verify(h.provider, never()).getKeypairFor(any(), any())
        verify(h.factory, never()).createForSubmit(any(), any(), any(), anyOrNull(), anyOrNull())
        verify(h.rpc, never()).submitExtrinsic(any(), any())
    }

    @Test fun staleReviewCannotStartPublicPreparationOrReadKeys() = runBlocking<Unit> {
        for (fee in listOf(false, true)) {
            val h = Harness(); h.current = false
            val error = runCatching { if (fee) h.fee() else h.submit() }.exceptionOrNull()
            assertTrue(error is CancellationException)
            verifyNoInteractions(h.wallets, h.provider, h.factory, h.builder, h.prepared, h.rpc)
        }
    }

    @Test fun revocationDuringMetadataPreventsBuilderKeyAndSign() = runBlocking<Unit> {
        val h = Harness(); var forms = 0
        whenever(h.factory.createForAuthorizedSubmit(any(), any(), any(), anyOrNull(), anyOrNull())).doSuspendableAnswer {
            yield(); h.current = false; AuthorizedExtrinsicBuilder(h.builder, null)
        }
        assertTrue(runCatching { h.submit { forms++ } }.exceptionOrNull() is CancellationException)
        assertEquals(0, forms)
        verify(h.provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
        verifyNoInteractions(h.prepared, h.rpc)
    }

    @Test fun revocationDuringScenarioBuilderPreventsKeyReadAndSign() = runBlocking<Unit> {
        val h = Harness()
        assertTrue(runCatching { h.submit { yield(); h.current = false } }.exceptionOrNull() is CancellationException)
        verify(h.provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
        verifyNoInteractions(h.prepared, h.rpc)
    }

    @Test fun revocationDuringAuthorizedKeyLookupPreventsSigning() = runBlocking<Unit> {
        val h = Harness()
        whenever(h.provider.getAuthorizedKeypairFor(any(), any(), any(), any())).doSuspendableAnswer {
            yield(); h.current = false; h.key
        }
        assertTrue(runCatching { h.submit() }.exceptionOrNull() is CancellationException)
        verify(h.prepared, never()).sign(any(), any())
        verifyNoInteractions(h.rpc)
    }

    @Test fun revocationAtSigningPrimitivePreventsTransport() = runBlocking<Unit> {
        val h = Harness()
        whenever(h.prepared.sign(any(), any())).thenAnswer { call ->
            h.current = false
            val signer = call.getArgument<(MultiChainEncryption, ByteArray, Keypair) -> SignatureWrapper>(1)
            signer(MultiChainEncryption.Substrate(EncryptionType.ED25519), ByteArray(32), h.key)
            "0x0102"
        }
        assertTrue(runCatching { h.submit() }.exceptionOrNull() is CancellationException)
        verifyNoInteractions(h.rpc)
    }

    @Test fun revocationAtTransportAdmissionPreventsWrite() = runBlocking<Unit> {
        val h = Harness(); var writes = 0
        whenever(h.rpc.submitAuthorizedExtrinsic(any(), any(), any(), any())).doSuspendableAnswer { call ->
            yield(); h.current = false
            call.getArgument<MutationExecutionGuard>(2).runSubmissionIfAuthorized(call.getArgument(3)) { writes++ }
            "fixture-hash"
        }
        assertTrue(runCatching { h.submit() }.exceptionOrNull() is CancellationException)
        assertEquals(0, writes)
    }

    @Test fun serviceCancellationIsRethrownWithExactReasonAndOrdinaryFailureIsRetained() = runBlocking<Unit> {
        for (error in listOf(ReadCancelled(Any()), IllegalStateException("fixture failed"))) {
            val h = Harness()
            whenever(h.provider.getAuthorizedKeypairFor(any(), any(), any(), any())).thenThrow(error)
            if (error is CancellationException) assertSame(error, runCatching { h.submit() }.exceptionOrNull())
            else assertSame(error, h.submit().exceptionOrNull())
            verify(h.prepared, never()).sign(any(), any())
            verifyNoInteractions(h.rpc)
        }
    }

    @Test fun feeUsesCapturedWalletMetadataAndNeverRequestsASecret() = runBlocking<Unit> {
        val h = Harness()
        assertEquals(BigInteger.ONE, h.fee())
        verify(h.wallets).forWallet(42L)
        verify(h.factory).createForFee(eq(h.chain), argThat { contentEquals(h.account) }, eq(h.provider), anyOrNull(), anyOrNull())
        verifyNoInteractions(h.provider)
        verify(h.rpc).estimateExtrinsicFee("fixture", "0x0102")
        verify(h.rpc, never()).submitAuthorizedExtrinsic(any(), any(), any(), any())
    }

    @Test fun staleFeeCompletionCannotBeReturnedAndCancellationRetainsIdentity() = runBlocking<Unit> {
        val h = Harness()
        whenever(h.rpc.estimateExtrinsicFee(any(), any())).doSuspendableAnswer { yield(); h.current = false; BigInteger.ONE }
        assertTrue(runCatching { h.fee() }.exceptionOrNull() is CancellationException)
        val other = Harness(); val reason = ReadCancelled(Any())
        whenever(other.rpc.estimateExtrinsicFee(any(), any())).thenThrow(reason)
        assertSame(reason, runCatching { other.fee() }.exceptionOrNull())
    }
}
