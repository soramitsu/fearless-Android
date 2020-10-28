package jp.co.soramitsu.feature_account_api.domain.model

sealed class SecuritySource(
    final override val seed: ByteArray?,
    val signingData: SigningData
) : WithJson, WithSeed {

    override fun jsonFormer() = jsonFormer(seed, signingData)

    class Create(
        seed: ByteArray?,
        signingData: SigningData,
        override val mnemonic: String,
        override val derivationPath: String?
    ) : SecuritySource(seed, signingData), WithMnemonic, WithDerivationPath

    class Seed(
        seed: ByteArray?,
        signingData: SigningData,
        override val derivationPath: String?
    ) : SecuritySource(seed, signingData), WithDerivationPath

    class Mnemonic(
        seed: ByteArray?,
        signingData: SigningData,
        override val mnemonic: String,
        override val derivationPath: String?
    ) : SecuritySource(seed, signingData), WithMnemonic, WithDerivationPath

    class Json(
        seed: ByteArray?,
        signingData: SigningData
    ) : SecuritySource(seed, signingData)
}

interface WithMnemonic {
    val mnemonic: String

    fun mnemonicWords() = mnemonic.split(" ")
}

interface WithSeed {
    val seed: ByteArray?
}

interface WithJson {
    fun jsonFormer(): JsonFormer
}

interface WithDerivationPath {
    val derivationPath: String?
}

sealed class JsonFormer {
    class KeyPair(val signingData: SigningData) : JsonFormer()

    class Seed(val seed: ByteArray) : JsonFormer()
}

fun jsonFormer(seed: ByteArray?, signingData: SigningData): JsonFormer {
    return if (seed != null) {
        JsonFormer.Seed(seed)
    } else {
        JsonFormer.KeyPair(signingData)
    }
}