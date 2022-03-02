package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import android.text.Editable
import android.text.TextWatcher

object EthereumDerivationPathTransformer : TextWatcher {

    private var shouldValidate = true
    private const val SLASH_SEPARATOR = "/"
    private const val DOUBLE_SLASH_SEPARATOR = "//"

    override fun afterTextChanged(s: Editable?) {
        s ?: return
        if (shouldValidate.not()) {
            if (s.endsWith('/') && s.endsWith("//").not()) {
                s.delete(s.length - 1, s.length)
            }
            return
        }

        val split = s.split(DOUBLE_SLASH_SEPARATOR).filter { it.isNotEmpty() }
        val others = split.getOrNull(2)?.split(SLASH_SEPARATOR)?.filter { it.isNotEmpty() }

        val purpose = split.getOrNull(0)
        val coinType = split.getOrNull(1)
        val account = others?.getOrNull(0)
        val change = others?.getOrNull(1)
        val addressIndex = others?.getOrNull(2)

        val startsWithDoubleSlash = s.startsWith(DOUBLE_SLASH_SEPARATOR)

        when {
            startsWithDoubleSlash.not() && s.startsWith(SLASH_SEPARATOR) -> s.insert(0, SLASH_SEPARATOR)
            startsWithDoubleSlash.not() -> s.insert(0, DOUBLE_SLASH_SEPARATOR)
            purpose != null && purpose.length == 2 && coinType.isNullOrEmpty() && s.endsWith(DOUBLE_SLASH_SEPARATOR).not() -> s.append(DOUBLE_SLASH_SEPARATOR)
            purpose != null && purpose.length > 2 -> s.insert(4, "//")
            coinType != null && coinType.length == 2 && account.isNullOrEmpty() && s.endsWith(DOUBLE_SLASH_SEPARATOR).not() -> s.append(DOUBLE_SLASH_SEPARATOR)
            coinType != null && coinType.length > 2 -> s.insert(8, "//")
            account != null && account.length == 1 && change.isNullOrEmpty() && s.endsWith(SLASH_SEPARATOR).not() -> s.append(SLASH_SEPARATOR)
            account != null && account.length > 1 -> s.insert(11, "/")
            change != null && change.length == 1 && addressIndex.isNullOrEmpty() && s.endsWith(SLASH_SEPARATOR).not() -> s.append(SLASH_SEPARATOR)
            change != null && change.length > 1 -> s.insert(13, "/")
            addressIndex != null && addressIndex.length > 1 -> s.delete(s.length - 1, s.length)
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // user erasing text, no need to transform derivation path
        shouldValidate = before <= count
    }
}
