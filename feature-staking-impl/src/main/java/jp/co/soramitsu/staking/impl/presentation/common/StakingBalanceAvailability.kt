package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager

internal fun stakingBalanceUnavailable(resourceManager: ResourceManager) = ValidationException(
    resourceManager.getString(jp.co.soramitsu.common.R.string.common_error_general_title),
    resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable)
)

internal fun <T : Any> requireStakingBalance(amount: T?, resourceManager: ResourceManager): T =
    amount ?: throw stakingBalanceUnavailable(resourceManager)
