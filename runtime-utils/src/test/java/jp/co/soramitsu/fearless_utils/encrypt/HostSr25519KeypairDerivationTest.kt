package jp.co.soramitsu.fearless_utils.encrypt

import org.junit.Before
import org.junit.Test

/**
 * Runs the Substrate sr25519 HDKD vectors (development phrase, soft, hard,
 * multi-level and `///password` paths) on the JVM with the host build of
 * libsr25519java. The same file also runs on devices in
 * AndroidSubstrateKeypairDerivationTest.
 */
class HostSr25519KeypairDerivationTest : SubstrateKeypairDerivationTest() {

    @Before
    fun requireHostLibrary() = Sr25519HostLibrary.assumeAvailable()

    @Test
    fun `should pass sr25519 tests`() {
        performSpecTests("crypto/sr25519HDKD.json", EncryptionType.SR25519)
    }
}
