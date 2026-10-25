package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicInteger

class ObservedChainCatalogTest {
    @Test fun finiteSourceFinalValueIsDeliveredOnceEvenWhenItCompletesBeforeCollectorAttaches() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val value=listOf(mock(Chain::class.java))
            val catalog=ObservedChainCatalog({flowOf(value)},scope)
            assertEquals(listOf(value),withTimeout(5_000){catalog.observe().toList()})
        } finally {scope.coroutineContext.job.cancelAndJoin()}
    }
    @Test fun emptyFiniteSourceCompletesWithoutInventingAnEmptyCatalogValue() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {assertTrue(withTimeout(5_000){ObservedChainCatalog({emptyFlow()},scope).observe().toList()}.isEmpty())}
        finally {scope.coroutineContext.job.cancelAndJoin()}
    }
    @Test fun overlappingCollectorsShareFiniteSourceAndBothReceiveItsFinalValueExactlyOnce() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val starts=AtomicInteger();val gate=CompletableDeferred<Unit>();val ready=Channel<Unit>(Channel.UNLIMITED)
        val first=listOf(mock(Chain::class.java));val last=listOf(mock(Chain::class.java))
        val catalog=ObservedChainCatalog({flow {starts.incrementAndGet();emit(first);gate.await();emit(last)}},scope)
        try {
            val a=async {catalog.observe().onEach {if(it===first)ready.send(Unit)}.toList()}
            val b=async {catalog.observe().onEach {if(it===first)ready.send(Unit)}.toList()}
            withTimeout(5_000){repeat(2){ready.receive()}};gate.complete(Unit)
            assertEquals(listOf(first,last),withTimeout(5_000){a.await()})
            assertEquals(listOf(first,last),withTimeout(5_000){b.await()})
            assertEquals(1,starts.get())
        } finally {gate.cancel();scope.coroutineContext.job.cancelAndJoin();ready.close()}
    }
    private class PausedDispatcher : CoroutineDispatcher() {
        val tasks=java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){tasks.add(block)}
        fun runQueued(){while(true)(tasks.poll()?:return).run()}
    }
    @Test fun scopeCancellationBeforeLazyProducerRunsReachesObserverWithoutReadingSource() = runBlocking {
        val dispatcher=PausedDispatcher();val scope=CoroutineScope(SupervisorJob()+dispatcher);val calls=AtomicInteger()
        val catalog=ObservedChainCatalog({calls.incrementAndGet();emptyFlow()},scope)
        val result=async(start=CoroutineStart.UNDISPATCHED) {
            try {catalog.observe().collect();null}catch(error:CancellationException){error}
        }
        try {
            assertEquals(0,calls.get());scope.cancel()
            withTimeout(5_000){while(!result.isCompleted){dispatcher.runQueued();yield()}}
            assertTrue(result.await() is CancellationException);assertEquals(0,calls.get())
        } finally {scope.cancel();dispatcher.runQueued();scope.coroutineContext.job.join()}
    }
    @Test fun alreadyCancelledScopeRejectsNewObservationBeforeInvokingSource() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val calls=AtomicInteger();scope.cancel()
        val catalog=ObservedChainCatalog({calls.incrementAndGet();emptyFlow()},scope)
        val error=try{catalog.observe().collect();null}catch(error:CancellationException){error}
        assertTrue(error is CancellationException);assertEquals(0,calls.get())
    }
    private class SourceCancelled(val marker: Any) : CancellationException("source cancelled")
    @Test fun directSourceCancellationKeepsItsIdentityAndAFreshGenerationCanRecover() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val reason=SourceCancelled(Any());var failing=true;val value=listOf(mock(Chain::class.java))
        val catalog=ObservedChainCatalog({flow {if(failing)throw reason else emit(value)}},scope)
        try {
            val caught=try{catalog.observe().collect();null}catch(error:CancellationException){error}
            assertSame(reason,caught);failing=false
            assertEquals(listOf(value),withTimeout(5_000){catalog.observe().toList()})
        } finally {scope.coroutineContext.job.cancelAndJoin()}
    }

    private class ReadFailed(val marker: Any) : IllegalStateException("read failed")
    @Test fun lateFirstCollectorCannotReceiveRetiredSuccessAfterAnUpstreamError() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val failNow=CompletableDeferred<Unit>()
        val reason=ReadFailed(Any());val value=listOf(mock(Chain::class.java));val errors=Channel<Throwable>(Channel.UNLIMITED)
        val catalog=ObservedChainCatalog({flow {emit(value);failNow.await();throw reason}},scope)
        val slow=launch {try{catalog.observe().collect {entered.complete(Unit);release.await()}}catch(error:ReadFailed){errors.send(error)}}
        try {
            withTimeout(5_000){entered.await()};failNow.complete(Unit)
            val caught=try{withTimeout(5_000){catalog.observe().first()};null}catch(error:ReadFailed){error}
            assertSame(reason,caught)
            release.complete(Unit);assertSame(reason,withTimeout(5_000){errors.receive()})
        } finally {release.complete(Unit);slow.cancelAndJoin();scope.coroutineContext.job.cancelAndJoin();errors.close()}
    }

}
