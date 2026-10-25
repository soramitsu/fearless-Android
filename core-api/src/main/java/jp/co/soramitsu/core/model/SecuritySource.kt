package jp.co.soramitsu.core.model

import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair

sealed class SecuritySource(
    val keypair: Keypair
) {

    open class Specified(
        final override val seed: ByteArray?,
        keypair: Keypair
    ) : SecuritySource(keypair), WithJson, WithSeed {

        override fun jsonFormer() = jsonFormer(seed)

        class Create(
            seed: ByteArray?,
            keypair: Keypair,
            override val mnemonic: String,
            override val derivationPath: String?
        ) : Specified(seed, keypair), WithMnemonic, WithDerivationPath

        class Seed(
            seed: ByteArray?,
            keypair: Keypair,
            override val derivationPath: String?
        ) : Specified(seed, keypair), WithDerivationPath

        class Mnemonic(
            seed: ByteArray?,
            keypair: Keypair,
            override val mnemonic: String,
            override val derivationPath: String?
        ) : Specified(seed, keypair), WithMnemonic, WithDerivationPath

        class Json(
            seed: ByteArray?,
            keypair: Keypair
        ) : Specified(seed, keypair)
    }

    open class Unspecified(
        keypair: Keypair
    ) : SecuritySource(keypair)
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
