package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel

@HiltViewModel
class PoolBondMoreViewModel @Inject constructor(
    resourceManager: ResourceManager,
    stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseEnterAmountViewModel(
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_bond_more_v1_9_0,
    asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset),
    resourceManager = resourceManager,
    feeEstimator = stakingPoolInteractor::estimateBondMoreFee,
    onNextStep = { amount ->
        stakingPoolSharedStateProvider.manageState.get()?.copy(amountInPlanks = amount)?.let { stakingPoolSharedStateProvider.manageState.set(it) }
        router.openPoolConfirmBondMore()
    }
) {
    fun onBackClick() {
        router.back()
    }
}
