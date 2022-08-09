package jp.co.soramitsu.featureaccountimpl.presentation.mnemonic.backup

import android.text.Editable
import android.text.TextWatcher

object EthereumDerivationPathTransformer : TextWatcher {

    private var shouldValidate = true
    private const val SLASH_SEPARATOR = "/"
    private const val DOUBLE_SLASH_SEPARATOR = "//"

    override fun afterTextChanged(s: Editable?) {
        s ?: return
        if (shouldValidate.not()) {
            listOf(DOUBLE_SLASH_SEPARATOR, SLASH_SEPARATOR).forEach {
                if (s.endsWith(it)) {
                    s.delete(s.length - it.length, s.length)
                }
            }
            return
        }

        if (s.isEmpty()) return

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
            purpose != null && purpose.length > 2 -> s.insert(4, DOUBLE_SLASH_SEPARATOR)
            coinType != null && coinType.length > 2 -> s.insert(8, DOUBLE_SLASH_SEPARATOR)
            account != null && account.length > 1 -> s.insert(11, SLASH_SEPARATOR)
            change != null && change.length > 1 -> s.insert(13, SLASH_SEPARATOR)
            addressIndex != null && addressIndex.length > 1 -> s.delete(s.length - 1, s.length)
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // user erasing text, no need to transform derivation path
        shouldValidate = before <= count
    }
}
