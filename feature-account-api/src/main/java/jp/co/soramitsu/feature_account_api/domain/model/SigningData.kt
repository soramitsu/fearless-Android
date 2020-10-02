package jp.co.soramitsu.feature_account_api.domain.model

class SigningData(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val nonce: ByteArray? = null
)