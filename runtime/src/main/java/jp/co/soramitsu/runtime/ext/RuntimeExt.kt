package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.core.model.Node
import java.util.Locale

fun Node.NetworkType.runtimeCacheName(): String {
    return readableName.toLowerCase(Locale.ROOT)
}
