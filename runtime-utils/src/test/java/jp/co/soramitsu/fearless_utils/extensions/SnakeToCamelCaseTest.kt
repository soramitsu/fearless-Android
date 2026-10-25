package jp.co.soramitsu.fearless_utils.extensions

import org.junit.Assert.*
import org.junit.Test

class SnakeToCamelCaseTest {

    @Test
    fun test() {
        runTest("", "")
        runTest("test", "test")
        runTest("one_two", "oneTwo")
        runTest("one_two_three", "oneTwoThree")
    }

    private fun runTest(
        origin: String,
        expected: String
    ) {
        assertEquals(expected, origin.snakeCaseToCamelCase())
    }
}