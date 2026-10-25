package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era.Companion.getPeriodPhaseFromBlockPeriod
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.*
import org.junit.Test

class EraTypeTest : BaseTypeTest() {

    @Test
    fun `get period and phase from block number`() {
        val (period, phase) = getPeriodPhaseFromBlockPeriod(97506, 64)
        performMortalEncodeTest("0x2502", period, phase)
    }

    @Test
    fun `should decode immortal`() {
        val decoded = EraType.fromHex(runtime, "0x00")

        assertEquals(Era.Immortal, decoded)
    }

    @Test
    fun `should decode mortal`() {
        performMortalDecodeTest("0x4e9c", period = 32768, phase = 20000)
        performMortalDecodeTest("0xc503", period = 64, phase = 60)
        performMortalDecodeTest("0x8502", period = 64, phase = 40)
    }

    @Test
    fun `should encode immortal`() {
        val inHex = "0x00"
        val encoded = EraType.toHex(runtime, Era.Immortal)

        assertEquals(inHex, encoded)
    }

    @Test
    fun `should encode mortal`() {
        performMortalEncodeTest("0x4e9c", period = 32768, phase = 20000)
        performMortalEncodeTest("0xc503", period = 64, phase = 60)
        performMortalEncodeTest("0x8502", period = 64, phase = 40)
    }

    private fun performMortalDecodeTest(inHex: String, period: Int, phase: Int) {
        val decoded = EraType.fromHex(runtime, inHex)

        assertInstance<Era.Mortal>(decoded)

        assertEquals(period, decoded.period)
        assertEquals(phase, decoded.phase)
    }

    private fun performMortalEncodeTest(inHex: String, period: Int, phase: Int) {
        val encoded = EraType.toHex(runtime, Era.Mortal(period, phase))

        assertEquals(inHex, encoded)
    }
}