package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

fun mapFeeRemoteToFee(feeResponse: FeeResponse, transfer: Transfer): Fee {
    return with(transfer) {
        Fee(
            transferAmount = amount,
            feeAmount = tokenType.amountFromPlanks(feeResponse.partialFee),
            type = tokenType
        )
    }
}