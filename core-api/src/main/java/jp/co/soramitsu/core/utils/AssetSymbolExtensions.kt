package jp.co.soramitsu.core.utils

fun String.removedXcPrefix(): String {
    return if (startsWith(XC_PREFIX) && length > XC_PREFIX.length) {
        removePrefix(XC_PREFIX)
    } else {
        this
    }
}

private const val XC_PREFIX = "xc"
