package jp.co.soramitsu.feature_account_api.domain.model

sealed class SecuritySource(
    val signingData: SigningData
) {

    open class Specified(
        final override val seed: ByteArray?,
        signingData: SigningData
    ) : SecuritySource(signingData), WithJson, WithSeed {

        override fun jsonFormer() = jsonFormer(seed)

        class Create(
            seed: ByteArray?,
            signingData: SigningData,
            override val mnemonic: String,
            override val derivationPath: String?
        ) : Specified(seed, signingData), WithMnemonic, WithDerivationPath

        class Seed(
            seed: ByteArray?,
            signingData: SigningData,
            override val derivationPath: String?
        ) : Specified(seed, signingData), WithDerivationPath

        class Mnemonic(
            seed: ByteArray?,
            signingData: SigningData,
            override val mnemonic: String,
            override val derivationPath: String?
        ) : Specified(seed, signingData), WithMnemonic, WithDerivationPath

        class Json(
            seed: ByteArray?,
            signingData: SigningData
        ) : Specified(seed, signingData)
    }

    open class Unspecified(
        signingData: SigningData
    ) : SecuritySource(signingData)
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
    object KeyPair : JsonFormer()

    class Seed(val seed: ByteArray) : JsonFormer()
}

fun jsonFormer(seed: ByteArray?): JsonFormer {
    return if (seed != null) {
        JsonFormer.Seed(seed)
    } else {
        JsonFormer.KeyPair
    }
}