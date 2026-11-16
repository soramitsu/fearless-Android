package jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frozen-balance sheet grows and shrinks between its block height and the parent height as the
 * list inside it scrolls, and settles on the nearer anchor after a fling or a toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpandableLazyListNestedScrollConnectionCoverageTest {

    private val parent = TestState(400.dp)
    private val block = TestState(100.dp)
    private val height = TestState(250.dp)
    private val scrolled = mutableListOf<Float>()

    @Test
    fun `an empty list never moves the sheet`() {
        val connection = connection(listState(total = 0, visible = emptyList()))

        val consumed = connection.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput)

        assertEquals(Offset.Zero, consumed)
        assertEquals(250.dp, height.value)
        assertEquals(listOf(0f), scrolled)
    }

    @Test
    fun `the sheet stops at the parent height and at the block height`() {
        val connection = connection(listState(total = 5, visible = listOf(0 to 0, 2 to 100)))

        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -400f), NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 400f), NestedScrollSource.UserInput))

        assertEquals(250.dp, height.value)
    }

    @Test
    fun `a list at its top hands the scroll to the sheet`() {
        val connection = connection(listState(total = 5, visible = listOf(0 to 0, 2 to 100)))

        val consumed = connection.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput)

        assertEquals(Offset(0f, 40f), consumed)
        assertEquals(230.dp, height.value)
        assertEquals(listOf(40f), scrolled)
    }

    @Test
    fun `a list scrolled to its end hands the scroll to the sheet`() {
        val connection = connection(listState(total = 5, visible = listOf(2 to -30, 4 to 120)))

        val consumed = connection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)

        assertEquals(Offset(0f, 40f), consumed)
        assertEquals(230.dp, height.value)
    }

    @Test
    fun `a list in the middle keeps the scroll, but the parent layout still moves the sheet`() {
        val connection = connection(listState(total = 5, visible = listOf(1 to -30, 3 to 120)))

        val consumed = connection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, consumed)
        assertEquals(250.dp, height.value)

        connection.preScrollFromParentLayout(Offset(0f, -40f))
        assertEquals(270.dp, height.value)
        assertEquals(listOf(0f, -40f), scrolled)
    }

    @Test
    fun `expansion is measured between the block and the parent height`() {
        val connection = connection(listState(total = 0, visible = emptyList()))

        assertEquals(0.5f, connection.expandPercent, 0.0001f)
        height.value = 400.dp
        assertEquals(1f, connection.expandPercent, 0.0001f)
    }

    @Test
    fun `a toggle animates to the other anchor`() = runTest {
        height.value = 130.dp
        val scope = animationScope()
        val connection = connection(listState(total = 0, visible = emptyList()), scope)

        connection.toggle()
        advanceUntilIdle()
        assertEquals(400f, height.value.value, 0.5f)

        connection.toggle()
        advanceUntilIdle()
        assertEquals(100f, height.value.value, 0.5f)
        scope.cancel()
    }

    @Test
    fun `a fling settles on the nearer anchor`() = runTest {
        height.value = 300.dp
        val scope = animationScope()
        val connection = connection(listState(total = 0, visible = emptyList()), scope)

        assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, 10f)))
        assertEquals(Velocity.Zero, connection.onPostFling(Velocity.Zero, Velocity(0f, 10f)))
        advanceUntilIdle()
        assertEquals(400f, height.value.value, 0.5f)

        height.value = 130.dp
        connection.onPostFling(Velocity.Zero, Velocity(0f, -10f))
        advanceUntilIdle()
        assertEquals(100f, height.value.value, 0.5f)
        scope.cancel()
    }

    @Test
    fun `a sheet without room to expand stays where it is`() = runTest {
        parent.value = 100.dp
        height.value = 100.dp
        val scope = animationScope()
        val connection = connection(listState(total = 0, visible = emptyList()), scope)
        assertTrue(connection.expandPercent.isNaN())

        connection.toggle()
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
        advanceUntilIdle()

        assertEquals(100f, height.value.value, 0.001f)
        scope.cancel()
    }

    private fun TestScope.animationScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + StepFrameClock())

    private fun connection(
        listState: LazyListState,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ) = ExpandableLazyListNestedScrollConnection(
        listState = listState,
        parentHeight = parent,
        blockHeight = block,
        height = height,
        scope = scope,
        localDensity = 2f,
        scrolled = { scrolled += it }
    )

    /** [visible] holds (index, offset) of each visible item. */
    private fun listState(total: Int, visible: List<Pair<Int, Int>>): LazyListState {
        val items = visible.map { (itemIndex, itemOffset) ->
            mockk<LazyListItemInfo> {
                every { index } returns itemIndex
                every { offset } returns itemOffset
            }
        }
        val layout = mockk<LazyListLayoutInfo> {
            every { totalItemsCount } returns total
            every { visibleItemsInfo } returns items
        }
        return mockk { every { layoutInfo } returns layout }
    }

    private class TestState<T>(override var value: T) : MutableState<T> {
        override operator fun component1(): T = value
        override operator fun component2(): (T) -> Unit = { value = it }
    }

    /** One 16 ms frame per request, in the test scheduler's virtual time. */
    private class StepFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MS)
            frameTimeNanos += FRAME_MS * NANOS_PER_MS
            return onFrame(frameTimeNanos)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val NANOS_PER_MS = 1_000_000L
    }
}
