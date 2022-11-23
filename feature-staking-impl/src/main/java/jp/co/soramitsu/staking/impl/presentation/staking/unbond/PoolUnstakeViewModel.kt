package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.InsufficientStakeBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.api.presentation.Validation
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

@HiltViewModel
class PoolUnstakeViewModel @Inject constructor(
    resourceManager: ResourceManager,
    stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseEnterAmountViewModel(
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_unbond_v1_9_0,
    balanceHintRes = R.string.common_available_format,
    asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset),
    resourceManager = resourceManager,
    feeEstimator = {
        stakingPoolInteractor.estimateUnstakeFee(
            requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.address),
            it
        )
    },
    onNextStep = { amount ->
        stakingPoolSharedStateProvider.manageState.get()?.copy(amountInPlanks = amount)?.let { stakingPoolSharedStateProvider.manageState.set(it) }
        router.openPoolConfirmUnstake()
    },
    validations = arrayOf(
        Validation(
            condition = {
                val stakedInPlanks = requireNotNull(stakingPoolSharedStateProvider.manageState.get()?.stakedInPlanks)
                it <= stakedInPlanks
            },
            error = InsufficientStakeBalanceException(resourceManager)
        )
    ),
    availableAmountForOperation = { it.token.amountFromPlanks(stakingPoolSharedStateProvider.requireManageState.stakedInPlanks) }
) {
    fun onBackClick() {
        router.back()
    }
}
