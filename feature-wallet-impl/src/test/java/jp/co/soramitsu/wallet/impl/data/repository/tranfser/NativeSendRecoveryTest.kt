package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.bitcoin.*
import jp.co.soramitsu.common.data.network.solana.*
import jp.co.soramitsu.common.data.network.send.*
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.bitcoinChain
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.bitcoinMetaAccount
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.solanaChain
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.solanaMetaAccount
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.MAINNET_ADDRESS
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.MAINNET_RECIPIENT
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.SOLANA_ADDRESS
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest.Companion.SOLANA_RECIPIENT
import jp.co.soramitsu.testshared.any
import jp.co.soramitsu.testshared.whenever
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anySet

class NativeSendRecoveryTest {
    @Test fun `restart recovery displays original payment and retry broadcasts only original bytes`() = runTest {
        val f = Fixture(); f.initialize()
        val original = f.journal.retain(f.scope, f.payment())
        val handle = requireNotNull(f.recovery.peek(f.chain.id, 1))
        assertEquals(original.reviewed, handle.reviewed); assertEquals("282", handle.feeUnits)
        val retried = f.recovery.refresh(handle, true)
        assertEquals(PendingWalletSend.State.Pending, retried.state)
        verify(f.bitcoin).broadcastTransaction(original.signedPayload, BitcoinIndexerRoutes.Network.Mainnet, f.chain.externalApi!!.history!!.url)
        assertEquals(original, WalletSendJournal(f.preferences).read(f.scope))
        verify(f.accounts, never()).getSubstrateSecrets(1)
    }

    @Test fun `confirmed status settles retained bytes and only matching app history row`() = runTest {
        val f = Fixture(); f.initialize(); val original = f.journal.retain(f.scope, f.payment())
        val operation = OperationLocal(original.transactionId, MAINNET_ADDRESS, f.chain.id, "BTC", 1,
            OperationLocal.Status.PENDING, OperationLocal.Source.APP, OperationLocal.Type.TRANSFER)
        whenever(f.operations.getOperation(original.transactionId)).thenReturn(operation)
        whenever(f.bitcoin.transactionStatus(any(), any(), any())).thenReturn(BitcoinEsploraTxStatus(true))
        assertEquals(PendingWalletSend.State.Confirmed, f.recovery.refresh(requireNotNull(f.recovery.peek(f.chain.id, 1)), true).state)
        verify(f.operations).insert(operation.copy(status = OperationLocal.Status.COMPLETED))
        verify(f.bitcoin, never()).broadcastTransaction(any(), any(), any())
        assertEquals(original.signedPayload, f.journal.read(f.scope)?.signedPayload)
        reset(f.operations)
        whenever(f.operations.getOperation(original.transactionId)).thenReturn(operation.copy(address = "other"))
        f.recovery.refresh(requireNotNull(f.recovery.peek(f.chain.id, 1)), false)
        verify(f.operations, never()).insert(any<OperationLocal>())
    }

