package jp.co.soramitsu.core.crypto

import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType

fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
    return when (cryptoType) {
        CryptoType.SR25519 -> EncryptionType.SR25519
        CryptoType.ED25519 -> EncryptionType.ED25519
        CryptoType.ECDSA -> EncryptionType.ECDSA
    }
}

fun mapEncryptionToCryptoType(encryptionType: EncryptionType): CryptoType {
    return when (encryptionType) {
        EncryptionType.SR25519 -> CryptoType.SR25519
        EncryptionType.ED25519 -> CryptoType.ED25519
        EncryptionType.ECDSA -> CryptoType.ECDSA
    }
}
