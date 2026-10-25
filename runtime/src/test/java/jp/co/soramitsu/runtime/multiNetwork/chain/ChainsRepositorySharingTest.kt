package jp.co.soramitsu.runtime.multiNetwork.chain

import android.util.Log
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.*
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.atomic.AtomicInteger

/** Actual repository, mapper and DAO catalog flow. Only Room query replies/invalidation are controlled. */
class ChainsRepositorySharingTest {
    // A stateful exception cannot be copied by coroutine stack-trace recovery, allowing an
    // identity assertion on the actual failure rather than on a debug-generated copy.
    private class CatalogReadFailure(val marker: Any) : IllegalStateException("public-fixture-read-failure")
    private class Fixture(private val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) : AutoCloseable {
        val logs = mockStatic(Log::class.java)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val catalogChanges = MutableStateFlow(0L)
        val nodeChanges = MutableStateFlow(0L)
        val reads = AtomicInteger()
        val queries = AtomicInteger()
        val failuresSeen = Channel<Throwable>(Channel.UNLIMITED)
        @Volatile var failure: Throwable? = null
        @Volatile var rows = listOf(ChainLocal("a",null,null,1,"Before",null,"",null,42,false,false,false,
            false,false,false,false,false,null,"Substrate",null,null,null,null))
        val dao: ChainDao = mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer { call ->
            when (call.method.name) {
                "getEndpointMutationLane", "joinChainInfoFlow" -> call.callRealMethod()
                "getJoinChainInfo" -> { reads.incrementAndGet(); failure?.let { failuresSeen.trySend(it); throw it }; call.callRealMethod() }
                "catalogInvalidationFlow" -> catalogChanges
                "runtimeNodeInvalidationFlow" -> nodeChanges
                "runtimeChainPage" -> { queries.incrementAndGet()
                    val wanted=call.getArgument<String?>(0); val after=call.getArgument<Long?>(1)
                    rows.mapIndexed { i,v -> RuntimeChainCatalogRow(v,i.toLong()+1,null,null) }
                        .filter { (wanted==null || it.chain.id==wanted) && (after==null || it.sourceRowId>after) }.take(64)
                }
                "runtimeAssetPage", "runtimeExplorerPage" -> { queries.incrementAndGet(); emptyList<Any>() }
                "readRuntimeNodes" -> emptyList<ChainNodeLocal>()
                else -> error("Unexpected DAO method: ${call.method.name}")
            }
        })
        // Baseline has only the DAO constructor; candidate injects its sharing lifetime for test cleanup.
        val repository: ChainsRepository = ChainsRepository::class.java.constructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(ChainDao::class.java, CoroutineScope::class.java))
        }?.newInstance(dao,scope) as? ChainsRepository ?: ChainsRepository(dao)
        fun change(name: String?) { rows=if(name==null)emptyList()else rows.ifEmpty { listOf(ChainLocal("a",null,null,1,name,null,"",null,42,false,false,false,false,false,false,false,false,null,"Substrate",null,null,null,null)) }.map { it.copy(name=name) }; catalogChanges.value++ }
        suspend fun stopped() { withTimeout(10_000) { catalogChanges.subscriptionCount.first { it==0 }; nodeChanges.subscriptionCount.first { it==0 } }; yield() }
        override fun close() {
            scope.cancel()
            runBlocking { withTimeout(10_000) {
                while(!scope.coroutineContext.job.isCompleted) {
                    if(dispatcher is PausedDispatcher)dispatcher.runQueued()
                    yield()
                }
            } }
            logs.close()
        }
    }
    private suspend fun <T> Channel<T>.next(): T = withTimeout(10_000) { receive() }

    @Test fun sixConcurrentListAndMapSubscribersUseOneActualDaoCatalogRead() = runBlocking {
        Fixture().use { f ->
            val output=Channel<List<Chain>>(Channel.UNLIMITED)
            val jobs=(0 until 6).map { i -> launch {
                (if(i%2==0)f.repository.chainsFlow()else f.repository.chainsByIdFlow().map { it.values.toList() })
                    .collect { output.send(it) }
            } }
            try {
                repeat(6) { assertEquals("Before",output.next().single().name) }
                assertEquals("Actual full-catalog calls",1,f.reads.get())
                assertEquals("Four actual bounded query calls for the single shared one-chain read",4,f.queries.get())
            } finally { jobs.forEach { it.cancelAndJoin() }; output.close() }
        }
    }
    @Test fun listAndMapSubscribersReceiveTheSameMappedChainInstance() = runBlocking {
        Fixture().use { f ->
            val list=Channel<List<Chain>>(Channel.UNLIMITED); val map=Channel<Map<String,Chain>>(Channel.UNLIMITED)
            val a=launch { f.repository.chainsFlow().collect { list.send(it) } }
            val b=launch { f.repository.chainsByIdFlow().collect { map.send(it) } }
            try { assertSame(list.next().single(),map.next().getValue("a")) }
            finally { a.cancelAndJoin();b.cancelAndJoin();list.close();map.close() }
        }
    }
    @Test fun oneInvalidationRefreshesBothSubscribersWithOneMoreRead() = runBlocking {
        Fixture().use { f ->
            val a=Channel<String>(Channel.UNLIMITED);val b=Channel<String>(Channel.UNLIMITED)
            val ja=launch { f.repository.chainsFlow().collect { a.send(it.single().name) } }
            val jb=launch { f.repository.chainsByIdFlow().collect { b.send(it.getValue("a").name) } }
            try { a.next();b.next();f.change("After");assertEquals("After",a.next());assertEquals("After",b.next());assertEquals(2,f.reads.get()) }
            finally { ja.cancelAndJoin();jb.cancelAndJoin();a.close();b.close() }
        }
    }
    @Test fun repositoryConstructionAndUncollectedAccessorsDoNotReadTheDatabase() {
        Fixture().use { f -> f.repository.chainsFlow();f.repository.chainsByIdFlow();assertEquals(0,f.reads.get()) }
    }
    @Test fun snapshotGettersStayFreshWhileAHotCatalogHasOlderData() = runBlocking {
        Fixture().use { f ->
            val out=Channel<String>(Channel.UNLIMITED);val j=launch { f.repository.chainsFlow().collect { out.send(it.single().name) } }
            try {
                assertEquals("Before",out.next());f.rows=f.rows.map { it.copy(name="Silent refresh") }
                assertEquals("Silent refresh",f.repository.getChains().single().name)
                assertEquals("Silent refresh",f.repository.getChainsById().getValue("a").name)
                assertEquals(3,f.reads.get())
            } finally { j.cancelAndJoin();out.close() }
        }
    }
    @Test fun getChainRetainsItsFreshInitialReadRatherThanReusingHotReplay() = runBlocking {
        Fixture().use { f ->
            val out=Channel<String>(Channel.UNLIMITED);val j=launch { f.repository.chainsFlow().collect { out.send(it.single().name) } }
            try {
                assertEquals("Before",out.next());f.rows=f.rows.map { it.copy(name="Silent single") }
                assertEquals("Silent single",f.repository.getChain("a").name)
                assertEquals(2,f.reads.get())
            } finally { j.cancelAndJoin();out.close() }
        }
    }
    @Test fun withdrawalAndRecoveryReachBothPublicViewsWithoutAStaleChain() = runBlocking {
        Fixture().use { f ->
            val a=Channel<List<Chain>>(Channel.UNLIMITED);val b=Channel<Map<String,Chain>>(Channel.UNLIMITED)
            val ja=launch { f.repository.chainsFlow().collect { a.send(it) } };val jb=launch { f.repository.chainsByIdFlow().collect { b.send(it) } }
            try {a.next();b.next();f.change(null);assertTrue(a.next().isEmpty());assertTrue(b.next().isEmpty());f.change("Recovered");assertEquals("Recovered",a.next().single().name);assertEquals("Recovered",b.next().getValue("a").name)}
            finally {ja.cancelAndJoin();jb.cancelAndJoin();a.close();b.close()}
        }
    }
    @Test fun finalUnsubscribeStopsUpstreamAndRestartCannotReplayTheRetiredCatalog() = runBlocking {
        Fixture().use { f ->
            assertEquals("Before",f.repository.chainsFlow().first().single().name)
            f.stopped(); f.rows=f.rows.map { it.copy(name="Restarted") }
            assertEquals("Restarted",f.repository.chainsByIdFlow().first().getValue("a").name)
            f.stopped();assertEquals(2,f.reads.get())
        }
    }
    @Test fun cancellingOneSubscriberKeepsTheOthersCatalogLive() = runBlocking {
        Fixture().use { f ->
            val a=Channel<String>(Channel.UNLIMITED);val b=Channel<String>(Channel.UNLIMITED)
            val ja=launch { f.repository.chainsFlow().collect { a.send(it.single().name) } };val jb=launch { f.repository.chainsFlow().collect { b.send(it.single().name) } }
            try {a.next();b.next();ja.cancelAndJoin();f.change("Remaining");assertEquals("Remaining",b.next());assertTrue(f.catalogChanges.subscriptionCount.value>0)}
            finally {ja.cancelAndJoin();jb.cancelAndJoin();a.close();b.close()}
        }
    }
    @Test fun upstreamFailureReachesEverySubscriberAndLaterFreshSubscriptionCanRecover() = runBlocking {
        Fixture().use { f ->
            val ready=Channel<Unit>(Channel.UNLIMITED);val errors=Channel<Throwable>(Channel.UNLIMITED)
            val jobs=(0..1).map { i -> launch {
                try { (if(i==0)f.repository.chainsFlow()else f.repository.chainsByIdFlow().map { it.values.toList() }).collect { ready.send(Unit) } }
                catch(error:IllegalStateException) { errors.send(error) }
            } }
            try {
                repeat(2){ready.next()};f.failure=CatalogReadFailure(Any());f.catalogChanges.value++
                repeat(2){assertSame(f.failure,errors.next())}
                jobs.forEach { it.join() };f.stopped();f.failure=null;f.change("Recovered after error")
                assertEquals("Recovered after error",f.repository.chainsFlow().first().single().name)
            } finally {jobs.forEach { it.cancelAndJoin() };ready.close();errors.close()}
        }
    }
    private class PausedDispatcher : CoroutineDispatcher() {
        private val tasks = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks.add(block) }
        fun runQueued() { while (true) (tasks.poll() ?: return).run() }
    }

    @Test fun immediateResubscribeCannotReceiveRetiredReplayBeforeSharingDispatcherRuns() = runBlocking {
        val dispatcher=PausedDispatcher()
        Fixture(dispatcher).use { f ->
            val initial=async { f.repository.chainsFlow().first().single().name }
            withTimeout(10_000) { while(!initial.isCompleted) { dispatcher.runQueued();yield() } }
            assertEquals("Before",initial.await())
            f.rows=f.rows.map { it.copy(name="New generation") }
            // Do not execute queued sharing-stop commands before the new collector attaches.
            val restarted=async(start=CoroutineStart.UNDISPATCHED) { f.repository.chainsFlow().first().single().name }
            withTimeout(10_000) { while(!restarted.isCompleted) { dispatcher.runQueued();yield() } }
            assertEquals("New generation",restarted.await())
            dispatcher.runQueued()
        }
    }

    @Test fun cancelledDaoRefreshDoesNotBecomePermanentErrorAndNextInvalidationRecovers() = runBlocking {
        Fixture().use { f ->
            val values=Channel<String>(Channel.UNLIMITED);val errors=Channel<Throwable>(Channel.UNLIMITED)
            val jobs=(0..1).map { launch {
                try { f.repository.chainsFlow().collect { values.send(it.single().name) } }
                catch(error:Throwable) { errors.send(error) }
            } }
            try {
                repeat(2){assertEquals("Before",values.next())}
                val reason=CancellationException("fixture catalog refresh cancelled")
                f.failure=reason;f.catalogChanges.value++
                assertSame(reason,f.failuresSeen.next())
                // Actual ChainDao uses mapLatest: cancellation of one read is not a terminal
                // upstream failure. Keep observing and let the next invalidation recover.
                assertTrue(jobs.all {it.isActive});assertTrue(errors.tryReceive().isFailure)
                f.failure=null;f.change("After cancellation")
                repeat(2){assertEquals("After cancellation",values.next())}
                assertTrue(errors.tryReceive().isFailure)
            } finally {jobs.forEach {it.cancelAndJoin()};values.close();errors.close()}
        }
    }
    @Test fun injectedScopeCancellationReachesThePublicCollector() = runBlocking {
        // Only the candidate has an injected ownership scope. The original caller-owned flow
        // does not participate in this new lifetime and is characterized in the other tests.
        if (ChainsRepository::class.java.constructors.none {
            it.parameterTypes.contentEquals(arrayOf(ChainDao::class.java,CoroutineScope::class.java))
        }) return@runBlocking
        Fixture().use { f ->
            val ready=Channel<Unit>(Channel.UNLIMITED);val errors=Channel<Throwable>(Channel.UNLIMITED)
            val job=launch { try { f.repository.chainsFlow().collect { ready.send(Unit) } }
                catch(error:CancellationException) { errors.send(error) } }
            try {ready.next();f.scope.cancel();assertTrue(errors.next() is CancellationException);job.join();f.stopped()}
            finally {job.cancelAndJoin();ready.close();errors.close()}
        }
    }
    @Test fun getChainStillWaitsForTheRequestedMissingChainDuringAnotherActiveObservation() = runBlocking {
        Fixture().use { f ->
            val ready=Channel<Unit>(Channel.UNLIMITED)
            val observer=launch {f.repository.chainsFlow().collect {ready.send(Unit)}}
            try {
                ready.next();val single=async {f.repository.getChain("b")}
                withTimeout(10_000) {while(f.reads.get()<2)yield()}
                assertFalse(single.isCompleted)
                f.rows=f.rows+f.rows.single().copy(id="b",name="New chain")
                f.catalogChanges.value++
                assertEquals("New chain",withTimeout(10_000){single.await()}.name)
            } finally {observer.cancelAndJoin();ready.close()}
        }
    }

}
