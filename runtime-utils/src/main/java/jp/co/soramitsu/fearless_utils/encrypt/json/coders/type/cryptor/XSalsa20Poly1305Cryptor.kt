package jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.cryptor

import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.JsonCryptor
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.JsonTypeDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.JsonTypeEncoder
import jp.co.soramitsu.fearless_utils.encrypt.json.copyBytes
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.SecretBox

private val NONCE_OFFSET = 0
private val NONCE_SIZE = 24

private val DATA_OFFSET = NONCE_OFFSET + NONCE_SIZE

object XSalsa20Poly1305Cryptor : JsonCryptor {

    override fun decrypt(keyGenerationResult: JsonTypeDecoder.KeyGenerationResult): ByteArray? {
        val byteData = keyGenerationResult.encryptedData

        val nonce = byteData.copyBytes(0, NONCE_SIZE)
        val encryptedData = byteData.copyOfRange(DATA_OFFSET, byteData.size)

        val secret = SecretBox(keyGenerationResult.secret).open(nonce, encryptedData)

        // SecretBox returns empty array if key is not correct
        return if (secret.isEmpty()) null else secret
    }

    override fun encrypt(
        keyGenerationResult: JsonTypeEncoder.KeyGenerationResult,
        data: ByteArray
    ): ByteArray {
        val secretBox = SecretBox(keyGenerationResult.encryptionKey)
        val nonce = secretBox.nonce(data)

        val secret = secretBox.seal(nonce, data)

        return keyGenerationResult.encryptingPrefix + nonce + secret
    }
}
