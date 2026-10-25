package jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonContentDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonSecretCoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory

internal object EcdsaJsonSecretCoder : OtherSubstrateJsonSecretCoder(EncryptionType.ECDSA)
internal object Ed25519JsonSecretCoder : OtherSubstrateJsonSecretCoder(EncryptionType.ED25519)

internal abstract class OtherSubstrateJsonSecretCoder(
    private val encryptionType: EncryptionType
) : JsonSecretCoder {

    override fun encode(keypair: Keypair, seed: ByteArray?): List<ByteArray> {
        requireNotNull(seed) { "Seed cannot be null" }

        return listOf(seed, keypair.publicKey)
    }

    override fun decode(data: List<ByteArray>): JsonContentDecoder.SecretDecoder.DecodedSecret {
        require(data.size == 2) { "Unknown secret structure (size: ${data.size}" }

        val seed = data[0].copyOfRange(0, 32) // crop to 32 bytes

        return JsonContentDecoder.SecretDecoder.DecodedSecret(
            seed = seed,
            multiChainEncryption = MultiChainEncryption.Substrate(encryptionType),
            keypair = SubstrateKeypairFactory.generate(encryptionType, seed)
        )
    }
}