    @Test fun `wallet A B A during status check prevents retry and a stale dialog cannot act`() = runTest {
        val f = Fixture(); f.initialize(); val original = f.journal.retain(f.scope, f.payment())
        val handle = requireNotNull(f.recovery.peek(f.chain.id, 1))
        whenever(f.bitcoin.transactionStatus(any(), any(), any())).thenAnswer {
            repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }; null
        }
        val failure = runCatching { f.recovery.refresh(handle, true) }.exceptionOrNull()
        assertEquals(NativeSendException.Code.WALLET_CHANGED, (failure as NativeSendException).code)
        assertEquals(original, f.journal.read(f.scope))
        verify(f.bitcoin, never()).broadcastTransaction(any(), any(), any())
        assertTrue(runCatching { f.recovery.refresh(handle, false) }.isFailure)
    }

    @Test fun `view only and remote pause permit status but forbid explicit retry`() = runTest {
        val f = Fixture(); f.initialize(); f.journal.retain(f.scope, f.payment())
        val handle = requireNotNull(f.recovery.peek(f.chain.id, 1))
        WalletViewOnlyAccess.enter()
        try {
            assertEquals(PendingWalletSend.State.Pending, f.recovery.refresh(handle, false).state)
            assertTrue(runCatching { f.recovery.refresh(handle, true) }.isFailure)
        } finally { WalletViewOnlyAccess.pinResetCompleted() }
        val paused = NativeSendRecovery(f.accounts, f.registry, f.bitcoin, f.solana, f.preferences, f.operations,
            SendPausePolicy({ false }, { false }) { SendPausedException("Paused", "Paused") })
        assertTrue(runCatching { paused.refresh(handle, true) }.exceptionOrNull() is SendPausedException)
        verify(f.bitcoin, never()).broadcastTransaction(any(), any(), any())
    }

    @Test fun `corrupt journal remains intact and never exposes a retry handle`() = runTest {
        val f = Fixture(); f.initialize(); f.journal.retain(f.scope, f.payment())
        val key = f.values.keys.single(); f.values[key] = "corrupt"
        assertTrue(runCatching { f.recovery.peek(f.chain.id, 1) }.exceptionOrNull() is WalletSendJournalException)
        assertEquals("corrupt", f.values[key]); verifyNoInteractions(f.bitcoin)
    }

    @Test fun `Solana expiry with unknown outcome preserves exact payment and never replaces blockhash`() = runTest {
        val f = Fixture(true); f.initialize(); val original = f.journal.retain(f.scope, f.payment())
        whenever(f.solana.blockHeight(any(), any())).thenReturn(101)
        val result = f.recovery.refresh(requireNotNull(f.recovery.peek(f.chain.id, 1)), true)
        verify(f.solana).withGenesisHash(jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH)
        assertTrue(result.expired); assertEquals(PendingWalletSend.State.Pending, result.state)
        assertEquals(original, f.journal.read(f.scope))
        verify(f.solana, never()).sendRawTransaction(any(), any(), any())
        verify(f.solana, never()).latestBlockhash(any(), any())
    }

    @Test fun `Solana alias network replacement cannot query or retry the retained payment`() = runTest {
        val f = Fixture(true, true); f.initialize(); f.journal.retain(f.scope, f.payment())
        val handle = requireNotNull(f.recovery.peek(f.chain.id, 1)); val durable = f.values.toMap()
        f.currentChain = f.chain.copy(isTestNet = true)
        val failure = runCatching { f.recovery.refresh(handle, true) }.exceptionOrNull()
        assertTrue("same-id alias replacement must reject old recovery", failure is NativeSendException)
        assertEquals(durable, f.values)
        verify(f.solana, never()).signatureStatus(any(), any())
        verify(f.solana, never()).sendRawTransaction(any(), any(), any())
    }

    @Test fun `Solana registry replacement during held status cannot settle the old payment`() = runTest {
        val f = Fixture(true, true); f.initialize(); f.journal.retain(f.scope, f.payment())
        val entered = CompletableDeferred<Unit>(); val reply = CompletableDeferred<SolanaRpcSignatureStatus?>()
        val client = object : SolanaRpcClient by f.solana {
            override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient = this
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient = this
            override suspend fun signatureStatus(signature: String, rpcUrl: String?): SolanaRpcSignatureStatus? {
                entered.complete(Unit); return reply.await()
            }
        }
        val recovery = NativeSendRecovery(f.accounts, f.registry, f.bitcoin, client, f.preferences, f.operations, SendPausePolicy.Unrestricted)
        val handle = requireNotNull(recovery.peek(f.chain.id, 1)); val durable = f.values.toMap()
        val work = async { runCatching { recovery.refresh(handle, false) } }
        entered.await(); f.currentChain = f.chain.copy(isTestNet = true)
        reply.complete(SolanaRpcSignatureStatus(slot = 1, confirmations = null, errorJson = null, confirmationStatus = "finalized"))
        assertTrue("late result cannot settle after registry identity replacement", work.await().exceptionOrNull() is NativeSendException)
        assertEquals(durable, f.values)
    }

    @Test fun `legacy Solana journals stay visible byte preserved and cannot infer network or explorer`() = runTest {
        for (version in listOf("1", "2")) {
            val f = Fixture(true, true); f.initialize(); f.journal.retain(f.scope, f.payment())
            val key = f.values.keys.single(); val fields = f.values.getValue(key).split('\n')
            f.values[key] = (listOf(version) + fields.drop(1).take(11) + if (version == "2") fields.drop(14) else emptyList()).joinToString("\n")
            val durable = f.values.toMap(); val handle = requireNotNull(f.recovery.peek(f.chain.id, 1))
            assertFalse(handle.networkAvailable)
            assertEquals("1".repeat(64), handle.transactionId)
            assertTrue(runCatching { f.recovery.refresh(handle, false) }.exceptionOrNull() is NativeSendException)
            assertTrue(runCatching { f.recovery.refresh(handle, true) }.exceptionOrNull() is NativeSendException)
            assertTrue(runCatching { f.recovery.explorerChain(handle) }.exceptionOrNull() is NativeSendException)
            assertEquals(durable, f.values); verifyNoInteractions(f.solana)
        }
    }

    @Test fun `same genesis provider change permits recovery and verified explorer while changed genesis does not`() = runTest {
        val f = Fixture(true, true); f.initialize(); f.journal.retain(f.scope, f.payment())
        val handle = requireNotNull(f.recovery.peek(f.chain.id, 1))
        assertTrue(handle.networkAvailable)
        f.currentChain = f.chain.copy(name = "Updated provider catalog")
        assertEquals(PendingWalletSend.State.Pending, f.recovery.refresh(handle, false).state)
        assertEquals(f.currentChain, f.recovery.explorerChain(handle))
        f.currentChain = f.chain.copy(isTestNet = true)
        assertFalse(requireNotNull(f.recovery.peek(f.chain.id, 1)).networkAvailable)
        assertTrue(runCatching { f.recovery.explorerChain(handle) }.exceptionOrNull() is NativeSendException)
    }

    @Test fun `explicit execution genesis survives recovery without replacing registry identity`() = runTest {
        val f = Fixture(true, true); f.initialize()
        val privateGenesis = "EdP1HZdRNKaMDpiNM4suXQb4RsGZK5sCeZoPQYoJvRTL"
        val payment = f.payment().copy(solanaNetwork = f.payment().solanaNetwork!!.copy(executionGenesisHash = privateGenesis))
        f.journal.retain(f.scope, payment)
        val handle = requireNotNull(f.recovery.peek(f.chain.id, 1))
        assertTrue(handle.networkAvailable)
        f.recovery.refresh(handle, false)
        verify(f.solana).withGenesisHash(privateGenesis)
        assertTrue(runCatching { f.recovery.explorerChain(handle) }.exceptionOrNull() is NativeSendException)
        assertEquals(payment, f.journal.read(f.scope))
    }

    @Test fun `registry replacement during held expiry read prevents retry without changing bytes`() = runTest {
        val f = Fixture(true, true); f.initialize(); f.journal.retain(f.scope, f.payment())
        val entered = CompletableDeferred<Unit>(); val reply = CompletableDeferred<Long>()
        val client = object : SolanaRpcClient by f.solana {
            override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient = this
            override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient = this
            override suspend fun blockHeight(commitment: SolanaRpcCommitment, rpcUrl: String?): Long {
                entered.complete(Unit); return reply.await()
            }
        }
        val recovery = NativeSendRecovery(f.accounts, f.registry, f.bitcoin, client, f.preferences, f.operations, SendPausePolicy.Unrestricted)
        val handle = requireNotNull(recovery.peek(f.chain.id, 1)); val durable = f.values.toMap()
        val work = async { runCatching { recovery.refresh(handle, true) } }
        entered.await(); f.currentChain = f.chain.copy(isTestNet = true); reply.complete(1)
        assertTrue(work.await().exceptionOrNull() is NativeSendException)
        verify(f.solana, never()).sendRawTransaction(any(), any(), any()); assertEquals(durable, f.values)
    }

    private class Fixture(val isSolana: Boolean = false, alias: Boolean = false) {
        val chain = (if (isSolana) solanaChain() else bitcoinChain()).let { original ->
            if (alias) original.copy(id = "solana-audit-alias", assets = original.assets.map { it.copy(chainId = "solana-audit-alias") }) else original
        }
        var currentChain = chain
        val wallet = if (isSolana) solanaMetaAccount(chain) else bitcoinMetaAccount(chain)
        val scope = "1:${chain.id.length}:${chain.id}"
        val values = mutableMapOf<String, String>()
        val preferences = mock(Preferences::class.java).also { p ->
            whenever(p.getString(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            whenever(p.replaceStringsDurably(anyMap(), anySet())).thenAnswer { values.putAll(it.getArgument<Map<String, String>>(0)); true }
        }
        val accounts = mock(AccountRepository::class.java)
        val registry = mock(ChainRegistry::class.java)
        val bitcoin = mock(BitcoinIndexerClient::class.java)
        val solana = mock(SolanaRpcClient::class.java)
        val operations = mock(OperationDao::class.java)
        val journal = WalletSendJournal(preferences)
        val recovery = NativeSendRecovery(accounts, registry, bitcoin, solana, preferences, operations, SendPausePolicy.Unrestricted)
        suspend fun initialize() {
            whenever(accounts.getSelectedMetaAccount()).thenReturn(wallet)
            whenever(registry.getChain(chain.id)).thenAnswer { currentChain }
            whenever(bitcoin.broadcastTransaction(any(), any(), any())).thenReturn("a".repeat(64))
            whenever(solana.withGenesisHash(any())).thenReturn(solana)
            whenever(solana.withEndpoints(any())).thenReturn(solana)
        }
        fun payment() = PendingWalletSend(WalletSendJournal.scopeHash(scope), "11111111-1111-4111-8111-111111111111", "b".repeat(64),
            if (isSolana) PendingWalletSend.Kind.Solana else PendingWalletSend.Kind.Bitcoin,
            if (isSolana) "1".repeat(64) else "a".repeat(64), if (isSolana) "AQID" else "00aa", "282", "0",
            if (isSolana) 100 else null, 1234, reviewed = ReviewedWalletSend(chain.id, chain.name, if (isSolana) "SOL" else "BTC", null,
                if (isSolana) "SOL" else "BTC", if (isSolana) 9 else 8, if (isSolana) SOLANA_ADDRESS else MAINNET_ADDRESS,
                if (isSolana) SOLANA_RECIPIENT else MAINNET_RECIPIENT, "50000"),
            solanaNetwork = if (isSolana) SolanaSendNetworkIdentity(
                jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH,
                jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH) else null)
    }
}
