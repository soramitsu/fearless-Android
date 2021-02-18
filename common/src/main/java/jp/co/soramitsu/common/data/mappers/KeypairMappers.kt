package jp.co.soramitsu.common.data.mappers

import jp.co.soramitsu.core.model.SigningData
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair

fun mapSigningDataToKeypair(singingData: SigningData): Keypair {
    return with(singingData) {
        Keypair(
            publicKey = publicKey,
            privateKey = privateKey,
            nonce = nonce
        )
    }
}

fun mapKeyPairToSigningData(keyPair: Keypair): SigningData {
    return with(keyPair) {
        SigningData(
            publicKey = publicKey,
            privateKey = privateKey,
            nonce = nonce
        )
    }
}