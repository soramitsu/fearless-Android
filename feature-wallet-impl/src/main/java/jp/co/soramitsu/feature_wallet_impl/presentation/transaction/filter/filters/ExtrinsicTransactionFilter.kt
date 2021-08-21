package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters

import android.os.Parcelable
import jp.co.soramitsu.common.utils.Filter
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import kotlinx.android.parcel.Parcelize

typealias HistoryFilter = Filter<Operation>

@Parcelize
object ExtrinsicFilter : HistoryFilter, Parcelable {
    override fun shouldInclude(model: Operation): Boolean {
        return model.transactionType is Operation.TransactionType.Extrinsic
    }
}

@Parcelize
object TransferFilter : HistoryFilter, Parcelable {
    override fun shouldInclude(model: Operation): Boolean {
        return model.transactionType is Operation.TransactionType.Transfer
    }
}

@Parcelize
object RewardFilter : HistoryFilter, Parcelable {
    override fun shouldInclude(model: Operation): Boolean {
        return model.transactionType is Operation.TransactionType.Reward
    }
}

data class HistoryFilters(
    val filters: List<HistoryFilter>
)
