package jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum

import jp.co.soramitsu.fearless_utils.encrypt.junction.Junction
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.derivePublicKey
import jp.co.soramitsu.fearless_utils.encrypt.keypair.generate
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.KeypairWithSeed

object EthereumKeypairFactory {

    fun generate(seed: ByteArray, junctions: List<Junction>): Keypair {
        return Bip32KeypairFactory.generate(seed, junctions)
    }

    fun createWithPrivateKey(privateKeyBytes: ByteArray): Keypair {
        return KeypairWithSeed(
            seed = privateKeyBytes,
            privateKey = privateKeyBytes,
            publicKey = ECDSAUtils.derivePublicKey(privateKeyOrSeed = privateKeyBytes)
        )
    }
}
