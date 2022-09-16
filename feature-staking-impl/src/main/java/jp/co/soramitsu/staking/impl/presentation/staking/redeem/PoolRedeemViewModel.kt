package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

@HiltViewModel
class PoolRedeemViewModel @Inject constructor(
    resourceManager: ResourceManager,
    stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseEnterAmountViewModel(
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_redeem,
    asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset),
    initialAmount = requireNotNull(stakingPoolSharedStateProvider.manageState.get()?.redeemInPlanks).let {
        val asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset)
        asset.token.amountFromPlanks(it).format()
    },
    isInputActive = false,
    resourceManager = resourceManager,
    feeEstimator = { stakingPoolInteractor.estimateRedeemFee(requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.address)) },
    onNextStep = { }
) {
    fun onBackClick() {
        router.back()
    }
}
