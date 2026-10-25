package jp.co.soramitsu.fearless_utils.encrypt.json

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.ContentCoderFactory
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.content.encode
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.TypeCoderFactory
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.encode
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.model.JsonAccountData
import org.spongycastle.util.encoders.Base64

@Suppress("EXPERIMENTAL_API_USAGE")
class JsonSeedEncoder(
    private val gson: Gson
) {
    fun generate(
        keypair: Keypair,
        seed: ByteArray?,
        password: String,
        name: String,
        multiChainEncryption: MultiChainEncryption,
        genesisHash: String,
        address: String
    ): String {
        val encoding = when (multiChainEncryption) {
            is MultiChainEncryption.Substrate -> {
                JsonAccountData.Encoding.substrate(multiChainEncryption.encryptionType)
            }
            MultiChainEncryption.Ethereum -> {
                JsonAccountData.Encoding.ethereum()
            }
        }
        val encoded = formEncodedField(keypair, seed, password, encoding)

        val importData = JsonAccountData(
            encoded = encoded,
            address = address,
            encoding = encoding,
            meta = JsonAccountData.Meta(
                name = name,
                whenCreated = System.currentTimeMillis(),
                genesisHash = genesisHash
            )
        )

        return gson.toJson(importData)
    }

    private fun formEncodedField(
        keypair: Keypair,
        seed: ByteArray?,
        password: String,
        encoding: JsonAccountData.Encoding
    ): String {
        val contentEncoder = ContentCoderFactory.getEncoder(encoding.content)!!
        val typeEncoder = TypeCoderFactory.getEncoder(encoding.type)!!

        val encodedContent = contentEncoder.encode(keypair, seed)
        val encryptedContent = typeEncoder.encode(encodedContent, password.encodeToByteArray())

        return Base64.toBase64String(encryptedContent)
    }
}
