package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model

class ValidatorStakeModel(
    val statusText: String,
    val statusColorRes: Int,
    val activeStakeModel: ActiveStakeModel?,
) {

    class ActiveStakeModel(
        val totalStake: String,
        val totalStakeFiat: String?,
        val nominatorsCount: String,
        val maxNominations: String?,
        val apy: String
    )
}
