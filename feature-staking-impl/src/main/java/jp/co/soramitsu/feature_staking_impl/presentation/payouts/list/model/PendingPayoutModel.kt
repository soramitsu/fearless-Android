package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model

import androidx.annotation.ColorRes

class PendingPayoutsStatisticsModel(
    val payouts: List<PendingPayoutModel>,
    val payoutAllTitle: String,
    val placeholderVisible: Boolean
)

class PendingPayoutModel(
    val validatorTitle: String,
    val timeLeft: Long,
    val createdAt: Long, // the timestamp when we counted left time. Without it the timer will restart all over again on scroll of recycler view
    @ColorRes val daysLeftColor: Int,
    val amount: String,
    val amountFiat: String?,
)
