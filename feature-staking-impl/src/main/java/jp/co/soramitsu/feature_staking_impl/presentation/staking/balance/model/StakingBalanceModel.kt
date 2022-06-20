package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model

import jp.co.soramitsu.feature_wallet_api.presentation.model.AmountModel

class StakingBalanceModel(
    val staked: AmountModel,
    val unstaking: AmountModel,
    val redeemable: AmountModel
)
