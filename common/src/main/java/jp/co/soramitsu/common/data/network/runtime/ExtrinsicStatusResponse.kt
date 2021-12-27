package jp.co.soramitsu.common.data.network.runtime

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256

sealed class ExtrinsicStatusResponse(val subscription: String) {
    class ExtrinsicStatusPending(subscription: String) : ExtrinsicStatusResponse(subscription)
    class ExtrinsicStatusFinalized(subscription: String, val inBlock: String) : ExtrinsicStatusResponse(subscription)
}

fun String.blake2b256String() = this.fromHex().blake2b256().toHexString(true)
