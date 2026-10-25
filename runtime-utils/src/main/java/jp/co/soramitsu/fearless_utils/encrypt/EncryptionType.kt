package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException

enum class EncryptionType(val rawName: String, val signatureVersion: Int) {
    ED25519("ed25519", 0),
    SR25519("sr25519", 1),
    ECDSA("ecdsa", 2);

    companion object {
        fun fromStringOrNull(string: String): EncryptionType? {
            return runCatching { fromString(string) }.getOrNull()
        }

        fun fromString(string: String): EncryptionType {
            return when (string) {
                SR25519.rawName -> SR25519
                ECDSA.rawName -> ECDSA
                ED25519.rawName -> ED25519
                else -> throw JsonSeedDecodingException.UnsupportedEncryptionTypeException()
            }
        }
    }
}

sealed class MultiChainEncryption(val encryptionType: EncryptionType) {

    companion object // extensions

    class Substrate(encryptionType: EncryptionType) : MultiChainEncryption(encryptionType)

    object Ethereum : MultiChainEncryption(EncryptionType.ECDSA)
}
