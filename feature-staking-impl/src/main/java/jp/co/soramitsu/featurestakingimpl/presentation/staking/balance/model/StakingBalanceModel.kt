package jp.co.soramitsu.featurestakingimpl.presentation.staking.balance.model

import jp.co.soramitsu.featurewalletapi.presentation.model.AmountModel

class StakingBalanceModel(
    val staked: AmountModel,
    val unstaking: AmountModel,
    val redeemable: AmountModel
)
