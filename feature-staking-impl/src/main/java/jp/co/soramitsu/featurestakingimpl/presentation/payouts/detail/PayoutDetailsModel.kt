package jp.co.soramitsu.featurestakingimpl.presentation.payouts.detail

import jp.co.soramitsu.common.address.AddressModel

class PayoutDetailsModel(
    val validatorAddressModel: AddressModel,
    val createdAt: Long,
    val eraDisplay: String,
    val reward: String,
    val rewardFiat: String?
)
