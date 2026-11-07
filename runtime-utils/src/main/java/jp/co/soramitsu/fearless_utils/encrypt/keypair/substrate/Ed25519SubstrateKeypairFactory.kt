package jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate

import jp.co.soramitsu.fearless_utils.encrypt.Ed25519Crypto

internal object Ed25519SubstrateKeypairFactory : OtherSubstrateKeypairFactory("Ed25519HDKD") {

    // The private key is the 32-byte RFC 8032 seed, as the PKCS#8 body the EdDSA-Java provider produced.
    override fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        return KeypairWithSeed(
            seed = seed,
            privateKey = seed.copyOf(),
            publicKey = Ed25519Crypto.publicKey(seed)
        )
    }
}
