package jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate

import jp.co.soramitsu.fearless_utils.encrypt.junction.Junction
import jp.co.soramitsu.fearless_utils.encrypt.junction.JunctionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.KeypairFactory
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.scale.dataType.string
import jp.co.soramitsu.fearless_utils.scale.dataType.toByteArray

class KeypairWithSeed(
    val seed: ByteArray,
    override val privateKey: ByteArray,
    override val publicKey: ByteArray
) : Keypair

abstract class OtherSubstrateKeypairFactory(
    private val hardDerivationPrefix: String
) : KeypairFactory<KeypairWithSeed> {

    override fun deriveChild(parent: KeypairWithSeed, junction: Junction): KeypairWithSeed {
        if (junction.type == JunctionType.HARD) {
            val prefix = string.toByteArray(hardDerivationPrefix)

            val newSeed = (prefix + parent.seed + junction.chaincode).blake2b256()

            return deriveFromSeed(newSeed)
        } else {
            throw KeypairFactory.SoftDerivationNotSupported()
        }
    }
}
