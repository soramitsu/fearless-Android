package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
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
                whenever(factory.createForAuthorizedSubmit(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(builder)
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
    private class TestGuard : MutationExecutionGuard {
        var allowed = true
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
            check(allowed)
            return operation()
        }
    }
}
