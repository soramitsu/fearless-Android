package jp.co.soramitsu.fearless_utils.encrypt

import org.junit.Test

class AndroidSubstrateKeypairDerivationTest : SubstrateKeypairDerivationTest() {

    /*
     Runs the vectors against the device build of libsr25519java. The same file runs on the
     JVM against the host build in HostSr25519KeypairDerivationTest.
     */
    @Test
    fun shouldRunSr25519Tests() {
        performSpecTests("crypto/sr25519HDKD.json", EncryptionType.SR25519)
    }
}