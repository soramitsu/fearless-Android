package jp.co.soramitsu.fearless_utils.encrypt.model

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType

const val ENCODING_SCRYPT = "scrypt"
const val ENCODING_SALSA = "xsalsa20-poly1305"
const val ENCODING_PKCS8 = "pkcs8"
const val ENCODING_ETHEREUM = "ethereum"

const val JSON_VERSION = "3"

class JsonAccountData(
    val address: String?,
    val encoded: String,
    val encoding: Encoding,
    val meta: Meta
) {
    class Encoding(
        val content: List<String>,
        val type: List<String>,
        val version: String
    ) {
        companion object {
            fun substrate(encryptionType: EncryptionType) = Encoding(
                content = listOf(ENCODING_PKCS8, encryptionType.rawName),
                type = listOf(ENCODING_SCRYPT, ENCODING_SALSA),
                version = JSON_VERSION
            )

            fun ethereum() = Encoding(
                content = listOf(ENCODING_PKCS8, ENCODING_ETHEREUM),
                type = listOf(ENCODING_SCRYPT, ENCODING_SALSA),
                version = JSON_VERSION
            )
        }
    }

    class Meta(
        val name: String?,
        val genesisHash: String,
        val whenCreated: Long
    )
}
