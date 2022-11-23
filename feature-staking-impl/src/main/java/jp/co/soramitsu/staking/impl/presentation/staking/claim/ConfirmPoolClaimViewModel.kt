package jp.co.soramitsu.staking.impl.presentation.staking.claim

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel

@HiltViewModel
class ConfirmPoolClaimViewModel @Inject constructor(
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    resourceManager: ResourceManager,
    private val router: StakingRouter
) : BaseConfirmViewModel(
    address = requireNotNull(poolSharedStateProvider.mainState.get()?.address),
    resourceManager = resourceManager,
    asset = requireNotNull(poolSharedStateProvider.mainState.get()?.asset),
    amountInPlanks = requireNotNull(poolSharedStateProvider.manageState.get()?.claimableInPlanks),
    feeEstimator = { stakingPoolInteractor.estimateClaimFee() },
    executeOperation = { address, _ -> stakingPoolInteractor.claim(address) },
    onOperationSuccess = { router.returnToManagePoolStake() },
    accountNameProvider = { stakingPoolInteractor.getAccountName(it) },
    titleRes = R.string.common_claim
) {
    fun onBackClick() {
        router.back()
    }
}
