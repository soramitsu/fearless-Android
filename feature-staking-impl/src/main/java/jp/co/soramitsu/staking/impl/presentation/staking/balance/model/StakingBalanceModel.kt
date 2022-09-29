package jp.co.soramitsu.staking.impl.presentation.staking.balance.model

import jp.co.soramitsu.wallet.api.presentation.model.AmountModel

class StakingBalanceModel(
    val staked: AmountModel,
    val unstaking: AmountModel,
    val redeemable: AmountModel
)
