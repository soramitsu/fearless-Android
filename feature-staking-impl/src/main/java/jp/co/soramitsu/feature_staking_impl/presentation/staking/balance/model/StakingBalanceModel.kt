package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model

import jp.co.soramitsu.feature_wallet_api.presentation.model.AmountModel

class StakingBalanceModel(
    val bonded: AmountModel,
    val unbonding: AmountModel,
    val redeemable: AmountModel
)
