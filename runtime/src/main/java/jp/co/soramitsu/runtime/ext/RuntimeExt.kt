package jp.co.soramitsu.runtime.ext

import java.util.Locale
import jp.co.soramitsu.core.model.Node

fun Node.NetworkType.runtimeCacheName(): String {
    return readableName.toLowerCase(Locale.ROOT)
}
