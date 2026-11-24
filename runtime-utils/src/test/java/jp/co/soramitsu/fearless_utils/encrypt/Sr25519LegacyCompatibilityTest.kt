package jp.co.soramitsu.fearless_utils.encrypt

import java.math.BigInteger
import java.util.Random
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.DEV_SEED
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.hex
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.publicPart
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.secretPart
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * Differential test of the current libsr25519java (schnorrkel 0.11) against the
 * library that Android 4.3.1 and earlier shipped (schnorrkel 0.9.1), both
 * loaded side by side in this JVM. It runs only when Gradle gets
 * `-Psr25519.legacyLibrary=<host build of the legacy crate>`; the script
 * scripts/verify-sr25519-legacy-compatibility.sh builds that library from Git
 * history and runs this test. All keys are synthetic.
 *
 * The legacy library aborts the process on a chain code that is not 32 bytes,
 * so this test never passes one to it.
 */
class Sr25519LegacyCompatibilityTest {

    private val random = Random(0x5EED_2551_9L)

    @Before
    fun loadBothLibraries() {
        Assume.assumeTrue(
            "legacy sr25519 library not configured: run scripts/verify-sr25519-legacy-compatibility.sh",
            Sr25519HostLibrary.legacyFile() != null
        )
        Sr25519HostLibrary.assumeAvailable()
    }

    @Test
    fun `the legacy and current libraries are distinct builds`() {
        val nonCanonical = ByteArray(64) { 0xff.toByte() }

        assertNotNull("legacy accepted a non-canonical Ed25519-form scalar", legacy.fromEd25519Bytes(nonCanonical))
        assertNull(current.fromEd25519Bytes(nonCanonical))
    }

    @Test
    fun `keypairs from seeds are identical`() {
        val seeds = listOf(DEV_SEED, ByteArray(32), ByteArray(32) { 0xff.toByte() }) + List(256) { bytes(32) }

        seeds.forEachIndexed { index, seed ->
            assertTrue("seed $index", legacy.keypairFromSeed(seed).contentEquals(current.keypairFromSeed(seed)))
        }
    }

    @Test
    fun `hard derivation is identical`() {
        repeat(128) { index ->
            val keypair = current.keypairFromSeed(bytes(32))
            val chainCode = bytes(32)

            val expected = legacy.deriveKeypairHard(keypair, chainCode)
            assertTrue("hard $index", expected.contentEquals(current.deriveKeypairHard(keypair, chainCode)))
        }
    }

    @Test
    fun `soft derivation gives identical scalars and public keys`() {
        repeat(128) { index ->
            val keypair = requireNotNull(current.keypairFromSeed(bytes(32)))
            val chainCode = bytes(32)

            val expected = requireNotNull(legacy.deriveKeypairSoft(keypair, chainCode))
            val actual = requireNotNull(current.deriveKeypairSoft(keypair, chainCode))
            assertTrue("soft scalar $index", expected.copyOfRange(0, 32).contentEquals(actual.copyOfRange(0, 32)))
            assertTrue("soft public $index", expected.publicPart().contentEquals(actual.publicPart()))
            assertTrue(
                "public derivation $index",
                actual.publicPart().contentEquals(legacy.derivePublicSoft(keypair.publicPart(), chainCode))
            )
        }
    }

    @Test
    fun `public soft derivation is identical`() {
        repeat(128) { index ->
            val public = requireNotNull(current.keypairFromSeed(bytes(32))).publicPart()
            val chainCode = bytes(32)

            val expected = legacy.derivePublicSoft(public, chainCode)
            assertTrue("public soft $index", expected.contentEquals(current.derivePublicSoft(public, chainCode)))
        }
    }

