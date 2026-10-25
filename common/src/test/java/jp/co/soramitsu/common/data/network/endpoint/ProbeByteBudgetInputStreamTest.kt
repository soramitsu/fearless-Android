package jp.co.soramitsu.common.data.network.endpoint

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ProbeByteBudgetInputStreamTest {
    @Test fun byteBulkSkipAndAvailabilityShareOneBudget() {
        val input = ProbeByteBudgetInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6)), 5)
        assertEquals(5, input.available()); assertEquals(1, input.read())
        val bytes = ByteArray(8); assertEquals(3, input.read(bytes, 1, 3)); assertArrayEquals(byteArrayOf(0, 2, 3, 4, 0, 0, 0, 0), bytes)
        assertEquals(1L, input.skip(99)); assertEquals(0, input.available())
        assertThrows(ProbeByteLimitException::class.java) { input.read() }
        assertThrows(ProbeByteLimitException::class.java) { input.read(bytes) }
        assertThrows(ProbeByteLimitException::class.java) { input.skip(1) }
    }

    @Test fun zeroLengthAndInvalidRangesDoNotConsumeAndEarlyEofIsPreserved() {
        val input = ProbeByteBudgetInputStream(ByteArrayInputStream(byteArrayOf(1)), 2)
        assertEquals(0, input.read(ByteArray(0))); assertEquals(0L, input.skip(-1)); assertFalse(input.markSupported())
        assertThrows(IndexOutOfBoundsException::class.java) { input.read(ByteArray(2), 2, 1) }
        assertEquals(1, input.read()); assertEquals(-1, input.read()); assertEquals(-1, input.read(ByteArray(1))); input.close()
        assertThrows(IllegalArgumentException::class.java) { ProbeByteBudgetInputStream(ByteArrayInputStream(byteArrayOf()), -1) }
    }
}
