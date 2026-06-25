package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletRegistry

object IrohaKeyDerivation {

    fun deriveAccount(
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String = UniversalWalletDerivationPaths.IROHA_DEFAULT
    ): IrohaAccount {
        val ed25519 = SolanaKeyDerivation.deriveAccount(
            mnemonic = mnemonic,
            passphrase = passphrase,
            derivationPath = derivationPath
        )
        val publicKeyHex = ed25519.publicKey.toHex()

        return IrohaAccount(
            derivationPath = derivationPath,
            privateKey = ed25519.privateKey,
            chainCode = ed25519.chainCode,
            publicKey = ed25519.publicKey,
            publicKeyHex = publicKeyHex,
            canonicalHex = IrohaAddressCodec.canonicalHex(publicKeyHex)
        )
    }

    fun deriveAddress(
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String = UniversalWalletDerivationPaths.IROHA_DEFAULT,
        chainDiscriminant: Int = UniversalWalletRegistry.taira.chainDiscriminant
    ): IrohaAddress {
        val account = deriveAccount(mnemonic, passphrase, derivationPath)

        return IrohaAddress(
            account = account,
            chainDiscriminant = chainDiscriminant,
            i105 = IrohaAddressCodec.encode(account.publicKeyHex, chainDiscriminant)
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    data class IrohaAccount(
        val derivationPath: String,
        val privateKey: ByteArray,
        val chainCode: ByteArray,
        val publicKey: ByteArray,
        val publicKeyHex: String,
        val canonicalHex: String
    )

    data class IrohaAddress(
        val account: IrohaAccount,
        val chainDiscriminant: Int,
        val i105: String
    )
}
