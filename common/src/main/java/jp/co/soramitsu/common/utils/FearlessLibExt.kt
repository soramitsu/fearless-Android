package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.model.Node

fun SS58Encoder.encode(publicKey: ByteArray, networkType: Node.NetworkType): String {
    return encode(publicKey, networkType.runtimeConfiguration.addressByte)
}