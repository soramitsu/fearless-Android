package jp.co.soramitsu.feature_staking_impl.domain.setup.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToWalletAccount
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

class EnoughToPayFeesValidation(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) : Validation<SetupStakingPayload, StakingValidationFailure> {

    override suspend fun validate(value: SetupStakingPayload): ValidationStatus<StakingValidationFailure> {
        val account = accountRepository.getAccount(value.stashSetup.controllerAddress)
        val asset = walletRepository.getAsset(mapAccountToWalletAccount(account), value.tokenType)!!

        return if (value.amount + value.maxFee < asset.transferable) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, StakingValidationFailure.CannotPayFee)
        }
    }
}
