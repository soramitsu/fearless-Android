package jp.co.soramitsu.staking.impl.domain.validations

import java.math.BigDecimal
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.state.chain

class NotZeroBalanceValidation(
    private val walletRepository: WalletRepository,
    private val stakingSharedState: StakingSharedState
) : Validation<SetControllerValidationPayload, SetControllerValidationFailure> {

    override suspend fun validate(value: SetControllerValidationPayload): ValidationStatus<SetControllerValidationFailure> {
        val chain = stakingSharedState.chain()

        val controllerBalance = walletRepository.getAccountFreeBalance(chain.id, chain.accountIdOf(value.controllerAddress)).toBigDecimal()

        return if (controllerBalance > BigDecimal.ZERO) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.WARNING, SetControllerValidationFailure.ZERO_CONTROLLER_BALANCE)
        }
    }
}
