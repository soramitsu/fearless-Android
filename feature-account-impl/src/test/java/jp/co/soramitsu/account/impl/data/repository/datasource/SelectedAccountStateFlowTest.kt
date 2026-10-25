package jp.co.soramitsu.account.impl.data.repository.datasource

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.impl.data.repository.AccountRepositoryImpl
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Actual datasource and repository methods; the mapper and MetaAccount/accountId are not mocked. */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectedAccountStateFlowTest {
    @Test fun initialSubscriptionRevokesBeforeLocalOrCatalogPublishes() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            runCurrent()
            assertEquals(listOf<MetaAccount?>(null), p.values)
            assertEquals(0, f.catalogs.size)
            assertFalse(p.job.isCompleted)
            p.job.cancelAndJoin()
            assertTrue(f.selectionClosed.isCompleted)
        }
    }

    @Test fun nullLocalSelectionDoesNotSubscribeCatalog() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(null)
            runCurrent()
            assertEquals(listOf<MetaAccount?>(null, null), p.values)
            assertEquals(0, f.catalogs.size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun readySelectionIsRevokedOnRemovalWhileCatalogSilent() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            f.catalogs.single().values.send(emptyMap())
            runCurrent()
            assertEquals(1L, p.values.last()!!.id)
            f.local.send(null)
            runCurrent()
            assertNull(p.values.last())
            assertEquals(listOf(null, null, 1L, null), p.values.map { it?.id })
            assertTrue(f.catalogs.single().closed.isCompleted)
            p.job.cancelAndJoin()
        }
    }

    @Test fun replacementRevokesBeforeItsDelayedCatalogCompletes() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            f.catalogs[0].values.send(emptyMap())
            runCurrent()
            f.local.send(local(2))
            runCurrent()
            assertNull(p.values.last())
            assertTrue(f.catalogs[0].closed.isCompleted)
            assertEquals(2, f.catalogs.size)
            assertEquals(listOf(1L), p.values.filterNotNull().map { it.id })
            f.catalogs[1].values.send(emptyMap())
            runCurrent()
            assertEquals(listOf(null, null, 1L, null, 2L), p.values.map { it?.id })
            p.job.cancelAndJoin()
        }
    }

    @Test fun pendingReplacementIsCancelledAndCannotPublishAfterNull() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            f.local.send(null)
            runCurrent()
            val pending = f.catalogs.single()
            assertTrue(pending.closed.isCompleted)
            pending.values.send(emptyMap()) // An obsolete storage callback has no collector now.
            runCurrent()
            assertTrue(p.values.all { it == null })
            assertEquals(1, f.catalogs.size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun sameWalletTonIdentityChangeIsNotSuppressedByDomainEquality() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(7, ton = 1))
            runCurrent()
            f.catalogs[0].values.send(emptyMap())
            runCurrent()
            val first = p.values.last()!!
            f.local.send(local(7, ton = 2))
            runCurrent()
            assertNull(p.values.last())
            f.catalogs[1].values.send(emptyMap())
            runCurrent()
            val second = p.values.last()!!
            assertNotSame(first, second)
            assertEquals(first, second) // Existing MetaAccount.equals omits TON; it is not altered here.
            assertArrayEquals(byteArrayOf(1), first.accountId(chainIdentity()))
            assertArrayEquals(byteArrayOf(2), second.accountId(chainIdentity()))
            assertEquals(2, p.values.filterNotNull().size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun chainOverrideReplacementAndRemovalUpdateCurrentAccount() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            for ((index, overrideByte) in listOf(31, 32, null).withIndex()) {
                f.local.send(local(7, ton = 10, overrideByte = overrideByte))
                runCurrent()
                assertNull(p.values.last())
                f.catalogs[index].values.send(emptyMap())
                runCurrent()
                val ready = p.values.last()!!
                assertArrayEquals(byteArrayOf((overrideByte ?: 10).toByte()), ready.accountId(chainIdentity()))
                assertEquals(overrideByte != null, ready.chainAccounts.containsKey(CHAIN_ID))
            }
            assertEquals(3, p.values.filterNotNull().size)
            assertTrue(f.catalogs.take(2).all { it.closed.isCompleted })
            p.job.cancelAndJoin()
        }
    }

    @Test fun completedEmptyCatalogPreservesIdentityAndLaterHydratesCurrentChainMetadata() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(7, overrideByte = 31))
            runCurrent()
            val catalog = f.catalogs.single()
            catalog.values.send(emptyMap())
            runCurrent()
            assertNull(p.values.last()!!.chainAccounts.getValue(CHAIN_ID).chain)
            assertArrayEquals(byteArrayOf(31), p.values.last()!!.accountId(chainIdentity()))
            val currentChain = mockk<Chain>()
            catalog.values.send(mapOf(CHAIN_ID to currentChain))
            runCurrent()
            assertSame(currentChain, p.values.last()!!.chainAccounts.getValue(CHAIN_ID).chain)
            assertEquals(1, f.catalogs.size)
            assertEquals(2, p.values.filterNotNull().size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun catalogFailurePropagatesAfterRevocationWithoutRestoringOldAccount() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            f.catalogs[0].values.send(emptyMap())
            runCurrent()
            f.local.send(local(2))
            runCurrent()
            val failure = StorageFailure(Any())
            f.catalogs[1].values.close(failure)
            runCurrent()
            assertSame(failure, p.failure.await())
            assertEquals(listOf(null, null, 1L, null), p.values.map { it?.id })
            assertTrue(f.selectionClosed.isCompleted)
            p.job.join()
        }
    }

    @Test fun mapperFailurePropagatesWithoutPublishingReplacement() = runTest {
        withFixture { f ->
            val failure = StorageFailure(Any())
            val damaged = mockk<RelationJoinedMetaAccountInfo> { every { chainAccounts } throws failure }
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(damaged)
            runCurrent()
            f.catalogs.single().values.send(emptyMap())
            runCurrent()
            assertSame(failure, p.failure.await())
            assertTrue(p.values.all { it == null })
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertTrue(f.selectionClosed.isCompleted)
            p.job.join()
        }
    }

    @Test fun localSelectionFailurePropagatesAndCancelsCurrentCatalog() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            val failure = StorageFailure(Any())
            f.local.close(failure)
            runCurrent()
            assertSame(failure, p.failure.await())
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertTrue(p.values.all { it == null })
            p.job.join()
        }
    }

    @Test fun collectionCancellationClosesLocalAndCatalogWithoutSyntheticRecovery() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            val before = p.values.toList()
            p.job.cancelAndJoin()
            assertTrue(p.job.isCancelled)
            assertTrue(f.selectionClosed.isCompleted)
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertFalse(p.failure.isCompleted)
            assertEquals(before, p.values)
        }
    }

    @Test fun rendezvousHandoffDropsOldQueuedReadyValueWhenSelectionChanges() = runTest {
        withFixture { f ->
            val releaseCaller = CompletableDeferred<Unit>()
            val p = observe(f.source.selectedMetaAccountStateFlow()) { value ->
                if (value?.id == 1L) releaseCaller.await()
            }
            f.local.send(local(1, overrideByte = 31))
            runCurrent()
            f.catalogs[0].values.send(emptyMap())
            runCurrent() // Caller now holds the first account; another old catalog result would queue.
            f.catalogs[0].values.send(mapOf(CHAIN_ID to mockk<Chain>()))
            runCurrent()
            f.local.send(local(2))
            runCurrent()
            assertTrue(f.catalogs[0].closed.isCompleted)
            releaseCaller.complete(Unit)
            runCurrent()
            assertEquals(listOf(null, null, 1L, null), p.values.map { it?.id })
            f.catalogs[1].values.send(emptyMap())
            runCurrent()
            assertEquals(listOf(null, null, 1L, null, 2L), p.values.map { it?.id })
            p.job.cancelAndJoin()
        }
    }

    @Test fun finiteLocalAndCatalogInputsCompleteWithoutPermanentFixtureCollectors() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            f.local.close()
            runCurrent()
            f.catalogs.single().values.send(emptyMap())
            f.catalogs.single().values.close()
            runCurrent()
            p.job.join()
            assertEquals(listOf(null, null, 1L), p.values.map { it?.id })
            assertTrue(f.selectionClosed.isCompleted)
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertFalse(p.failure.isCompleted)
        }
    }

    @Test fun repositoryPassesExactNullableFlowWithoutFilteringOrSharing() = runTest {
        val expected = flowOf<MetaAccount?>(null, null)
        val source = mockk<AccountDataSource>()
        every { source.selectedMetaAccountStateFlow() } returns expected
        val repository = repository(source)
        assertSame(expected, repository.selectedMetaAccountStateFlow())
        assertEquals(listOf<MetaAccount?>(null, null), repository.selectedMetaAccountStateFlow().toList())
        verify(exactly = 0) { source.selectedMetaAccountFlow() }
    }

    @Test fun repositoryCallerSeesReplacementAndAbsenceBeforeCatalogReady() = runTest {
        withFixture { f ->
            val p = observe(repository(f.source).selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            f.catalogs[0].values.send(emptyMap())
            runCurrent()
            f.local.send(local(2))
            runCurrent()
            assertNull(p.values.last())
            f.local.send(null)
            runCurrent()
            assertEquals(listOf(null, null, 1L, null, null), p.values.map { it?.id })
            assertTrue(f.catalogs.all { it.closed.isCompleted })
            p.job.cancelAndJoin()
        }
    }

    @Test fun repositoryPreservesFailureIdentityAndDoesNotFabricateAnAccount() = runTest {
        val source = mockk<AccountDataSource>()
        val failure = StorageFailure(Any())
        every { source.selectedMetaAccountStateFlow() } returns flow<MetaAccount?> {
            emit(null)
            throw failure
        }
        val p = observe(repository(source).selectedMetaAccountStateFlow())
        runCurrent()
        assertSame(failure, p.failure.await())
        assertEquals(listOf<MetaAccount?>(null), p.values)
        p.job.join()
    }

    @Test fun compatibilityNonnullableFlowStillDelegatesToItsOriginalPath() = runTest {
        val expected = flowOf(mockk<MetaAccount>())
        val source = mockk<AccountDataSource>()
        every { source.selectedMetaAccountFlow() } returns expected
        assertSame(expected, repository(source).selectedMetaAccountFlow())
        verify(exactly = 0) { source.selectedMetaAccountStateFlow() }
    }

    @Test fun replacementRevokesBeforeCancelledCatalogCleanupCanFinish() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            val old = f.catalogs.single()
            old.values.send(emptyMap())
            runCurrent()
            val cleanup = CompletableDeferred<Unit>()
            old.cleanupGate = cleanup
            f.local.send(local(2))
            runCurrent()
            assertTrue(old.cleanupEntered.isCompleted)
            assertFalse(old.closed.isCompleted)
            assertEquals(listOf(null, null, 1L, null), p.values.map { it?.id })
            assertEquals(1, f.catalogs.size) // Replacement mapping must wait for old cleanup.
            cleanup.complete(Unit)
            runCurrent()
            assertTrue(old.closed.isCompleted)
            assertEquals(2, f.catalogs.size)
            f.catalogs[1].values.send(emptyMap())
            runCurrent()
            assertEquals(listOf(null, null, 1L, null, 2L), p.values.map { it?.id })
            p.job.cancelAndJoin()
        }
    }

    @Test fun absenceRevokesBeforeCancelledCatalogCleanupCanFinish() = runTest {
        withFixture { f ->
            val p = observe(f.source.selectedMetaAccountStateFlow())
            f.local.send(local(1))
            runCurrent()
            val old = f.catalogs.single()
            old.values.send(emptyMap())
            runCurrent()
            val cleanup = CompletableDeferred<Unit>()
            old.cleanupGate = cleanup
            f.local.send(null)
            runCurrent()
            assertTrue(old.cleanupEntered.isCompleted)
            assertFalse(old.closed.isCompleted)
            assertNull(p.values.last())
            assertEquals(listOf(null, null, 1L, null), p.values.map { it?.id })
            cleanup.complete(Unit)
            runCurrent()
            assertTrue(old.closed.isCompleted)
            assertEquals(1, f.catalogs.size)
            p.job.cancelAndJoin()
        }
    }

    private fun TestScope.observe(
        values: Flow<MetaAccount?>,
        after: suspend (MetaAccount?) -> Unit = {}
    ): Probe {
        val probe = Probe()
        probe.job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                values.collect {
                    probe.values += it
                    after(it)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                probe.failure.complete(failure)
            }
        }
        return probe
    }

    private suspend fun TestScope.withFixture(body: suspend TestScope.(Fixture) -> Unit) {
        val fixture = Fixture()
        // The unchanged eager compatibility collector receives two finite empty inputs first.
        // Await both on a real dispatcher so the virtual scheduler cannot race the GlobalScope job.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                fixture.legacyLocalFinished.await()
                fixture.legacyCatalogFinished.await()
            }
        }
        try {
            body(fixture)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    private class Probe {
        val values = mutableListOf<MetaAccount?>()
        val failure = CompletableDeferred<Throwable>()
        lateinit var job: kotlinx.coroutines.Job
    }

    private class Catalog {
        val values = Channel<Map<String, Chain>>(Channel.UNLIMITED)
        val closed = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        var cleanupGate: CompletableDeferred<Unit>? = null
        fun flow(): Flow<Map<String, Chain>> = flow {
            try {
                emitAll(values.receiveAsFlow())
            } finally {
                cleanupEntered.complete(Unit)
                cleanupGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
                closed.complete(Unit)
            }
        }
    }

    private class Fixture {
        val local = Channel<RelationJoinedMetaAccountInfo?>(Channel.UNLIMITED)
        val catalogs = mutableListOf<Catalog>()
        val selectionClosed = CompletableDeferred<Unit>()
        val legacyLocalFinished = CompletableDeferred<Unit>()
        val legacyCatalogFinished = CompletableDeferred<Unit>()
        private val localSubscriptions = AtomicInteger()
        private val catalogRequests = AtomicInteger()
        private val dao = mockk<MetaAccountDao>()
        private val chains = mockk<ChainsRepository>()
        val source: AccountDataSourceImpl

        init {
            every { dao.selectedMetaAccountInfoFlow() } returns flow {
                if (localSubscriptions.getAndIncrement() == 0) {
                    legacyLocalFinished.complete(Unit)
                } else {
                    try { emitAll(local.receiveAsFlow()) } finally { selectionClosed.complete(Unit) }
                }
            }
            every { chains.chainsByIdFlow() } answers {
                if (catalogRequests.getAndIncrement() == 0) {
                    flow<Map<String, Chain>> { legacyCatalogFinished.complete(Unit) }
                } else {
                    Catalog().also { catalogs += it }.flow()
                }
            }
            source = AccountDataSourceImpl(mockk(), mockk(), Gson(), dao, mockk(), chains)
        }

        fun close() {
            local.close()
            catalogs.forEach {
                it.cleanupGate?.complete(Unit)
                it.values.close()
            }
        }
    }

    /** Extra state disables coroutine exception-copy reconstruction, so identity is meaningful. */
    private class StorageFailure(val marker: Any) : RuntimeException("synthetic-storage-failure")

    private fun repository(source: AccountDataSource) = AccountRepositoryImpl(
        accountDataSource = source,
        metaAccountDao = mockk(),
        legacyStoreV2 = mockk(),
        jsonSeedDecoder = mockk(),
        jsonSeedEncoder = mockk(),
        languagesHolder = mockk(),
        chainsRepository = mockk(),
        nomisScoresDao = mockk(),
        substrateSecretStore = mockk(),
        ethereumSecretStore = mockk(),
        tonSecretStore = mockk(),
        accountRepositoryDelegate = mockk(),
        walletSecretMutationCoordinator = mockk(),
        walletSecretAccessGuard = mockk()
    )

    private fun chainIdentity(): IChain = mockk {
        every { id } returns CHAIN_ID
        every { ecosystem } returns Ecosystem.Ton
    }

    private fun local(id: Long, ton: Int = 10, overrideByte: Int? = null): RelationJoinedMetaAccountInfo {
        val account = MetaAccountLocal(
            substratePublicKey = null, substrateCryptoType = CryptoType.SR25519, substrateAccountId = null,
            ethereumPublicKey = null, ethereumAddress = null, tonPublicKey = byteArrayOf(ton.toByte()),
            name = "wallet$id", isSelected = true, position = 0, isBackedUp = true,
            googleBackupAddress = null, initialized = true
        ).also { it.id = id }
        val chainAccounts = overrideByte?.let {
            listOf(ChainAccountLocal(id, CHAIN_ID, byteArrayOf(it.toByte()), byteArrayOf(it.toByte()),
                CryptoType.ED25519, "override", true))
        }.orEmpty()
        return RelationJoinedMetaAccountInfo(account, chainAccounts, emptyList())
    }

    companion object { private const val CHAIN_ID = "ton-current-account-fixture" }
}
