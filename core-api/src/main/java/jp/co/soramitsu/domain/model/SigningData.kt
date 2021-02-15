package jp.co.soramitsu.domain.model

class SigningData(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val nonce: ByteArray? = null
)