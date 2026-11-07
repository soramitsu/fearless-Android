package jp.co.soramitsu.common.data.storage.envelope

import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletRootKeyKindTest {

    private val l = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val n = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)

    private fun littleEndian(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val bigEndian = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
        return (ByteArray(32 - bigEndian.size) + bigEndian).reversedArray()
    }

    @Test
    fun `an sr25519 key reaches one form whether or not the scalar was reduced and whatever its nonce`() {
        val scalar = BigInteger("1234567890123456789012345678901234567890")
        val canonical = littleEndian(scalar)
        val unreduced = littleEndian(scalar.add(l))
        val withNonceA = canonical + ByteArray(32) { 1 }
        val withNonceB = canonical + ByteArray(32) { 2 }

        val expected = WalletRootKeyKind.SR25519.canonical(canonical)
        assertArrayEquals(canonical, expected)
        assertArrayEquals(expected, WalletRootKeyKind.SR25519.canonical(unreduced))
        assertArrayEquals(expected, WalletRootKeyKind.SR25519.canonical(withNonceA))
        assertArrayEquals(expected, WalletRootKeyKind.SR25519.canonical(withNonceB))
    }

    @Test
    fun `an ed25519 key is its 32-byte seed, also from a 64-byte secret`() {
        val seed = SyntheticSecrets.bytes(5)
        assertArrayEquals(seed, WalletRootKeyKind.ED25519.canonical(seed))
        assertArrayEquals(seed, WalletRootKeyKind.ED25519.canonical(seed + SyntheticSecrets.bytes(6)))
        assertNull(WalletRootKeyKind.ED25519.canonical(ByteArray(31)))
    }

    @Test
    fun `a secp256k1 key is a fixed 32-byte scalar mod n`() {
        val key = SyntheticSecrets.bytes(9)
        val canonical = WalletRootKeyKind.SECP256K1.canonical(key)!!
        assertEquals(32, canonical.size)
        assertArrayEquals(canonical, WalletRootKeyKind.SECP256K1.canonical(byteArrayOf(0) + key))
        val small = byteArrayOf(1, 2, 3)
        assertArrayEquals(ByteArray(29) + small, WalletRootKeyKind.SECP256K1.canonical(small))
        val overflow = n.add(BigInteger.valueOf(7)).toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
        assertArrayEquals(ByteArray(31) + byteArrayOf(7), WalletRootKeyKind.SECP256K1.canonical(overflow))
        assertNull(WalletRootKeyKind.SECP256K1.canonical(n.toByteArray()))
        assertNull(WalletRootKeyKind.SECP256K1.canonical(ByteArray(34) { 1 }))
    }
}
