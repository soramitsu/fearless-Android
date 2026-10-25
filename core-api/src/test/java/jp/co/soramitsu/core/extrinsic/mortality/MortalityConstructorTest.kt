package jp.co.soramitsu.core.extrinsic.mortality

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class MortalityConstructorTest {

    @Test
    fun `constructs five minute mortal era from current block and block hash`() {
        val blockHash = "0x" + "11".repeat(32)

        val mortality = constructMortalEra(
            currentBlock = BigInteger.valueOf(97_506),
            blockHashCount = null,
            expectedBlockTimeInMillis = BigInteger.valueOf(6_000),
            blockHash = blockHash
        )

        val era = requireNotNull(mortality).era

        assertTrue(era is Era.Mortal)
        era as Era.Mortal
        assertEquals(64, era.period)
        assertEquals(34, era.phase)
        assertArrayEquals(blockHash.fromHex(), mortality.blockHash)
        assertEquals(ExtrinsicValidity(BigInteger.valueOf(97_506), 64), mortality.validity)
    }

    @Test
    fun `birth block is the latest block with the era phase`() {
        assertEquals(BigInteger.valueOf(10_000), mortalBirthBlock(BigInteger.valueOf(10_000), Era.Mortal(64, 16)))
        assertEquals(BigInteger.valueOf(10_000), mortalBirthBlock(BigInteger.valueOf(10_063), Era.Mortal(64, 16)))
        assertEquals(BigInteger.valueOf(10_064), mortalBirthBlock(BigInteger.valueOf(10_064), Era.Mortal(64, 16)))
        // Periods above 4096 blocks quantize the phase, so the birth can precede the current block.
        val quantized = Era.getEraFromBlockPeriod(8_193, 8_192)
        assertEquals(BigInteger.valueOf(8_192), mortalBirthBlock(BigInteger.valueOf(8_193), quantized))
    }

    @Test
    fun `validity is unknown for an immortal era or a missing birth block`() {
        assertNull(Mortality(Era.Immortal, ByteArray(32)).validity)
        assertNull(Mortality(Era.Mortal(64, 12), ByteArray(32)).validity)
    }

    @Test
    fun `validity window ends one period after its birth block`() {
        val window = ExtrinsicValidity(BigInteger.valueOf(500), 32)

        assertEquals(BigInteger.valueOf(532), window.deathBlock)
        assertThrows(IllegalArgumentException::class.java) { ExtrinsicValidity(BigInteger.valueOf(-1), 32) }
        assertThrows(IllegalArgumentException::class.java) { ExtrinsicValidity(BigInteger.ONE, 2) }
    }

    @Test
    fun `caps mortal period by block hash count`() {
        val mortality = constructMortalEra(
            currentBlock = BigInteger.TEN,
            blockHashCount = BigInteger.valueOf(128),
            expectedBlockTimeInMillis = BigInteger.valueOf(1_000),
            blockHash = "0x" + "22".repeat(32)
        )

        val era = requireNotNull(mortality).era as Era.Mortal

        assertEquals(128, era.period)
        assertEquals(10, era.phase)
    }

    @Test
    fun `rejects block hash count smaller than substrate minimum`() {
        val mortality = constructMortalEra(
            currentBlock = BigInteger.TEN,
            blockHashCount = BigInteger.valueOf(3),
            expectedBlockTimeInMillis = BigInteger.valueOf(6_000),
            blockHash = "0x" + "33".repeat(32)
        )

        assertNull(mortality)
    }

    @Test
    fun `rejects non positive expected block time`() {
        val mortality = constructMortalEra(
            currentBlock = BigInteger.TEN,
            blockHashCount = null,
            expectedBlockTimeInMillis = BigInteger.ZERO,
            blockHash = "0x" + "44".repeat(32)
        )

        assertNull(mortality)
    }

    @Test
    fun `rejects oversized current block numbers`() {
        val mortality = constructMortalEra(
            currentBlock = BigInteger.valueOf(Int.MAX_VALUE.toLong()) + BigInteger.ONE,
            blockHashCount = null,
            expectedBlockTimeInMillis = BigInteger.valueOf(6_000),
            blockHash = "0x" + "55".repeat(32)
        )

        assertNull(mortality)
    }

    @Test
    fun `accepts largest current block number representable by mortal era`() {
        val mortality = constructMortalEra(
            currentBlock = BigInteger.valueOf(Int.MAX_VALUE.toLong()),
            blockHashCount = null,
            expectedBlockTimeInMillis = BigInteger.valueOf(6_000),
            blockHash = "0x" + "66".repeat(32)
        )

        assertTrue(requireNotNull(mortality).era is Era.Mortal)
    }

    @Test
    fun `rejects malformed block hashes`() {
        val mortality = constructMortalEra(
            currentBlock = BigInteger.TEN,
            blockHashCount = null,
            expectedBlockTimeInMillis = BigInteger.valueOf(6_000),
            blockHash = "0x1234"
        )

        assertNull(mortality)
    }
}