    @Test
    fun `multi-level substrate paths derive identical keys`() {
        val seed = SubstrateSeedFactory.deriveSeed(DEV_PHRASE, null).seed.copyOf(32)
        val paths = listOf(
            "/foo" to "40b9675df90efa6069ff623b0fdfcf706cd47ca7452a5056c7ad58194d23440a",
            "//foo" to "547d4a55642ec7ebadc0bd29b6e570b8c926059b3c0655d4948075e9a7e6f31e",
            "//foo/bar" to "3841947ffcde6f5fef26fb68b59bb8665637e30e32ec2051f99cf6b9c674fe09",
            "/foo//bar" to "dc142f7476a7b0aa262aeccf207f1d18daa90762db393006741e8a31f39dbc53",
            "//foo/bar//42/69" to "a2e56b06407a6d1e819d2fc33fa0ec604b29c2e868b70b3696bb049b8725934b",
            "//Alice" to "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d",
            "//polkadot/0" to null,
            "//kusama//staking/1/2//3" to null,
        )

        paths.forEach { (path, expectedPublic) ->
            val junctions = SubstrateJunctionDecoder.decode(path).junctions
            val legacyKey = derive(legacy, seed, junctions.map { it.type to it.chaincode })
            val currentKey = derive(current, seed, junctions.map { it.type to it.chaincode })

            assertKeysMatch(path, legacyKey, currentKey)
            expectedPublic?.let { assertEquals(path, it, currentKey.publicPart().hex()) }
        }
    }

    @Test
    fun `random multi-level paths derive identical keys`() {
        repeat(64) { index ->
            val seed = bytes(32)
            val steps = List(1 + random.nextInt(6)) {
                (if (random.nextBoolean()) JunctionType.SOFT else JunctionType.HARD) to bytes(32)
            }

            assertKeysMatch("path $index", derive(legacy, seed, steps), derive(current, seed, steps))
        }
    }

    @Test
    fun `signatures verify across both libraries`() {
        repeat(64) { index ->
            val keypair = requireNotNull(current.keypairFromSeed(bytes(32)))
            val other = requireNotNull(current.keypairFromSeed(bytes(32)))
            val message = bytes(random.nextInt(200))

            val legacySignature = legacy.sign(keypair.publicPart(), keypair.secretPart(), message)
            val currentSignature = current.sign(keypair.publicPart(), keypair.secretPart(), message)

            assertTrue("legacy -> current $index", current.verify(legacySignature, message, keypair.publicPart()))
            assertTrue("current -> legacy $index", legacy.verify(currentSignature, message, keypair.publicPart()))
            val tampered = message + byteArrayOf(1)
            assertFalse(current.verify(legacySignature, tampered, keypair.publicPart()))
            assertFalse(legacy.verify(currentSignature, tampered, keypair.publicPart()))
            assertFalse(current.verify(legacySignature, message, other.publicPart()))
            assertFalse(legacy.verify(currentSignature, message, other.publicPart()))
        }
    }

    @Test
    fun `verification agrees on malformed signatures and keys`() {
        val keypair = requireNotNull(current.keypairFromSeed(DEV_SEED))
        val message = "malformed".toByteArray()
        val valid = requireNotNull(current.sign(keypair.publicPart(), keypair.secretPart(), message))

        repeat(512) { index ->
            val signature = when (index % 4) {
                0 -> bytes(64)
                1 -> bytes(64).also { it[63] = (it[63].toInt() or 0x80).toByte() }
                2 -> valid.copyOf().also { it[random.nextInt(64)] = random.nextInt(256).toByte() }
                else -> bytes(random.nextInt(70))
            }
            val public = if (index % 3 == 0) bytes(32) else keypair.publicPart()

            assertEquals(
                "verify $index",
                outcome { legacy.verify(signature, message, public) },
                outcome { current.verify(signature, message, public) }
            )
        }
    }

    @Test
    fun `public key validation agrees`() {
        val chainCode = bytes(32)

        repeat(512) { index ->
            val candidate = bytes(32)
            val expected = outcome { legacy.derivePublicSoft(candidate, chainCode)?.hex() }

            assertEquals("public $index", expected, outcome { current.derivePublicSoft(candidate, chainCode)?.hex() })
        }
    }

    @Test
    fun `secret key validation agrees`() {
        val public = requireNotNull(current.keypairFromSeed(DEV_SEED)).publicPart()

        repeat(512) { index ->
            val secret = bytes(64).also { if (index % 2 == 0) it[31] = (it[31].toInt() and 0x0f).toByte() }

            val legacyOutcome = outcome { legacy.toEd25519Bytes(secret)?.let(::digest) }
            assertEquals("ed25519 form $index", legacyOutcome, outcome { current.toEd25519Bytes(secret)?.let(::digest) })
            val legacySigned = outcome { legacy.sign(public, secret, MESSAGE)?.size }
            assertEquals("sign $index", legacySigned, outcome { current.sign(public, secret, MESSAGE)?.size })
        }
    }

