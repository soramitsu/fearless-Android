package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests

import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic

class FeeCalculationRequest(submittableExtrinsic: EncodableStruct<SubmittableExtrinsic>) : RuntimeRequest(
    method = "payment_queryInfo",
    params = listOf(
        SubmittableExtrinsic.toHexString(submittableExtrinsic)
    )
)