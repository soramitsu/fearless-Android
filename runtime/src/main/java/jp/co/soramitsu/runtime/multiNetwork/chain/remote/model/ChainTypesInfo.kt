package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

import android.net.Uri

class ChainTypesInfo(
    val url: String,
    val overridesCommon: Boolean
) {
    val androidUrl: String
        get() {
            val filename = Uri.parse(url).lastPathSegment
            return url.replace("/$filename", "/android/$filename")
        }
}
