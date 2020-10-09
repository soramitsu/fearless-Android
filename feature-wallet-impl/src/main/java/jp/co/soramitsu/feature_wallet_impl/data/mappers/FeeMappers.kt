package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.FeeRemote

fun mapFeeRemoteToFee(feeRemote: FeeRemote, token: Asset.Token): Fee {
    return with(feeRemote) {
        Fee(
            amountInPlanks = partialFee,
            token = token
        )
    }
}