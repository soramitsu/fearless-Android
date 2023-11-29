package jp.co.soramitsu.wallet.impl.domain.model

data class QrContentSora(
    //substrate:[user address]:[user public key]:[user name]:[token id]:<amount>
    val address: String,
    val publicKey: String,
    val userName: String,
    val tokenId: String,
    val amount: String?
)