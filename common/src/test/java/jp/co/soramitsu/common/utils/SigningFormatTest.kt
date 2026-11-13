package jp.co.soramitsu.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

class SigningFormatTest {
    @Test
    fun `token amounts retain the smallest unit`() {
        assertTrue(BigInteger.ONE.formatExactTokenAmount(18, "eth").contains("000000000000000001"))
        assertTrue(BigInteger.ZERO.formatExactTokenAmount(6, "usdc").endsWith(" USDC"))
    }

    @Test
    fun `readable text requires valid UTF8 and rejects bidi and control bytes`() {
        assertEquals("hello\n世界", readableUtf8Text("hello\n世界".toByteArray()))
        assertNull(readableUtf8Text(byteArrayOf(0xc3.toByte(), 0x28)))
        assertNull(readableUtf8Text(byteArrayOf()))
        assertNull(readableUtf8Text("pay\u202e123".toByteArray()))
        assertFalse(isDisplayableText("\u0000"))
        assertFalse(isDisplayableText(String(Character.toChars(0xE0001))))
        assertTrue(isDisplayableText("a\tb\n"))
    }

    @Test
    fun `expiry rendering rejects values that overflow milliseconds`() {
        assertTrue(formatSigningTime(BigInteger("1800000000")).isNotBlank())
        try {
            formatSigningTime(BigInteger.ONE.shiftLeft(256))
            fail("overflowed timestamp must not wrap")
        } catch (_: ArithmeticException) {
            // A caller must retain the original value instead of showing an unrelated date.
        }
    }
}
