package jp.co.soramitsu.account.impl.domain

import java.util.Locale
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress

/**
 * The password rule for every new Google Drive backup and JSON export. Their files are scrypt-encrypted
 * Polkadot JSON (N=2^15), which other wallets must keep importing, so the password carries the
 * protection: at least [MIN_LENGTH_WITH_CLASSES] characters from at least [MIN_CHARACTER_CLASSES] of
 * lowercase, uppercase, digits and other characters, or at least [MIN_LENGTH_ANY] characters; and never
 * the PIN, the wallet name or one of its addresses. Restoring or importing accepts any password.
 */
object BackupPasswordRule {
    const val MIN_LENGTH_WITH_CLASSES = 12
    const val MIN_CHARACTER_CLASSES = 3
    const val MIN_LENGTH_ANY = 16

    // PINs are exactly six digits (PinHealth), so the length minimum already excludes them; a
    // candidate in PIN form is refused on its own anyway, without reading the PIN.
    private const val PIN_LENGTH = 6
    private const val HEX_PREFIX = "0x"

    // Substrate address forms a user is likely to have seen: Polkadot, Kusama and the generic one.
    private val SUBSTRATE_ADDRESS_PREFIXES = listOf<Short>(0, 2, 42)

    enum class Problem { TOO_SHORT_OR_SIMPLE, PIN, WALLET_NAME, ADDRESS }

    /** Null when [password] may protect a new backup or export. */
    fun check(password: String, walletName: String?, addresses: Collection<String>): Problem? {
        val normalized = normalize(password)
        return when {
            isPinForm(password) -> Problem.PIN
            walletName != null && normalize(walletName).isNotEmpty() && normalized == normalize(walletName) -> Problem.WALLET_NAME
            addresses.any { normalize(it).isNotEmpty() && normalized == normalize(it) } -> Problem.ADDRESS
            !isStrong(password) -> Problem.TOO_SHORT_OR_SIMPLE
            else -> null
        }
    }

    fun isStrong(password: String): Boolean {
        val length = password.codePointCount(0, password.length)
        return length >= MIN_LENGTH_ANY || length >= MIN_LENGTH_WITH_CLASSES && characterClasses(password) >= MIN_CHARACTER_CLASSES
    }

    /** Lowercase, uppercase, digits, and everything else (symbols, spaces, letters without case). */
    fun characterClasses(password: String): Int {
        var lower = false
        var upper = false
        var digit = false
        var other = false
        password.codePoints().forEach { codePoint ->
            when {
                Character.isLowerCase(codePoint) -> lower = true
                Character.isUpperCase(codePoint) -> upper = true
                Character.isDigit(codePoint) -> digit = true
                else -> other = true
            }
        }
        return listOf(lower, upper, digit, other).count { it }
    }

    /** The addresses of [wallet] a user may have copied: one per ecosystem plus common Substrate forms. */
    fun walletAddresses(wallet: LightMetaAccount): Set<String> {
        val substrate = wallet.substratePublicKey?.let { publicKey ->
            SUBSTRATE_ADDRESS_PREFIXES.mapNotNull { prefix -> runCatching { publicKey.toAddress(prefix) }.getOrNull() }
        }.orEmpty()
        val perEcosystem = runCatching { wallet.supportedEcosystemWithIconAddress().values }.getOrDefault(emptyList())
        return (perEcosystem + substrate).toSet()
    }

    private fun isPinForm(password: String): Boolean = password.length == PIN_LENGTH && password.all { it in '0'..'9' }

    // Case-insensitive and with or without 0x, so pasting an address in another form still matches.
    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).removePrefix(HEX_PREFIX)
}
