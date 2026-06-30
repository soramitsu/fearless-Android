package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths

object TonKeyDerivation {

    fun deriveAccount(
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String = UniversalWalletDerivationPaths.TON_DEFAULT
    ): TonAccount {
        val ed25519 = SolanaKeyDerivation.deriveAccount(
            mnemonic = mnemonic,
            passphrase = passphrase,
            derivationPath = derivationPath
        )
        val publicKeyHex = ed25519.publicKey.toHex()

        return TonAccount(
            derivationPath = derivationPath,
            privateKey = ed25519.privateKey,
            chainCode = ed25519.chainCode,
            publicKey = ed25519.publicKey,
            publicKeyHex = publicKeyHex,
            addressBounceable = ed25519.publicKey.v4r2tonAddress(isTestnet = false, bounceable = true),
            addressNonBounceable = ed25519.publicKey.v4r2tonAddress(isTestnet = false),
            testnetNonBounceable = ed25519.publicKey.v4r2tonAddress(isTestnet = true),
            accountId = ed25519.publicKey.tonAccountId(isTestnet = false)
        )
    }

    fun addressFromPublicKey(
        publicKey: ByteArray,
        isTestnet: Boolean = false,
        bounceable: Boolean = false
    ): String {
        require(publicKey.size == PUBLIC_KEY_LENGTH) { "TON public key must be 32 bytes" }

        return publicKey.v4r2tonAddress(isTestnet = isTestnet, bounceable = bounceable)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    data class TonAccount(
        val derivationPath: String,
        val privateKey: ByteArray,
        val chainCode: ByteArray,
        val publicKey: ByteArray,
        val publicKeyHex: String,
        val addressBounceable: String,
        val addressNonBounceable: String,
        val testnetNonBounceable: String,
        val accountId: String
    )

    private const val PUBLIC_KEY_LENGTH = 32
}
