package jp.co.soramitsu.walletconnect.impl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WcEncodingTest {

    @Test
    fun `hex round trips and refuses anything else`() {
        assertEquals("00ff10ab", WcEncoding.hex(byteArrayOf(0, -1, 16, -85)))
        assertArrayEquals(byteArrayOf(0, -1, 16, -85), WcEncoding.unhex("00FF10ab"))
        assertThrows(IllegalArgumentException::class.java) { WcEncoding.unhex("abc") }
        assertThrows(IllegalArgumentException::class.java) { WcEncoding.unhex("zz") }
    }

    @Test
    fun `isHex checks the exact byte length`() {
        assertTrue(WcEncoding.isHex("ab".repeat(32), 32))
        assertFalse(WcEncoding.isHex("ab".repeat(31), 32))
        assertFalse(WcEncoding.isHex("xy".repeat(32), 32))
        assertFalse(WcEncoding.isHex(null, 32))
    }

    @Test
    fun `base64 variants round trip`() {
        val bytes = byteArrayOf(-5, -17, 0, 1, 2)
        assertArrayEquals(bytes, WcEncoding.unbase64(WcEncoding.base64(bytes)))
        assertEquals("--8AAQI", WcEncoding.base64Url(bytes))
        assertArrayEquals(bytes, WcEncoding.unbase64Url("--8AAQI"))
    }

    @Test
    fun `base58 uses the bitcoin alphabet and keeps leading zeros`() {
        assertEquals("2NEpo7TZRRrLZSi2U", WcEncoding.base58("Hello World!".toByteArray()))
        assertEquals("112", WcEncoding.base58(byteArrayOf(0, 0, 1)))
        assertEquals("", WcEncoding.base58(ByteArray(0)))
    }
}
