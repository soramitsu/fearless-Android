package jp.co.soramitsu.common.validation

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.resources.ResourceManager

class StakeInsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.staking_setup_too_big_error)
)

class SpendInsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.choose_amount_error_too_big)
)

class ExistentialDepositCrossedException(resourceManager: ResourceManager) : ValidationWarning(
    resourceManager.getString(R.string.common_existential_warning_title),
    resourceManager.getString(R.string.common_existential_warning_message),
    resourceManager.getString(R.string.common_proceed),
    resourceManager.getString(R.string.common_cancel)
)

class TransferToTheSameAddressException(resourceManager: ResourceManager) : ValidationWarning(
    resourceManager.getString(R.string.common_warning),
    resourceManager.getString(R.string.same_address_transfer_warning_message),
    resourceManager.getString(R.string.common_proceed),
    resourceManager.getString(R.string.common_cancel)
)

class DeadRecipientException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_amount_low),
    resourceManager.getString(R.string.wallet_send_dead_recipient_message)
)

class InsufficientStakeBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.pool_staking_unstake_error)
)

class MinPoolCreationThresholdException(resourceManager: ResourceManager, minToCreateAmount: String) : ValidationException(
    resourceManager.getString(R.string.min_to_create_pool_threshold_error_title),
    resourceManager.getString(R.string.min_to_create_pool_threshold_error_message, minToCreateAmount)
)

class TransferAddressNotValidException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.address_not_valid_error_title),
    resourceManager.getString(R.string.transfer_address_not_valid_error_message)
)

class AddressNotValidException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.address_not_valid_error_title),
    resourceManager.getString(R.string.address_not_valid_error_message)
)

class WaitForFeeCalculationException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.fee_not_yet_loaded_title),
    resourceManager.getString(R.string.fee_not_yet_loaded_message)
)

class FeeInsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.common_not_enough_funds_message)
)

class AmountTooLowToStakeException(resourceManager: ResourceManager, minimumAmount: String) : ValidationException(
    resourceManager.getString(R.string.common_error_general_title),
    resourceManager.getString(R.string.staking_setup_amount_too_low, minimumAmount)
)
