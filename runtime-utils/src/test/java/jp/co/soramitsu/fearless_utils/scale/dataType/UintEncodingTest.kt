package jp.co.soramitsu.fearless_utils.scale.dataType

import com.google.gson.Gson
import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.BaseTypeTest
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.UIntType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/** AL-6: values with the top bit set (u64 >= 2^63, u128 >= 2^127, u8 >= 128, ...) used to overflow the write buffer. */
class UintEncodingTest : BaseTypeTest() {

    private class Vectors(val cases: List<Case>)

    private class Case(val sizeBytes: Int, val value: String, val encoded: String)

    private val vectors = Gson().fromJson(getResourceReader("parity/uint-vectors.json"), Vectors::class.java)

    @Test
    fun `writes match production 4_2_0 vectors including top-bit values`() {
        vectors.cases.forEach { case ->
            assertEquals("u${case.sizeBytes * 8} ${case.value}", case.encoded, write(case.sizeBytes, BigInteger(case.value)))
        }
    }

    @Test
    fun `reads round-trip every vector`() {
        vectors.cases.forEach { case ->
            val read = uint(case.sizeBytes).read(ScaleCodecReader(case.encoded.fromHex()))

            assertEquals("u${case.sizeBytes * 8} ${case.encoded}", BigInteger(case.value), read)
        }
    }

    @Test
    fun `runtime unsigned types use the same encoding`() {
        vectors.cases.forEach { case ->
            val type = UIntType(case.sizeBytes * 8)

            assertEquals("${type.name} ${case.value}", case.encoded, type.toHex(runtime, BigInteger(case.value)))
        }
    }

    @Test
    fun `refuses values that do not fit instead of truncating`() {
        assertThrows<IllegalArgumentException> { write(1, BigInteger.valueOf(256)) }
        assertThrows<IllegalArgumentException> { write(8, BigInteger.ONE.shiftLeft(64)) }
        assertThrows<IllegalArgumentException> { write(16, BigInteger.ONE.shiftLeft(128)) }
    }

    @Test
    fun `refuses negative values instead of encoding a different number`() {
        assertThrows<IllegalArgumentException> { write(1, BigInteger.valueOf(-1)) }
        assertThrows<IllegalArgumentException> { write(16, BigInteger.valueOf(-1)) }
    }

    private fun write(size: Int, value: BigInteger): String = ByteArrayOutputStream().use { stream ->
        uint(size).write(ScaleCodecWriter(stream), value)
        stream.toByteArray().toHexString(withPrefix = true)
    }
}
