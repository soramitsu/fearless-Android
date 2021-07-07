package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model

import jp.co.soramitsu.feature_wallet_api.presentation.model.AmountModel

data class UnbondingModel(
    val index: Int, // for DiffUtil to be able to distinguish unbondings with the same amount and days left
    val timeLeft: Long,
    val amountModel: AmountModel
)
