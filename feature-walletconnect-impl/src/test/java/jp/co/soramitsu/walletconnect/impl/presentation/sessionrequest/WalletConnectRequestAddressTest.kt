package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import jp.co.soramitsu.fearless_utils.encrypt.Base58
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletConnectRequestAddressTest {
    private val key = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun exactSubstrateAddressUsesDecodedAccountIdentity() {
        val address = key.toAddress(0)
        assertTrue(matchesWalletConnectRequestAddress(false, address, address))
        assertArrayEquals(key, walletConnectRequestAccountId(false, address))
    }

    @Test
    fun substratePrefixCanDifferForSameAccountIdentity() {
        assertTrue(matchesWalletConnectRequestAddress(false, key.toAddress(0), key.toAddress(42)))
    }

    @Test
    fun differentlyCasedSubstrateAddressCannotMatch() {
        val address = key.toAddress(0)
        val changed = address.map {
            if (it.isLowerCase()) {
                it.uppercaseChar()
            } else if (it.isUpperCase()) {
                it.lowercaseChar()
            } else {
                it
            }
        }.joinToString("")
        assertNotEquals(address, changed)
        assertFalse(matchesWalletConnectRequestAddress(false, address, changed))
    }

    @Test
    fun anotherSubstrateAccountCannotMatch() {
        assertFalse(matchesWalletConnectRequestAddress(false, key.toAddress(0), ByteArray(32) { 9 }.toAddress(0)))
    }

    @Test
    fun trailingDecodedBytesCannotPassLegacySs58Decoder() {
        val base58 = Base58()
        val address = key.toAddress(0)
        val appended = base58.encode(base58.decode(address) + byteArrayOf(1))
        assertNull(walletConnectRequestAccountId(false, appended))
        assertFalse(matchesWalletConnectRequestAddress(false, address, appended))
    }

    @Test
    fun validEvmHexIsCaseInsensitiveAndUsesTwentyDecodedBytes() {
        val lower = "0xabcdef0123456789abcdef0123456789abcdef01"
        assertTrue(matchesWalletConnectRequestAddress(true, lower, lower.uppercase()))
        assertEquals(20, walletConnectRequestAccountId(true, lower)!!.size)
        assertEquals(0xab.toByte(), walletConnectRequestAccountId(true, lower)!![0])
    }

    @Test
    fun evmPrefixIsOptionalWithoutChangingIdentity() {
        val bare = "abcdef0123456789abcdef0123456789abcdef01"
        assertTrue(matchesWalletConnectRequestAddress(true, bare, "0x" + bare))
    }

    @Test
    fun differentEvmAccountCannotMatch() {
        assertFalse(matchesWalletConnectRequestAddress(true, "0x" + "ab".repeat(20), "0x" + "cd".repeat(20)))
    }

    @Test
    fun sameMalformedEvmAddressCannotMatchItself() {
        for (address in listOf("0x123", "0x" + "ab".repeat(19), "0x" + "zz".repeat(20), " " + "ab".repeat(20), "0x0X" + "ab".repeat(20), "0X0x" + "ab".repeat(20), "0x0x" + "ab".repeat(20))) {
            assertNull(walletConnectRequestAccountId(true, address))
            assertFalse(matchesWalletConnectRequestAddress(true, address, address))
        }
    }

    @Test
    fun missingOrMalformedSubstrateAddressCannotMatch() {
        assertFalse(matchesWalletConnectRequestAddress(false, null, null))
        assertFalse(matchesWalletConnectRequestAddress(false, "bad address", "bad address"))
        assertFalse(matchesWalletConnectRequestAddress(false, key.toAddress(0), null))
    }

    @Test
    fun addressCannotBeReinterpretedAsAnotherNetwork() {
        assertNull(walletConnectRequestAccountId(true, key.toAddress(0)))
        assertNull(walletConnectRequestAccountId(false, "0x" + "ab".repeat(20)))
    }
}
