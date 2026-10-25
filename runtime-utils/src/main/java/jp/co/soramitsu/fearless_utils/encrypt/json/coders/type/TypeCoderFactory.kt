package jp.co.soramitsu.fearless_utils.encrypt.json.coders.type

import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.cryptor.XSalsa20Poly1305Cryptor
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.keyGenerator.ScryptKeyGenerator

private class JsonTypeDecoderImpl(
    override val encryptionKeyGenerator: JsonTypeDecoder.EncryptionKeyGenerator,
    override val decryptor: JsonTypeDecoder.Decryptor
) : JsonTypeDecoder

private class JsonTypeEncoderImpl(
    override val encryptionKeyGenerator: JsonTypeEncoder.EncryptionKeyGenerator,
    override val encryptor: JsonTypeEncoder.Encryptor
) : JsonTypeEncoder

object TypeCoderFactory {

    private val keyGenerators: Map<String, JsonEncryptionKeyGenerator> = mapOf(
        "scrypt" to ScryptKeyGenerator
    )

    private val decryptors: Map<String, JsonCryptor> = mapOf(
        "xsalsa20-poly1305" to XSalsa20Poly1305Cryptor
    )

    /**
     * @return null if cannot construct decoder
     */
    fun getDecoder(configuration: List<String>): JsonTypeDecoder? {
        return retrieveConfiguration(configuration)?.let { (keyGenerator, decryptor) ->
            JsonTypeDecoderImpl(
                encryptionKeyGenerator = keyGenerator,
                decryptor = decryptor
            )
        }
    }

    /**
     * @return null if cannot construct encoder
     */
    fun getEncoder(configuration: List<String>): JsonTypeEncoder? {
        return retrieveConfiguration(configuration)?.let { (keyGenerator, encryptor) ->
            JsonTypeEncoderImpl(
                encryptionKeyGenerator = keyGenerator,
                encryptor = encryptor
            )
        }
    }

    private fun retrieveConfiguration(
        configuration: List<String>
    ): Pair<JsonEncryptionKeyGenerator, JsonCryptor>? {
        if (configuration.size != 2) return null

        val (keyGeneratorName, decryptorName) = configuration

        val keyGenerator = keyGenerators[keyGeneratorName] ?: return null
        val encryptor = decryptors[decryptorName] ?: return null

        return keyGenerator to encryptor
    }
}
