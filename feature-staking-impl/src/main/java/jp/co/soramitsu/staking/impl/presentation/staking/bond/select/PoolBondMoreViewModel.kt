package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.StakeInsufficientBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.api.presentation.Validation
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

@HiltViewModel
class PoolBondMoreViewModel @Inject constructor(
    resourceManager: ResourceManager,
    stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseEnterAmountViewModel(
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_bond_more_v1_9_0,
    balanceHintRes = R.string.common_available_format,
    asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset),
    resourceManager = resourceManager,
    feeEstimator = stakingPoolInteractor::estimateBondMoreFee,
    onNextStep = { amount ->
        stakingPoolSharedStateProvider.manageState.get()?.copy(amountInPlanks = amount)?.let { stakingPoolSharedStateProvider.manageState.set(it) }
        router.openPoolConfirmBondMore()
    },
    validations = arrayOf(
        Validation(
            condition = {
                val asset = requireNotNull(stakingPoolSharedStateProvider.mainState.get()?.asset)
                val transferableInPlanks = asset.token.planksFromAmount(asset.transferable)
                it < transferableInPlanks
            },
            error = StakeInsufficientBalanceException(resourceManager)
        )
    ),
    errorAlertPresenter = {
        router.openAlert(it)
    }
) {
    fun onBackClick() {
        router.back()
    }
}