    @Test
    fun `ed25519 conversion agrees on canonical scalars and refuses the rest`() {
        repeat(512) { index ->
            val expanded = bytes(64)
            if (index % 2 == 0) expanded[31] = (expanded[31].toInt() and 0x7f).toByte()

            val legacyResult = legacy.fromEd25519Bytes(expanded)
            val currentResult = current.fromEd25519Bytes(expanded)
            if (isCanonicalAfterCofactorDivision(expanded)) {
                assertTrue("canonical $index", legacyResult.contentEquals(currentResult))
            } else {
                assertNotNull("legacy accepted non-canonical $index", legacyResult)
                assertNull("current refuses non-canonical $index", currentResult)
            }
        }
        repeat(64) { index ->
            val secret = requireNotNull(current.keypairFromSeed(bytes(32))).secretPart()
            val expanded = legacy.toEd25519Bytes(secret)
            assertTrue("round trip $index", secret.contentEquals(current.fromEd25519Bytes(expanded)))
        }
    }

    @Test
    fun `refused inputs throw the same exceptions`() {
        val keypair = requireNotNull(current.keypairFromSeed(DEV_SEED))

        (0..64).filter { it != 32 }.forEach { length ->
            val seed = ByteArray(length)
            assertEquals("seed $length", outcome { legacy.keypairFromSeed(seed) }, outcome { current.keypairFromSeed(seed) })
        }
        assertEquals(outcome { legacy.keypairFromSeed(null) }, outcome { current.keypairFromSeed(null) })
        assertEquals(
            outcome { legacy.sign(keypair.publicPart(), null, MESSAGE) },
            outcome { current.sign(keypair.publicPart(), null, MESSAGE) }
        )
        assertEquals(
            outcome { legacy.verify(null, MESSAGE, keypair.publicPart()) },
            outcome { current.verify(null, MESSAGE, keypair.publicPart()) }
        )
        val badPair = keypair.copyOf(95)
        val legacyPair = outcome { legacy.deriveKeypairHard(badPair, ByteArray(32)) }
        assertTrue(legacyPair.startsWith("java.lang.Exception: Provided pair is invalid: "))
        assertEquals(
            "java.lang.Exception: Provided pair is invalid",
            outcome { current.deriveKeypairHard(badPair, ByteArray(32)) }
        )
    }

    private fun derive(
        library: IsolatedSr25519,
        seed: ByteArray,
        steps: List<Pair<JunctionType, ByteArray>>
    ): ByteArray = steps.fold(requireNotNull(library.keypairFromSeed(seed))) { keypair, (type, chainCode) ->
        requireNotNull(
            when (type) {
                JunctionType.HARD -> library.deriveKeypairHard(keypair, chainCode)
                JunctionType.SOFT -> library.deriveKeypairSoft(keypair, chainCode)
            }
        )
    }

    /** Scalar and public key must match; a soft step draws a random nonce in both libraries. */
    private fun assertKeysMatch(label: String, legacyKey: ByteArray, currentKey: ByteArray) {
        assertTrue("$label scalar", legacyKey.copyOfRange(0, 32).contentEquals(currentKey.copyOfRange(0, 32)))
        assertTrue("$label public", legacyKey.publicPart().contentEquals(currentKey.publicPart()))
    }

    private fun bytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun digest(bytes: ByteArray): String = Sr25519NativeTest.sha256(bytes)

    /** The result, or the exception class and message, as a comparable string. */
    private fun outcome(block: () -> Any?): String = try {
        "value: " + block().toString()
    } catch (failure: Exception) {
        failure.javaClass.name + ": " + failure.message
    }

    private fun isCanonicalAfterCofactorDivision(expanded: ByteArray): Boolean {
        val littleEndian = expanded.copyOfRange(0, 32).reversedArray()
        return BigInteger(1, littleEndian).shiftRight(3) < GROUP_ORDER
    }

    private companion object {
        const val DEV_PHRASE = Sr25519NativeTest.DEV_PHRASE
        val MESSAGE = "compatibility".toByteArray()
        val GROUP_ORDER: BigInteger = BigInteger.ONE.shiftLeft(252)
            .add(BigInteger("27742317777372353535851937790883648493"))

        val legacy: IsolatedSr25519 by lazy { IsolatedSr25519(requireNotNull(Sr25519HostLibrary.legacyFile())) }
        val current: IsolatedSr25519 by lazy { IsolatedSr25519(requireNotNull(Sr25519HostLibrary.file())) }
    }
}
