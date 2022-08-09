package jp.co.soramitsu.featurewalletimpl.data.mappers

import java.math.BigInteger
import jp.co.soramitsu.featurewalletapi.domain.model.Fee
import jp.co.soramitsu.featurewalletapi.domain.model.Transfer
import jp.co.soramitsu.featurewalletapi.domain.model.amountFromPlanks

fun mapFeeRemoteToFee(fee: BigInteger, transfer: Transfer): Fee {
    return with(transfer) {
        Fee(
            transferAmount = amount,
            feeAmount = chainAsset.amountFromPlanks(fee),
            type = chainAsset
        )
    }
}
