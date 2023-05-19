package jp.co.soramitsu.runtime.multiNetwork.chain.model

import android.net.Uri

class ChainTypesInfo(
    val url: String
) {
    // because of using ios/v3 chains.json but store android types in /android folder
    val androidUrl: String
        get() {
            val filename = Uri.parse(url).lastPathSegment
            return url.replace("/$filename", "/android/$filename")
        }
}
