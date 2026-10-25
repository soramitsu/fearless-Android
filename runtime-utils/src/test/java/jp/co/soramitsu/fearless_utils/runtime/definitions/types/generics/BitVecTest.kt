package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.*
import org.junit.Test

class BitVecTest : BaseTypeTest() {

    @Test
    fun `should decode empty array`() {
        val decoded = BitVec.fromHex(
            runtime,
            "0x00"
        )

        assertArrayEquals(booleanArrayOf(), decoded)
    }

    @Test
    fun `should decode size 3`() {
        val decoded = BitVec.fromHex(runtime, "0x0c07")

        assertArrayEquals(booleanArrayOf(true, true, true), decoded)
    }

    @Test
    fun `should decode size 2`() {
        val decoded = BitVec.fromHex(runtime, "0x0803")

        assertArrayEquals(booleanArrayOf(true, true), decoded)
    }

    @Test
    fun `should decode size 2 bytes`() {
        val decoded = BitVec.fromHex(runtime, "0x28fd02")

        assertArrayEquals(
            booleanArrayOf(true, false, true, true, true, true, true, true, false, true),
            decoded
        )
    }

    @Test
    fun `should encode size 3`() {
        val decoded = BitVec.toHex(runtime, booleanArrayOf(true, true, true))

        assertEquals("0x0c07", decoded)
    }

    @Test
    fun `should encode false true`() {
        val decoded = BitVec.toHex(runtime, booleanArrayOf(false, true))

        assertEquals("0x0802", decoded)
    }

    @Test
    fun `should encode true false`() {
        val decoded = BitVec.toHex(runtime, booleanArrayOf(true, false))

        assertEquals("0x0801", decoded)
    }

    @Test
    fun `should encode size 2 bytes`() {
        val decoded = BitVec.toHex(
            runtime,
            booleanArrayOf(true, false, true, true, true, true, true, true, false, true)
        )

        assertEquals("0x28fd02", decoded)
    }

    @Test
    fun `should encode empty array`() {
        val decoded = BitVec.toHex(
            runtime,
            booleanArrayOf()
        )

        assertEquals("0x00", decoded)
    }
}