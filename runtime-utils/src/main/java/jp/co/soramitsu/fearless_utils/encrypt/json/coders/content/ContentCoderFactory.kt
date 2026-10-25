package jp.co.soramitsu.fearless_utils.encrypt.json.coders.content

import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.checksumCoder.Pkcs8ChecksumCoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder.EcdsaJsonSecretCoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder.Ed25519JsonSecretCoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder.EthereumJsonSecretCoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder.Sr25519JsonSecretCoder

private class JsonContentDecoderImpl(
    override val checksumDecoder: JsonContentDecoder.ChecksumDecoder,
    override val secretDecoder: JsonContentDecoder.SecretDecoder
) : JsonContentDecoder

private class JsonContentEncoderImpl(
    override val secretEncoder: JsonContentEncoder.SecretEncoder,
    override val checksumEncoder: JsonContentEncoder.ChecksumEncoder
) : JsonContentEncoder

object ContentCoderFactory {

    private val secretCoders: Map<String, JsonSecretCoder> = mapOf(
        "ecdsa" to EcdsaJsonSecretCoder,
        "sr25519" to Sr25519JsonSecretCoder,
        "ed25519" to Ed25519JsonSecretCoder,
        "ethereum" to EthereumJsonSecretCoder
    )

    private val checksumCoders: Map<String, JsonChecksumCoder> = mapOf(
        "pkcs8" to Pkcs8ChecksumCoder
    )

    /**
     * @return null if cannot construct decoder
     */
    fun getDecoder(configuration: List<String>): JsonContentDecoder? {
        return retrieveConfiguration(configuration)?.let { (checksumCoder, secretCoder) ->
            JsonContentDecoderImpl(
                secretDecoder = secretCoder,
                checksumDecoder = checksumCoder
            )
        }
    }

    /**
     * @return null if cannot construct encoder
     */
    fun getEncoder(configuration: List<String>): JsonContentEncoder? {
        return retrieveConfiguration(configuration)?.let { (checksumCoder, secretCoder) ->
            JsonContentEncoderImpl(
                secretEncoder = secretCoder,
                checksumEncoder = checksumCoder
            )
        }
    }

    private fun retrieveConfiguration(
        configuration: List<String>
    ): Pair<JsonChecksumCoder, JsonSecretCoder>? {
        if (configuration.size != 2) return null

        val (checksumCoderName, secretCoderName) = configuration

        val checksumCoder = checksumCoders[checksumCoderName] ?: return null
        val secretCoder = secretCoders[secretCoderName] ?: return null

        return checksumCoder to secretCoder
    }
}
