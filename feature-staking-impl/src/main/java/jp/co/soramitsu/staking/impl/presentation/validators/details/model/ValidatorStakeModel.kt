package jp.co.soramitsu.staking.impl.presentation.validators.details.model

class ValidatorStakeModel(
    val statusText: String,
    val statusColorRes: Int,
    val activeStakeModel: ActiveStakeModel?,
    val prefs: Prefs?
) {

    class ActiveStakeModel(
        val totalStake: String,
        val totalStakeFiat: String?,
        val nominatorsCount: String,
        val maxNominations: String?,
        val apy: String
    )

    class Prefs(
        val isBlocked: Boolean,
        val commission: String
    )
}
