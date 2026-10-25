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

/**
 * Actual fixed-wallet datasource/repository and real mapper/MetaAccount. Controlled DAO/catalog
 * flows expose subscription ownership; fixtures adapt the accepted selected-account SA tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FixedAccountStateFlowTest {
    @Test fun fixedObserverIsColdAndUsesOnlyRequestedDaoIdentity() = runTest {
        withFixture { f ->
            val values = f.source.metaAccountStateFlow(7)
            runCurrent()
            assertTrue(f.locals.isEmpty())
            assertTrue(f.catalogs.isEmpty())
            verify(exactly = 0) { f.dao.observeJoinedMetaAccountInfo(any()) }
            val p = observe(values)
            runCurrent()
            assertEquals(listOf<MetaAccount?>(null), p.values)
            assertEquals(listOf(7L), f.locals.map { it.metaId })
            verify(exactly = 1) { f.dao.observeJoinedMetaAccountInfo(7) }
            assertTrue(f.catalogs.isEmpty())
            p.job.cancelAndJoin()
            assertTrue(f.locals.single().closed.isCompleted)
            assertFalse(p.failure.isCompleted)
        }
    }

    @Test fun twoRepositoryCollectorsOwnIndependentDaoLifetimes() = runTest {
        withFixture { f ->
            val values = repository(f.source).metaAccountStateFlow(7)
            val first = observe(values)
            val second = observe(values)
            runCurrent()
            assertEquals(2, f.locals.size)
            first.job.cancelAndJoin()
            assertTrue(f.locals[0].closed.isCompleted)
            assertFalse(f.locals[1].closed.isCompleted)
            f.locals[1].values.send(local(7, 2))
            runCurrent()
            f.catalogs.single().values.send(emptyMap())
            runCurrent()
            assertArrayEquals(byteArrayOf(2), second.values.last()!!.accountId(chainIdentity()))
            assertEquals(listOf<MetaAccount?>(null), first.values)
            second.job.cancelAndJoin()
            assertTrue(f.locals.all { it.closed.isCompleted })
            assertTrue(f.catalogs.single().closed.isCompleted)
        }
    }

    @Test fun fixedWalletDeletionRevokesWithoutWaitingForNewCatalog() = runTest {
        withFixture { f ->
            val p = observe(repository(f.source).metaAccountStateFlow(7))
            runCurrent()
            f.locals.single().values.send(local(7, 1))
            runCurrent()
            f.catalogs.single().values.send(emptyMap())
            runCurrent()
            assertEquals(7L, p.values.last()!!.id)
            f.locals.single().values.send(null)
            runCurrent()
            assertEquals(listOf(null, null, 7L, null), p.values.map { it?.id })
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertEquals(1, f.catalogs.size)
            p.job.cancelAndJoin()
            assertTrue(f.locals.single().closed.isCompleted)
        }
    }

    @Test fun realTonOnlyReplacementSurvivesExistingMetaAccountEquality() = runTest {
        withFixture { f ->
            val p = observe(repository(f.source).metaAccountStateFlow(7))
            runCurrent()
            f.locals.single().values.send(local(7, 1))
            runCurrent()
            f.catalogs[0].values.send(emptyMap())
            runCurrent()
            val first = p.values.last()!!
            f.locals.single().values.send(local(7, 2))
            runCurrent()
            assertNull(p.values.last())
            assertTrue(f.catalogs[0].closed.isCompleted)
            f.catalogs[1].values.send(emptyMap())
            runCurrent()
            val replacement = p.values.last()!!
            assertNotSame(first, replacement)
            assertEquals(first, replacement) // Existing equality omits TON; no global equality edit.
            assertArrayEquals(byteArrayOf(1), first.accountId(chainIdentity()))
            assertArrayEquals(byteArrayOf(2), replacement.accountId(chainIdentity()))
            assertEquals(2, p.values.filterNotNull().size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun replacementRevokesBeforeNonCancellableCatalogCleanupEnds() = runTest {
        withFixture { f ->
            val p = observe(repository(f.source).metaAccountStateFlow(7))
            runCurrent()
            f.locals.single().values.send(local(7, 1))
            runCurrent()
            val old = f.catalogs.single()
            old.values.send(emptyMap())
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate
            f.locals.single().values.send(local(7, 2))
            runCurrent()
            assertTrue(old.cleanupEntered.isCompleted)
            assertFalse(old.closed.isCompleted)
            assertEquals(listOf(null, null, 7L, null), p.values.map { it?.id })
            assertEquals(1, f.catalogs.size)
            gate.complete(Unit)
            runCurrent()
            assertTrue(old.closed.isCompleted)
            assertEquals(2, f.catalogs.size)
            f.catalogs[1].values.send(emptyMap())
            runCurrent()
            assertArrayEquals(byteArrayOf(2), p.values.last()!!.accountId(chainIdentity()))
            assertEquals(2, p.values.filterNotNull().size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun deletionRevokesBeforeNonCancellableCleanupAndNeverRestoresOldWallet() = runTest {
        withFixture { f ->
            val p = observe(f.source.metaAccountStateFlow(7))
            runCurrent()
            f.locals.single().values.send(local(7, 1))
            runCurrent()
            val old = f.catalogs.single()
            old.values.send(emptyMap())
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            old.cleanupGate = gate
            f.locals.single().values.send(null)
            runCurrent()
            assertTrue(old.cleanupEntered.isCompleted)
            assertFalse(old.closed.isCompleted)
            assertNull(p.values.last())
            old.values.send(emptyMap()) // An obsolete storage callback has no active publisher.
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(null, null, 7L, null), p.values.map { it?.id })
            assertTrue(old.closed.isCompleted)
            assertEquals(1, f.catalogs.size)
            p.job.cancelAndJoin()
        }
    }

    @Test fun cancellationStopsDaoAndCatalogWithoutAnotherSharedObserver() = runTest {
        withFixture { f ->
            val p = observe(repository(f.source).metaAccountStateFlow(7))
            runCurrent()
            f.locals.single().values.send(local(7, 1))
            runCurrent()
            f.catalogs.single().values.send(emptyMap())
            runCurrent()
            val before = p.values.toList()
            p.job.cancelAndJoin()
            assertTrue(p.job.isCancelled)
            assertTrue(f.locals.single().closed.isCompleted)
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertFalse(p.failure.isCompleted)
            assertEquals(before, p.values)
            assertEquals(1, f.locals.size)
            verify(exactly = 0) { f.dao.observeJoinedMetaAccountsInfo() }
        }
    }

    @Test fun repositoryPassesExactNullableFlowWithoutLegacySharingOrFiltering() = runTest {
        val expected = flowOf<MetaAccount?>(null, null)
        val source = mockk<AccountDataSource>()
        every { source.metaAccountStateFlow(7) } returns expected
        val actual = repository(source)
        assertSame(expected, actual.metaAccountStateFlow(7))
        assertEquals(listOf<MetaAccount?>(null, null), actual.metaAccountStateFlow(7).toList())
        verify(exactly = 0) { source.observeAllMetaAccounts() }
        verify(exactly = 0) { source.selectedMetaAccountStateFlow() }
    }

    @Test fun fixedDaoFailurePropagatesThroughActualRepositoryAndStopsCatalog() = runTest {
        withFixture { f ->
            val p = observe(repository(f.source).metaAccountStateFlow(7))
            runCurrent()
            f.locals.single().values.send(local(7, 1))
            runCurrent()
            val failure = StorageFailure(Any())
            f.locals.single().values.close(failure)
            runCurrent()
            assertSame(failure, p.failure.await())
            assertTrue(f.locals.single().closed.isCompleted)
            assertTrue(f.catalogs.single().closed.isCompleted)
            assertTrue(p.values.all { it == null })
            p.job.join()
        }
    }

    private fun TestScope.observe(values: Flow<MetaAccount?>): Probe {
        val probe = Probe()
        probe.job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                values.collect { probe.values += it }
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
        // Let the unchanged constructor's eager compatibility collector finish finite inputs.
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

    private class Local(val metaId: Long) {
        val values = Channel<RelationJoinedMetaAccountInfo?>(Channel.UNLIMITED)
        val closed = CompletableDeferred<Unit>()
        fun flow(): Flow<RelationJoinedMetaAccountInfo?> = flow {
            try { emitAll(values.receiveAsFlow()) } finally { closed.complete(Unit) }
        }
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
        val locals = mutableListOf<Local>()
        val catalogs = mutableListOf<Catalog>()
        val legacyLocalFinished = CompletableDeferred<Unit>()
        val legacyCatalogFinished = CompletableDeferred<Unit>()
        val dao = mockk<MetaAccountDao>()
        private val chains = mockk<ChainsRepository>()
        private val catalogRequests = AtomicInteger()
        val source: AccountDataSourceImpl

        init {
            every { dao.selectedMetaAccountInfoFlow() } returns flow { legacyLocalFinished.complete(Unit) }
            every { dao.observeJoinedMetaAccountInfo(any()) } answers {
                Local(firstArg()).also { locals += it }.flow()
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
            locals.forEach { it.values.close() }
            catalogs.forEach {
                it.cleanupGate?.complete(Unit)
                it.values.close()
            }
        }
    }

    /** Extra state disables exception-copy reconstruction, so the identity assertion is meaningful. */
    private class StorageFailure(val marker: Any) : RuntimeException("synthetic-fixed-wallet-failure")

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

    private fun local(id: Long, ton: Int): RelationJoinedMetaAccountInfo {
        val account = MetaAccountLocal(
            substratePublicKey = null, substrateCryptoType = CryptoType.SR25519, substrateAccountId = null,
            ethereumPublicKey = null, ethereumAddress = null, tonPublicKey = byteArrayOf(ton.toByte()),
            name = "wallet$id", isSelected = true, position = 0, isBackedUp = true,
            googleBackupAddress = null, initialized = true
        ).also { it.id = id }
        return RelationJoinedMetaAccountInfo(account, emptyList(), emptyList())
    }

    companion object { private const val CHAIN_ID = "ton-fixed-account-fixture" }
}
