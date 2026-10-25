package jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.secretCoder

import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonContentDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.JsonSecretCoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.derivePublicKey

object EthereumJsonSecretCoder : JsonSecretCoder {

    override fun encode(keypair: Keypair, seed: ByteArray?): List<ByteArray> {
        return listOf(keypair.privateKey, keypair.publicKey)
    }

    override fun decode(data: List<ByteArray>): JsonContentDecoder.SecretDecoder.DecodedSecret {
        require(data.size == 2) { "Unknown secret structure (size: ${data.size}" }

        val privateKey = data[0]

        return JsonContentDecoder.SecretDecoder.DecodedSecret(
            seed = null,
            multiChainEncryption = MultiChainEncryption.Ethereum,
            keypair = BaseKeypair(
                privateKey = privateKey,
                publicKey = ECDSAUtils.derivePublicKey(privateKey)
            )
        )
    }
}
