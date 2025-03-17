package jp.co.soramitsu.staking.impl.domain.validations.setup

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingRelaychainScenarioViewModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class MinimumAmountValidation(
    private val stakingScenarioInteractor: StakingScenarioInteractor
) : Validation<SetupStakingPayload, SetupStakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<SetupStakingValidationFailure> {
        val assetConfiguration = value.asset.token.configuration

        val networkInfo = stakingScenarioInteractor.observeNetworkInfoState().map { it as? NetworkInfo.RelayChain }.firstOrNull()
        val minimumBondInPlanks = stakingScenarioInteractor.getMinimumStake(assetConfiguration)
        val minStakeMultiplier: Double = if (networkInfo?.shouldUseMinimumStakeMultiplier == true) {
            StakingRelaychainScenarioViewModel.STAKE_EXTRA_MULTIPLIER // 15% increase
        } else {
            1.0
        }

        val minimumBond = assetConfiguration.amountFromPlanks(minimumBondInPlanks) * BigDecimal(minStakeMultiplier)

        // either first time bond or already existing bonded balance
        val amountToCheckAgainstMinimum = value.bondAmount ?: value.asset.bonded.orZero()

        return if (amountToCheckAgainstMinimum >= minimumBond) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, SetupStakingValidationFailure.TooSmallAmount(minimumBond))
        }
    }
}
