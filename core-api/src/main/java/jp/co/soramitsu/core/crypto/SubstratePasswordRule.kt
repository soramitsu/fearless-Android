package jp.co.soramitsu.core.crypto

import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory

/**
 * How a Fearless client turned the `///password` of a Substrate derivation path into the salt of the
 * account's mini secret (substrate-password-vectors.json):
 * - [NFKD]: NFKD(password), as this app always has and as BIP39 specifies. New accounts use it.
 * - [NFC]: NFC(password), as the Fearless browser extension 4.2 did.
 * - [AS_TYPED]: the password exactly as entered, as the browser extension 3.0 and earlier did.
 * The rules agree for ASCII passwords. An import may open an account the extension made under an
 * earlier rule; nothing stores which rule a wallet uses, so every check that re-derives a stored key
 * accepts whichever rule reproduces it.
 */
enum class SubstratePasswordRule {
    NFKD,
    NFC,
    AS_TYPED;

    fun saltPassword(password: String): String = when (this) {
        NFKD -> Normalizer.normalize(password, Normalizer.Form.NFKD)
        NFC -> Normalizer.normalize(password, Normalizer.Form.NFC)
        AS_TYPED -> password
    }

    companion object {

        /** The rules that salt [password] differently, [NFKD] first; only [NFKD] without a password. */
        fun distinctFor(password: String?): List<SubstratePasswordRule> {
            if (password.isNullOrEmpty()) return listOf(NFKD)
            return entries.distinctBy { it.saltPassword(password) }
        }
    }
}

/** The 32-byte mini secret of the BIP39 [mnemonicWords] with the path [password] read by [rule]. */
fun substrateMiniSecret(mnemonicWords: String, password: String?, rule: SubstratePasswordRule): ByteArray {
    if (rule == SubstratePasswordRule.NFKD || password.isNullOrEmpty()) {
        // The derivation every earlier version of this app used, unchanged.
        return SubstrateSeedFactory.deriveSeed(mnemonicWords, password).seed.copyOfRange(0, MINI_SECRET_BYTES)
    }
    val entropy = MnemonicCreator.fromWords(mnemonicWords).entropy
    val salt = ("mnemonic" + rule.saltPassword(password)).toByteArray(Charsets.UTF_8)
    return pbkdf2HmacSha512(entropy, salt).copyOfRange(0, MINI_SECRET_BYTES)
}

private const val MINI_SECRET_BYTES = 32
private const val PBKDF2_ROUNDS = 2048

// One 64-byte PBKDF2 block, the whole BIP39 seed: U1 = HMAC(P, S || INT(1)), Ui = HMAC(P, Ui-1).
private fun pbkdf2HmacSha512(password: ByteArray, salt: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA512").apply { init(SecretKeySpec(password, "HmacSHA512")) }
    var block = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
    val result = block.copyOf()
    repeat(PBKDF2_ROUNDS - 1) {
        block = mac.doFinal(block)
        for (index in result.indices) {
            result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
        }
    }
    return result
}
