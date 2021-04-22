package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model

import jp.co.soramitsu.feature_wallet_api.presentation.model.AmountModel

data class UnbondingModel(
    val daysLeft: String,
    val amountModel: AmountModel
)
