package jp.co.soramitsu.core.model

class SigningData(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val nonce: ByteArray? = null
)
