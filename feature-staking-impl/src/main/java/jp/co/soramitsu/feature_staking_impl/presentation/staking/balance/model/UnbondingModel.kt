package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model

import jp.co.soramitsu.feature_wallet_api.presentation.model.AmountModel

data class UnbondingModel(
    val index: Int, // for DiffUtil to be able to distinguish unbondings with the same amount and days left
    val timeLeft: Long,
    val calculatedAt: Long, // the timestamp when we counted left time. Without it the timer will restart all over again on scroll of recycler view
    val amountModel: AmountModel
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnbondingModel

        if (amountModel != other.amountModel) return false

        when {
            timeLeft == 0L && other.timeLeft == 0L -> return true
            timeLeft == 0L && calculatedAt > other.timeLeft + other.calculatedAt -> return true
            timeLeft + calculatedAt == other.timeLeft + other.calculatedAt -> return true
            timeLeft != 0L && other.timeLeft == 0L -> return false
        }

        return true
    }

    override fun hashCode(): Int {
        return index
    }
}
