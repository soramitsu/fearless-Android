package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import java.math.BigInteger

fun mapFeeRemoteToFee(fee: BigInteger, transfer: Transfer): Fee {
    return with(transfer) {
        Fee(
            transferAmount = amount,
            feeAmount = chainAsset.amountFromPlanks(fee),
            type = chainAsset
        )
    }
}
