package jp.co.soramitsu.wallet.impl.data.mappers

import java.math.BigInteger
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

fun mapFeeRemoteToFee(fee: BigInteger, transfer: Transfer): Fee {
    return with(transfer) {
        Fee(
            transferAmount = amount,
            feeAmount = chainAsset.amountFromPlanks(fee),
            type = chainAsset
        )
    }
}
