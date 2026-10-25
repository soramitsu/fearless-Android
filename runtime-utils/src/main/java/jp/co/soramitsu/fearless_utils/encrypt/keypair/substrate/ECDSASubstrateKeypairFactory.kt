package jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate

import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.keypair.derivePublicKey

internal object ECDSASubstrateKeypairFactory : OtherSubstrateKeypairFactory("Secp256k1HDKD") {

    override fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        return KeypairWithSeed(
            seed = seed,
            privateKey = seed,
            publicKey = ECDSAUtils.derivePublicKey(privateKeyOrSeed = seed)
        )
    }
}
