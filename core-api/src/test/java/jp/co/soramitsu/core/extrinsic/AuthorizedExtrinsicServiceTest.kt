package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.PreparedExtrinsic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class AuthorizedExtrinsicServiceTest {
    @Test fun `wallet bound fee rejects an unscoped provider before preparation`() = runBlocking {
        val h = Harness()
        val result = runCatching { h.service.estimateFeeForWallet(h.chain, h.account, 42) {} }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        verifyNoInteractions(h.provider, h.factory, h.rpc, h.prepared)
    }
    @Test fun `wallet bound submission rejects an unscoped provider before preparation`() = runBlocking {
        val h = Harness()
        assertTrue(h.service.submitAuthorizedExtrinsicForWallet(h.chain, h.account, 42, h::authorize) {}.isFailure)
        verifyNoInteractions(h.provider, h.factory, h.rpc, h.prepared)
    }
    @Test fun `wallet bound submission uses only the requested provider through signing and send`() = runBlocking<Unit> {
        val h = Harness()
        val wallets = mock<WalletBoundKeypairProvider>()
        whenever(wallets.forWallet(42)).thenReturn(h.provider)
        val result = ExtrinsicService(h.rpc, wallets, h.factory)
            .submitAuthorizedExtrinsicForWallet(h.chain, h.account, 42, h::authorize) {}
        assertEquals("hash", result.getOrThrow())
        verify(wallets).forWallet(42)
        verifyNoMoreInteractions(wallets)
        verify(h.factory).createForAuthorizedSubmit(eq(h.chain), any(), eq(h.provider), anyOrNull(), anyOrNull())
        verify(h.provider).getAuthorizedKeypairFor(eq(h.chain), any(), eq(h.guard), eq(h.intent))
        verify(h.prepared).sign(eq(h.key), any())
        verify(h.rpc).submitAuthorizedExtrinsic("chain", "0xsigned", h.guard, h.intent)
    }
    @Test fun `wallet binding failure is returned without preparing a transaction`() = runBlocking {
        val h = Harness()
        val wallets = mock<WalletBoundKeypairProvider>()
        val failure = IllegalStateException("No local wallet binding")
        whenever(wallets.forWallet(42)).thenThrow(failure)
        val result = ExtrinsicService(h.rpc, wallets, h.factory)
            .submitAuthorizedExtrinsicForWallet(h.chain, h.account, 42, h::authorize) {}
        assertSame(failure, result.exceptionOrNull())
        verifyNoInteractions(h.provider, h.factory, h.rpc, h.prepared)
    }
    @Test fun `missing guarded provider and denied authority never access keys or sign`() = runBlocking {
        val h = Harness()
        assertTrue(ExtrinsicService(h.rpc, mock<KeypairProvider>(), h.factory)
            .submitAuthorizedExtrinsic(h.chain, h.account, h::authorize) {}.isFailure)
        h.guard.allowed = false
        assertTrue(h.submit().isFailure)
        verify(h.provider, never()).getAuthorizedKeypairFor(any(), any(), any(), any())
        verify(h.prepared, never()).sign(any(), any())
        verifyNoInteractions(h.rpc)
    }
    @Test fun `revocation while key provider suspends prevents signing`() = runBlocking {
        val h = Harness()
        whenever(h.provider.getAuthorizedKeypairFor(any(), any(), any(), any())).doSuspendableAnswer {
            h.guard.allowed = false
            h.key
        }
        assertTrue(h.submit().isFailure)
        verify(h.prepared, never()).sign(any(), any())
        verifyNoInteractions(h.rpc)
    }
    @Test fun `expiry during payload preparation is rechecked at the signing primitive`() = runBlocking {
        val h = Harness()
        whenever(h.prepared.sign(any(), any())).thenAnswer { call ->
            h.guard.allowed = false
            val signer = call.getArgument<(jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption,
                ByteArray, jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair) ->
                jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper>(1)
            signer(jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption.Substrate(
                jp.co.soramitsu.fearless_utils.encrypt.EncryptionType.ED25519), ByteArray(32), h.key)
            "0xsigned"
        }
        assertTrue(h.submit().isFailure)
        verifyNoInteractions(h.rpc)
    }
    @Test fun `revocation after signing prevents actual transport handoff`() = runBlocking {
        val h = Harness()
        var broadcasts = 0
        whenever(h.rpc.submitAuthorizedExtrinsic(any(), any(), any(), any())).doSuspendableAnswer { call ->
            h.guard.allowed = false
            call.getArgument<MutationExecutionGuard>(2).runIfAuthorized(call.getArgument(3)) { broadcasts++ }
            "hash"
        }
        assertTrue(h.submit().isFailure)
        verify(h.prepared).sign(any(), any())
        assertEquals(0, broadcasts)
    }
    @Test fun `valid lease is reused for one key read one sign and guarded RPC`() = runBlocking {
        val h = Harness()
        assertEquals("hash", h.submit().getOrThrow())
        verify(h.provider).getAuthorizedKeypairFor(eq(h.chain), any(), eq(h.guard), eq(h.intent))
        verify(h.prepared).sign(eq(h.key), any())
        verify(h.rpc).submitAuthorizedExtrinsic("chain", "0xsigned", h.guard, h.intent)
        verify(h.provider, never()).getKeypairFor(any(), any())
        whenever(h.provider.hasLocalSubstrateKey(h.chain, h.account)).thenReturn(true)
        assertTrue(h.service.hasLocalSubstrateKey(h.chain, h.account))
    }
    @Test fun `intent digest binds chain sender and complete frozen payload`() {
        val original = mutationIntentSha256("chain", byteArrayOf(1), byteArrayOf(2))
        assertEquals(original, mutationIntentSha256("chain", byteArrayOf(1), byteArrayOf(2)))
        assertNotEquals(original, mutationIntentSha256("other", byteArrayOf(1), byteArrayOf(2)))
        assertNotEquals(original, mutationIntentSha256("chain", byteArrayOf(3), byteArrayOf(2)))
        assertNotEquals(original, mutationIntentSha256("chain", byteArrayOf(1), byteArrayOf(4)))
        assertNotEquals(mutationIntentSha256("chain", byteArrayOf(1, 2), byteArrayOf(3)),
            mutationIntentSha256("chain", byteArrayOf(1), byteArrayOf(2, 3)))
    }
    @Test fun `tracked submission validates local hash before acknowledgement and always finishes`() = runBlocking<Unit> {
        for (responseMatches in listOf(true, false)) {
            val h = Harness()
            val wallets = mock<WalletBoundKeypairProvider>()
            whenever(wallets.forWallet(42)).thenReturn(h.provider)
            val signed = "0x01020304"
            val expectedHash = signed.fromHex().blake2b256().toHexString(withPrefix = true)
            whenever(h.prepared.sign(any(), any())).thenReturn(signed)
            val order = mutableListOf<String>()
            val retainedGuard = TestGuard()
            val tracking = mock<ExtrinsicSubmissionTracking> {
                on { validity(any()) } doAnswer {
                    assertEquals(VALIDITY, it.getArgument<ExtrinsicValidity>(0)); order += "validity"; Unit
                }
                on { guard(any(), any(), any()) } doAnswer {
                    assertEquals(signed, it.getArgument<String>(0))
                    assertSame(h.guard, it.getArgument<MutationExecutionGuard>(2))
                    order += "retained"
                    retainedGuard
                }
                on { acknowledged(any()) } doAnswer {
                    assertEquals(expectedHash, it.getArgument<String>(0)); order += "acknowledged"; Unit
                }
                on { finished() } doAnswer { order += "finished"; Unit }
            }
            whenever(h.rpc.submitAuthorizedExtrinsic(any(), any(), any(), any())).doSuspendableAnswer {
                assertSame(retainedGuard, it.getArgument<MutationExecutionGuard>(2))
                order += "rpc"
                if (responseMatches) expectedHash else "0x" + "ff".repeat(32)
            }
            val result = ExtrinsicService(h.rpc, wallets, h.factory)
                .submitTrackedExtrinsicForWallet(h.chain, h.account, 42, h::authorize, tracking) {}
            if (responseMatches) {
                assertEquals(expectedHash, result.getOrThrow())
                assertEquals(listOf("validity", "retained", "rpc", "acknowledged", "finished"), order)
            } else {
                assertTrue(result.isFailure)
                assertEquals(listOf("validity", "retained", "rpc", "finished"), order)
            }
        }
    }

    @Test fun `tracking failure prevents RPC and seals the attempt`() = runBlocking<Unit> {
        val h = Harness()
        val wallets = mock<WalletBoundKeypairProvider>()
        whenever(wallets.forWallet(42)).thenReturn(h.provider)
        val tracking = mock<ExtrinsicSubmissionTracking>()
        whenever(tracking.guard(any(), any(), any())).thenThrow(IllegalStateException("local durable write rejected"))
        val result = ExtrinsicService(h.rpc, wallets, h.factory)
            .submitTrackedExtrinsicForWallet(h.chain, h.account, 42, h::authorize, tracking) {}
        assertTrue(result.isFailure)
        verifyNoInteractions(h.rpc)
        verify(tracking).finished()
        verify(tracking, never()).acknowledged(any())
    }

    @Test fun `tracked RPC cancellation finishes without acknowledging`() = runBlocking<Unit> {
        val h = Harness()
        val wallets = mock<WalletBoundKeypairProvider>()
        whenever(wallets.forWallet(42)).thenReturn(h.provider)
        whenever(h.prepared.sign(any(), any())).thenReturn("0x01020304")
        val tracking = mock<ExtrinsicSubmissionTracking>()
        whenever(tracking.guard(any(), any(), any())).thenReturn(h.guard)
        whenever(h.rpc.submitAuthorizedExtrinsic(any(), any(), any(), any()))
            .thenThrow(kotlinx.coroutines.CancellationException("local lost response"))
        val result = ExtrinsicService(h.rpc, wallets, h.factory)
            .submitTrackedExtrinsicForWallet(h.chain, h.account, 42, h::authorize, tracking) {}
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        verify(tracking).finished()
        verify(tracking, never()).acknowledged(any())
    }

    private class Harness {
        val chain = mock<IChain> { on { id } doReturn "chain" }
        val account = byteArrayOf(1)
        val rpc = mock<RpcCalls>()
        val factory = mock<ExtrinsicBuilderFactory>()
        val builder = mock<ExtrinsicBuilder>()
        val prepared = mock<PreparedExtrinsic>()
        val provider = mock<GuardedKeypairProvider>()
        val key = BaseKeypair(privateKey = ByteArray(32), publicKey = ByteArray(32))
        val guard = TestGuard()
        var intent = ""
        val service = ExtrinsicService(rpc, provider, factory)
        init {
            runBlocking {
                whenever(factory.createForAuthorizedSubmit(any(), any(), any(), anyOrNull(), anyOrNull()))
                    .thenReturn(AuthorizedExtrinsicBuilder(builder, VALIDITY))
                whenever(provider.getAuthorizedKeypairFor(any(), any(), any(), any())).thenReturn(key)
                whenever(rpc.submitAuthorizedExtrinsic(any(), any(), any(), any())).thenReturn("hash")
            }
            whenever(builder.prepareSigning(any())).thenReturn(prepared)
            whenever(prepared.intentBytes()).thenReturn(byteArrayOf(9))
            whenever(prepared.sign(any(), any())).thenReturn("0xsigned")
        }
        fun authorize(intent: String): MutationExecutionGuard { guard.check(intent); this.intent = intent; return guard }
        suspend fun submit() = service.submitAuthorizedExtrinsic(chain, account, ::authorize) {}
    }
    private companion object {
        val VALIDITY = ExtrinsicValidity(java.math.BigInteger.valueOf(1_000), 64)
    }

    private class TestGuard : MutationExecutionGuard {
        var allowed = true
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
            check(allowed)
            return operation()
        }
    }
}
