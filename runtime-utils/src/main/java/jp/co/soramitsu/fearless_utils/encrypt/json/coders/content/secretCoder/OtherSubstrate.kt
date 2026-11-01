package jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonContentDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonSecretCoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory

private const val SEED_LENGTH = 32

internal object EcdsaJsonSecretCoder : OtherSubstrateJsonSecretCoder(EncryptionType.ECDSA)
internal object Ed25519JsonSecretCoder : OtherSubstrateJsonSecretCoder(EncryptionType.ED25519)

internal abstract class OtherSubstrateJsonSecretCoder(
    private val encryptionType: EncryptionType
) : JsonSecretCoder {

    // Writes the secret that opens this keypair. For an account derived with a junction, `seed` is the
    // root seed: it opens the root account instead, and every other account derived from that phrase.
    override fun encode(keypair: Keypair, seed: ByteArray?): List<ByteArray> {
        val secret = requireNotNull(listOfNotNull(seed, keypair.privateKey).firstOrNull { opens(it, keypair) }) {
            "No secret opens this keypair"
        }

        return listOf(secret, keypair.publicKey)
    }

    override fun decode(data: List<ByteArray>): JsonContentDecoder.SecretDecoder.DecodedSecret {
        require(data.size == 2) { "Unknown secret structure (size: ${data.size}" }

        val seed = data[0].copyOfRange(0, SEED_LENGTH) // crop to 32 bytes

        return JsonContentDecoder.SecretDecoder.DecodedSecret(
            seed = seed,
            multiChainEncryption = MultiChainEncryption.Substrate(encryptionType),
            keypair = SubstrateKeypairFactory.generate(encryptionType, seed)
        )
    }

    // Decode generates the keypair from the first 32 bytes, so they must generate this public key.
    private fun opens(secret: ByteArray, keypair: Keypair): Boolean = runCatching {
        SubstrateKeypairFactory.generate(encryptionType, secret.copyOfRange(0, SEED_LENGTH))
            .publicKey.contentEquals(keypair.publicKey)
    }.getOrDefault(false)
}
