package jp.co.soramitsu.core.utils

import java.util.Locale

fun String.removedXcPrefix(): String {
    return if (startsWith(XC_PREFIX) && length > XC_PREFIX.length) {
        removePrefix(XC_PREFIX)
    } else {
        this
    }
}

/**
 * The symbol that identifies an asset across networks: trimmed, without the lowercase `xc` prefix that
 * Moonbeam-style chains give bridged tokens (`xcDOT` is DOT), and upper-cased. Only that lowercase prefix is
 * removed, so a token whose own symbol starts with the letters XC (`XCN`) keeps them.
 */
fun String.normalizedAssetSymbol(): String = trim().removedXcPrefix().uppercase(Locale.US)

private const val XC_PREFIX = "xc"
