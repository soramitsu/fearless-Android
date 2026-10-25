package jp.co.soramitsu.core.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressExtensionsTest {

    @Test
    fun `mixed-case addresses with a correct EIP-55 checksum are valid`() {
        // The test vectors of EIP-55.
        listOf(
            "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
            "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB",
            "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb"
        ).forEach { assertTrue(it, it.isEvmChecksumValid()) }
    }

    @Test
    fun `a mistyped letter case breaks the checksum`() {
        assertFalse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAeD".isEvmChecksumValid())
        assertFalse("0xFB6916095ca1df60bB79Ce92cE3Ea74c37c5d359".isEvmChecksumValid())
    }

    @Test
    fun `single-case addresses carry no checksum and are accepted`() {
        assertTrue("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed".isEvmChecksumValid())
        assertTrue("0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED".isEvmChecksumValid())
    }

    @Test
    fun `anything but a 0x-prefixed 20-byte hex address is invalid`() {
        assertFalse(" 0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed".isEvmChecksumValid())
        assertFalse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAe".isEvmChecksumValid())
        assertFalse("5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed00".isEvmChecksumValid())
        assertFalse("0xZaAeb6053F3E94C9b9A09f33669435E7Ef1BeAed".isEvmChecksumValid())
    }
}
