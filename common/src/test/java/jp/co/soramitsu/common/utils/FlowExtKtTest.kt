package jp.co.soramitsu.common.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowExtKtTest {

    @Test
    fun testDiffed() {
        runBlocking {
            performTest(
                first = emptyList(),
                second = listOf(1, 2, 3),
                expectedDiff = ListDiff(
                    removed = emptyList(),
                    addedOrModified = listOf(1, 2, 3)
                )
            )

            performTest(
                first = listOf(1, 2, 3),
                second = listOf(1, 2, 3),
                expectedDiff = ListDiff(
                    removed = emptyList(),
                    addedOrModified = emptyList()
                )
            )

            performTest(
                first = listOf(1, 2, 3),
                second = emptyList(),
                expectedDiff = ListDiff(
                    removed = listOf(1, 2, 3),
                    addedOrModified = emptyList()
                )
            )

            performTest(
                first = listOf(1, 2),
                second =  listOf(2, 3),
                expectedDiff = ListDiff(
                    removed = listOf(1),
                    addedOrModified =  listOf(3)
                )
            )
        }
    }

    private suspend fun performTest(
        first: List<Int>,
        second: List<Int>,
        expectedDiff: ListDiff<Int>
    ) {
        val diffed = kotlinx.coroutines.flow.flowOf(first, second)
            .diffed()
            .withIndex().first { (index, _) -> index == 1 } // take second element which will actually represent diff
            .value

        assertEquals(expectedDiff, diffed)
    }
}
