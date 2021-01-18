package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.feature_account_api.domain.model.Node

fun SS58Encoder.encode(publicKey: ByteArray, networkType: Node.NetworkType): String {
    return encode(publicKey, networkType.runtimeConfiguration.addressByte)
}