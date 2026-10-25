package jp.co.soramitsu.fearless_utils.extensions

import org.junit.Assert.*
import org.junit.Test

class SplitByteArrayTest {

    @Test
    fun shouldSplitWithoutDivider() {
        runTest(
            array = byteArrayOf(1, 2, 3),
            divider = byteArrayOf(4),
            expected = listOf(
                byteArrayOf(1, 2, 3)
            )
        )
    }

    @Test
    fun shouldSplitDividerAtStart() {
        runTest(
            divider = byteArrayOf(1, 2, 3),
            array = byteArrayOf(1, 2, 3, 0, 0, 0),
            expected = listOf(
                byteArrayOf(),
                byteArrayOf(0, 0, 0)
            )
        )
    }

    @Test
    fun shouldSplitDividerAtEnd() {
        runTest(
            divider = byteArrayOf(1, 2, 3),
            array = byteArrayOf(0, 0, 0, 1, 2, 3),
            expected = listOf(
                byteArrayOf(0, 0, 0),
            )
        )
    }

    @Test
    fun shouldSplitMergedDividers() {
        runTest(
            divider = byteArrayOf(1, 2, 3),
            array = byteArrayOf(0, 1, 2, 3, 1, 2, 3, 0),
            expected = listOf(
                byteArrayOf(0),
                byteArrayOf(),
                byteArrayOf(0)
            )
        )
    }

    @Test
    fun shouldSplit() {
        runTest(
            array = byteArrayOf(0, 0, 0, 1, 2, 3, 0, 0),
            divider = byteArrayOf(1, 2, 3),
            expected = listOf(
                byteArrayOf(0, 0, 0),
                byteArrayOf(0, 0)
            )
        )

        runTest(
            array = byteArrayOf(0, 1, 2, 0, 1, 2, 0),
            divider = byteArrayOf(1, 2),
            expected = listOf(
                byteArrayOf(0),
                byteArrayOf(0),
                byteArrayOf(0)
            )
        )
    }

    fun runTest(
        array: ByteArray,
        divider: ByteArray,
        expected: List<ByteArray>
    ) {

        val split = array.split(divider)

        assertEquals(expected.size, split.size)

        expected.zip(split).mapIndexed { index, (expectedElement, actualElement) ->
            assertArrayEquals(
                "Not actual elements at index $index. Expected: ${expectedElement.toListLikeString()}. Got: ${actualElement.toListLikeString()}",
                expectedElement,
                actualElement
            )
        }
    }

    private fun ByteArray.toListLikeString(): String {
        return joinToString(prefix = "[", postfix = "]", separator = ",")
    }
}
