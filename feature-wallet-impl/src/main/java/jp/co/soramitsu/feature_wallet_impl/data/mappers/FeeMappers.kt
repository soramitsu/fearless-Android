package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse

fun mapFeeRemoteToFee(feeResponse: FeeResponse, transfer: Transfer): Fee {
    return with(transfer) {
        Fee(
            transferAmount = amount,
            feeAmount = type.amountFromPlanks(feeResponse.partialFee),
            type = type
        )
    }
}