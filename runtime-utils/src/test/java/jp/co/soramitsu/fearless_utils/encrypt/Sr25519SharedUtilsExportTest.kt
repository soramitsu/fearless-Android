package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.DEV_SEED
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.chainCode
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.publicPart
import jp.co.soramitsu.fearless_utils.encrypt.Sr25519NativeTest.Companion.secretPart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import jp.co.soramitsu.shared_utils.encrypt.Sr25519 as SharedSr25519

/** The `shared_utils` export set behaves exactly like the `fearless_utils` one. */
class Sr25519SharedUtilsExportTest {

    @Before
    fun requireHostLibrary() = Sr25519HostLibrary.assumeAvailable()

    @Test
    fun `deterministic exports agree across both package names`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)

        assertArrayEquals(keypair, SharedSr25519.keypairFromSeed(DEV_SEED))
        assertArrayEquals(
            Sr25519.deriveKeypairHard(keypair, chainCode("Alice")),
            SharedSr25519.deriveKeypairHard(keypair, chainCode("Alice"))
        )
        assertArrayEquals(
            Sr25519.derivePublicSoft(keypair.publicPart(), chainCode("foo")),
            SharedSr25519.derivePublicSoft(keypair.publicPart(), chainCode("foo"))
        )
        val soft = SharedSr25519.deriveKeypairSoft(keypair, chainCode("foo"))
        assertArrayEquals(Sr25519.deriveKeypairSoft(keypair, chainCode("foo")).publicPart(), soft.publicPart())
        val expanded = SharedSr25519.toEd25519Bytes(keypair.secretPart())
        assertArrayEquals(Sr25519.toEd25519Bytes(keypair.secretPart()), expanded)
        assertArrayEquals(keypair.secretPart(), SharedSr25519.fromEd25519Bytes(expanded))
        assertEquals(Sr25519.test("shared"), SharedSr25519.test("shared"))
    }

    @Test
    fun `signatures cross between both package names`() {
        val keypair = Sr25519.keypairFromSeed(DEV_SEED)
        val message = "shared utils".toByteArray()

        val shared = SharedSr25519.sign(keypair.publicPart(), keypair.secretPart(), message)
        val fearless = Sr25519.sign(keypair.publicPart(), keypair.secretPart(), message)

        assertTrue(Sr25519.verify(shared, message, keypair.publicPart()))
        assertTrue(SharedSr25519.verify(fearless, message, keypair.publicPart()))
    }

    @Test
    fun `shared exports throw the same exceptions`() {
        val failure = runCatching { SharedSr25519.keypairFromSeed(ByteArray(31)) }.exceptionOrNull()

        assertEquals(Exception::class.java, failure?.javaClass)
        assertEquals("Provided seed is invalid", failure?.message)
    }
}
