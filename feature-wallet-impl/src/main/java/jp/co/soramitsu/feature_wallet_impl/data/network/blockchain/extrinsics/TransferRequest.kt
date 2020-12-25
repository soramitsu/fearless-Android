package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics

import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic

class TransferRequest(extrinsic: EncodableStruct<SubmittableExtrinsic>) : RuntimeRequest(
    method = "author_submitExtrinsic",
    params = listOf(
        SubmittableExtrinsic.toHexString(extrinsic)
    )
)