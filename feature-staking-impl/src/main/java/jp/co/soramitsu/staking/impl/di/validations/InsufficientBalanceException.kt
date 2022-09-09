package jp.co.soramitsu.staking.impl.di.validations

import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R

class InsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.staking_setup_too_big_error)
)
