package jp.co.soramitsu.common.data.mappers

import jp.co.soramitsu.core.model.SigningData
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair

fun mapSigningDataToKeypair(singingData: SigningData): Keypair {
    return with(singingData) {
        BaseKeypair(
            publicKey = publicKey,
            privateKey = privateKey
        )
    }
}

fun mapKeyPairToSigningData(keyPair: Keypair): SigningData {
    return with(keyPair) {
        SigningData(
            publicKey = publicKey,
            privateKey = privateKey,
//            nonce = nonce
        )
    }
}
