package jp.co.soramitsu.feature_staking_impl.presentation.setup

import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter

private const val CURRENT_ICON_SIZE = 40

private val DEFAULT_AMOUNT = 10.toBigDecimal()

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class StakingReturns(
    val monthly: PeriodReturns,
    val yearly: PeriodReturns
)

class SetupStakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory
) : BaseViewModel()