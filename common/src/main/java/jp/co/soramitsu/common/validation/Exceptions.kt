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
