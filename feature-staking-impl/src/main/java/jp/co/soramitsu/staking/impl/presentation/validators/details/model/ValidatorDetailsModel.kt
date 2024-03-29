package jp.co.soramitsu.staking.impl.presentation.validators.details.model

import android.graphics.drawable.PictureDrawable

class ValidatorDetailsModel(
    val stake: ValidatorStakeModel,
    val address: String,
    val addressImage: PictureDrawable,
    val identity: IdentityModel?
)

class CollatorDetailsModel(
    val address: String,
    val addressImage: PictureDrawable,
    val identity: IdentityModel?,
    val statusText: String,
    val statusColor: Int,
    val delegations: String,
    val estimatedRewardsApr: String,
    val totalStake: String?,
    val totalStakeFiat: String?,
    val minBond: String,
    val selfBonded: String,
    val effectiveAmountBonded: String
)
