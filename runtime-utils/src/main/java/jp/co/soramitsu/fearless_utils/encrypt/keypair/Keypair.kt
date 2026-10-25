package jp.co.soramitsu.fearless_utils.encrypt.keypair

interface Keypair {
    val privateKey: ByteArray
    val publicKey: ByteArray
}

class BaseKeypair(
    override val privateKey: ByteArray,
    override val publicKey: ByteArray
) : Keypair
