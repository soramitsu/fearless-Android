package jp.co.soramitsu.fearless_utils.encrypt.keypair

import jp.co.soramitsu.fearless_utils.encrypt.junction.Junction

internal interface KeypairFactory<K : Keypair> {

    class SoftDerivationNotSupported : Exception()

    fun deriveFromSeed(seed: ByteArray): K

    fun deriveChild(parent: K, junction: Junction): K
}
