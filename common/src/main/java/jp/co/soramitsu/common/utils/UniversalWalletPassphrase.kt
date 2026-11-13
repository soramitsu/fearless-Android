package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import java.text.Normalizer

/**
 * The BIP39 passphrase of a wallet's universal accounts (Bitcoin, Solana, TON and Taira/Nexus).
 *
 * It is the password after `///` in the wallet's Substrate derivation path or, when that path
 * has none, the one in its Ethereum path while the Ethereum secret holds the same words. Empty
 * means the accounts every earlier version derived (universal-wallet-v2-vectors.json); a
 * password gives those of universal-wallet-v2-passphrase-vectors.json.
 */
object UniversalWalletPassphrase {

    fun resolve(
        substrateDerivationPath: String?,
        ethereumDerivationPath: String?,
        ethereumSharesWords: Boolean
    ): String = substratePassword(substrateDerivationPath).ifEmpty {
        if (ethereumSharesWords) ethereumPassword(ethereumDerivationPath) else ""
    }

    /** The order every signer tries: the password, then the empty one earlier versions used. */
    fun candidates(passphrase: String): List<String> =
        if (passphrase.isEmpty()) listOf("") else listOf(passphrase, "")

    /** BIP39 normalises both the words and the passphrase to NFKD before PBKDF2. */
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)

    fun substratePassword(derivationPath: String?): String =
        password(derivationPath) { SubstrateJunctionDecoder.decode(it).password }

    fun ethereumPassword(derivationPath: String?): String =
        password(derivationPath) { BIP32JunctionDecoder.decode(it).password }

    // A path that does not decode never derived the stored key, so it carries no password.
    private inline fun password(derivationPath: String?, decode: (String) -> String?): String {
        val path = derivationPath?.takeIf(String::isNotEmpty) ?: return ""
        return try {
            decode(path).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
