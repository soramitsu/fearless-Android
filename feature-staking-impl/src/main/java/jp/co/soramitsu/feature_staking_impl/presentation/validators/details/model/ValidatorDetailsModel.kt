package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model

class ValidatorDetailsModel(
    val totalStake: String,
    val totalStakeFiat: String?,
    val address: String,
    val identity: IdentityModel?,
    val nominatorsCount: String,
    val apy: String
)