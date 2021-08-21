package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class TransactionFilter(
    val isRewards: Boolean,
    val isTransfers: Boolean,
    val isExtrinsics: Boolean
) : Parcelable
