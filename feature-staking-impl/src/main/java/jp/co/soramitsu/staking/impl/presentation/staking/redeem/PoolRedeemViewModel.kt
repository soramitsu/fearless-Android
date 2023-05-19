package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import javax.inject.Inject

@HiltViewModel
class PoolRedeemViewModel @Inject constructor(
    resourceManager: ResourceManager,
    stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseEnterAmountViewModel(
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_redeem,
    balanceHintRes = R.string.common_balance_format,
    asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset),
    initialAmount = requireNotNull(stakingPoolSharedStateProvider.manageState.get()?.redeemInPlanks).let {
        val asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset)
        asset.token.amountFromPlanks(it)
    },
    isInputActive = false,
    resourceManager = resourceManager,
    feeEstimator = { stakingPoolInteractor.estimateRedeemFee(requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.address)) },
    onNextStep = { router.openPoolConfirmRedeem() },
    errorAlertPresenter = {
        router.openAlert(it)
    }
) {
    fun onBackClick() {
        router.back()
    }
}
