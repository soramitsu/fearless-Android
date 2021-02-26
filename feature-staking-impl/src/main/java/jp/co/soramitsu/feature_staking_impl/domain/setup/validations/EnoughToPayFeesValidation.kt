package jp.co.soramitsu.feature_staking_impl.domain.setup.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevels
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToWalletAccount
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

class EnoughToPayFeesValidation(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val maxFeeEstimator: MaxFeeEstimator
) : Validation<SetupStakingPayload, StakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<StakingValidationFailure> {
        val account = accountRepository.getAccount(value.accountAddress)
        val asset = walletRepository.getAsset(mapAccountToWalletAccount(account), value.token.type)!!

        val fee = maxFeeEstimator.estimateMaxSetupStakingFee(account, value.token.type)

        return if (value.amount + fee < asset.transferable) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevels.ERROR, StakingValidationFailure.CANNOT_PAY_FEE)
        }
    }
}