package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.presentation.LoadingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowOperatorsTest {
    @Test
    fun shouldEmitLoadingThenEveryValue() = runTest {
        val states = flowOf(1, 2).withLoading().toList()
        assertTrue(states.first() is LoadingState.Loading)
        assertEquals(listOf(1, 2), states.filterIsInstance<LoadingState.Loaded<Int>>().map { it.data })
        val mapped = flowOf(listOf(1, 2)).mapList { it * 3 }.first()
        assertEquals(listOf(3, 6), mapped)
        assertEquals(4 to "DOT", combineToPair(flowOf(4), flowOf("DOT")).first())
    }

    @Test
    fun shouldCancelTheOldLoadingSupplierWhenTheSelectionChanges() = runTest {
        val selection = MutableStateFlow(1)
        val states = mutableListOf<LoadingState<String>>()
        val collector = backgroundScope.launch {
            selection.withLoading { value -> flow { delay(100); emit("value:$value") } }.collect { states += it }
        }
        runCurrent()
        advanceTimeBy(50)
        selection.value = 2
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertEquals(2, states.count { it is LoadingState.Loading })
        assertEquals(listOf("value:2"), states.filterIsInstance<LoadingState.Loaded<String>>().map { it.data })
        collector.cancel()
        val single = flowOf(4).withLoadingSingle { it * 2 }.toList()
        assertTrue(single.first() is LoadingState.Loading)
        assertEquals(8, (single.last() as LoadingState.Loaded).data)
    }

    @Test
    fun shouldRememberOnlyMatchingPreviousItemsAndReplayTheLatestEvent() = runTest {
        assertEquals(listOf(null to 1, 1 to 2, 2 to 3), flowOf(1, 2, 3).zipWithPrevious().toList())
        assertEquals(listOf(null to 1, null to 2, 2 to 3, 2 to 4),
            flowOf(1, 2, 3, 4).rememberAndZipAsPreviousIf { it % 2 == 0 }.toList())
        val replay = singleReplaySharedFlow<Int>()
        replay.emit(1)
        replay.emit(2)
        assertEquals(2, replay.first())
        val toggle = MutableStateFlow(false)
        toggle.toggle()
        assertTrue(toggle.value)
        toggle.toggle()
        assertFalse(toggle.value)
        assertEquals("produced", jp.co.soramitsu.common.utils.flowOf { "produced" }.first())
    }

    @Test
    fun shouldRunEveryConcurrentRequestIncludingTheLastPartialChunk() = runTest {
        val input = (1..37).toList()
        val output = input.concurrentRequestFlow<Int, Int>(coroutineContext.minusKey(Job)) { emit(it * it) }.toList()
        assertEquals(input.map { it * it }.sorted(), output.sorted())
        assertEquals(emptyList<Int>(), emptyList<Int>().concurrentRequestFlow<Int, Int>(coroutineContext.minusKey(Job)) { emit(it) }.toList())
    }

    @Test
    fun shouldCancelChildScopesWithTheirParentWithoutCancellingTheParentOnChildCancellation() {
        for (supervised in listOf(true, false)) {
            val parent = Job()
            val scope = CoroutineScope(parent)
            val first = scope.childScope(supervised).coroutineContext[Job]!!
            val second = scope.childScope(supervised).coroutineContext[Job]!!
            first.cancel()
            assertTrue(parent.isActive)
            assertTrue(second.isActive)
            parent.cancel()
            assertTrue(first.isCancelled)
            assertTrue(second.isCancelled)
        }
    }
}
