package jp.co.soramitsu.common.validation

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager

class InsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.staking_setup_too_big_error)
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
