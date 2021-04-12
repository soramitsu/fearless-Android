package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model

import androidx.annotation.ColorRes

class PendingPayoutsStatisticsModel(
    val payouts: List<PendingPayoutModel>,
    val payoutAllTitle: String,
    val placeholderVisible: Boolean
)

class PendingPayoutModel(
    val validatorTitle: String,
    val daysLeft: String,
    @ColorRes val daysLeftColor: Int,
    val amount: String,
    val amountFiat: String?,
)
