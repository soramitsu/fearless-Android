package jp.co.soramitsu.fearless_utils.encrypt.ton

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.crypto.Ed25519
import java.security.SecureRandom
import java.util.Random

/** TonEd25519 against ton-kotlin 0.3.1 (PrivateKeyEd25519, org.ton.crypto.Ed25519 on curve25519-kotlin 0.0.4). */
class TonEd25519Test {

    private val random = Random(7_432)

    @Test
    fun `public keys and signatures over cell hashes match ton-kotlin`() {
        var tonKotlinRejected = 0
        repeat(400) { index ->
            val seed = bytes(32)
            val legacy = PrivateKeyEd25519(seed)
            val publicKey = TonEd25519.publicKey(seed)
            assertArrayEquals("$index", legacy.publicKey().key.toByteArray(), publicKey)

            val cellHash = randomCell(depth = 0).hash().toByteArray()
            assertEquals(32, cellHash.size)
            val signature = TonEd25519.sign(seed, cellHash)
            assertArrayEquals("$index", legacy.sign(cellHash), signature)
            assertTrue("$index", TonEd25519.verify(publicKey, cellHash, signature))
            if (!Ed25519.verify(publicKey, cellHash, signature)) tonKotlinRejected++

            val message = bytes(random.nextInt(300))
            assertArrayEquals("$index", Ed25519.sign(seed, message), TonEd25519.sign(seed, message))
            assertFalse(TonEd25519.verify(publicKey, message + 1, TonEd25519.sign(seed, message)))
        }
        // Every valid signature verifies here. ton-kotlin's verifier rejected a share of them (fixed upstream in 0.0.7).
        println("ton-kotlin 0.3.1 Ed25519.verify rejected $tonKotlinRejected of 400 valid signatures")
    }

    @Test
    fun `shared keys match ton-kotlin and agree from both sides`() {
        repeat(150) { index ->
            val mine = bytes(32)
            val theirs = bytes(32)
            val minePublic = TonEd25519.publicKey(mine)
            val theirsPublic = TonEd25519.publicKey(theirs)
            val shared = TonEd25519.sharedKey(mine, theirsPublic)
            assertArrayEquals("$index", Ed25519.sharedKey(mine, theirsPublic), shared)
            assertArrayEquals("$index", shared, TonEd25519.sharedKey(theirs, minePublic))
        }
    }

    @Test
    fun `shared keys reject what ton-kotlin rejects`() {
        val mine = bytes(32)
        val offCurve = (0 until 64).map { bytes(32) }.first { candidate ->
            runCatching { Ed25519.sharedKey(mine, candidate) }.isFailure
        }
        assertTrue(runCatching { TonEd25519.sharedKey(mine, offCurve) }.isFailure)

        val identity = ByteArray(32).also { it[0] = 1 }
        assertTrue(runCatching { Ed25519.sharedKey(mine, identity) }.isFailure)
        assertTrue(runCatching { TonEd25519.sharedKey(mine, identity) }.isFailure)

        assertThrows(IllegalArgumentException::class.java) { TonEd25519.sharedKey(ByteArray(31), TonEd25519.publicKey(mine)) }
    }

    @Test
    fun `short and long public keys are padded or cut like ton-kotlin`() {
        val mine = bytes(32)
        val theirs = TonEd25519.publicKey(bytes(32))
        assertArrayEquals(Ed25519.sharedKey(mine, theirs + byteArrayOf(9, 9)), TonEd25519.sharedKey(mine, theirs + byteArrayOf(9, 9)))
        val short = theirs.copyOf(31)
        val legacy = runCatching { Ed25519.sharedKey(mine, short) }
        val actual = runCatching { TonEd25519.sharedKey(mine, short) }
        assertEquals(legacy.isSuccess, actual.isSuccess)
        if (legacy.isSuccess) assertArrayEquals(legacy.getOrThrow(), actual.getOrThrow())
    }

    @Test
    fun `private keys parse like PrivateKeyEd25519_of`() {
        val seed = bytes(32)
        assertArrayEquals(PrivateKeyEd25519.of(seed).key.toByteArray(), TonEd25519.privateKeyOf(seed))

        val boxed = PrivateKeyEd25519.encodeToByteArray(PrivateKeyEd25519(seed), true)
        assertEquals(36, boxed.size)
        assertArrayEquals(PrivateKeyEd25519.of(boxed).key.toByteArray(), TonEd25519.privateKeyOf(boxed))
        assertArrayEquals(seed, TonEd25519.privateKeyOf(boxed))

        val wrongId = boxed.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertTrue(runCatching { PrivateKeyEd25519.of(wrongId) }.isFailure)
        assertThrows(IllegalArgumentException::class.java) { TonEd25519.privateKeyOf(wrongId) }
        for (size in listOf(0, 31, 33, 35, 37, 64)) {
            assertTrue(runCatching { PrivateKeyEd25519.of(ByteArray(size)) }.isFailure)
            assertThrows(IllegalArgumentException::class.java) { TonEd25519.privateKeyOf(ByteArray(size)) }
        }
        val copy = TonEd25519.privateKeyOf(seed)
        copy.fill(0)
        assertNotEquals(seed.toList(), copy.toList())
    }

    @Test
    fun `signing needs a 32-byte private key like ton-kotlin`() {
        for (size in listOf(0, 31, 33, 64)) {
            assertTrue(runCatching { Ed25519.sign(ByteArray(size), byteArrayOf(1)) }.isFailure)
            assertThrows(IllegalArgumentException::class.java) { TonEd25519.sign(ByteArray(size), byteArrayOf(1)) }
            assertThrows(IllegalArgumentException::class.java) { TonEd25519.publicKey(ByteArray(size)) }
        }
    }

    @Test
    fun `fresh private keys are random 32-byte seeds`() {
        val first = TonEd25519.generatePrivateKey()
        val second = TonEd25519.generatePrivateKey(SecureRandom())
        assertEquals(TonEd25519.KEY_SIZE_BYTES, first.size)
        assertNotEquals(first.toList(), second.toList())
        assertArrayEquals(PrivateKeyEd25519(first).publicKey().key.toByteArray(), TonEd25519.publicKey(first))
    }

    // Random cells with random bits and up to two levels of references, as transfer bodies are built.
    private fun randomCell(depth: Int): Cell = CellBuilder.createCell {
        storeBytes(bytes(random.nextInt(100)))
        repeat(random.nextInt(8)) { storeBit(random.nextBoolean()) }
        if (depth < 2) repeat(random.nextInt(3)) { storeRef(randomCell(depth + 1)) }
    }

    private fun bytes(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)
}
