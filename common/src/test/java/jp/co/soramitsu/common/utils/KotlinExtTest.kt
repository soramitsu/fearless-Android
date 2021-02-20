package jp.co.soramitsu.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinExtTest {

    @Test
    fun `should calculate median of single element list`() {
        val median = listOf(2.0).median()

        assertEquals(2.0, median, 0.0001)
    }

    @Test
    fun `should calculate median of odd sized list`() {
        val median = listOf(2.0, 2.0, 3.0, 4.0, 5.0).median()

        assertEquals(3.0, median, 0.0001)
    }

    @Test
    fun `should calculate median of even sized list`() {
        val median = listOf(2.0, 2.0, 4.0, 5.0).median()

        assertEquals(3.0, median, 0.0001)
    }
}